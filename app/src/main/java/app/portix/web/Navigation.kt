package app.portix.web

import java.net.URI
import java.net.URLDecoder

/** What happens to a page the site tries to open. */
sealed interface Decision {
    data object Allow : Decision
    /** Stays in the tab, at [url] (the web fallback of an app link). */
    data class Redirect(val url: String) : Decision
    /** An app, the store, or a non-web scheme: never handed to Android. */
    data class Block(val reason: String) : Decision
}

/**
 * Top-level navigations. Only http(s) pages load; links meant to open an app or the store are
 * refused, except `intent://` links that carry a web fallback, which load it in the tab instead.
 * Nothing here ever starts an Intent: a site can never leave the app on its own.
 */
object Navigation {
    private val STORE_HOSTS = listOf("play.google.com", "market.android.com", "apps.apple.com", "itunes.apple.com")

    fun decide(url: String): Decision {
        val scheme = url.substringBefore(':', "").lowercase()
        return when (scheme) {
            "http", "https" -> {
                val host = try { URI(url).host?.lowercase() } catch (e: Exception) { null }
                if (host != null && STORE_HOSTS.any { host == it || host.endsWith(".$it") }) Decision.Block("Lien vers une boutique d'applis bloqué")
                else Decision.Allow
            }
            "about", "data", "blob", "javascript" -> Decision.Allow
            "intent" -> fallback(url)?.let { Decision.Redirect(it) } ?: Decision.Block("Ouverture d'une appli bloquée")
            "market" -> Decision.Block("Lien vers une boutique d'applis bloqué")
            else -> Decision.Block("Ouverture d'une appli bloquée")
        }
    }

    /** `intent://…#Intent;…;S.browser_fallback_url=<encoded>;end` → the fallback, when it is a web page. */
    fun fallback(intentUrl: String): String? {
        val extras = intentUrl.substringAfter("#Intent;", "").split(';')
        val raw = extras.firstOrNull { it.startsWith("S.browser_fallback_url=") }?.substringAfter('=') ?: return null
        val url = try { URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { return null }
        val scheme = url.substringBefore(':', "").lowercase()
        return if ((scheme == "http" || scheme == "https") && decide(url) == Decision.Allow) url else null
    }
}
