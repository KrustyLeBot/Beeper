package app.vigie

import android.app.Application
import app.vigie.data.AccountStore
import app.vigie.suite.SuiteUpdater
import app.vigie.web.Browser
import app.vigie.web.Extensions

class VigieApp : Application() {
    val accountStore by lazy { AccountStore(this) }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/vigie", "$packageName.fileprovider") }

    // Lazy: Gecko's content processes run this class too, and must not start a second engine.
    val runtime by lazy { Browser.create(this) }
    val extensions by lazy { Extensions(this, runtime).also { it.start() } }
}
