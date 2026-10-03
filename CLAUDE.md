# RHC (Rock Hard Christianity)

A distraction blocker and "time reclamation" app with two native clients that
share logic:

| Path | What it is | Toolchain |
|---|---|---|
| `rhc-android/` | Kotlin app, package `com.rockhard.blocker`, 4 product flavors (Gamers/Timesavers × Male/Female) | Gradle 8.7, AGP + Kotlin 1.9.22, JDK 17, compileSdk 34 |
| `rhc-desktop/` | Win32/GDI C++17 app + Windows service + NSIS installer | mingw-w64 cross-compile, `makensis` |
| `rhc-common/` | Shared C++ engines (SQLite `DatabaseManager`, Momentum, ShieldRuleEngine, Leaderboard) used by desktop; mirrors the Android Kotlin logic | — |
| `sprite_studio/` | Sprite Studio (Python, port 8080, start with `./launch-sprite-studio.sh`) + sprite autogen (`sprite_studio/autogen`) | Python 3 + Pillow |
| `tools/` | Icon generator (Node/sharp), 3D world preview, helper scripts | Node, JVM |

Feature-level detail (blocking, cooldown, Momentum, Netbeasts) lives in
`rhc-android/README.md`. Read it before changing behaviour.

More docs, read only when the work calls for them:
- `ROADMAP.md`: which step of which plan comes next, across the three plans
  below, and the code they share. Read it before starting planned work.
- `NETBEASTS_PLAN.md`: what isn't built yet for Netbeasts and the Wilds, and
  the agreed order to build it in (moving the battle rules into `world-core`,
  the web client, multiplayer, then Wilds phases 4-6).
- `HOMEVISITS_PLAN/`: the plan for Homevisits, the game in the female Gamers
  APK (walking your home with the camera, visitors, the emotions engine). It
  has one file per step: read its `README.md`, then only your step's file.
- `NETBEASTS_GO_PLAN/`: the plan for Netbeasts outdoors on foot (beasts at
  real places, the step bank, fights through the camera). Laid out the same
  way: its `README.md`, then only your step's file.
- `sprite_studio/CLAUDE.md`: how creatures, props and battle effects are
  drawn (the cacheon style). Read it before drawing or adding any art.

## Build & release

- `./release.sh`: interactive menu, then always publishes a GitHub release to
  `frostyosty/htc-downloads-rhc` with a bumped `vX.Y.Z` tag. Option `1`
  (Desktop + all four Android flavors) is the default and the usual choice.
  Before building it quicksaves by running `zz_quicksave.txt` (`git add -A`,
  commit, `pull --rebase`, `push` to `origin/main`), so edit that file to
  change what the quicksave does.
  **Running it commits and pushes everything and publishes a release, so don't
  run it unless asked.**
- Desktop only: `bash rhc-desktop/build.sh` (run from the repo root; it `cd`s to `/workspaces/RHC-App`).
- One Android flavor: `cd rhc-android && ./gradlew assemble<Flavor>Release`,
  e.g. `assembleGamersMaleNetbeastsRelease`. Faster check without signing:
  `./gradlew compileGamersMaleNetbeastsReleaseKotlin`.
- Built `.apk`/`.exe` files, `local.properties` and `*.jks` are gitignored.
  The release keystore `rhc-android/app/rockhard-keystore.jks` is not in git.
- `zz_quicksave.txt` is the user's add/commit/pull/push script. Don't run it
  unless asked.

## Environment

`.devcontainer/` sets up a fresh Codespace (JDK 17, Android SDK 34 at
`~/android`, mingw-w64, NSIS, and the tools below). `post-create.sh` can be
re-run safely. It restores the keystore from the `RHC_KEYSTORE_B64` Codespaces
secret if that secret is set.

## Tools: when to use them

- **`rg` (ripgrep)**: all text search. `rg -t kotlin 'ShieldRuleEngine'`,
  `rg -t cpp 'WinHttp' rhc-desktop rhc-common`. Skip `rhc-common/src/sqlite3.c`
  (vendored, huge) with `-g '!sqlite3.c'`.
- **`fd`**: finding files by name. `fd -e kt Guardian rhc-android`,
  `fd -e cpp . rhc-desktop/src/ui`. It respects `.gitignore`, so build output
  stays out of results.
- **`shellcheck`**: run after editing any `.sh` (`release.sh`,
  `rhc-desktop/build.sh`, `tools/**/*.sh`, `rhc-android/scripts/*.sh`,
  `.devcontainer/post-create.sh`). `bash -n` only checks syntax; shellcheck
  catches quoting and word-splitting bugs. Aim for zero warnings.
- **`clang-tidy` + `bear`**: static analysis for the desktop/common C++.
  `bear -- bash rhc-desktop/build.sh` writes `compile_commands.json` (gitignore
  it, it's local), then e.g.
  `clang-tidy -p . rhc-desktop/src/Guardian.cpp --checks='-*,bugprone-*,clang-analyzer-*'`.
  clang-tidy doesn't know mingw's Windows headers by default. If it can't find
  `windows.h`, add `--extra-arg=--target=x86_64-w64-mingw32`. Use it on files
  you've changed, not the whole tree, and never on `sqlite3.c`.
- **`ccache`**: `rhc-desktop/build.sh` uses it automatically when it's
  installed, and just compiles normally when it isn't. A full rebuild with
  nothing changed takes about 2.5s instead of about 85s. It only helps because
  each source file is compiled to its own object in `rhc-desktop/obj/` before
  linking, so keep that structure: new `.cpp` files go in the
  `DESKTOP_OBJS`/`COMMON_OBJS` loops, not onto the link line. Check the hit
  rate with `ccache -s`.

## Netbeasts: the 3D Wilds

The first-person exploring mode, the default way to explore in the Gamers
flavors. The sim and renderer are in `rhc-android/world-core` (Kotlin
Multiplatform: jvm for the app, wasmJs for the web); the Android side is in
`rhc-android/app/.../world/`. Section 5.1 of `rhc-android/README.md` is the
source of truth for how it plays and how it's built: read it before changing
exploring. Rules that keep the design working:

- `world/sim/` and `world/render/` must stay **pure Kotlin with no `android.*`
  imports**. The sim is the future multiplayer authority: keep it deterministic
  (fixed ticks, inputs only, its own seeded RNG, no wall-clock time), and put
  anything a remote player must see into `EntitySnapshot`.
- **Sim trig goes through `DetMath`**, never `kotlin.math`'s
  `sin`/`cos`/`atan2`/`hypot`, which differ in the last bit between the JVM and
  wasm. Map generation avoids trig altogether (rejection sampling, smoothstep
  waves). `DeterminismTest` pins the map, a coastal NZ map and a scripted fight:
  run `./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest`, and re-pin
  only when you meant to change generation or the sim.
- The map is `WorldMap.generate(seed, species, region)`. The `Region` (from
  `RegionProbe`: sea side, hills, flora, house style) is part of the map's
  identity, so keep it small whole numbers and enums.
- **The land is endless, made a big tile at a time.** `WorldMap.tile(tx, ty)`
  makes a 96 × 96 `MapTile` when it's first needed; you start in the middle of
  tile (0, 0). A tile must come from the seed, the region and its own
  coordinates only, never from its neighbours or the order tiles were made in:
  the sim, the renderer, the minimap and the app's background tile-maker all
  ask at different times. Whatever crosses a join (ground, woods, tracks)
  comes from noise or nodes hashed from world coordinates. Sim state that
  depends on tiles goes by where the players are (`World.wake`), not by which
  tiles exist.
- **Grounded, not fantastical.** The user wants the Wilds to feel like their
  real surroundings (they're in Tauranga, NZ), so no giant fantasy props.
  Creatures are the only strange thing out there.
- **Beasts never ambush.** They shy away from you (`World.SHY_SPEED`, slower
  than walking) and a fight only starts when you catch one in front of you, so
  fighting is always the player's choice. The preview soak checks both a
  wanderer (no fights) and a hunter.
- **No walls or colliders.** The player auto-walks and must never get stuck;
  obstacles are walk-through billboards and water is wadeable. The walk
  sidesteps trunks (plan C) but never stops or turns for them.
- Test sim/render changes with `bash tools/world_preview/run.sh` (JVM soak test
  + preview PNGs, including `map_*.png` overviews and the fight sequence) before
  building an APK.
- Fights stay in the world (`world/WorldFight.kt`). The rules are still the
  battle code in `engines/combat/`; it reaches the world only through the hooks
  in `playSpriteAnim`, `playFx`, `showBattleArena`, the battle timer and
  `printLog`. Don't fork the rules into the world code.
- New creature or scenery art comes from `sprite_studio/autogen`
  (`designs.py`; props are `prop_*.gif`), not hand-made files. Two renderer
  constants must match autogen: `TerrainRenderer.CREATURE_CANVAS` (42/32, the
  creature frame padding) and `TREE_LOD` (`D` in `scenery.py`).

## Conventions

- Match the surrounding style: emoji-prefixed `echo` status lines in shell
  scripts, and `set -euo pipefail` in new scripts.
- Keep the Android Kotlin logic and the `rhc-common` C++ engines in sync. A
  change to the rules or scoring in one usually needs the same change in the other.
- Android's runtime permission prompts come from the permission controller,
  which the Guardian blocks as anti-tamper. Ask the way
  `LocationEngine.requestPermission` does: set `ALLOW_PERMISSION_PROMPT_UNTIL`
  (`ShieldRuleEngine` then lets only that package through) and clear it in
  `onRequestPermissionsResult`. Don't use `ALLOW_SETTINGS_UNTIL` for this,
  because it unlocks all of Settings.
