package app.vigie.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * The account list, as `accounts.json` in the public `Documents/Vigie/` folder, which Synchrotron
 * syncs with Proton Drive and which survives a reinstall. It needs the "all files access" permission:
 * the app asks for it and the list stays empty and read-only until it is granted. Logins, cookies and
 * history never go there: they stay in each account's private browsing context.
 *
 * An unreadable file raises an error and is never overwritten.
 */
class AccountStore(private val context: Context) {
    private val publicDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Vigie")

    /** Folder that Synchrotron syncs with Proton Drive. */
    val syncPath: String get() = publicDir.path

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private val file: File get() = File(publicDir, AccountsFile.NAME)

    @Synchronized
    fun load(): List<Account> = if (hasStorageAccess()) file.let { if (it.exists()) read(it) else emptyList() } else emptyList()

    @Synchronized
    fun save(accounts: List<Account>) {
        val f = file
        // Rewriting an identical file would still wake Synchrotron's watcher for nothing.
        val current = if (f.exists()) AccountsFile.toJson(read(f)).toString() else null
        if (current != AccountsFile.toJson(accounts).toString()) AtomicFiles.write(f, AccountsFile.encode(accounts))
    }

    /** A sync may be replacing the file: a parse failure is read again once before being reported. */
    private fun read(f: File): List<Account> = try {
        AtomicFiles.read(f) { AccountsFile.decode(it) } ?: emptyList()
    } catch (e: UnreadableFileException) {
        throw IllegalStateException("${f.path} est illisible. Il ne sera pas modifié.", e)
    }
}
