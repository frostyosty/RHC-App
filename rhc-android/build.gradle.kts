@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) // the Binaryen settings below

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
}

// Kotlin/Wasm (world-core, netbeasts-web) would download its own Node and Yarn
// by adding project-level repositories, which FAIL_ON_PROJECT_REPOS rejects.
// Use the ones on PATH instead (the devcontainer installs Node and Yarn).
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().download.set(false)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec>().download.set(false)
    }
    // Binaryen is still downloaded, but from the repository declared in settings.gradle.kts
    plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
