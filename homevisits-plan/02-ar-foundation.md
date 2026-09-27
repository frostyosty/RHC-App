# 02. AR foundation

**Needs:** 01 · **Size:** M · **Track:** camera
**Read first:** [README](README.md); `CLAUDE.md` → Conventions (how to ask for an
Android permission past the Guardian). Nothing else.

## Goal

In the Homevisits APK, a hidden AR test screen:

- shows the camera and finds floors, benches and walls;
- lets you tap to drop a sprite that stays put as you walk around;
- tells you in plain words when tracking struggles.

The camera permission and the ARCore install both work with the Guardian running.
Before anything is built on top of this, the user tries it at home, and we write
down what works.

## Why ARCore, and which parts of it

- ARCore gives six-degree tracking (where the phone is and which way it points),
  planes (floors, tables, walls), hit tests (what a tap on the screen touches),
  depth on many phones, a light estimate, and recording and playback of sessions.
  It all runs on the phone.
- **Not used:** Cloud Anchors, because they upload what the camera sees (README
  rule 1). Geospatial and Scene Semantics, because they're for outdoors.
- ARCore sessions don't remember anything between app runs. Step 05 handles coming
  back.
- **No Sceneform or SceneView.** We only draw flat sprites and pictures, and a small
  GL renderer is less to carry. ARCore's `hello_ar_kotlin` sample (Apache 2.0) has
  a camera-background renderer and plane drawing to start from. Keep its licence
  header on anything you copy.

## Build

1. **The dependency, Homevisits only.** Add
   `add("gamersFemaleHomevisitsImplementation", "com.google.ar:core:<current>")` to
   `app/build.gradle.kts`, with the current version from Google's Maven. The app's
   minSdk (26) is already above what ARCore needs.
2. **The flavor manifest** (`app/src/gamersFemaleHomevisits/AndroidManifest.xml`).
   Add:
   - the `CAMERA` permission;
   - `<uses-feature android:name="android.hardware.camera.ar" android:required="false"/>`;
   - `<meta-data android:name="com.google.ar.core" android:value="optional"/>`.

   "AR optional" means the APK installs on any phone and checks at runtime. The APKs
   are sideloaded, so Play Store filtering doesn't matter.
3. **What this phone can do.** `ar/ArTier.kt` asks `ArCoreApk.checkAvailability` (it
   can answer "still checking" at first, so ask again for a moment) and
   `Session.isDepthModeSupported`:
   - `FULL`: ARCore with depth.
   - `BASIC`: ARCore without depth.
   - `NONE`: no ARCore, or it can't be installed (no Play Store). These phones get
     the 2D visits of step 09. Until 09 exists, show a kind "not on this phone yet"
     screen.
4. **The camera permission past the Guardian.** `ar/CameraPermission.kt` follows
   `LocationEngine.requestPermission` and `onPermissionResult`:
   1. Show the in-story explanation first.
   2. Set `ALLOW_PERMISSION_PROMPT_UNTIL`.
   3. Call `requestPermissions(CAMERA)`.
   4. Clear it again in `onRequestPermissionsResult`.

   An answer in under 500 ms means Android denied it without asking the player.
   Never use `ALLOW_SETTINGS_UNTIL` for this.
5. **Installing ARCore.** `ArCoreApk.requestInstall(activity, true)` sends the
   player to the Play Store for "Google Play Services for AR". Try it with the
   Guardian on, both normally and with No Internet mode on (No Internet blocks
   `vending`). If it's blocked, add an `ALLOW_STORE_UNTIL` check in
   `ShieldRuleEngine.evaluate`, next to the permission-prompt check. It lets only
   `com.android.vending` through for a few minutes. Set it just before
   `requestInstall`, and clear it when the player comes back.
6. **The session.** `ar/ArSession.kt` creates, resumes and pauses the session along
   with the activity. Its config:
   - planes, horizontal and vertical;
   - depth `AUTOMATIC` when the phone supports it;
   - light estimation `AMBIENT_INTENSITY`;
   - instant placement `LOCAL_Y_UP`;
   - focus `AUTO`;
   - a 30 fps camera config, which uses less battery and makes less heat than 60.

   The screen is portrait only. Call `setDisplayGeometry` whenever the layout
   changes.
7. **Drawing.** `ar/ArRenderer.kt` uses a `GLSurfaceView` (GLES 3). It draws
   ARCore's camera image as the background, then quads on top:
   - **billboards** (people, parcels, plants): upright, turned to face the camera
     around the vertical axis only, feet on the floor;
   - **wall quads** (paintings): flat on the wall;
   - **floor decals**: contact shadows and glowing tap targets.

   How to draw them:
   - Use nearest-neighbour filtering so pixel art stays crisp, and premultiplied
     alpha.
   - Tint sprites by the light estimate so they don't glow in a dim room.
   - Decode sprites from the autogen GIFs the way `world/SpriteBank.kt` does it
     (`android.graphics.Movie`, sampled on a fixed clock), and upload one texture
     atlas per animation.
   - On `FULL` phones, add a hook for occlusion: hiding sprite pixels that are
     behind real things, using the depth image. Step 10 tunes it.

   Buttons, prompts and dialogue are ordinary Android views on top of the GL view.
8. **Surfaces.** `ar/Surfaces.kt` turns a tap on the screen into what it hit, with a
   pose and the way it faces:

   | Kind | What it is |
   |---|---|
   | `FLOOR` | the floor |
   | `BENCH` | a flat surface 0.8–1.0 m above the floor |
   | `TABLE` | 0.55–0.8 m above the floor |
   | `SEAT` | 0.35–0.55 m above the floor |
   | `WALL` | a vertical surface |
   | `OTHER` | anything else |

   The floor is the lowest large upward-facing plane under the camera. A hand-held
   phone is usually 1.1–1.7 m above it. All heights are starting values; tune them
   from the at-home test.

   Plain painted walls are the known hard case, because ARCore needs texture to find
   vertical planes. Try these in order:
   1. a depth hit (`FULL` phones only);
   2. a vertical plane;
   3. a plane fitted to feature points near the tap (door frames, switches and
      pictures give points);
   4. Instant Placement at arm's length, assuming the wall faces the camera.

   Step 04 wraps these in the story.
9. **When tracking struggles.** `ar/Tracking.kt` turns ARCore's reason into an
   in-character hint:

   | ARCore's reason | What the player sees |
   |---|---|
   | too dark | "It's a bit dark in here. Can you switch a light on?" |
   | moving too fast | "Whoa, slow down!" |
   | nothing to see | "Point it at something with a bit more going on." |
   | camera unavailable | AR closes politely |

   ARCore needs some movement before it can start tracking. That's one reason the
   first beat of every session is a walk.

   **Stairs:** if the camera moves up or down faster than about 0.25 m/s for a
   second, hide the text and say "Careful on the stairs" (README rule 6).
10. **The debug screen.** Open it by tapping the Homevisits title seven times. It
    shows:
    - the camera, with outlines of the planes found;
    - a HUD with the tier, the tracking state and reason, fps, planes by kind, the
      floor height, the camera's height above the floor, whether depth is on, the
      battery level and the temperature;
    - tap anywhere to drop the `player` sprite on whatever you hit;
    - [Record] and [Export report] buttons. The report is JSON with no images, sent
      through the share sheet.
11. **Recording.** ARCore can record a session (camera and sensors) to an MP4 and
    play it back into a session later. So one walk through the user's home could be
    replayed without walking it again. It's video of their home, so:
    - it only records when the user presses Record;
    - the file stays in app-private storage and is deleted from the phone after
      use;
    - it's never committed. If any are copied into the Codespace, add
      `tools/homevisits_sim/recordings/` to `.gitignore`.

## At home (the user, on their phone)

Install a build of the Homevisits APK, turn on the preview switch (01) and open the
debug screen. Then:

1. **The camera prompt.** Did it appear with the Guardian on? Was ARCore already
   installed? If not, did the Play Store open?
2. **Tracking.** Walk from the lounge to the kitchen and on to a bedroom, holding
   the phone naturally. Did tracking hold? Where did it drop: a hallway, a dark
   room, the stairs?
3. **Surfaces.** How long did it take to find the floor, the kitchen bench, a table
   and a plain wall? Did it find the wall at all without depth?
4. **Staying put.** Drop a sprite in each room, walk away and come back. Is each
   sprite still in the same place? About how far off is it?
5. **Battery.** After ten minutes of use, how much battery was used, and how warm is
   the phone?
6. **Evening.** Do the same in the evening with the lights on.

Write the answers under Findings below.

## The emulator

The Android emulator can run ARCore with a virtual room (an x86_64 image,
`-camera-back virtualscene`, and Google's ARCore APK for the emulator). It wants GPU
graphics, though, and the Codespace only has software rendering. Try it once and
write the result under Findings. If it works, playing back the user's recordings
could become a regression test.

## Interfaces (for later steps)

- `ArTier.detect(activity)`; `CameraPermission.ensure(activity, onResult)`.
- `ArSession`: the lifecycle, and a per-frame callback with the camera pose, the
  tracking state and hint, the light estimate, and access to the CPU grey image and
  the point cloud (step 05 uses those two).
- `Surfaces.hit(x, y): SurfaceHit?` (the kind, the pose, the way it faces, the
  height above the floor) and `Surfaces.floorY`.
- `ArRenderer`: add, move and remove sprites (billboard, wall or decal) at world
  poses, each with an animation, a facing and a tint.
- `Tracking`: the current hint (or null), and the stairs flag.

## Ask

- **Phones without ARCore.** Default: the 2D visits (09). The alternative is a
  camera-only mode later, where things are fixed to compass directions and you
  can't walk around them.
- **Camera permission permanently denied.** Android then only lets you change it in
  Settings, which the Guardian locks. Default: explain, and use the 2D visits. The
  alternative is a narrow allowance for our own App Info page.

## Findings

(Filled in from the at-home test and the emulator try.)
