plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.parallaxwallpaper"
    compileSdk = 35

    defaultConfig {
        // Unique id so it never clashes with an earlier test install.
        applicationId = "com.reykok.neonmech"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        // Fixed key committed to the repo so every GitHub build has the same signature
        // and new versions install over old ones. Personal-use key, not for Play Store.
        create("fixed") {
            storeFile = file("parallax.p12")
            storeType = "pkcs12"
            storePassword = "parallax123"
            keyAlias = "parallax"
            keyPassword = "parallax123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
