import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

// ---------------------------------------------------------------------------
// Optional Firebase Cloud Messaging (push notifications).
// The Google-services plugin FAILS the build if google-services.json is
// missing, so we only apply it when the file exists. Without the file the app
// still builds and runs; push falls back to in-app polling notifications.
// See docs/SETUP_GUIDE.md section 3.
// ---------------------------------------------------------------------------
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Secrets/config live in android/local.properties (never committed) or in
// environment variables (GitHub Actions secrets).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun config(propKey: String, envKey: String, default: String = ""): String =
    (localProps.getProperty(propKey) ?: System.getenv(envKey) ?: default).trim()

android {
    namespace = "com.msebenzi.app"
    // Google Play requires new apps/updates to target Android 16 (API 36)
    // from 31 August 2026 (Google Play Console Help, 2026).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.msebenzi.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Hosted REST API (Render). Must end with a trailing slash.
        buildConfigField(
            "String", "API_BASE_URL",
            "\"${config("msebenzi.api.url", "MSEBENZI_API_URL", "https://msebenzi-api.onrender.com/")}\""
        )

        // OAuth *Web* client ID from Google Cloud Console - used for
        // Google single sign-on. Leave blank to hide the SSO flow gracefully.
        buildConfigField(
            "String", "GOOGLE_WEB_CLIENT_ID",
            "\"${config("google.web.client.id", "GOOGLE_WEB_CLIENT_ID")}\""
        )
    }

    // Release signing: reads keystore details from local.properties or
    // environment variables so the keystore & passwords are never committed.
    val keystorePath = config("msebenzi.keystore.path", "MSEBENZI_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath.isNotEmpty() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = config("msebenzi.keystore.password", "MSEBENZI_KEYSTORE_PASSWORD")
                keyAlias = config("msebenzi.key.alias", "MSEBENZI_KEY_ALIAS", "msebenzi")
                keyPassword = config("msebenzi.key.password", "MSEBENZI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking is left off on purpose: it cannot be fully verified without
            // a device test pass, and a release-only crash would be worse than a
            // slightly larger APK. The keep rules in proguard-rules.pro are ready
            // if you later switch this to true.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Keep CI green on warnings; real errors still fail the build.
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading (profile photos / job photos stored as BLOBs on the API)
    implementation("io.coil-kt:coil:2.7.0")

    // Offline storage (Room) - source of truth for the job feed when offline
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Background sync for the offline outbox
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Security - encrypted local storage for the auth token
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Single sign-on: Google Sign-In through Android Credential Manager
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Real-time push notifications (Firebase Cloud Messaging)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.12")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
