# 05. The AR kit

**Needs:** Homevisits 01 (the flavor's own source set) · **Size:** S
**Read first:** [README](README.md), rule 8;
`HOMEVISITS_PLAN/02-ar-foundation.md` (what the camera code is for and how far
it has got); `CLAUDE.md` → Conventions (permissions past the Guardian).

## Goal

The camera and ARCore code that Homevisits started becomes one kit that both
Gamers APKs build. Nothing new is shown to a player. Homevisits' AR test
screen works as it did.

Do this before Homevisits 02 goes any further. Today the kit is about 320
lines, so moving it is cheap. After 02, 04 and 05 of that plan it won't be.

## What exists today

In `app/src/gamersFemaleHomevisits/java/com/rockhard/blocker/homevisits/ar/`:

| File | What it does now | Still to come (Homevisits 02) |
|---|---|---|
| `ArTier.kt` | Says whether the phone has ARCore | — |
| `CameraPermission.kt` | Asks for the camera past the Guardian | The "Android denied it without asking" check that `LocationEngine` has |
| `ArSession.kt` | Makes and configures the session (planes, depth, 30 fps) | The per-frame callback |
| `BackgroundRenderer.kt`, `ArRenderer.kt` | Draws the camera image; counts planes | Sprites: billboards, wall quads, floor decals |
| `Surfaces.kt` | A tap to a plane hit | Telling a floor from a bench, table, seat |
| `Tracking.kt` | Turns ARCore's trouble into a hint | The stairs warning |

## Build

1. **Move it.** `app/src/ar/java/com/rockhard/blocker/ar/`, package
   `com.rockhard.blocker.ar`. In `app/build.gradle.kts`, add that directory to
   both Gamers flavors' source sets (`java.srcDir("src/ar/java")` on
   `gamersMaleNetbeasts` and `gamersFemaleHomevisits`; the Kotlin plugin
   compiles the `.kt` files there too), and give Netbeasts the ARCore
   dependency Homevisits already has
   (`add("gamersMaleNetbeastsImplementation", "com.google.ar:core:…")`, the
   same version). The Timesavers APKs get neither.
2. **The Netbeasts manifest.** `app/src/gamersMaleNetbeasts/AndroidManifest.xml`
   with what the Homevisits one has for the camera: the `CAMERA` permission,
   `android.hardware.camera.ar` not required, and the `com.google.ar.core`
   meta-data set to `optional`. ([02](02-step-bank.md) adds its permission to
   the same file.)
3. **One way to ask for a permission** (`app/src/main/.../PermissionGate.kt`).
   `LocationEngine` and `CameraPermission` each have their own copy of
   "set `ALLOW_PERMISSION_PROMPT_UNTIL`, ask, clear it on the answer", and the
   step counter would be a third. Put it in one place:
   `PermissionGate.request(activity, permissions, code)` and
   `PermissionGate.onResult(activity, permissions): Boolean`, with
   `LocationEngine`'s under-500 ms check and its toast. `LocationEngine` and
   `CameraPermission` then call it. `ShieldRuleEngine` doesn't change.
4. **What stays out of the kit.** The kit knows nothing about either game.
   - Homevisits keeps what's about rooms: surface kinds by height, the beat
     runner, spot capture.
   - `Tracking`'s wording becomes a table the game passes in. "Can you switch
     a light on?" is right in a lounge and wrong in a park.
5. **Sprites, once.** When Homevisits 02 builds billboards (its Build item 7),
   it builds them here, decoding the autogen GIFs the way
   `world/SpriteBank.kt` does. [06](06-ar-encounters.md) then draws beasts
   with the same code that draws Rachel.
6. **Docs.** Add a line for the kit to `CLAUDE.md`, and change the two paths
   in `HOMEVISITS_PLAN/README.md` ("How it fits together") and
   `HOMEVISITS_PLAN/02-ar-foundation.md`.

## Interfaces (for later steps)

These are Homevisits 02's interfaces under their new names. That file's
Interfaces section stays the full description.

- `ar.ArTier`, `ArTierChecker.detect(context)`.
- `ar.CameraPermission.hasPermission / requestPermission / onPermissionResult`.
- `ar.ArSession`: `setup()`, `resume()`, `pause()`, `destroy()`, `tier`.
- `ar.ArRenderer`: the camera background now; sprites at world poses when
  Homevisits 02 adds them.
- `ar.Surfaces.hit(frame, x, y)`; `ar.Tracking.getHint(camera, phrases)`.
- `PermissionGate.request / onResult` (in `main`, for every flavor).

## Done when

- All four flavors compile:
  `cd rhc-android && ./gradlew compileGamersMaleNetbeastsReleaseKotlin compileGamersFemaleHomevisitsReleaseKotlin compileTimesaversMaleMomentumReleaseKotlin compileTimesaversFemaleMomentumReleaseKotlin`.
- The Homevisits APK's AR test screen (seven taps on the title) opens as
  before.
- The Timesavers APKs don't ask for the camera: check their merged manifests.
- The location prompt still works past the Guardian (emulator).
