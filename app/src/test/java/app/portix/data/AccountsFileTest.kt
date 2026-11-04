package app.portix.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountsFileTest {
    private val a = Account("a1", "Instagram", "https://www.instagram.com/")
    private val b = Account("b2", "TikTok perso", "https://www.tiktok.com/", desktop = true, linkDefault = true)

    @Test fun roundTrip() = assertEquals(listOf(a, b), AccountsFile.decode(AccountsFile.encode(listOf(a, b))))

    @Test fun missingOptionalsTakeDefaults() {
        val sites = AccountsFile.decode("""{"format_version":1,"accounts":[{"id":"x","name":"X","url":"https://x.fr/","block_trackers":false,"extra":1}]}""")
        assertEquals(listOf(Account("x", "X", "https://x.fr/")), sites)
    }

    @Test fun emptyOrMissingList() {
        assertEquals(emptyList<Account>(), AccountsFile.decode("""{"format_version":1}"""))
        assertEquals(emptyList<Account>(), AccountsFile.decode("""{"accounts":[]}"""))
    }

    @Test fun duplicateIdsKeepTheFirst() {
        val text = """{"accounts":[{"id":"a1","name":"A","url":"https://a.fr/"},{"id":"a1","name":"B","url":"https://b.fr/"}]}"""
        assertEquals(listOf("A"), AccountsFile.decode(text).map { it.name })
    }

    @Test fun newerFormatIsRefused() {
        assertThrows(IllegalStateException::class.java) { AccountsFile.decode("""{"format_version":2,"accounts":[]}""") }
    }

    @Test fun brokenJsonThrows() {
        assertThrows(Exception::class.java) { AccountsFile.decode("""{"accounts":[""") }
    }

}
