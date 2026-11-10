package app.beeper.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * The account list, as `accounts.json` in the public `Documents/Beeper/` folder, which Synchrotron
 * syncs with Proton Drive and which survives a reinstall. It needs the "all files access" permission:
 * the app asks for it and the list stays empty and read-only until it is granted. Logins, cookies and
 * history never go there: they stay in each account's private browsing context.
 *
 * An unreadable file raises an error and is never overwritten.
 */
class AccountStore(private val context: Context) {
    private val publicDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Beeper")

    /** Folder that Synchrotron syncs with Proton Drive. */
    val syncPath: String get() = publicDir.path

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private val file: File get() = File(publicDir, AccountsFile.NAME)

    @Synchronized
    fun load(): List<Account> {
        if (!hasStorageAccess()) return emptyList()
        if (!file.exists()) importLegacy()
        val accounts = file.let { if (it.exists()) read(it) else emptyList() }
        if (file.exists()) removeLegacy()
        return accounts
    }

    /**
     * The app was called Portix, and Vigie before that: the first of their lists that parses is copied
     * once into a still empty `Documents/Beeper/`.
     */
    private fun importLegacy() {
        for (folder in LEGACY_FOLDERS) {
            val legacy = File(documents, folder).resolve(AccountsFile.NAME)
            if (!legacy.exists()) continue
            val accounts = try { read(legacy) } catch (e: Exception) { continue }
            if (accounts.isEmpty()) continue
            AtomicFiles.write(file, AccountsFile.encode(accounts))
            return
        }
    }

    /**
     * Once `Documents/Beeper/accounts.json` exists, the old folders are emptied of what the app put
     * there and removed; a file the app does not know is left, and so is its folder. Synchrotron
     * mirrors the deletion if the old folder is still paired.
     */
    private fun removeLegacy() {
        for (folder in LEGACY_FOLDERS) {
            val dir = File(documents, folder)
            if (!dir.isDirectory) continue
            // An old list that does not parse may hold accounts never imported: kept for the user to look at.
            val list = dir.resolve(AccountsFile.NAME)
            if (list.exists() && runCatching { read(list) }.isFailure) continue
            dir.listFiles().orEmpty().filter { it.isFile && LegacyFiles.isAppFile(it.name) }.forEach { it.delete() }
            if (dir.list().isNullOrEmpty()) dir.delete()
        }
    }

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

    private val documents: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

    private companion object {
        val LEGACY_FOLDERS = listOf("Portix", "Vigie")
    }
}
