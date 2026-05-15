plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    buildFeatures {
        buildConfig = true
    }
    namespace = "com.rockhard.blocker"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.rockhard.blocker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("release") {
            storeFile = file("rockhard-keystore.jks")
            storePassword = "rockhard123"
            keyAlias = "key0"
            keyPassword = "rockhard123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }

    flavorDimensions += "version"
    
    productFlavors {
        create("gamersMaleNetbeasts") {
            dimension = "version"
            applicationIdSuffix = ".netbeasts"
            resValue("string", "app_name", "RHC")
            resValue("string", "flavor_id", "rhc") 
            resValue("string", "overlay_title", "NOT TODAY.")
            resValue("string", "brick_message", "Stay strong. Defend your Netbeasts.")
            resValue("string", "essay_prompt", "GAME_MODE")
        }
        create("gamersFemaleHomevisits") {
            dimension = "version"
            applicationIdSuffix = ".homevisits"
            resValue("string", "app_name", "RHC")
            resValue("string", "flavor_id", "female_gamers")
            resValue("string", "overlay_title", "AVERT YOUR EYES.")
            resValue("string", "brick_message", "Take a deep breath and step away.")
            resValue("string", "essay_prompt", "GAME_MODE")
        }
        create("timesaversMaleMomentum") {
            dimension = "version"
            applicationIdSuffix = ".momentum_m"
            resValue("string", "app_name", "RHC")
            resValue("string", "flavor_id", "momentum_male")
            resValue("string", "overlay_title", "MAINTAIN MOMENTUM.")
            resValue("string", "brick_message", "Your time is valuable. Protect it.")
            resValue("string", "essay_prompt", "MOMENTUM_MODE")
        }
        create("timesaversFemaleMomentum") {
            dimension = "version"
            applicationIdSuffix = ".momentum_f"
            resValue("string", "app_name", "RHC")
            resValue("string", "flavor_id", "momentum_female")
            resValue("string", "overlay_title", "MAINTAIN MOMENTUM.")
            resValue("string", "brick_message", "Your time is valuable. Protect it.")
            resValue("string", "essay_prompt", "MOMENTUM_MODE")
        }
    }
}