package app.beeper.data

import app.beeper.links.Networks
import app.beeper.links.Owner
import java.net.URI
import java.security.SecureRandom

/**
 * A social network account (or a site) the user added. [id] names its own browsing context
 * (cookies, storage, cache), so it never changes: renaming the account keeps its login.
 * [linkDefault]: with two accounts on the same network, the one links open in.
 */
data class Account(
    val id: String,
    val name: String,
    val url: String,
    val desktop: Boolean = false,
    val linkDefault: Boolean = false,
) {
    val host: String get() = WebAddress.host(url) ?: url

    val owner: Owner get() = Owner(id, host, linkDefault)

    companion object {
        private val random = SecureRandom()
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        fun newId(): String = String(CharArray(10) { ALPHABET[random.nextInt(ALPHABET.length)] })
    }
}

/** What the user types in the address field, made into an address. */
object WebAddress {
    /** "instagram.com", "www.tiktok.com/" or "http://…" become an https URL; null when it is not a web address. */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = when {
            text.startsWith("https://", ignoreCase = true) -> text
            text.startsWith("http://", ignoreCase = true) -> "https://" + text.substring(7)
            text.contains("://") -> return null
            else -> "https://$text"
        }
        val uri = try { URI(withScheme) } catch (e: Exception) { return null }
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return "https://$host$port$path$query"
    }

    fun host(url: String): String? = try { URI(url).host?.lowercase() } catch (e: Exception) { null }

    /** Default tab name: the network's name, else from the domain ("www.leboncoin.fr" gives "Leboncoin"). */
    fun defaultName(url: String): String {
        val host = host(url) ?: return url
        Networks.of(host)?.let { return it.name }
        val labels = host.split('.').filter { it.isNotEmpty() }
        val main = when {
            labels.size >= 3 && labels[labels.size - 2] in SECOND_LEVEL -> labels[labels.size - 3]
            labels.size >= 2 -> labels[labels.size - 2]
            else -> labels.firstOrNull() ?: host
        }
        return main.replaceFirstChar { it.uppercase() }
    }

    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gouv", "ac")
}
