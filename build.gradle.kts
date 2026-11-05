// Top-level build file. Plugins are declared here (not applied) so every module
// resolves the same versions from gradle/libs.versions.toml.
//
// AGP 9 provides built-in Kotlin support, so the org.jetbrains.kotlin.android
// plugin is intentionally NOT used. Declaring the Kotlin Compose and
// Serialization plugins at version `kotlin` puts that Kotlin Gradle Plugin on
// the build classpath, which built-in Kotlin then uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
