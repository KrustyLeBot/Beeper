package app.vigie.links

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Android only hands the app the hosts its manifest lists: the catalogue and the manifest must agree. */
class NetworksManifestTest {
    @Test fun everyCatalogueHostIsInTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("android:host=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toSet()
        val wanted = Networks.ALL.flatMap { it.hosts }.toSet()
        assertEquals(emptySet<String>(), wanted - declared)
        assertEquals(emptySet<String>(), declared - wanted)
    }
}
