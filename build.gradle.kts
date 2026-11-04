plugins {
    id("com.android.application") version "9.5.0" apply false
    // AGP 9 compiles Kotlin itself; this plugin also pins the Kotlin version (GeckoView needs 2.4).
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
