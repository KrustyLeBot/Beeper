package app.vigie.web

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** What Android lets Vigie open: [allowed] is the "Ouverture liens pris en charge" switch, [selected] of [total] addresses ticked. */
data class LinkStatus(val allowed: Boolean, val selected: Int, val total: Int) {
    val complete: Boolean get() = allowed && selected == total
}

/** Hand-offs to the rest of the phone. */
object PhoneBrowser {
    /**
     * Android's own record of which network addresses the user let Vigie open (Android 12 and later;
     * null before, where the system asks with a chooser instead).
     */
    fun linkStatus(context: Context): LinkStatus? {
        if (Build.VERSION.SDK_INT < 31) return null
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return null
        val state = try {
            manager.getDomainVerificationUserState(context.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        } ?: return null
        val hosts = state.hostToStateMap
        val selected = hosts.values.count { it != DomainVerificationUserState.DOMAIN_STATE_NONE }
        return LinkStatus(state.isLinkHandlingAllowed, selected, hosts.size)
    }

    /**
     * Opens [url] in the phone's browser. Vigie itself opens the networks' links, so asking Android to
     * "view" one would come straight back here: the browser is found from a neutral address and
     * named explicitly, or picked from a chooser that leaves Vigie out.
     */
    fun open(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val browser = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/")), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (browser != null && browser != context.packageName && browser != "android") {
            context.startActivity(view.setPackage(browser))
        } else {
            val chooser = Intent.createChooser(view, "Ouvrir avec")
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, "app.vigie.MainActivity")))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    /** Android's "Ouvrir par défaut" page for Vigie, where the networks' addresses are ticked once. */
    fun openLinkSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
