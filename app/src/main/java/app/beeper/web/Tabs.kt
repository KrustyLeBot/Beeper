package app.beeper.web

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.beeper.Startup
import app.beeper.data.Account
import app.beeper.links.Links
import app.beeper.links.Owner
import app.beeper.links.Target
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/** What the UI shows about one tab. */
class TabState {
    var progress by mutableIntStateOf(100)
    var url by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
}

/**
 * The open tabs: one Gecko session per account, created the first time it is needed and kept so that
 * switching tabs never reloads a page, plus the temporary browser ([TEMPORARY]).
 *
 * Each account browses in its own context (`account_<id>`): its own cookies, storage and cache. The
 * temporary browser is a private session: no account, nothing written to disk, all forgotten when it
 * closes. A link tapped in a tab that belongs to another network, or to no account, is handed to
 * [onRoute] instead of loading there (see [Links.inTab]), so each network only ever sees its own
 * pages. Nothing a page does can open another app (see [Navigation]).
 */
class Tabs(
    private val activity: Activity,
    private val runtime: GeckoRuntime,
    private val extensions: Extensions,
    private val prompts: Prompts,
    private val downloads: Downloads,
    private val owners: () -> List<Owner>,
    private val onRoute: (Target, String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val startup: Startup,
) {
    private val sessions = HashMap<String, GeckoSession>()
    private val configs = HashMap<String, Account>()
    private val states = HashMap<String, TabState>()
    private val lastPages = activity.getSharedPreferences("last_pages", Context.MODE_PRIVATE)
    private var shown: String? = null
    private val main = Handler(Looper.getMainLooper())
    // Addresses waiting for their tab to be on screen: a page loaded in a hidden tab starts "hidden"
    // and some players (Instagram's reels) then never autoplay.
    private val pending = HashMap<String, String>()
    // Invisible windows opened by window.open(), kept until they get their address.
    private val catchers = HashSet<GeckoSession>()

    /** True while the temporary browser has a page open (it then shows as a tab). */
    var temporaryOpen by mutableStateOf(false)
        private set

    /** Bumped when a tab was closed after a crash, so the screen shows it again. */
    var restarted by mutableIntStateOf(0)
        private set

    fun state(key: String): TabState = states.getOrPut(key) { TabState() }

    fun session(key: String): GeckoSession? = sessions[key]

    /**
     * Puts the tab [key] ([account] null for the temporary browser) in [view], creating it on first use,
     * then loads what waits for it: a page only ever loads in a tab that is on screen and active.
     */
    fun show(key: String, account: Account?, view: GeckoView) {
        val existing = sessions[key]
        if (existing == null && account == null && key !in pending) return
        val session = existing?.also { if (account != null) apply(it, account) } ?: create(key, account)
        if (shown != key) {
            shown?.let { old -> sessions[old]?.let { deactivate(it) } }
            shown = key
        }
        if (view.session !== session) {
            view.session?.let { view.releaseSession() }
            view.setSession(session)
        }
        session.setActive(true)
        session.setFocused(true)
        runtime.webExtensionController.setTabActive(session, true)
        val url = pending.remove(key) ?: if (existing == null) account?.let { startUrl(it) } else null
        url?.let { session.loadUri(it) }
    }

    /**
     * Loads [url] in the tab [key] ([account] null: the temporary browser). Straight away when that tab
     * is on screen; otherwise it waits for [show], which the screen calls once it selected the tab.
     */
    fun open(key: String, account: Account?, url: String) {
        val session = sessions[key]
        if (session != null && shown == key) {
            session.loadUri(url)
            return
        }
        pending[key] = url
        if (session == null) create(key, account)
        if (key == TEMPORARY) temporaryOpen = true
    }

    /** Closes the temporary browser: being private, everything it saw is forgotten. */
    fun closeTemporary() {
        close(TEMPORARY)
        states.remove(TEMPORARY)
        temporaryOpen = false
    }

    fun goBack(key: String) = sessions[key]?.goBack()

    fun reload(key: String) = sessions[key]?.reload()

    fun home(account: Account) = sessions[account.id]?.loadUri(account.url)

    /**
     * Closes the tabs of accounts no longer in the list (e.g. removed on another phone through the sync)
     * and returns the page each one showed, so the one on screen can carry on elsewhere. Their cookies
     * stay: the list may only be unreadable for a moment, and a login must not go with it.
     */
    fun retain(ids: Set<String>): Map<String, String> {
        val gone = sessions.keys - ids - TEMPORARY
        return gone.associateWith { key -> state(key).url.also { close(key); states.remove(key) } }
    }

    /** Forgets the account entirely: its tab, its cookies, storage and cache, its last page. */
    fun remove(id: String) {
        close(id)
        states.remove(id)
        lastPages.edit().remove(id).apply()
        runtime.storageController.clearDataForSessionContext(contextId(id))
    }

    /** Logs the account out: its cookies, storage and cache only. */
    fun clearData(account: Account) {
        runtime.storageController.clearDataForSessionContext(contextId(account.id))
        sessions[account.id]?.apply { purgeHistory(); loadUri(account.url) }
    }

    fun onStop() {
        sessions.values.forEach { it.setActive(false) }
    }

    fun onStart() {
        shown?.let { sessions[it]?.setActive(true) }
    }

    fun closeAll() {
        sessions.keys.toList().forEach { close(it) }
        temporaryOpen = false
    }

    private fun deactivate(session: GeckoSession) {
        session.setFocused(false)
        session.setActive(false)
        runtime.webExtensionController.setTabActive(session, false)
    }

    private fun close(key: String) {
        val session = sessions.remove(key) ?: return
        configs.remove(key)
        pending.remove(key)
        if (shown == key) shown = null
        extensions.detach(session)
        session.close()
    }

    /** A new tab, opened but empty: its first page is loaded by [show]. */
    private fun create(key: String, account: Account?): GeckoSession {
        val desktop = account?.desktop == true
        val builder = GeckoSessionSettings.Builder()
            .useTrackingProtection(true)
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
        if (account != null) builder.contextId(contextId(account.id)) else builder.usePrivateMode(true)
        val session = GeckoSession(builder.build())
        delegates(session, key)
        session.open(runtime)
        extensions.attach(session)
        sessions[key] = session
        account?.let { configs[key] = it }
        if (key == TEMPORARY) temporaryOpen = true
        return session
    }

    private fun apply(session: GeckoSession, account: Account) {
        val old = configs[account.id] ?: return
        configs[account.id] = account
        if (old.desktop != account.desktop) {
            session.settings.userAgentMode = if (account.desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            session.settings.viewportMode = if (account.desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        when {
            old.url != account.url -> { lastPages.edit().remove(account.id).apply(); session.loadUri(account.url) }
            old.desktop != account.desktop -> session.reload()
        }
    }

    /** The tab's last page when it still belongs to the account, else the account's address. */
    private fun startUrl(account: Account): String {
        val last = lastPages.getString(account.id, null) ?: return account.url
        val host = Uri.parse(last).host ?: return account.url
        return if (Links.owns(account.owner, host)) last else account.url
    }

    /** The account owning the tab [key], or null for the temporary browser. */
    private fun ownerOf(key: String): Owner? = configs[key]?.owner

    private fun delegates(session: GeckoSession, key: String) {
        val state = state(key)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                decide(session, request.uri, request.hasUserGesture)?.let { return GeckoResult.fromValue(it) }
                // A tapped link that belongs elsewhere (another network, no account) leaves this tab.
                val elsewhere = Links.inTab(ownerOf(key), request.uri, request.hasUserGesture, request.isRedirect, owners())
                if (elsewhere != null) {
                    val url = Links.clean(request.uri)
                    main.post { onRoute(elsewhere, url) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                // An Instagram reel or post opened without `l=1` would stop on the "open the app" page.
                val web = Links.instagramWeb(request.uri)
                if (web != request.uri) {
                    main.post { session.loadUri(web) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                // A web page meant for a new window (target=_blank) opens in this tab instead.
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW && request.uri.startsWith("http")) {
                    main.post { session.loadUri(request.uri) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            // Frames (ad slots included) get the same rule, silently: only web pages, never an app or the store.
            override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> =
                GeckoResult.fromValue(if (Navigation.decide(request.uri) == Decision.Allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)

            /**
             * `window.open()` with no address (an "open in the app" banner does this, then sends the
             * new window to the app): an invisible window that takes the first real address it is
             * given, routes it like a tapped link, refuses an app link, and closes.
             */
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                val catcher = GeckoSession(GeckoSessionSettings.Builder(session.settings).build())
                catchers += catcher
                catcher.navigationDelegate = object : GeckoSession.NavigationDelegate {
                    override fun onLoadRequest(s: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny> {
                        if (request.uri == "about:blank") return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                        if (decide(session, request.uri, true) == null) {
                            val elsewhere = Links.inTab(ownerOf(key), request.uri, true, false, owners())
                            val url = Links.clean(request.uri)
                            main.post { if (elsewhere != null) onRoute(elsewhere, url) else session.loadUri(url) }
                        }
                        main.post { catchers.remove(catcher); catcher.close() }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                }
                // Nothing came: the window is dropped.
                main.postDelayed({ if (catchers.remove(catcher)) catcher.close() }, 30_000)
                return GeckoResult.fromValue(catcher)
            }

            override fun onLocationChange(session: GeckoSession, url: String?, perms: MutableList<ContentPermission>, hasUserGesture: Boolean) {
                url ?: return
                state.url = url
                val account = configs[key] ?: return
                val host = Uri.parse(url).host ?: return
                if (url.startsWith("http") && Links.owns(account.owner, host)) lastPages.edit().putString(key, url).apply()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                state.canGoBack = canGoBack
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                startup.mark(Startup.PAGE)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                state.progress = progress
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                state.progress = 100
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                downloads.save(response)
            }

            override fun onFirstContentfulPaint(session: GeckoSession) {
                startup.mark(Startup.PAINT)
            }

            override fun onCrash(session: GeckoSession) = restart(key)

            override fun onKill(session: GeckoSession) = restart(key)
        }
        session.promptDelegate = prompts
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            // GeckoView asks before any video starts on its own, and stores the answer for the site for
            // good. Muted autoplay (reels, feeds) is allowed, as in Firefox; with sound it waits for a tap.
            // Location, camera, microphone, notifications…: refused, a feed needs none of them.
            override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int> =
                GeckoResult.fromValue(
                    if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE) ContentPermission.VALUE_ALLOW
                    else ContentPermission.VALUE_DENY,
                )

            override fun onMediaPermissionRequest(
                session: GeckoSession, uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) = callback.reject()
        }
    }

    /**
     * What [Navigation] says about [uri] for a page in [session]: null to let it load, DENY when it was
     * refused (with a message if the user tapped) or redirected to its web fallback.
     */
    private fun decide(session: GeckoSession, uri: String, byUser: Boolean): AllowOrDeny? = when (val d = Navigation.decide(uri)) {
        Decision.Allow -> null
        is Decision.Redirect -> { main.post { session.loadUri(d.url) }; AllowOrDeny.DENY }
        is Decision.Block -> { if (byUser) onNotice(d.reason); AllowOrDeny.DENY }
    }

    /** The content process died: the tab is closed, and rebuilt (accounts on their last page) when shown again. */
    private fun restart(key: String) {
        close(key)
        if (key == TEMPORARY) temporaryOpen = false
        onNotice("La page a planté : elle est rechargée")
        restarted++
    }

    companion object {
        const val TEMPORARY = "temporary"

        private fun contextId(id: String) = "account_$id"
    }
}
