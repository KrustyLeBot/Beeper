package app.portix.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private val insta = Owner("i1", "www.instagram.com", linkDefault = false)
    private val insta2 = Owner("i2", "www.instagram.com", linkDefault = true)
    private val tiktok = Owner("t1", "www.tiktok.com", linkDefault = false)
    private val shop = Owner("s1", "www.leboncoin.fr", linkDefault = false)

    @Test fun networksByHost() {
        assertEquals("tiktok", Networks.of("vm.tiktok.com")?.id)
        assertEquals("x", Networks.of("twitter.com")?.id)
        assertEquals("youtube", Networks.of("youtu.be")?.id)
        assertEquals("instagram", Networks.of("instagr.am")?.id)
        assertNull(Networks.of("notinstagram.com"))
        assertNull(Networks.of("example.org"))
    }

    @Test fun linkToAKnownNetworkOpensItsAccount() {
        assertEquals(Target.Account("t1"), Links.route("https://vm.tiktok.com/ZMabc/", listOf(insta, tiktok)))
        assertEquals(Target.Account("i1"), Links.route("https://www.instagram.com/reel/C1/", listOf(insta, tiktok)))
    }

    @Test fun theDefaultAccountWinsAmongTwo() =
        assertEquals(Target.Account("i2"), Links.route("https://instagram.com/p/x", listOf(insta, insta2)))

    @Test fun noAccountMeansTemporaryBrowser() {
        assertEquals(Target.Temporary, Links.route("https://www.facebook.com/watch?v=1", listOf(insta, tiktok)))
        assertEquals(Target.Temporary, Links.route("https://lemonde.fr/article", listOf(insta)))
    }

    @Test fun anAccountOutsideTheCatalogueTakesItsOwnSite() =
        assertEquals(Target.Account("s1"), Links.route("https://www.leboncoin.fr/ad/1", listOf(insta, shop)))

    @Test fun shimsAreUnwrapped() {
        assertEquals("https://lemonde.fr/a", Links.clean("https://l.instagram.com/?u=https%3A%2F%2Flemonde.fr%2Fa%3Futm_source%3Dig&e=AT1"))
        assertEquals("https://example.org/x", Links.clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.org%2Fx%3Ffbclid%3D9&h=AT"))
        assertEquals("https://example.org/", Links.clean("https://www.google.com/url?q=https://example.org/&sa=D"))
    }

    @Test fun trackingParametersAreDropped() {
        assertEquals("https://www.instagram.com/reel/C1/?l=1", Links.clean("https://www.instagram.com/reel/C1/?igsh=abc&utm_source=wa"))
        assertEquals("https://www.tiktok.com/@a/video/1", Links.clean("https://www.tiktok.com/@a/video/1?_t=8x&_r=1&is_from_webapp=1"))
        assertEquals("https://x.com/a/status/1", Links.clean("https://x.com/a/status/1?s=46&t=AbC"))
        // On YouTube `t` is the start time and stays.
        assertEquals("https://youtu.be/abc?t=42", Links.clean("https://youtu.be/abc?si=xyz&t=42"))
        assertEquals("https://example.org/search?s=chaise", Links.clean("https://example.org/search?s=chaise"))
    }

    @Test fun instagramLinksSkipTheAppInterstitial() {
        assertEquals("https://www.instagram.com/reel/DfT1QabR-0o/?mdxt=MTk&l=1", Links.clean("https://www.instagram.com/reel/DfT1QabR-0o/?mdxt=MTk"))
        assertEquals("https://www.instagram.com/p/x/?l=1", Links.clean("https://www.instagram.com/p/x/?l=1"))
        assertEquals("https://www.instagram.com/", Links.clean("https://www.instagram.com/"))
        assertEquals("https://www.instagram.com/accounts/login/", Links.clean("https://www.instagram.com/accounts/login/"))
        assertEquals("https://www.tiktok.com/@a", Links.clean("https://www.tiktok.com/@a"))
    }

    @Test fun tappedLinksToAnotherNetworkLeaveTheTab() {
        val all = listOf(insta, tiktok)
        assertEquals(Target.Account("t1"), Links.inTab(insta, "https://www.tiktok.com/@a", byUser = true, isRedirect = false, owners = all))
        assertEquals(Target.Temporary, Links.inTab(insta, "https://lemonde.fr/article", byUser = true, isRedirect = false, owners = all))
        assertEquals(Target.Temporary, Links.inTab(insta, "https://l.instagram.com/?u=https%3A%2F%2Flemonde.fr%2Fa", byUser = true, isRedirect = false, owners = all))
    }

    @Test fun ownPagesRedirectsAndSignInStay() {
        val all = listOf(insta, tiktok)
        assertNull(Links.inTab(insta, "https://www.instagram.com/explore/", byUser = true, isRedirect = false, owners = all))
        assertNull(Links.inTab(insta, "https://cdn.example.net/x", byUser = false, isRedirect = false, owners = all))
        assertNull(Links.inTab(insta, "https://lemonde.fr/a", byUser = true, isRedirect = true, owners = all))
        assertNull(Links.inTab(insta, "https://www.facebook.com/v19.0/dialog/oauth?client_id=1", byUser = true, isRedirect = false, owners = all))
        assertNull(Links.inTab(Owner("y", "m.youtube.com", false), "https://accounts.google.com/ServiceLogin", byUser = true, isRedirect = false, owners = all))
    }

    @Test fun temporaryBrowserHandsKnownNetworksToTheirAccount() {
        val all = listOf(insta, tiktok)
        assertEquals(Target.Account("i1"), Links.inTab(null, "https://www.instagram.com/p/1", byUser = true, isRedirect = false, owners = all))
        assertNull(Links.inTab(null, "https://lemonde.fr/b", byUser = true, isRedirect = false, owners = all))
    }

    @Test fun firstUrlOfASharedText() {
        assertEquals("https://vm.tiktok.com/ZMabc/", Links.firstUrl("Regarde ça https://vm.tiktok.com/ZMabc/ !"))
        assertNull(Links.firstUrl("pas de lien"))
    }
}
