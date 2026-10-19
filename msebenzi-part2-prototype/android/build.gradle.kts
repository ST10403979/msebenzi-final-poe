// Top-level build file.
// Plugin versions chosen to satisfy Google Play's API 36 target requirement
// (AGP 8.13 + Gradle 8.13 is the documented pairing for compileSdk 36).
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Only applied in app/build.gradle.kts when google-services.json exists.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
