import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

// The 3D Wilds sim + renderer, shared by the Android app (jvm) and the web
// client (wasmJs). commonMain must stay pure Kotlin: no java.*, no android.*.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        compilations.all { kotlinOptions.jvmTarget = "17" }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs() // runs commonTest under the local Node (no browser in the Codespace)
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
