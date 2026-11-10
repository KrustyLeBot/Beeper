package app.beeper.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFilesTest {
    @Test
    fun appFilesAreRemoved() {
        listOf("accounts.json", ".syncignore", ".accounts.json.synctmp-1f2e", "x.tmp", "y.part")
            .forEach { assertTrue(it, LegacyFiles.isAppFile(it)) }
    }

    @Test
    fun otherFilesAreKept() {
        listOf("notes.txt", "accounts.json.bak", "photo.jpg", "synctmp-but-not-hidden")
            .forEach { assertFalse(it, LegacyFiles.isAppFile(it)) }
    }
}
