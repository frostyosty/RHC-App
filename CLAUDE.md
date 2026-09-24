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

## Build & release

- `./release.sh`: interactive menu, then always publishes a GitHub release to
  `frostyosty/htc-downloads-rhc` with a bumped `vX.Y.Z` tag. Option `1`
  (Desktop + all four Android flavors) is the default and the usual choice.
  Before building it quicksaves (the `zz_quicksave.txt` steps: `git add -A`,
  commit, `pull --rebase`, `push` to `origin/main`).
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

`rhc-android/.../world/` is the first-person exploring mode, the default way to
explore in the Gamers flavors. The plan and roadmap are in section 5.1 of
`rhc-android/README.md`, which is the source of truth: read it before changing
exploring. Rules that keep the design working:

- `world/sim/` and `world/render/` must stay **pure Kotlin with no `android.*`
  imports**. The sim is the future multiplayer authority: keep it deterministic
  (fixed ticks, inputs only, its own seeded RNG, no wall-clock time), and put
  anything a remote player must see into `EntitySnapshot`.
- **No walls or colliders.** The player auto-walks and must never get stuck;
  obstacles are walk-through billboards and water is wadeable.
- Test sim/render changes with `bash tools/world_preview/run.sh` (JVM soak test
  + preview PNGs) before building an APK.
- New creature or scenery art comes from `sprite_studio/autogen`
  (`designs.py`; props are `prop_*.gif`), not hand-made files.

## Netbeast sprite art: the cacheon style

`cacheon` in `sprite_studio/autogen/designs.py` is the reference for how every
creature should look and be built. The user signed off on it. How it was made:

- **Look.** Dark and hard, not cute. Small head relative to the body, long
  limbs, angular armour plates (`p.poly`, not round `p.ell` blobs), a
  wedge-shaped snout with teeth, narrow slit eyes that meet in a V from the
  front and turn red (`#FF4D5E`) when angry or hurt, and spikes or blades on
  ears, spine and tail. Deep palette (steel `#4A5263`, plate `#646E82`, joints
  `#2A303C`, near-black `#15181F`/`#0B0D12`) with one glowing accent colour for
  the creature's theme (cacheon's is cyan) on vents, ear tips and eyes. Keep the
  colours in a small per-creature kit class (`CacheonKit`).
- **64 grid.** `@design(..., size=64, views=ALL_VIEWS)`. The feet rest on
  `p.ground` (size - 3). Use helpers that take `p.size`/`p.ground`, not
  hardcoded 32-grid numbers. `dleg` draws a jointed leg (a back-bent hind leg with
  a thigh, or a near-straight front leg) with a 3px walk stride.
- **5 views.** Write one function per view (`_cacheon_side`, `_front`, `_fq`,
  `_bq`, `_back`) and dispatch on `s.view`. Side and the ¾ views face right.
  Front and back are drawn as the left half only, and the Painter mirrors them.
  To draw something off-centre in a mirrored view, turn `p.mirror` off around
  it, the way cacheon's back-view tail does. Draw from back to front: tail, far
  legs, body, near legs, then the head (`fq`), or the far legs and head first
  in `bq` and `back`, where the rump is nearest. Share parts between views
  where you can (`cacheon_head_side`).
- **Poses.** Every view has to handle `s.step` (walk), `s.eyes`
  (open/closed/hurt/happy/angry), `s.mouth` (open jaw showing teeth) and `s.t`
  (glow flicker). Autogen builds every animation from those, including `turn`,
  the 8-direction spin.
- **Loop.** Iterate visually, not blind:
  1. Render the views with `python3 sprite_studio/autogen/autogen.py --turnaround views.png --only <row>`,
     and a big side-by-side of normal and `Pose(mouth=True, eyes='angry')`,
     then look at the images.
  2. Fix whatever reads badly: a floating head, pillar legs, a featureless
     back of the head.
  3. Repeat until every view reads as the same creature.
  4. Write the GIFs with `autogen.py --only <row>` (it must print no ⚠️
     edge warnings).
  5. Check `turn.png` from `bash tools/world_preview/run.sh`.
- **Canvas.** Frames are padded to 42/32 of the grid, so lunges never clip.
  The 3D renderer's `CREATURE_CANVAS` matches that, so don't change one without
  the other.

**TODO:** redraw every other row in this style (64 grid, all 5 views, darker,
less cute), one row at a time, checking each with the loop above:
1. The 16 Wilds beasts: bytelet, technophasia, chirplet, viralia, trendrake,
   noobit, skirmalot, grindlord, bufferoo, streamlet, bingewyrm, zephyrlet,
   airstream, stratolord, cartini (plus cacheon, done).
2. player and poacher.
3. The battle-only rows: aegis, titan, laser, bite, net. These may not need
   the ¾ and back views.

## Conventions

- Match the surrounding style: emoji-prefixed `echo` status lines in shell
  scripts, and `set -euo pipefail` in new scripts.
- Keep the Android Kotlin logic and the `rhc-common` C++ engines in sync. A
  change to the rules or scoring in one usually needs the same change in the other.
