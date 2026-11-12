import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// The Wilds in a browser (NETBEASTS_WEB_PLAN). A thin shell: the canvas, input
// and sprite loading. Every rule stays in world-core.
//   ./gradlew :netbeasts-web:wasmJsBrowserDevelopmentRun   serves it locally
//   ./gradlew :netbeasts-web:wasmJsBrowserDistribution     builds the static files
//     into build/dist/wasmJs/productionExecutable/
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("netbeasts")
        browser {
            commonWebpackConfig { outputFileName = "netbeasts.js" }
        }
        binaries.executable()
    }
    sourceSets {
        all { languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop") } // js() and DOM events are the whole shell
        wasmJsMain.dependencies {
            implementation(project(":world-core"))
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
        }
    }
}

// The autogen GIFs as PNG sprite sheets (tools/web_sprites/make_sheets.py, Python 3 + Pillow,
// the art toolchain). They're made into build/ and served as sprites/, never committed.
val spriteSheets by tasks.registering(Exec::class) {
    val script = rootProject.file("../tools/web_sprites/make_sheets.py")
    val gifs = rootProject.file("app/src/main/res/drawable-nodpi")
    val out = layout.buildDirectory.dir("sprite-sheets")
    inputs.file(script)
    inputs.dir(gifs)
    outputs.dir(out)
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    doFirst { delete(out) } // a GIF that's gone takes its sheet with it
    commandLine(python, "-I", script.absolutePath, gifs.absolutePath, out.get().asFile.absolutePath)
}
kotlin.sourceSets.named("wasmJsMain") { resources.srcDir(spriteSheets) }
