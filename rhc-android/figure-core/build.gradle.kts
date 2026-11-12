plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// Rigged 3D people (glTF): loading, moves and poses, in pure Kotlin. The AR kit
// draws them; tools/figures/preview renders them without a phone.
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
