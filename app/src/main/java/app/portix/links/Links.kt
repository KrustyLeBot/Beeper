package app.portix.links

import java.net.URI
import java.net.URLDecoder

/** Where a link opens: in one of the user's accounts, or in the temporary browser (no account, nothing kept). */
sealed interface Target {
    data class Account(val id: String) : Target
    data object Temporary : Target
}

/** An account as the router sees it. */
data class Owner(val id: String, val host: String, val linkDefault: Boolean)

/**
 * Link routing, the app's whole point: a link to a network opens in the user's account for that
 * network, anything else in the temporary browser.
 */
object Links {
    // Link shims that wrap the real address in a parameter (and log the click).
    private val SHIMS = mapOf(
        "l.instagram.com" to "u", "l.facebook.com" to "u", "lm.facebook.com" to "u", "l.messenger.com" to "u",
        "www.google.com" to "q", "google.com" to "q", "www.youtube.com" to "q", "m.youtube.com" to "q",
    )
    private val SHIM_PATHS = mapOf("www.google.com" to "/url", "google.com" to "/url", "www.youtube.com" to "/redirect", "m.youtube.com" to "/redirect")

    // Parameters that only say who shared what with whom: on every site, then per network (`t` is X's
    // share tracker but YouTube's start time, so it is only dropped where it is a tracker).
    private val TRACKING = setOf("fbclid", "gclid", "dclid", "msclkid", "mibextid")
    private val TRACKING_BY_NETWORK = mapOf(
        "instagram" to setOf("igshid", "igsh", "img_index"),
        "tiktok" to setOf("_t", "_r", "is_from_webapp", "sender_device", "share_app_id", "share_link_id", "social_sharing", "share_item_id"),
        "x" to setOf("s", "t", "ref_src", "ref_url"),
        "youtube" to setOf("si", "feature", "pp"),
        "reddit" to setOf("share_id", "rdt", "ref", "ref_source", "context"),
        "facebook" to setOf("sfnsn", "extid", "rdid", "share_url"),
        "linkedin" to setOf("trk", "trackingId", "rcm", "lipi"),
    )

    // Sign-in pages that must stay in the tab that opened them, whatever their domain.
    private val AUTH_HOSTS = setOf(
        "accounts.google.com", "accounts.youtube.com", "appleid.apple.com", "login.microsoftonline.com", "login.live.com",
        "api.twitter.com", "api.x.com", "challenges.cloudflare.com",
    )
    private val AUTH_WORDS = listOf("oauth", "login", "signin", "sign_in", "auth", "consent", "checkpoint", "two_factor", "challenge")

    fun host(url: String): String? = try { URI(url).host?.lowercase() } catch (e: Exception) { null }

    /** The real address behind a shim, without its tracking parameters, Instagram's web-view flag added. */
    fun clean(url: String): String = instagramWeb(stripped(url))

    /**
     * Instagram shows a reel or a post behind an "open the app / see on the web" page, unless the link
     * carries `l=1`, which its own « Voir sur le web » button adds.
     */
    fun instagramWeb(url: String): String {
        val uri = try { URI(url) } catch (e: Exception) { return url }
        if (Networks.of(uri.host)?.id != "instagram" || uri.host == "l.instagram.com") return url
        val path = uri.path.orEmpty()
        if (path.isEmpty() || path == "/" || path.startsWith("/accounts/")) return url
        val query = uri.rawQuery
        if (query != null && query.split('&').any { it == "l=1" || it.startsWith("l=") }) return url
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        val base = url.substringBefore('#').substringBefore('?')
        return if (query.isNullOrEmpty()) "$base?l=1$fragment" else "$base?$query&l=1$fragment"
    }

    private fun stripped(url: String): String {
        val uri = try { URI(url) } catch (e: Exception) { return url }
        val host = uri.host?.lowercase() ?: return url
        SHIMS[host]?.let { param ->
            val path = SHIM_PATHS[host]
            if (path == null || uri.path == path) {
                query(uri.rawQuery)[param]?.let { inner -> if (inner.startsWith("http")) return stripped(inner) }
            }
        }
        val raw = uri.rawQuery ?: return url
        val dropped = TRACKING + TRACKING_BY_NETWORK[Networks.of(host)?.id].orEmpty().map { it.lowercase() }
        val kept = raw.split('&').filter { part ->
            val key = URLDecoder.decode(part.substringBefore('='), "UTF-8").lowercase()
            !(key in dropped || key.startsWith("utm_"))
        }
        val base = url.substringBefore('?')
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        return if (kept.isEmpty()) base.substringBefore('#') + fragment else "$base?${kept.joinToString("&")}".substringBefore('#') + fragment
    }

    /** Where a link opened from outside (WhatsApp, a share) goes. */
    fun route(url: String, owners: List<Owner>): Target {
        val host = host(url) ?: return Target.Temporary
        val candidates = owners.filter { owns(it, host) }
        val pick = candidates.firstOrNull { it.linkDefault } ?: candidates.firstOrNull() ?: return Target.Temporary
        return Target.Account(pick.id)
    }

    /**
     * A navigation inside a tab ([current] null: the temporary browser): null to let it load there,
     * or where it should go instead. Only what the user tapped is moved; redirects and the sign-in
     * pages a site sends you to stay, or logging in with Google or Facebook would break.
     */
    fun inTab(current: Owner?, url: String, byUser: Boolean, isRedirect: Boolean, owners: List<Owner>): Target? {
        if (!byUser || isRedirect) return null
        val host = host(url) ?: return null
        if (SHIMS.containsKey(host)) {
            val inner = clean(url)
            if (inner != url) return route(inner, owners).takeIf { it != here(current) } ?: return null
        }
        if (current != null && owns(current, host)) return null
        if (host in AUTH_HOSTS || isSignIn(url)) return null
        val target = route(url, owners)
        return if (target == here(current)) null else target
    }

    private fun here(current: Owner?): Target = current?.let { Target.Account(it.id) } ?: Target.Temporary

    /** The account [owner] takes links to [host]: same network, or same site for an account outside the catalogue. */
    fun owns(owner: Owner, host: String): Boolean {
        val mine = Networks.of(owner.host)
        val theirs = Networks.of(host)
        return if (mine != null) mine == theirs else theirs == null && registrable(host) == registrable(owner.host)
    }

    private fun isSignIn(url: String): Boolean {
        val path = try { URI(url).path.orEmpty().lowercase() } catch (e: Exception) { return false }
        return AUTH_WORDS.any { path.contains(it) }
    }

    fun registrable(host: String): String {
        val labels = host.lowercase().trimEnd('.').split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val keep = if (labels[labels.size - 2] in setOf("co", "com", "org", "net", "gouv", "ac") && labels.last().length == 2) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }

    /** The first web address in a shared text ("Regarde ça https://vm.tiktok.com/… !"). */
    fun firstUrl(text: String): String? = Regex("https?://[^\\s<>\"']+").find(text)?.value?.trimEnd('.', ',', ')', '!', '?', ';')

    private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
}
