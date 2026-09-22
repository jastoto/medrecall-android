import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.appdistribution")
}

val releaseSigningProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        load(FileInputStream(localPropsFile))
    }
}

android {
    namespace = "com.asok.medrecall"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.asok.medrecall"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFileName = releaseSigningProps.getProperty("RELEASE_STORE_FILE")
            if (storeFileName != null) {
                storeFile = file("$storeFileName")
                storePassword = releaseSigningProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(11)
}

firebaseAppDistribution {
    releaseNotes = "Beta build"
    groups = "beta-testers"
    // Fix 2026-09-21: appDistributionUploadRelease started failing with
    // "Could not find credentials" -- the plugin stopped reliably reading the
    // Firebase CLI's cached `firebase login` session (Asok confirmed he was
    // still logged in via the CLI, yet the build kept failing). Switched to an
    // explicit service account key instead, the same way this file already
    // keeps the release-signing credentials out of git via local.properties
    // (see releaseSigningProps above). The key itself
    // (app/firebase-service-account.json) is gitignored -- never commit it.
    val serviceAccountFileName = releaseSigningProps.getProperty("FIREBASE_SERVICE_ACCOUNT_FILE")
    if (serviceAccountFileName != null) {
        serviceCredentialsFile = file(serviceAccountFileName).absolutePath
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.navigation:navigation-compose:2.10.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)
    // Two-way Google Calendar sync reconciliation (see data/calendar/CalendarSyncScheduler.kt)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // Google Sign-In + Drive account connector (Settings > Account)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    // Microsoft/OneDrive account connector (Settings > Account) -- MSAL, see
    // data/account/MicrosoftAccountManager.kt and res/raw/msal_config.json.
    implementation("com.microsoft.identity.client:msal:8.4.2")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Backup & Restore -- Google Drive / OneDrive connectors.
    // Only used to sign in and obtain an OAuth access token; the actual
    // Drive/Graph API calls are plain REST (HttpURLConnection) in
    // data/backup/, so no heavy generated API client libraries are needed.
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.microsoft.identity.client:msal:6.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}