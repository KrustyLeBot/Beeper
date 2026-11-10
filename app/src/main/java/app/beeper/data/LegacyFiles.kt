package app.beeper.data

/** Which files of an old app folder are the app's own, so removable once the list has moved. */
object LegacyFiles {
    /** The list, `.syncignore`, and the temporary files of a write or a sync. */
    fun isAppFile(name: String): Boolean =
        name == AccountsFile.NAME || name == ".syncignore" ||
            (name.startsWith(".") && ".synctmp-" in name) || name.endsWith(".tmp") || name.endsWith(".part")
}
