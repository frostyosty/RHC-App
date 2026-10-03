# 01. Groundwork

**Needs:** nothing · **Size:** M
**Read first:** [README](README.md); `CLAUDE.md` → "Netbeasts: the 3D Wilds"
(the sim rules) and Conventions (asking for a permission past the Guardian).

## Goal

With the outdoor game switched on, the phone follows where you are while the
game is open, and stops when it isn't. The pure code that names places and
times exists and is tested. A fake walk can be played without a phone. Nothing
is shown to the player yet beyond a debug line.

Ask the four "all" questions in the README before starting.

## 1. The switch

- Settings (`MainActivity.openSettingsMenu`) gets a checkbox next to "Explore
  Netbeasts in 3D": "Netbeasts outdoors", pref `GO_MODE`, default off, shown
  only when `Flavor.isGamers && !Flavor.homevisits()`.
- With it on, the Netbeasts screen gets a way in under the Wilds window (step
  03 builds what it opens). Until then it opens the debug line of part 5.

## 2. Places and time as whole numbers (`world-core`, `world/go/`)

Rule 6 needs every phone to name a place the same way, so nothing here uses
floating point to decide which cell you're in.

- **`GeoPoint(latE6: Int, lonE6: Int)`**: millionths of a degree. The app
  rounds a fix into one; the core never sees a `Double` latitude.
- **`GeoGrid.cell(p): GeoCell(row, col)`**. A cell is about 50 m square.
  - `row = floorDiv(latE6, 450)` (450 millionths of a degree of latitude is
    about 50 m everywhere).
  - Longitude narrows toward the poles, so the column width depends on the
    row's latitude band: one band per whole degree, width
    `round(450 / DetMath.cos(band's middle latitude))`, at most 5,000. Rows are
    then courses of bricks: columns don't line up across a band's edge, and
    nothing needs them to.
  - `GeoGrid.around(cell, n)`: the cells within `n` of it, across band edges.
- **`GeoGrid.metres(a, b)`** and **`bearing(a, b)`**: flat-earth over these
  short distances, with `DetMath` (`hypot`, `atan2`, `cos`). 0 is north,
  clockwise, the way `Region.seaOctant` counts.
- **`GoClock(day: Int, slot: Int)`**: days since 1970 and the half hour of the
  day (0-47), both in UTC so that two phones agree. The app passes it in. The
  core never reads a clock.
- **`GoHash`**: lift `mix` and the lattice `hash` out of `WorldMap` (they're
  private there) into something both can call, without changing what
  `WorldMap` gets from them. `DeterminismTest`'s pins must not move.
- Tests in `commonTest`: known points land in known cells (Tauranga, London,
  the equator, 60° south, both sides of a band edge and of longitude 180);
  `metres` is within 1% of a hand-checked distance.

## 3. Following where you are (`app/src/main/.../go/GoTracker.kt`)

`LocationEngine` only takes single fixes. Add following, and keep the asking in
`LocationEngine` where it is:

- `GoTracker.start(activity, onFix)` in `onResume`, `stop()` in `onPause`.
  Never from a service: the README's "Not planned" list rules out finding you
  while the game is closed.
- Ask the GPS provider, and the fused one on Android 12 and up, for a fix
  every 2 s or 3 m. Drop fixes that claim to be worse than about 30 m, and
  ones older than 10 s.
- Each fix becomes `GoFix(point: GeoPoint, accuracyM: Int, speedMmS: Int,
  atMs: Long)`. Take the speed from `Location.speed` when the fix has one,
  else from the last two fixes.
- **Precise location.** The radar needs it, and the weather didn't. On Android
  12 and up the player can allow only approximate. Add
  `LocationEngine.hasPrecise(context)` and ask in the game's own words first
  ("To find beasts on your street I need to know which street"), then with
  `LocationEngine.requestPermission`. Approximate only: say so, and leave the
  outdoor game off.
- **Which way you're facing.** `Sensor.TYPE_ROTATION_VECTOR`, turned to true
  north with `GeomagneticField`. While you're walking faster than 1 m/s, use
  the fixes' own bearing instead, because a phone swinging in a hand makes a
  poor compass.
- Keep the last fix as `LocationEngine` already does. Save nothing else about
  where you've been (rule 5).

## 4. Fake walks (`tools/go_sim/`)

Copy how `tools/world_preview/run.sh` compiles `world-core` with the bare
Kotlin compiler. `GoSim.kt` makes lists of `GoFix` and runs them through
whatever the core has so far:

- a stroll round a block at 1.4 m/s, with a few metres of jitter;
- standing still for two minutes with a fix that wanders 10 m;
- a car at 14 m/s;
- a fix that jumps 300 m and comes back.

In this step it prints the cells each walk crossed and the distance each one
adds up to. The stroll must come out within 10% of its true length, and
standing still within 20 m of nothing. Later steps add to it.

## 5. The debug line

Until step 03, the way in shows one line of text, updated on each fix: the
cell, the accuracy, the speed, the heading, and the provider. It's how the
user checks this step on a walk.

## On a walk (the user, on their phone)

1. Did Android's location prompt appear with the Guardian on, and did it offer
   "Precise"?
2. Walk to the end of the street and back. Does the cell change about every
   50 m? Is the speed about right? Does the heading follow you?
3. Stand still for a minute. Does the cell stay put?
4. Indoors: what does the accuracy read?

Write the answers under Findings.

## Interfaces (for later steps)

- `GeoPoint`, `GeoCell`, `GeoGrid.cell / around / metres / bearing`.
- `GoClock(day, slot)`; `GoHash`.
- `GoFix(point, accuracyM, speedMmS, atMs)`.
- `GoTracker.start(activity, onFix)`, `stop()`, `heading(): Double?`.
- `LocationEngine.hasPrecise(context)`.
- `GameActivity.goEnabled` (the `GO_MODE` pref).
- `bash tools/go_sim/run.sh`, and the four fake walks as functions later steps
  reuse.

## Done when

- `./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest` passes, with the
  existing pins unchanged.
- `bash tools/go_sim/run.sh` passes its two checks.
- All four flavors compile, and with the switch off nothing has changed.
- On the emulator, `adb emu geo fix` in a loop moves the debug line's cell.

## Findings

(Filled in from the walk.)
