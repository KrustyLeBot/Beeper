package app.vigie

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vigie.data.Account
import app.vigie.links.Links
import app.vigie.links.Target
import app.vigie.suite.SuiteUpdates
import app.vigie.sync.SkipReason
import app.vigie.sync.SyncState
import app.vigie.sync.SynchrotronClient
import app.vigie.web.Tabs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

enum class Screen { Main, Settings, Extension }

/** A link waiting for its tab: [key] is an account id or [Tabs.TEMPORARY]. */
data class PendingLink(val key: String, val url: String)

class VigieViewModel(app: Application) : AndroidViewModel(app) {
    private val vigie = app as VigieApp
    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.Main)
    /** The extension page [Screen.Extension] shows (uBlock's dashboard, Consent-O-Matic's settings). */
    var extensionPage by mutableStateOf<Pair<String, String>?>(null)

    var storeError by mutableStateOf<String?>(null)
        private set
    var accounts by mutableStateOf(readAccounts())
        private set
    /** The shown tab: an account id or [Tabs.TEMPORARY]; device-only, never synced. */
    var selectedKey by mutableStateOf(prefs.getString(KEY_SELECTED, null))
        private set

    /** The account shown when the temporary browser is not (the screen decides between the two), or null with no account. */
    val selected: Account?
        get() = accounts.firstOrNull { it.id == selectedKey } ?: accounts.firstOrNull()

    /** The link the screen must open next, once the blockers are in. */
    var pendingLink by mutableStateOf<PendingLink?>(null)

    val updates = SuiteUpdates(app, vigie.updater, viewModelScope)

    private val noticeChannel = Channel<String>(Channel.CONFLATED)
    /** Short messages, shown as snackbars. */
    val notices = noticeChannel.receiveAsFlow()

    // --- Proton Drive sync through Synchrotron ---

    /** A sync is running: the account list is read-only until it ends (browsing goes on). */
    var syncing by mutableStateOf(false)
        private set
    /** The app may not read and write `Documents/Vigie` yet: ask for it. */
    var needsStorageAccess by mutableStateOf(!vigie.accountStore.hasStorageAccess())
        private set
    private var stopSync: (() -> Unit)? = null

    val syncFolder = "Documents/Vigie"

    fun show(text: String) {
        noticeChannel.trySend(text)
    }

    // --- Links ---

    /** A link from another app (WhatsApp, a share): its account if the user has one, else the temporary browser. */
    fun openLink(raw: String) {
        val url = Links.clean(raw)
        route(Links.route(url, accounts.map { it.owner }), url)
    }

    /** Opens [url] where [target] says, and shows that tab. */
    fun route(target: Target, url: String) {
        val key = when (target) {
            is Target.Account -> target.id
            Target.Temporary -> Tabs.TEMPORARY
        }
        screen = Screen.Main
        select(key)
        pendingLink = PendingLink(key, url)
    }

    // --- Accounts ---

    private fun readAccounts(): List<Account> = try {
        vigie.accountStore.load().also { storeError = null }
    } catch (e: Exception) {
        storeError = e.message
        emptyList()
    }

    /** Re-reads the list, e.g. after a sync brought another device's changes. */
    private fun reloadAccounts() {
        accounts = readAccounts()
    }

    /**
     * Checks the folder permission (granted from Android's settings, so re-checked each time the app
     * comes back), then asks Synchrotron for a sync so the account list is up to date.
     */
    fun syncOnOpen() {
        if (syncing) return
        val granted = vigie.accountStore.hasStorageAccess()
        if (granted && needsStorageAccess) reloadAccounts()
        needsStorageAccess = !granted
        if (!granted) return
        // So the folder can be picked in Synchrotron before the first account is saved there.
        File(vigie.accountStore.syncPath).mkdirs()
        stopSync?.invoke()
        stopSync = SynchrotronClient.syncOnOpen(vigie, vigie.accountStore.syncPath) { state ->
            when (state) {
                SyncState.Syncing -> syncing = true
                is SyncState.Done -> {
                    syncing = false
                    if (state.changes > 0) reloadAccounts()
                    if (state.changes > 0) show("Comptes synchronisés")
                }
                is SyncState.Failed -> { syncing = false; show("Échec de la synchro") }
                SyncState.TimedOut -> { syncing = false; show("Synchro longue : elle continue en arrière-plan") }
                is SyncState.Skipped -> {
                    syncing = false
                    if (state.reason == SkipReason.NEEDS_SETUP) show("Termine la configuration dans Synchrotron")
                }
            }
        }
    }

    /** True when the list cannot be changed now (no folder access, sync running, unreadable file), with the reason shown. */
    fun readOnly(): Boolean {
        when {
            needsStorageAccess -> show("Autorise d'abord l'accès aux fichiers (bandeau en haut)")
            syncing -> show("Synchronisation en cours…")
            storeError != null -> show("accounts.json est illisible : corrige-le ou supprime-le")
            else -> return false
        }
        return true
    }

    override fun onCleared() {
        stopSync?.invoke()
    }

    fun select(key: String) {
        selectedKey = key
        prefs.edit().putString(KEY_SELECTED, key).apply()
    }

    /** Adds [account], or replaces the account with the same id. One link default per network at most. */
    fun upsert(account: Account): Boolean {
        if (readOnly()) return false
        val index = accounts.indexOfFirst { it.id == account.id }
        var list = if (index >= 0) accounts.toMutableList().also { it[index] = account } else accounts + account
        if (account.linkDefault) {
            list = list.map { other -> if (other.id != account.id && other.linkDefault && Links.owns(other.owner, account.host)) other.copy(linkDefault = false) else other }
        }
        if (!save(list)) return false
        if (index < 0) select(account.id)
        return true
    }

    fun remove(account: Account): Boolean {
        if (readOnly()) return false
        if (!save(accounts.filter { it.id != account.id })) return false
        show("${account.name} supprimé")
        return true
    }

    /** Live move while a tab is dragged; saved once by [orderChanged]. */
    fun move(from: Int, to: Int) {
        if (syncing || storeError != null) return
        if (from !in accounts.indices || to !in accounts.indices) return
        accounts = accounts.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun orderChanged() {
        if (readOnly()) {
            reloadAccounts()
            return
        }
        save(accounts)
    }

    private fun save(list: List<Account>): Boolean = try {
        vigie.accountStore.save(list)
        accounts = list
        true
    } catch (e: Exception) {
        show("Enregistrement impossible : ${e.message}")
        reloadAccounts()
        false
    }

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
