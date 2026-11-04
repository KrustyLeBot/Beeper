package app.vigie.links

/** A social network the app knows: its links open in the matching account. */
class Network(val id: String, val name: String, val home: String, val domains: List<String>, val extraHosts: List<String> = emptyList()) {
    /** Hosts declared in the manifest (Android needs exact hosts): each domain, its www/m, and the extra ones. */
    val hosts: List<String> get() = (domains.flatMap { listOf(it, "www.$it", "m.$it") } + extraHosts).distinct()
}

/**
 * The catalogue of social networks. Adding one here also needs its hosts in the manifest
 * (`NetworksManifestTest` checks the two agree).
 */
object Networks {
    val ALL = listOf(
        Network("instagram", "Instagram", "https://www.instagram.com/", listOf("instagram.com", "instagr.am"), listOf("l.instagram.com")),
        Network("tiktok", "TikTok", "https://www.tiktok.com/", listOf("tiktok.com"), listOf("vm.tiktok.com", "vt.tiktok.com")),
        Network("facebook", "Facebook", "https://m.facebook.com/", listOf("facebook.com", "fb.com", "fb.watch", "fb.me"), listOf("l.facebook.com", "lm.facebook.com", "web.facebook.com")),
        Network("x", "X", "https://x.com/", listOf("x.com", "twitter.com", "t.co"), listOf("mobile.twitter.com", "mobile.x.com")),
        Network("youtube", "YouTube", "https://m.youtube.com/", listOf("youtube.com", "youtu.be"), listOf("music.youtube.com")),
        Network("reddit", "Reddit", "https://www.reddit.com/", listOf("reddit.com", "redd.it"), listOf("old.reddit.com", "new.reddit.com")),
        Network("linkedin", "LinkedIn", "https://www.linkedin.com/", listOf("linkedin.com", "lnkd.in"), listOf("fr.linkedin.com")),
        Network("pinterest", "Pinterest", "https://www.pinterest.fr/", listOf("pinterest.fr", "pinterest.com", "pin.it"), listOf("fr.pinterest.com")),
        Network("threads", "Threads", "https://www.threads.net/", listOf("threads.net", "threads.com")),
        Network("bluesky", "Bluesky", "https://bsky.app/", listOf("bsky.app")),
        Network("snapchat", "Snapchat", "https://www.snapchat.com/", listOf("snapchat.com")),
        Network("twitch", "Twitch", "https://m.twitch.tv/", listOf("twitch.tv"), listOf("clips.twitch.tv")),
        Network("tumblr", "Tumblr", "https://www.tumblr.com/", listOf("tumblr.com")),
    )

    /** The network [host] belongs to (the domain itself or any subdomain), or null. */
    fun of(host: String?): Network? {
        val h = host?.lowercase()?.trimEnd('.') ?: return null
        return ALL.firstOrNull { n -> n.domains.any { h == it || h.endsWith(".$it") } }
    }
}
