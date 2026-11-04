package app.portix

import android.app.Application
import app.portix.data.AccountStore
import app.portix.suite.SuiteUpdater
import app.portix.web.Browser
import app.portix.web.Extensions

class PortixApp : Application() {
    val accountStore by lazy { AccountStore(this) }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/Portix", "$packageName.fileprovider") }

    // Lazy: Gecko's content processes run this class too, and must not start a second engine.
    val startup by lazy { Startup(this) }
    val runtime by lazy { Browser.create(this).also { startup.mark(Startup.ENGINE) } }
    val extensions by lazy { Extensions(this, runtime, onReady = { startup.mark(Startup.EXTENSIONS) }).also { it.start() } }
}
