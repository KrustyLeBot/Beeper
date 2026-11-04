package app.vigie.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * `accounts.json`: `{"format_version": 1, "accounts": [{"id", "name", "url", "desktop", "link_default"}]}`,
 * in tab order. Unknown fields are ignored, missing optional ones take their defaults.
 */
object AccountsFile {
    const val NAME = "accounts.json"
    const val FORMAT_VERSION = 1

    fun encode(accounts: List<Account>): String =
        JSONObject().put("format_version", FORMAT_VERSION).put("accounts", toJson(accounts)).toString(2)

    /** The accounts part alone, to tell whether a write would change anything. */
    fun toJson(accounts: List<Account>): JSONArray = JSONArray().also { arr ->
        accounts.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("url", it.url)
                    .put("desktop", it.desktop)
                    .put("link_default", it.linkDefault)
            )
        }
    }

    fun decode(text: String): List<Account> {
        val root = JSONObject(text)
        val version = root.optInt("format_version", 1)
        if (version > FORMAT_VERSION) throw IllegalStateException("format_version $version : mets l'application à jour")
        val arr = root.optJSONArray("accounts") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Account(
                id = o.getString("id"),
                name = o.getString("name"),
                url = o.getString("url"),
                desktop = o.optBoolean("desktop", false),
                linkDefault = o.optBoolean("link_default", false),
            )
        }.distinctBy { it.id }
    }

    fun merge(theirs: List<Account>, mine: List<Account>): List<Account> = theirs + mine.filter { m -> theirs.none { it.id == m.id } }
}
