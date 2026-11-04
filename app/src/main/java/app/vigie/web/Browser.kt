package app.vigie.web

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * The Firefox engine, one per app, with Firefox's own protections at their strictest: Enhanced
 * Tracking Protection "strict" (trackers, fingerprinters, cryptominers, social trackers), Total Cookie
 * Protection (a third party's cookies are partitioned per site), tracking parameters stripped from
 * links, Global Privacy Control sent. The extensions come on top of that.
 */
object Browser {
    fun create(context: Context): GeckoRuntime {
        val blocking = ContentBlocking.Settings.Builder()
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .strictSocialTrackingProtection(true)
            .queryParameterStrippingEnabled(true)
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .contentBlocking(blocking)
            .globalPrivacyControlEnabled(true)
            .loginAutofillEnabled(false)
            .consoleOutput(false)
            .remoteDebuggingEnabled(false)
            .aboutConfigEnabled(false)
            .build()
        return GeckoRuntime.create(context, settings)
    }
}
