plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Rigged 3D people (glTF): loading, moves and poses, in pure Kotlin. The AR kit
// draws them; tools/figures/preview renders them without a phone.
kotlin {
    jvm {
        compilations.all { kotlinOptions.jvmTarget = "17" }
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
