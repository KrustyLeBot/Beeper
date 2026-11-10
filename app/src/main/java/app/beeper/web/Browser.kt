package app.beeper.web

import android.content.Context
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

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
        return GeckoRuntime.create(context, settings).also { forgetAutoplayRefusals(context, it) }
    }

    /**
     * Builds up to 6 refused every permission, muted autoplay included, and GeckoView stored that
     * refusal per site for good: reels then waited for a tap. Those stored refusals are dropped once,
     * so the next request reaches the delegate, which now allows it.
     */
    private fun forgetAutoplayRefusals(context: Context, runtime: GeckoRuntime) {
        val prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUTOPLAY_RESET, false)) return
        runtime.storageController.allPermissions.accept({ list ->
            list.orEmpty()
                .filter { it.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE && it.value == ContentPermission.VALUE_DENY }
                .forEach { runtime.storageController.setPermission(it, ContentPermission.VALUE_PROMPT) }
            prefs.edit().putBoolean(KEY_AUTOPLAY_RESET, true).apply()
        }, {})
    }

    private const val KEY_AUTOPLAY_RESET = "autoplay_refusals_forgotten"
}
