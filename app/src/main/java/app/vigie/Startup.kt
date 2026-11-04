package app.vigie

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How long a cold start took, step by step, measured from the process start: the Firefox engine, the
 * extensions, the page's request, its first paint. Kept for the last start and shown in Réglages, so
 * a slow start can be pinned on the right step instead of guessed.
 */
class Startup(context: Context) {
    private val prefs = context.getSharedPreferences("startup", Context.MODE_PRIVATE)
    private val origin = Process.getStartElapsedRealtime()
    private val marks = LinkedHashMap<String, Long>()
    private var fromLink = false

    var last by mutableStateOf(prefs.getString(KEY, null))
        private set

    /** Records [step] the first time it happens in this process. */
    fun mark(step: String) {
        if (step in marks || marks.containsKey(DONE)) return
        marks[step] = SystemClock.elapsedRealtime() - origin
        if (step == PAINT) finish()
    }

    fun openedFromLink() {
        fromLink = true
    }

    private fun finish() {
        marks[DONE] = 0
        val text = marks.filterKeys { it != DONE }.entries.joinToString(" · ") { (k, v) -> "$k %.1f s".format(v / 1000.0) } +
            if (fromLink) " (ouverte par un lien)" else ""
        last = text
        prefs.edit().putString(KEY, text).apply()
    }

    companion object {
        const val ENGINE = "moteur"
        const val EXTENSIONS = "extensions"
        const val PAGE = "page demandée"
        const val PAINT = "page affichée"
        private const val DONE = "_done"
        private const val KEY = "last"
    }
}
