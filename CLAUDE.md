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

`rhc-android/.../world/` is the first-person exploring mode, the default way to
explore in the Gamers flavors. The plan and roadmap are in section 5.1 of
`rhc-android/README.md`, which is the source of truth: read it before changing
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
rows (aegis, titan, net) draw only the front and side views.
Redraw a new row the same way.

**Battle effects are not creatures.** `laser`, `bite` and `net` are one-shot
effects in `sprite_studio/autogen/effects.py` (`fx_<name>.gif`, 64 grid at
2x, facing right, ending on an empty frame), written by
`autogen.py --only laser,bite,net`. They have no idle/walk/faint and aren't
matrix rows. Only real creatures (and player/poacher/aegis/titan) are rows.
- Moves link to an effect through the `fx` field in
  `SkillEngine.SKILL_DATABASE`. `playAttackFx(onPlayer, move)` in
  `BattleSpriteAnim.kt` plays it over the defender when the hit lands.
- Items call `playFx(onPlayer, name)` directly. The mid-battle Net button in
  `GameSetup.kt` plays `fx_net` over the enemy.
- The overlays are `spritePlayerFx`/`spriteEnemyFx` in `game_arena.xml`. The
  player's is mirrored, because the enemy attacks from the right.
- A new effect is a new `@effect` in `effects.py`, `fx = "<name>"` on its
  moves (or a `playFx` call), and its name in `EFFECTS` in
  `sprite_studio/server.py` so the Studio dashboard shows it.

## Planned work (agreed plan)

A, B, C and E are done. D is next: a long-running goal worked through a few
effects at a time.

### A. Sprite Studio: "✏️ EDIT" does nothing (done 2026-09-24)

Fixed. `/load` in `server.py` detects autogen's 2x write and returns frames
at their real size (84, or 32 for props/fx) with per-frame `durations` and
`scale`. The page's `setCanvasSize(n, keepFrames)` adds a missing `<option>`,
and there's no more timeout. `/save` writes through `pixelkit.save_gif` at
the loaded scale, keeps the durations, and appends the file to
`sprite_studio/autogen/hand_edits.txt`. `autogen.py` skips listed files
unless `--force`. Set `STUDIO_PORT` to run a second Studio on another port.

### B. Trees: 10 kinds x 10 distance levels (done 2026-09-24)

Built as planned below. `scenery.py` draws the 100 `prop_tree_<kind>_d<i>.gif`
(`autogen.py --scenery`, review sheet `--trees sheet.png`), `PropKind` has one
entry per kind (height and trunk radius match `@tree(...)` in `scenery.py`),
`TerrainRenderer.TREE_LOD`/`treeLevel` picks the level and `WorldMap.placeTrees`
makes the groves. Grove points are picked by rejection sampling, not `cos`/`sin`,
because trig isn't bit-identical on JVM and wasm and `DeterminismTest` pins the
map. `SpriteBank` shares repeated frames, so slow 2-frame sways stay cheap.
The world code lives in `rhc-android/world-core/src/commonMain/.../world/`.
The original plan, kept for reference:

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
dead snag (outer stage-3 ring) · giant mushroom (tall grass; removed in E) ·
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

### E. The Wilds look like home, and fights stay in them (done 2026-09-25)

The user asked for this: no giant mushrooms (tiny ones are fine), terrain from
their real location (a big harbour for Tauranga), local trees and houses,
rain synced to the real weather, open meadows and small dense forests, and
battles that don't leave the 3D world. Built:
- `Region` (world-core) from `RegionProbe` (one Open-Meteo elevation request
  of 36 points in rings; the sea reads as exactly 0 m) and the country code.
  Coastal regions get a sea band on the real compass side.
- A woodland noise field splits the land into `Terrain.FOREST`, meadows
  (flowers) and groves. The flora sets are TEMPERATE, NZ (pōhutukawa, cabbage,
  ponga, nīkau, some plantation pine), TROPICAL and BOREAL. New props:
  `flowers`, `mushrooms` (tiny), `fern`.
- The horizon (`Skyline` in `TerrainRenderer`): hills, a flat sea line toward
  the sea, and towns of `prop_house_<style>_<n>` (5 styles x 8 in
  `scenery.py`, `autogen.py --houses sheet.png`) with lit windows at night.
  Fetching real photos or tree images was considered and dropped: photos near
  a GPS point are unreliable and would clash with the pixel art.
- Weather: rain, storm, snow and fog palettes, with streaks and ripples.
- Later (same day): beasts stopped chasing. They shy away and you pick your
  fights. Mud (0.8x pace) and walking tracks (`Terrain.PATH`, 1.1x) were added.
- Fights: circling in the sim (`World.ENGAGED`, `PlayerInput.throwCage`,
  `WorldEvent.CompanionOut`), the cage and move panel on the left, and the
  beast's patience (7s: before a cage it knocks one loose, after that it gets
  a free hit, and with no netbeasts it goes for you).

### C. Trees without collisions: the "sidestep" (done 2026-09-25)

Built as planned below, in `World.sidestep` (constants `PROBE`, `CLEARANCE`,
`STRAFE_MAX`, `MIN_FORWARD`, `GAP_GAIN`), with the tree buckets as
`WorldMap.treeStart`/`treeIds`. Two changes from the plan: the 3x3 tiles are
searched around the middle of the corridor (0.6 ahead), so trunks right at
your feet are covered too; and with trunks on both sides you head for the
middle of the gap, which leans toward the side with more room, rather than
always stepping to the roomier side, which bounced between two trunks. The
lean is worked out in the renderer from how the camera moved. The soak test in
`tools/world_preview` measures forest walks: 0.00-0.01% of ticks inside a
trunk (2% with the sidestep off), and forward pace never under 0.70. The
original plan:

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

### D. Every attack gets an effect (eventually)

Goal: every move in `SkillEngine.SKILL_DATABASE` has an `fx`, so no attack
lands with just the flying move-name text. Items and other actions (potion,
repel spray, the human punch in `executeHumanPunch`) should get one too, via
`playFx`. Build them the way laser, bite and net were built (see "Battle
effects are not creatures" above): a function in `effects.py`, rendered and
looked at frame by frame, in the dark creature style.

Done so far: laser (Ping, Static, Aero Beam, Light Pulse, Chrono Blast,
Orbital Cannon, Fatal Exception), bite (Bite, Binge, Data Drain, Feral
Strike, Apex Predator) and net (the thrown Net item).

Still without an effect (43 moves): Glitch, Overclock, Timeshift, Tweet,
Cancel, Doxx, Doomscroll, Ratio, Annihilate, Cleanse, Basic Attack, Spam
Click, Rage Quit, G-Fuel, XP Boost, Loot Box, Lag, Skip, Autoplay, Ad Break,
Cyber Strike, Mecha Dash, Pixel Slash, Tackle, Scratch, Growl, Swipe, System
Wipe, Firewall, Viral Surge, Deplatform, Critical Strike, Parry, Marathon,
Hypnotize, Nova Shield, Ambush, The Algorithm, Tryhard Mode, DMCA Takedown,
Sky-Breaker, Cataclysm, Obliterate.

Several moves can share one effect when they really are the same kind of
hit (e.g. slash/claw moves, shield/buff moves, poison/drain moves), which
keeps the GIF count down. Ultimates should each get their own. Ask the user
before settling a grouping. When adding effects, update the two lists above.

## Conventions

- Match the surrounding style: emoji-prefixed `echo` status lines in shell
  scripts, and `set -euo pipefail` in new scripts.
- Keep the Android Kotlin logic and the `rhc-common` C++ engines in sync. A
  change to the rules or scoring in one usually needs the same change in the other.
