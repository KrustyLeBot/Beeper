package app.beeper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebAddressTest {
    @Test fun bareDomainGetsHttps() = assertEquals("https://amazon.fr/", WebAddress.normalize("amazon.fr"))

    @Test fun httpBecomesHttps() = assertEquals("https://www.leroymerlin.fr/", WebAddress.normalize("http://www.leroymerlin.fr"))

    @Test fun keepsPathQueryAndPort() =
        assertEquals("https://shop.example.com:8443/fr/cat?id=3", WebAddress.normalize("  https://Shop.Example.com:8443/fr/cat?id=3 "))

    @Test fun rejectsNonWeb() {
        assertNull(WebAddress.normalize(""))
        assertNull(WebAddress.normalize("amazon"))
        assertNull(WebAddress.normalize("ftp://amazon.fr"))
        assertNull(WebAddress.normalize("amazon fr"))
        assertNull(WebAddress.normalize("intent://amazon.fr"))
    }

    @Test fun networksGetTheirName() {
        assertEquals("Instagram", WebAddress.defaultName("https://www.instagram.com/"))
        assertEquals("TikTok", WebAddress.defaultName("https://vm.tiktok.com/x"))
    }

    @Test fun defaultNames() {
        assertEquals("Leroymerlin", WebAddress.defaultName("https://www.leroymerlin.fr/"))
        assertEquals("Amazon", WebAddress.defaultName("https://amazon.co.uk/"))
        assertEquals("Decathlon", WebAddress.defaultName("https://decathlon.fr/"))
    }

    @Test fun idsAreRandom() {
        val ids = List(100) { Account.newId() }.toSet()
        assertEquals(100, ids.size)
        assert(ids.all { it.length == 10 && it.all { c -> c.isLowerCase() || c.isDigit() } })
    }
}
