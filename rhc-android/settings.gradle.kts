pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Binaryen (wasm-opt) for netbeasts-web's production build. Kotlin would add this repository
        // itself, which FAIL_ON_PROJECT_REPOS rejects, so it's declared here (see the root build).
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen"
            patternLayout { artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}
rootProject.name = "RockHardBlocker"
include(":app", ":world-core", ":homevisits-core", ":figure-core", ":netbeasts-web")
