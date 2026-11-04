package app.vigie.web

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse
import java.io.File

/**
 * Files a page sends instead of showing (invoices, PDFs), saved to the phone's Downloads folder. The
 * response comes from the tab itself, so a file behind the site's login downloads with that login.
 */
class Downloads(private val context: Context, private val scope: CoroutineScope, private val onNotice: (String) -> Unit) {
    fun save(response: WebResponse) {
        val body = response.body ?: return onNotice("Ce fichier ne peut pas être téléchargé")
        val disposition = response.headers.entries.firstOrNull { it.key.equals("Content-Disposition", true) }?.value
        val mime = response.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.substringBefore(';')?.trim()
        val name = URLUtil.guessFileName(response.uri, disposition, mime)
        onNotice("Téléchargement de $name")
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    body.use { input ->
                        if (Build.VERSION.SDK_INT >= 29) {
                            val values = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, name)
                                put(MediaStore.Downloads.MIME_TYPE, mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")))
                            }
                            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@use false
                            context.contentResolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: return@use false
                        } else {
                            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }
                            File(dir, name).outputStream().use { input.copyTo(it) }
                        }
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }
            onNotice(if (ok) "$name enregistré dans Téléchargements" else "Téléchargement de $name impossible")
        }
    }
}
