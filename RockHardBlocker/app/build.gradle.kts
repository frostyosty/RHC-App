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

    flavorDimensions += "audience"

    productFlavors {
        create("rhc") {
            dimension = "audience"
            applicationIdSuffix = ".rhc"
            resValue("string", "app_name", "Rock Hard Christians")
            resValue("string", "flavor_id", "rhc")
            resValue("string", "overlay_title", "NOT TODAY.")
            resValue("string", "brick_message", "Oh no, your phone is bricked up.")
            resValue("string", "essay_prompt", "I am a grown man with a fully functioning prefrontal cortex. I will not lose a psychological battle against a glowing glass rectangle today. I am choosing strength over weakness.")
        }
        create("behaviour") {
            dimension = "audience"
            applicationIdSuffix = ".behaviour"
            resValue("string", "app_name", "Behaviour")
            resValue("string", "flavor_id", "behaviour")
            resValue("string", "overlay_title", "AVERT YOUR EYES.")
            resValue("string", "brick_message", "Let us take a minute of reflection.")
            resValue("string", "essay_prompt", "I am a woman of dignity and grace. I will not compromise my peace of mind for fleeting pixels. I am choosing my future over this moment of weakness, and I will step away from this device.")
        }
        create("bounceland") {
            dimension = "audience"
            applicationIdSuffix = ".bounceland"
            resValue("string", "app_name", "Bounceland")
            resValue("string", "flavor_id", "bounceland")
            resValue("string", "overlay_title", "WILD GLITCH APPEARED!")
            resValue("string", "brick_message", "Time out for 10 minutes!")
            resValue("string", "essay_prompt", "GAME_MODE")
        }
    }
}
