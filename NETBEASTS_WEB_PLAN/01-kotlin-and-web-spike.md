# 01. Kotlin 2.x, then the Wilds in a browser

**Needs:** nothing · **Size:** M
**Read first:** [README](README.md) (rules, facts); `CLAUDE.md` → "Netbeasts: the 3D Wilds"
**Status:** built 2026-11-12 on desktop Chrome; Firefox, Safari and a real phone still to try (see "Results")

## Goal

A web page, built from this repo, that shows today's Wilds and lets you walk
and steer with the mouse, touch or keyboard: no fights, no HUD beyond a
frame-time readout. It proves the three risky things before anything is built
on them: Kotlin/Wasm in real browsers, getting a frame from Kotlin onto a
canvas fast enough, and loading the autogen art.

## Part A: Kotlin 2.x (its own change)

Kotlin 1.9.22's `wasmJs` is experimental. Upgrade first, as a separate change,
because it touches the Android build too.

- Bump `org.jetbrains.kotlin.android` and `org.jetbrains.kotlin.multiplatform`
  in `rhc-android/build.gradle.kts` to the newest stable 2.x that works with
  AGP 8.3 / Gradle 8.7, or bump AGP and Gradle with it if it needs that.
  Kotlin 2.x also needs the Compose compiler plugin if any module uses
  Compose (check `app/build.gradle.kts`).
- Kotlin 2.x moved some `kotlinOptions` to `compilerOptions`. `world-core`
  uses `compilations.all { kotlinOptions.jvmTarget = "17" }`; update it and
  the same in `homevisits-core` and `figure-core`.
- The Node/Yarn settings in the root build (`download = false`,
  `nodeCommand = scripts/node-wasm.sh`): the extension names changed in
  2.x (there are separate wasm Node/Yarn extensions). Keep "use the Node on
  PATH". Drop `node-wasm.sh` if 2.x no longer passes `--experimental-wasm-*`.
- K2 is stricter in places. Fix what it flags; don't change behaviour.

**Done when (A):** all four flavors compile
(`compileGamersMaleNetbeastsReleaseKotlin`,
`compileGamersFemaleHomevisitsReleaseKotlin`,
`compileTimesaversMaleMomentumReleaseKotlin`,
`compileTimesaversFemaleMomentumReleaseKotlin`), `:world-core:jvmTest
:world-core:wasmJsNodeTest`, `:homevisits-core:jvmTest` and
`:figure-core:jvmTest` pass, and `tools/world_preview/run.sh` still passes.
The determinism pins must not move: a compiler upgrade that changes a pin
means float behaviour changed, and that has to be understood before going on.

## Part B: the spike

- New Gradle module **`netbeasts-web`** (in `settings.gradle.kts`),
  `wasmJs { browser { binaries.executable() } }`, depending on `world-core`.
  Keep it thin: everything that's a rule stays in `world-core`.
- `main()` makes `WorldMap.generate(seed, species, region)` for today (the
  seed is the date, as on the phone; use the generic region until
  [05](05-save-identity-aether.md) brings the player's), a `World`, a
  `LocalWorldSession`, and a `TerrainRenderer` at the phone's sizes (200 wide
  portrait, 320 landscape, height from the canvas aspect).
- The frame loop is `requestAnimationFrame`: `session.update(dt)`, then
  `renderer.render(...)`, then copy `fb` to the canvas.
- **The copy is the thing to measure.** `fb` is ARGB in a Kotlin `IntArray`
  (a wasm GC array, not linear memory), and `ImageData` wants RGBA bytes. A
  per-pixel loop through JS interop can cost several milliseconds for
  ~70,000 pixels. Try, in order, and keep the first that's under ~2 ms a
  frame on a mid phone:
  1. Kotlin writes into an `org.khronos.webgl.Uint8ClampedArray` /
     `Int32Array` (swapping ARGB to ABGR, which is RGBA bytes on
     little-endian) and `putImageData`.
  2. Upload as a WebGL texture once a frame and draw one quad (also gives
     free scaling with nearest-neighbour filtering).
  3. Ask the renderer to write RGBA order directly behind a flag, so the
     swap goes away.
  Scale up with CSS `image-rendering: pixelated`, like the phone's
  scaled-up look.
- **Input:** drag sideways to steer and up/down to look, as on the phone
  (`WorldView` has the feel: stiffer near the limits, drifting back to level).
  Add keyboard on desktop: A/D or arrows to steer, W/S to look.
- **Sprites:** a build step turns `drawable-nodpi/{spr,prop,fx}_*.gif` into
  PNG sprite sheets plus a JSON index (frame rects and per-frame
  durations). Python + Pillow is already the art toolchain (`sprite_studio`),
  so a script in `tools/` is natural; Node + `sharp` (used by `tools/icons`)
  also works. The web `SpriteSource` loads sheets lazily the first time a
  key is asked for and returns `null` until it's in (the renderer already
  copes with missing sprites). Keep the files out of git; the build makes
  them.
- An old-browser message: if `WebAssembly` or wasm GC isn't there, show "Your
  browser can't run this yet: try a recent Chrome, Edge, Firefox or Safari"
  instead of a blank page.

## Ask

Nothing new. (The open questions in the README come up from 02 on.)

## Interfaces (for later steps)

- **`netbeasts-web`** (`rhc-android/netbeasts-web/`), run locally with
  `./gradlew :netbeasts-web:wasmJsBrowserDevelopmentRun` (or
  `wasmJsBrowserProductionRun`, which is what was measured), built for release
  with `./gradlew :netbeasts-web:wasmJsBrowserDistribution`. The output is
  `netbeasts-web/build/dist/wasmJs/productionExecutable/`: `index.html`,
  `style.css`, `boot.js`, `netbeasts.js`, the `.wasm` (about 144 KB after
  Binaryen) and `sprites/`. All paths in it are relative, so it can be served
  from any folder (02's `/play/netbeasts/`).
  - `boot.js` checks for wasm GC and shows `#old` instead of loading
    `netbeasts.js` when it's missing.
  - `Main.kt`: `class Wilds` is the frame loop (map, session, renderer,
    sink, controls). Today's seed is `yyyyMMdd * 2654435761L` from the
    browser's local date, the region `Region.DEFAULT`, the species
    `GameData.species`. URL parameter `?sink=typed` picks the fallback sink.
  - `Controls.kt`: drag and keys, `pitch` and `tick(dt)`, the phone's feel.
  - `SheetSprites.kt`: `SheetSprites(base)` is the `SpriteSource`.
- **`GameData` is in `world-core`** now (`com.rockhard.blocker.GameData`,
  same package, moved unchanged from the app's `GameModels.kt`), with
  `GameData.species`, the list every map is made from. The phone
  (`WorldBridge`), the web and `tools/world_preview` all use it, so they
  make the same map from the same seed.
- **The sprite sheets:** `tools/web_sprites/make_sheets.py <drawable-nodpi>
  <out>` (Python 3 + Pillow) writes `<out>/sprites/<key>.png`, the frames of
  one GIF side by side, and `<out>/sprites/index.txt`, one line a sheet:
  `<key> <frameW> <frameH> <ms> <ms> ...`, one duration a frame. Frames are
  sampled exactly as `SpriteBank` plays the GIF (50 ms steps, at most 2 s,
  repeats merged into a longer frame), so the web animates like the phone.
  589 sheets, 3.2 MB (the GIFs are 8.2 MB). The Gradle task
  `:netbeasts-web:spriteSheets` runs it into `build/sprite-sheets/` and the
  build copies that into the output, so 02 doesn't need to run it itself;
  it does need Python 3 and Pillow on the build machine (`python` on Windows,
  `python3` elsewhere). Nothing generated is committed.
- **`FrameSink`** (`FrameSink.kt`): `put(fb: IntArray)`. The default is
  **`MemorySink`**: Kotlin writes the ABGR pixels into wasm linear memory
  (`withScopedMemoryAllocator`, `Pointer.storeInt`) and one JS call wraps
  that memory (`kotlin.wasm.unsafe.wasmMemory`) as the `ImageData`. The
  plan's option 1, `TypedArraySink` (each pixel set on an `Int32Array`), is
  kept as `?sink=typed`. WebGL (option 2) and a renderer flag (option 3)
  weren't needed.
- **Kotlin 2.4.21, AGP 8.5.2, Gradle 8.7** (AGP 8.5.2 is the oldest Kotlin
  2.4 accepts). Kotlin warns that 2.5 will need Gradle 8.14.4. Binaryen
  (the wasm optimiser production builds run) comes from an ivy repository
  declared in `settings.gradle.kts`; Node and Yarn come from PATH.

## Results (2026-11-12)

Frame times in ms, averaged by the page's own readout (`#stats`). Headless
Chrome on the desktop the work was done on, with Chrome's CPU throttling as
a stand-in for phones until one is tried:

| Where | Size | Render | Copy: memory | Copy: typed array | fps |
|---|---|---|---|---|---|
| Desktop | 200 x 306 (portrait) | 2.5-3.9 | 0.1 | 0.4-0.6 | 60 |
| Desktop | 320 x 120-158 (landscape) | 3.3-5.5 | 0.1 | 0.2-0.4 | 60 |
| CPU 4x slower | 200 x 306 | 8.8-10 | 0.4 | 1.5-1.7 | 58-60 |
| CPU 6x slower | 200 x 306 | 13-16 | 0.6-0.9 | 2.7-2.9 | 42-60 |

The sim is 0.1-0.3 ms. The copy is solved: writing through wasm memory is
about 4 times cheaper than the typed array and stays under 1 ms even 6x
slower. **The renderer is the cost now**, as on the phone: if a real
mid-range phone can't hold 30 fps, look at `TerrainRenderer` first, not the
copy. Screenshots showed trees, props, the track and creatures from the
sheets in the right colours, and steering with the keyboard turned the view.

**Not tried yet** (no Firefox, Safari or phone on the machine this was done
on): Firefox, Safari / iPhone and a real mid-range phone, which the "Done
when" below asks for. `./gradlew :netbeasts-web:wasmJsBrowserProductionRun`
also serves on the machine's LAN address, which is the quick way to try a
phone on the same network.

## Done when

- Part A's checks pass.
- The page walks today's Wilds in Chrome, Firefox and Safari (iPhone if
  possible), with trees, props and creatures drawn from the sheets, at a
  steady 30 fps on a mid-range phone. Write the frame-time numbers here.
- `DeterminismTest` passes on JVM and wasm after the upgrade, pins unmoved.
