// -----------------------------------------------------------------------------
// MoChat: deliberately vulnerable super-app training target.
// Single release build with R8 obfuscation enabled. All chains are app-layer
// (genuinely exploitable on Android 13/14/15).
// -----------------------------------------------------------------------------
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load signing credentials from keystore.properties (root project). The keystore
// and this file are gitignored so secrets never enter version control.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(FileInputStream(f))
}

android {
    namespace = "com.mochat.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mochat.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.containsKey("keyAlias")) {
                // Resolve the keystore path from the ROOT project dir, since
                // keystore.properties paths are relative to the repo root.
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                // Enable all three APK signature schemes for max compatibility and to
                // support smali-patch-and-resign training workflows.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true   // intentional — chain #12 (jdwp) needs this
            isMinifyEnabled = false
        }
        getByName("release") {
            // Release build: R8 minify + obfuscation ON. ProGuard rules keep the
            // Manifest-declared components, the JNI bridge, the @JavascriptInterface
            // methods, and the service api interfaces (so reflection resolves); the
            // impl classes under com.mochat.app.impl are mangled.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ---- Native build ----------------------------------------------------------
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    ndkVersion = "27.2.12479018"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // Lint: this app is deliberately vulnerable; security lints are the lesson,
    // not bugs to fix at build time.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf("META-INF/*")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}
