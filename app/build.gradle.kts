// Not a secret - see the comment on GOOGLE_WEB_CLIENT_ID below.
val GOOGLE_WEB_CLIENT_ID_DEFAULT = "992403696762-uclk49gh10k9ot1fncppqb0d66dt0g80.apps.googleusercontent.com"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.atomichabits.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atomichabits.tracker"
        minSdk = 26
        targetSdk = 35
        // CI passes -PappVersionCode=<github.run_number>, so each build has a
        // unique, increasing version the in-app updater can compare against.
        // Falls back to 1 for plain local builds where that property isn't set.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.${versionCode}"

        // Web client ID from Firebase Console -> Authentication -> Sign-in method
        // -> Google (after enabling that provider). Not a secret - it's a public
        // OAuth client identifier, safe to commit like google-services.json - so
        // it's just hardcoded here directly rather than routed through a CI
        // secret. Update this constant once the Google provider is enabled.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$GOOGLE_WEB_CLIENT_ID_DEFAULT\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // A fixed, checked-in debug keystore (keystore/debug.keystore) instead of
        // the default machine-generated one. This matters specifically because
        // Google Sign-In validates the calling app's signing certificate (SHA-1)
        // against the one registered in the Firebase/Google Cloud console - if
        // every CI run signed with a fresh random debug key, sign-in would break
        // unpredictably. Using a fixed key keeps the SHA-1 stable across every
        // build forever. This is a debug-only key with well-known default
        // credentials (alias/password "androiddebugkey"/"android") - never used
        // for a release/Play Store build, so there's nothing sensitive about
        // committing it.
        create("fixedDebug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixedDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room (local database - the on-device cache; Firestore is the cloud source of truth)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines (used directly in BroadcastReceivers for background DB work)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Lets suspend functions await() a Firebase Task (Auth/Firestore APIs return Tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Firebase (Firestore = cloud database, Auth = Google Sign-In gating access to it)
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // Google Sign-In via Credential Manager (current recommended API, replaces the
    // old deprecated GoogleSignInClient / GoogleSignInOptions approach)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    // Networking for the GitHub Releases update-checker (see update/)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
