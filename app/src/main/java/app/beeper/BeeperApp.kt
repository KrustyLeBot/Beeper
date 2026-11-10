package app.beeper

import android.app.Application
import app.beeper.data.AccountStore
import app.beeper.suite.SuiteUpdater
import app.beeper.web.Browser
import app.beeper.web.Extensions

class BeeperApp : Application() {
    val accountStore by lazy { AccountStore(this) }
    val updater by lazy { SuiteUpdater(this, "KrustyLeBot/Beeper", "$packageName.fileprovider") }

    // Lazy: Gecko's content processes run this class too, and must not start a second engine.
    val startup by lazy { Startup(this) }
    val runtime by lazy { Browser.create(this).also { startup.mark(Startup.ENGINE) } }
    val extensions by lazy { Extensions(this, runtime, onReady = { startup.mark(Startup.EXTENSIONS) }).also { it.start() } }
}
