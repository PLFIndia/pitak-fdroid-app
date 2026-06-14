import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// Release signing — Play upload key.
//
// Reads credentials from two sources, in order of preference:
//   1. Environment variables (preferred — passwords never sit on disk):
//        PITAK_UPLOAD_STORE_FILE        absolute path to the .jks
//        PITAK_UPLOAD_KEY_ALIAS         key alias inside the .jks
//        PITAK_UPLOAD_STORE_PASSWORD    keystore password
//        PITAK_UPLOAD_KEY_PASSWORD      key password (often same as store)
//   2. Repo-root keystore.properties (gitignored; convenience fallback):
//        storeFile=/Users/you/keys/pitak-upload.jks
//        keyAlias=pitak-upload
//        storePassword=...
//        keyPassword=...
//
// If neither source is configured, the release build is left UNSIGNED so
// debug development still works. Trying to install an unsigned release APK
// will fail loudly; that is the intended signal to set up signing.
// ---------------------------------------------------------------------------
val keystorePropsFile: File = rootProject.file("keystore.properties")
val keystoreProps: Properties? = if (keystorePropsFile.exists()) {
    Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
} else {
    null
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProps?.getProperty(propName)?.takeIf { it.isNotBlank() }

val uploadStoreFile: String? = signingValue("PITAK_UPLOAD_STORE_FILE", "storeFile")
val uploadKeyAlias: String? = signingValue("PITAK_UPLOAD_KEY_ALIAS", "keyAlias")
val uploadStorePassword: String? = signingValue("PITAK_UPLOAD_STORE_PASSWORD", "storePassword")
val uploadKeyPassword: String? = signingValue("PITAK_UPLOAD_KEY_PASSWORD", "keyPassword")

val haveReleaseSigning: Boolean =
    uploadStoreFile != null &&
        uploadKeyAlias != null &&
        uploadStorePassword != null &&
        uploadKeyPassword != null &&
        File(uploadStoreFile).exists()

android {
    namespace = "dev.khoj.pitaka"
    compileSdk = 35

    defaultConfig {
        // F-Droid variant identity. namespace (code/R package) stays
        // dev.khoj.pitaka so no source changes; only the INSTALL id diverges,
        // letting this build sit side-by-side with the Play build
        // (dev.khoj.pitaka). Debug appends .debug → dev.khoj.pitaka.fdroid.debug.
        applicationId = "dev.khoj.pitaka.fdroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (haveReleaseSigning) {
            create("release") {
                storeFile = file(uploadStoreFile!!)
                keyAlias = uploadKeyAlias
                storePassword = uploadStorePassword
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (haveReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Treat Compose-stability-warning noise quietly; we use the stable APIs only.
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Image loading (covers; placeholder usage in Phase 1, real usage from Phase 2)
    implementation(libs.coil.compose)

    // Networking (Phase 2)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // CameraX (Phase 2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Barcode + QR scanning (Phase 2) — ZXing only (F-Droid variant: no
    // proprietary ML Kit / Play Services). ZXing reads EAN-13 + QR and also
    // generates the pairing QR (QrEncoder).
    implementation(libs.zxing.core)

    // Manual cover crop (F-Droid variant) — vanniktech, Apache-2.0, no GMS.
    implementation(libs.image.cropper)

    // Permissions helper (Phase 2)
    implementation(libs.accompanist.permissions)

    // Vault (Phase 4)
    implementation(libs.sqlcipher.android)
    implementation(libs.argon2kt)
    implementation(libs.androidx.biometric)

    // Custom Tabs — open contributor/GitHub web links in a browser tab
    // (prevents the GitHub Android app from intercepting issue-form URLs).
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp.mockwebserver)

    // Android tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
