plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rockhard.blocker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rockhard.blocker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // THE FIX: Force both compilers to use Java 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint { abortOnError = false }

    flavorDimensions += "audience"

    productFlavors {
        create("bro") {
            dimension = "audience"
            applicationIdSuffix = ".bro"
            resValue("string", "app_name", "Rock Hard Christians")
            resValue("string", "brick_message", "Oh no, your phone is bricked up.")
            resValue("string", "overlay_title", "Not today, brother.")
        }
        create("puritan") {
            dimension = "audience"
            applicationIdSuffix = ".puritan"
            resValue("string", "app_name", "A Gentle Shield of Purity")
            resValue("string", "brick_message", "Oh heavens! Let us take a minute of silent reflection.")
            resValue("string", "overlay_title", "Avert thine eyes, sister.")
        }
        create("child") {
            dimension = "audience"
            applicationIdSuffix = ".child"
            resValue("string", "app_name", "Family Web Filter")
            resValue("string", "brick_message", "Device locked for 60 seconds.")
            resValue("string", "overlay_title", "Website Blocked.")
        }
    }
}
