package app.beeper.web

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** An extension from addons.mozilla.org that every tab runs. */
class ExtensionSpec(val id: String, val slug: String, val title: String, val role: String) {
    val url get() = "https://addons.mozilla.org/firefox/downloads/latest/$slug/latest.xpi"
}

/**
 * The extensions that do the blocking, installed from addons.mozilla.org (signed by Mozilla) the
 * first time the app starts and updated by their authors' releases: uBlock Origin (ads, trackers,
 * app banners, chat widgets, cookie notices, through its own filter lists, which it keeps up to date
 * itself) and Consent-O-Matic (answers cookie consent pop-ups with "refuse").
 *
 * Their buttons (the per-site switch and uBlock's element picker live there) open as popups:
 * [popup] is the page to show, null when none is open.
 */
class Extensions(private val context: Context, private val runtime: GeckoRuntime, private val onReady: () -> Unit = {}) {
    private val controller = runtime.webExtensionController
    private val prefs = context.getSharedPreferences("extensions", Context.MODE_PRIVATE)
    // Each extension's button, per tab and by default.
    private val defaultActions = HashMap<String, WebExtension.Action>()
    private val tabActions = HashMap<Pair<String, GeckoSession>, WebExtension.Action>()

    val installed = mutableStateMapOf<String, WebExtension>()

    /** True once every extension had its chance to install: until then no tab loads. */
    var ready by mutableStateOf(false)
        private set
    var installing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var popup by mutableStateOf<GeckoSession?>(null)
        private set

    /** The user said uBlock's two one-time settings (app banners list, annoyances) are done. */
    var setupDone by mutableStateOf(prefs.getBoolean(KEY_SETUP, false))
        private set

    fun markSetupDone() {
        setupDone = true
        prefs.edit().putBoolean(KEY_SETUP, true).apply()
    }

    /** Short messages for the user (an extension still starting). */
    var onNotice: (String) -> Unit = {}

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            if (session == null) defaultActions[extension.id] = action else tabActions[extension.id to session] = action
        }

        override fun onTogglePopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> = openPopup()

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession> = openPopup()
    }

    // Extensions do not open tabs of their own here (welcome pages, links from a popup).
    private val tabDelegate = object : WebExtension.TabDelegate {
        override fun onNewTab(source: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? = null
    }

    fun start() {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollection: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                // Only the two extensions below are ever installed: their permissions are accepted, private
                // browsing included (the temporary browser is private).
                GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, false))

            override fun onUpdatePrompt(
                extension: WebExtension, permissions: Array<String>, origins: Array<String>, dataCollection: Array<String>,
            ): GeckoResult<AllowOrDeny> = GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }
        // Already installed on an earlier start: tabs may load at once. Gecko starts the extensions with
        // the engine, and uBlock Origin holds the network until its filter lists are loaded.
        if (prefs.getBoolean(KEY_INSTALLED, false)) markReady()
        controller.list().accept({ list ->
            list.orEmpty().filter { ext -> SPECS.any { it.id == ext.id } }.forEach(::adopt)
            install(onlyMissing = true)
        }, { install(onlyMissing = true) })
    }

    /** Installs the missing extensions; with [onlyMissing] false, also looks for updates. */
    fun install(onlyMissing: Boolean = false) {
        if (installing) return
        installing = true
        error = null
        val failed = ArrayList<String>()
        val pending = SPECS.toMutableList()
        fun next() {
            val spec = pending.removeFirstOrNull()
            if (spec == null) {
                installing = false
                markReady()
                refresh()
                if (SPECS.all { it.id in installed }) prefs.edit().putBoolean(KEY_INSTALLED, true).apply()
                if (failed.isNotEmpty()) error = "Installation impossible : ${failed.joinToString(", ")}. Vérifie la connexion."
                else if (!onlyMissing) prefs.edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
                return
            }
            val have = installed[spec.id]
            val result = when {
                have == null -> controller.install(spec.url)
                onlyMissing -> null
                else -> controller.update(have)
            }
            if (result == null) return next()
            result.accept({ ext -> ext?.let(::adopt); next() }, { failed += spec.title; next() })
        }
        next()
    }

    private fun markReady() {
        if (ready) return
        ready = true
        onReady()
    }

    /** Looks for new versions every few days, as Firefox does. */
    fun updateIfStale() {
        if (ready && System.currentTimeMillis() - prefs.getLong(KEY_UPDATED, 0) > UPDATE_EVERY_MS) install(onlyMissing = false)
    }

    /** Gives a new tab the extensions' buttons. */
    fun attach(session: GeckoSession) {
        installed.values.forEach { session.webExtensionController.setActionDelegate(it, actionDelegate) }
    }

    fun detach(session: GeckoSession) {
        tabActions.keys.removeAll { it.second == session }
    }

    /** Presses the extension's button for [session], which opens its popup. */
    fun openButton(spec: ExtensionSpec, session: GeckoSession) {
        val default = defaultActions[spec.id]
        val action = tabActions[spec.id to session]?.let { if (default != null) it.withDefault(default) else it } ?: default
        if (action == null) return starting(spec)
        action.click()
    }

    /**
     * The extension's own settings page (uBlock's dashboard: filter lists, my filters…), or [page]
     * inside the extension. Null, with a message, while the extension is still starting: right after
     * its installation its `moz-extension://` address is not known yet (uBlock prepares its lists for
     * a minute or two the first time).
     */
    fun optionsUrl(spec: ExtensionSpec, page: String? = null): String? {
        val ext = installed[spec.id] ?: run { onNotice("${spec.title} n'est pas installé"); return null }
        val options = ext.metaData.optionsPageUrl?.takeIf { it.startsWith(SCHEME) }
        val base = ext.metaData.baseUrl.takeIf { it.startsWith(SCHEME) } ?: options?.let { it.substringBeforeLast('/') + "/" }
        if (base == null) {
            starting(spec)
            return null
        }
        return if (page != null) base + page else options ?: base
    }

    private fun starting(spec: ExtensionSpec) {
        refresh()
        onNotice("${spec.title} démarre encore : réessaie dans un instant")
    }

    /** Reads the installed extensions again: their addresses and buttons appear once they have started. */
    private fun refresh() {
        controller.list().accept({ list -> list.orEmpty().filter { ext -> SPECS.any { it.id == ext.id } }.forEach(::adopt) }, {})
    }

    fun closePopup() {
        popup?.close()
        popup = null
    }

    private fun openPopup(): GeckoResult<GeckoSession> {
        closePopup()
        val session = GeckoSession()
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCloseRequest(session: GeckoSession) {
                if (popup == session) closePopup()
            }
        }
        // GeckoView only loads the popup page into it: opening is ours.
        session.open(runtime)
        popup = session
        return GeckoResult.fromValue(session)
    }

    private fun adopt(ext: WebExtension) {
        installed[ext.id] = ext
        ext.setActionDelegate(actionDelegate)
        ext.setTabDelegate(tabDelegate)
    }

    companion object {
        private const val SCHEME = "moz-extension://"
        private const val KEY_INSTALLED = "installed"
        private const val KEY_UPDATED = "updated"
        private const val KEY_SETUP = "ublock_setup_done"
        private const val UPDATE_EVERY_MS = 4 * 24 * 60 * 60 * 1000L

        val UBLOCK = ExtensionSpec("uBlock0@raymondhill.net", "ublock-origin", "uBlock Origin", "Pubs, traqueurs, bandeaux, widgets")
        val CONSENT = ExtensionSpec("gdpr@cavi.au.dk", "consent-o-matic", "Consent-O-Matic", "Refus automatique des cookies")
        val SPECS = listOf(UBLOCK, CONSENT)

        /** "Open in the app" banners: in no list of uBlock's catalogue, Amazon's included. */
        val MOBILE_BANNERS = "Fanboy's Mobile Notifications" to "https://secure.fanboy.co.nz/fanboy-mobile-notifications.txt"

        /** uBlock's own page for subscribing to a list by its address: one tap on « S'abonner ». */
        fun subscribePage(list: Pair<String, String>): String =
            "asset-viewer.html?url=${Uri.encode(list.second)}&title=${Uri.encode(list.first)}&subscribe=1"
    }
}
