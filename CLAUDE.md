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

**Done (2026-09-24):** every row now follows this style. Each creature line
has its own dark `Kit` subclass with one accent colour: Tech is cyan,
`SocialKit` magenta (viralia lime), `GamingKit` violet, `StreamingKit` ember
orange (not red, so the red angry eyes still read), `FlyingKit` ice blue,
Shopping green and `LegendKit` gold. Views are wired with
`by_view({...})`. Shared parts are in `designs.py`: `dleg`, `bird_leg`,
`raptor_leg`, `roo_leg`, `stomp_leg`, `hum_leg`, `gauntlet`, `claw_arm`,
`bat_wing`, `blade_wing`, `feather_wing`, `fangs` and `sq`. The battle-only
rows (aegis, titan, laser, bite, net) draw only the front and side views.
Redraw a new row the same way.

## Planned work (not started, agreed plan)

Nothing below is implemented yet. Do it in this order, since each part is
independent and the first is the smallest.

### A. Sprite Studio: "✏️ EDIT" does nothing

**Cause (confirmed):** in `sprite_studio/index.html`, `loadFromMatrix` sets
the `<select id="canvasSize">` to the GIF's width. That select only offers 32,
64 and 128, but autogen GIFs are now 168 px (84 px frames written at 2x;
props and fx are 64). A value with no matching option leaves the select
empty, `parseInt('')` gives NaN, and the canvas becomes 0x0, so nothing
appears and there's no error. The `setTimeout(..., 100)` that swaps in the
frames after `updateCanvasSize()` has already blanked them is also fragile.

**Fix plan:**
1. `/load` in `server.py` detects the 2x write (every 2x2 block is one
   colour) and returns frames at their real size (84 or 32). It also
   returns the per-frame `durations` and a `scale` of 2. The user then paints
   real pixels, not half-pixels.
2. In the page, split canvas sizing from "new blank sprite". Add a
   `setCanvasSize(n, keepFrames)` that inserts an `<option>` for `n` if it's
   missing. `loadFromMatrix` sets the size, the frames and the durations in
   one step, with no timeout. Show an error in the page if `/load` fails or
   returns no frames.
3. `/save` writes through `pixelkit.save_gif`, which uses a shared palette
   with index 0 transparent. It upscales back by the loaded `scale` and keeps
   each frame's duration. Autogen durations vary (for example, faint holds
   for 60 s), and the current save flattens them all to 125 ms and can lose
   transparency.
4. Protect hand edits: each Studio save appends the file name to
   `sprite_studio/autogen/hand_edits.txt`, which is committed. `autogen.py`
   skips listed files and says so, unless `--force` is given. Without this,
   the next autogen run silently overwrites painted frames.
5. Test: launch the Studio, click EDIT on `spr_cacheon_idle`. It should show
   8 frames of 84x84 in the left panel. Paint a pixel, save, and check that
   the GIF is 168x168, has the same durations and a transparent background,
   and that `autogen.py --only cacheon` skips it.

### B. Trees: 10 kinds x 10 distance levels

**Pipeline: same autogen, new module.** Reuse `pixelkit` (Painter, shading,
outline, `save_gif`) and the "render, look, fix" loop. Put the trees in a new
`sprite_studio/autogen/scenery.py` with its own `@tree(name, ...)` registry,
so `designs.py` stays creature-only. Wire it into `autogen.py` as
`--scenery` (all trees) and `--only oak,...`, plus `--turnaround`-style
review sheets: `--trees sheet.png` with one row per kind and one column per
distance. A separate pipeline would duplicate the shading and GIF code and
drift from the creature style, so don't make one.

**What "10 distances" means: hand-made mip levels.** The renderer currently
scales one 32 px `prop_tree` with nearest-neighbour. Near trees go blocky,
and far ones shimmer as pixels pop in and out. Instead, draw each tree
natively at 10 heights:
`D = [128, 96, 72, 56, 44, 32, 24, 16, 12, 8]` px, where `d0` is nearest.
Draw each level at its own size, not as a downscale of the big one, because
downscaled pixel art turns to mush. Each tree is one function
`fn(p, s, lod)` written in relative units (`p.size`). The `lod` drops detail
level by level:
- `d0`–`d2`: bark lines, leaf clusters with highlights, roots, 1 px twigs,
  2-frame sway.
- `d3`–`d5`: clusters merged, no twigs, 2-frame sway.
- `d6`–`d9`: silhouette plus two tones, no outline below 16 px, 1 frame
  (static far trees).

The rule at every level: the silhouette and trunk position stay the same, so
switching level never looks like a pop. Write the files at 1x (native pixels
are the point) as `prop_tree_<kind>_d<0-9>.gif`, which is 100 GIFs.

**The 10 kinds** (moody, a little darker than today's bright props, so they
sit with the dark creatures; placement in brackets):
oak (grass) · pine (grass, hills) · birch (grass) · willow (next to water) ·
palm (sand) · cypress/poplar (grass) · autumn maple (tall grass) ·
dead snag (outer stage-3 ring) · giant mushroom (tall grass) ·
wire-tree (a glitched tech tree with cables and a faint cyan glow; outer ring
only, as a hint that stage-3 beasts are near).

**Renderer (`TerrainRenderer`, pure Kotlin):** after computing the on-screen
height `sh`, pick the level whose native height is the smallest one at or
above `sh * 0.9` (clamped to `d0`/`d9`), and use the key
`prop_tree_<kind>_d<i>`, falling back to the old `prop_tree`. Keep the
level heights in one table that matches `D` in `scenery.py`, and treat it
like `CREATURE_CANVAS`: change both or neither. Drawing costs about the same,
because sampling is per screen pixel, and `SpriteBank` loads each key once,
only when it's first seen.

**Sim (`WorldMap`):** replace `TREE`/`PINE` with a tree `PropKind` per kind
(sprite base, world height and trunk radius). Placement stays seeded and
terrain-aware, as above. Place trees in groves with at least 0.9 tiles
between trunks, using grid rejection, so there's always a way through (see
C).

### C. Trees without collisions: the "sidestep"

No colliders, and the rule still holds: the player must never get stuck.
Instead, the auto-walk leans around a tree it's about to hit, which is your
idea of "slow down and move aside when a tree is big (close)", done in the
sim so it stays deterministic:

1. **Precompute once** at map generation: `treeCells`, a per-tile bucket of
   tree indices (an `IntArray` of offsets and ids) built from the seed. It's
   derived data, so snapshots don't change.
2. **Each tick, players only:** probe the point 1.2 tiles ahead along the
   heading, and look at the trees in that tile and its 8 neighbours
   (usually 0–2 trees). For each tree, compute forward distance
   `f = dot(tree - pos, heading)` and sideways offset
   `lat = dot(tree - pos, right)`. It's a threat if `0 < f < 1.2` and
   `|lat| < radius + 0.35`.
3. **Respond to the nearest threat:**
   - Strafe away from it at up to 0.9 tiles/s along `right * -sign(lat)`.
     If `lat` is exactly 0, pick the side from the tree index's parity so it
     stays deterministic.
   - Scale forward speed by `lerp(1.0, 0.7, closeness)`.
   - Leave the heading alone, so steering input and the auto-walk route
     aren't disturbed. The path just shifts slightly and never bounces back.
4. **Never blocks:** forward speed never drops below 70%. If both sides are
   closed in, pick the side with the larger `|lat|` and carry on. If a trunk
   is still overlapped, just walk through it, since it's a billboard. Beasts
   don't dodge, which is cheaper, and a chase through a grove looks
   deliberate.
5. **Render-only polish (no sim state):**
   - The camera leans about 2° toward the strafe.
   - When a tree's `depthZ` drops under about 0.8, dither-fade it with the
     Bayer pattern, as `pixelkit.dither` does. Brushing past a canopy then
     never fills the screen with blown-up pixels.
   - A few leaf pixels flick past when the player passes inside a canopy
     radius.
6. **Cost:** at most 9 bucket reads and a few dot products per player per
   tick, with no allocation and no pairwise checks.
7. **Tests** in `tools/world_preview` (WorldPreview soak):
   - Determinism is unchanged (same seed and inputs give the same snapshots).
   - Ticks spent inside a trunk radius should be close to 0.
   - Forward progress never drops below 0.7 x walk speed over any 1 s window.
   - Add a preview PNG walking straight at a grove.
   - Update `rhc-android/README.md` §5.1 with the new behaviour when it's
     built.

## Conventions

- Match the surrounding style: emoji-prefixed `echo` status lines in shell
  scripts, and `set -euo pipefail` in new scripts.
- Keep the Android Kotlin logic and the `rhc-common` C++ engines in sync. A
  change to the rules or scoring in one usually needs the same change in the other.
