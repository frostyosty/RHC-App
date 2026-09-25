plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.multiplatform") version "1.9.22" apply false
}

// Kotlin/Wasm (world-core) would download its own Node and Yarn by adding
// project-level repositories, which FAIL_ON_PROJECT_REPOS rejects. Use the
// ones on PATH instead (the devcontainer installs Node).
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().download = false
    // Kotlin 1.9's wasm test runner passes --experimental-wasm-gc, which Node 22+
    // rejects (wasm GC is built in there); the wrapper drops it.
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().nodeCommand = file("scripts/node-wasm.sh").absolutePath
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().download = false
}
