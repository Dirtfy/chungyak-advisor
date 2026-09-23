// Top-level build file. Plugin versions declared here, applied in :app.
// Versions pinned to match the geo-reminder toolchain that already builds in
// the cirruslabs/android-sdk:34 container (Gradle 8.9).
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
}
