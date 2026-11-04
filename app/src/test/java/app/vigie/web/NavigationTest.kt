package app.vigie.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test fun webPagesLoad() {
        assertEquals(Decision.Allow, Navigation.decide("https://www.amazon.fr/dp/123"))
        assertEquals(Decision.Allow, Navigation.decide("http://example.com"))
        assertEquals(Decision.Allow, Navigation.decide("about:blank"))
        // 3-D Secure and payment pages are just other https sites.
        assertEquals(Decision.Allow, Navigation.decide("https://acs.bank.example/3ds?x=1"))
    }

    @Test fun storesAndAppsAreBlocked() {
        assertTrue(Navigation.decide("https://play.google.com/store/apps/details?id=com.amazon") is Decision.Block)
        assertTrue(Navigation.decide("market://details?id=com.amazon") is Decision.Block)
        assertTrue(Navigation.decide("https://apps.apple.com/fr/app/x/id1") is Decision.Block)
        assertTrue(Navigation.decide("amazon://home") is Decision.Block)
        assertTrue(Navigation.decide("tel:0102030405") is Decision.Block)
        assertTrue(Navigation.decide("intent://scan/#Intent;scheme=zxing;package=com.google.zxing;end") is Decision.Block)
    }

    @Test fun intentWithWebFallbackStaysInTheTab() {
        val url = "intent://product/1#Intent;scheme=shop;package=com.shop;S.browser_fallback_url=https%3A%2F%2Fshop.fr%2Fp%2F1;end"
        assertEquals(Decision.Redirect("https://shop.fr/p/1"), Navigation.decide(url))
    }

    @Test fun intentFallbackToTheStoreIsBlocked() {
        val url = "intent://x#Intent;S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Da;end"
        assertTrue(Navigation.decide(url) is Decision.Block)
        assertNull(Navigation.fallback("intent://x#Intent;S.browser_fallback_url=market%3A%2F%2Fdetails;end"))
    }
}
