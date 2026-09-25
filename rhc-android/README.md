# PROJECT OVERVIEW: Rock Hard Christianity (RHC)

Rock Hard Christianity (RHC) is a cross-platform environment protector and time-reclamation engine with native Android (Kotlin) and Windows (C++/Win32) implementations.

## 1. Architecture

* **Android:** Kotlin/Gradle, four flavors — Gamers/Timesavers × Male/Female — centered around `MainActivity.kt`.
* **Windows:** Native C++/Win32 using Win32 API, GDI, Microsoft UI Automation, SQLite, and WinHTTP.
* **Shared Logic:** `rhc-common/` mirrors Android persistence and engine behavior through a custom SQLite-based `DatabaseManager`.

## 2. Protection & Blocking

### Android

* `GuardianService` and `ShieldRuleEngine` scan accessibility/DOM content for hard and soft block triggers.
* Dynamically detects installed apps and normalizes package names to catch variants such as Lite editions.
* Monitors launchers and uninstall dialogs to enforce protection.
* A 15-minute watchdog heartbeat detects service termination and issues a **SHIELD DOWN** notification.

### Windows

* Native UI Automation scans browser tabs, URLs, and window titles.
* `SendInput` navigates away from blocked sites.
* Hosts-file sinkholing blocks configured domains.
* A background watchdog monitors Task Manager.
* Shutdown/startup protection maintains the blocker across restarts.

## 3. Mandatory Cooldown / Self-Destruct

Users can select a one-way 3, 5, 7, or 14-day cooldown before disabling protection.

After expiration:

* Android permits access to protected settings.
* Windows removes the blocker executable and restores the hosts file.

## 4. Momentum Core

Momentum converts blocked distractions into reclaimed time rather than rewarding repeated blocking.

* Daily yields are based on the user's blocklist.
* Urge farming produces no points.
* Earned Momentum gradually evaporates after two hours.
* Anonymous UUIDs and scores sync to Supabase using RLS through Android `HttpURLConnection` and Windows `winhttp.dll`.

## 5. Netbeast Safari — Gamers

* Retro RPG layer built around serialized 19-variable Netbeast data.
* Dynamic combat with traits such as Vampiric, Thick-Skinned, Spiked, and Elusive.
* Weather/GPS systems influence combat and recovery mechanics.
* Fleeing the Red Wall can permanently kill three weakest Netbeasts and trigger a 15-minute Demon Domain lockout.

### 5.1 The Wilds (3D exploring)

Exploring is a first-person walk through an open 3D field. It's on by default. Settings (⚙️ on the main screen, so never mid-walk) has **"Explore Netbeasts in 3D"** to switch back to the text-log expeditions.

**How it plays (built):**

* Tapping **EXPLORE THE WILDS** starts a 3-minute walk. You **auto-walk the whole time and can't stop or leave** until the timer runs out. Aether keeps draining as usual.
* You only steer. Drag sideways to turn, and drag up or down to look. Looking gets stiffer near its limits and drifts back to level when you let go, so you can't end up staring at the sky or your feet and steering stays easy on a phone.
* The world is open meadows (wildflowers, almost no trees), small dense forests (a leaf-litter floor, ferns, tiny mushrooms), groves in between, sand, mud (on wet shores, tidal flats and in hollows), water and hills, with dirt walking tracks running out from the start and looping round the map. Your pace depends on the ground: a track is 10% quicker, sand 10% slower, forest 10% slower, tall grass 15% slower, mud 20% slower and wading 45% slower. **Nothing blocks you**: there are no colliders. Instead the auto-walk **sidesteps** trunks: when one is in the 1.2 tiles ahead, you ease sideways around it (up to 0.9 tiles/s) and slow to no less than 70% pace, without your heading changing, and the view leans a little into it. Between two trunks you head for the middle of the gap. If you still clip one you walk through it (it's a billboard and dithers away), and leaves flick past as you go under a canopy. Water is wadeable (camera drops, no drowning) and the map wraps at the edges. You can never get stuck.
* **It looks like where you are.** From the location the app already has, one Open-Meteo elevation request (36 points in rings out to 12 km; the sea reads as exactly 0 m) gives the map a `Region`: on the coast a big sea or harbour on the real compass side (Tauranga gets its harbour to the north), hills from how much the land rises, the local trees (NZ: pōhutukawa on the coast, cabbage trees in the paddocks, ponga and nīkau in the bush, some pine plantation) and, painted along the far horizon, a row of houses in the country's style (NZ weatherboard under corrugated iron, European brick, US clapboard, Nordic timber, tropical concrete). Their windows light up at night. The region is cached in prefs, so it works offline; before the first lookup the Wilds are generic temperate land with no houses.
* **The real weather:** rain streaks and ripples on the water, snow, storms with lightning, fog, and night.
* Creatures patrol territories (zones). Stage 1 beasts live near the start, stage 3 on the far side of the map. **They never ambush you.** One that notices you (within 8 tiles) walks off at 1 tile/s, well under your pace, stepping out of your path rather than straight ahead of you, so a fight is always your choice: head for one and keep steering at it until you're within about 2.4 tiles with it in front of you. One at your side or behind you just shies off. (A 3-minute random walk in the soak test meets 0-1 beasts; one that heads for them meets dozens.)
* **Fights happen right there in the world.** When you catch up to a creature, you square up a couple of tiles apart and circle each other, face to face. You can't run away. Your netbeasts' cages appear down the left of the screen (sprite, level against the wild one, type, health): tap one to throw it. It lands, your netbeast comes out, and the two beasts circle each other while you keep circling them both. Shout moves at your netbeast with the three buttons on the left (hold one for what it does); its equipped nets, potions and sprays are there too, and tapping another cage swaps (it costs a turn). Health bars float over both beasts.
* **Don't dawdle.** The wild beast's patience shows under its health bar (7s). Before you've thrown a cage it lunges at you and knocks one loose: that netbeast has to fight and takes the first hit. After that it gets a free hit on your netbeast. With no netbeasts left, it goes for you.
* The rules are the normal battle code (`engines/combat/`), so damage, traits, weather infusions, catching and so on work as on the battle screen. When the fight ends the walk goes on from where you were. A beaten creature faints and respawns in its territory 45s later; a caught one is gone; one you sprayed away goes back to patrolling.
* Everyone in the same area gets the same map on the same day (the seed is the date; the region is part of the map's identity too).

**Code:** `app/src/main/java/com/rockhard/blocker/world/`

| Part | What it does |
|---|---|
| `sim/` (`WorldMap`, `World`, `WorldSession`, `Region`, `DetMath`) | The rules. **Pure Kotlin with no Android imports.** It runs in fixed 30Hz ticks, takes player inputs only (steering, and throwing a cage in a fight), uses its own seeded RNG and has `snapshot()`/`applySnapshot()`. Its trig is `DetMath` (only `+ - * /`, `floor`, `sqrt`), because `kotlin.math`'s `sin`/`cos`/`atan2` differ in the last bit between the JVM and wasm and a circling fight would drift apart. The host's battle rules show their results with `perform(role, action)` (attack, hit, faint, victory poses) and `effect(role, fx)`, which go into the snapshot. |
| `render/TerrainRenderer` | A "voxel space" heightmap renderer, also pure Kotlin. It draws the terrain, fog, weather palette and billboard sprites into an `IntArray`, with a depth buffer so hills hide things behind them. |
| `WorldView`, `SpriteBank` | The Android side: the frame loop, touch steering and look, the HUD (timer, minimap, health bars over the fighters, the beast's patience, battle captions), and decoding the autogen GIFs into textures. |
| `WorldBridge.kt` | Glue into `GameActivity`: starting a walk shaped by the region, resuming after a fight, and the end-of-walk reward. |
| `WorldFight.kt` | The in-world fight: the cage and move panel on the left (`game_world.xml`), the patience timer, and the hooks the battle code calls (`playSpriteAnim`, `playFx`, `showBattleArena`, the battle timer, `printLog`). |
| `RegionProbe.kt` | The elevation lookup that builds the `Region`, called from `WeatherEngine` after the weather. |

Creatures are seen from 8 directions (45° steps) using 5 drawn views: front, fq (¾ front), side, bq (¾ back) and back; the right-facing views are mirrored for the other side. Walking uses `walk_front`, `walk_fq`, `explore` (side), `walk_bq` and `walk_back`; standing uses `idle_front`, `idle_fq`, `idle` (side), `idle_bq` and `idle_back`. A row that has no ¾/back art yet falls back to the old front/side GIFs. Scenery and the cage are `prop_*.gif` from `sprite_studio/autogen`.

Trees come in 13 kinds (oak, pine, birch, willow, palm, cypress, maple, dead snag, the glitched wire-tree, and the NZ pōhutukawa, cabbage tree, ponga and nīkau), each hand-drawn by `sprite_studio/autogen/scenery.py` at 10 distance levels, `prop_tree_<kind>_d0`–`d9` (128 px down to 8 px). The renderer draws the level closest to the tree's on-screen height instead of rescaling one sprite, so near trees keep their detail and far ones don't shimmer. `TerrainRenderer.TREE_LOD` must match `D` in `scenery.py`. `WorldMap` fills forest tiles with trees and places single-kind groves elsewhere, picking kinds by terrain and the region's flora (willows or pōhutukawa by water, pines on temperate hills, snags and wire-trees only in the outer stage-3 ring). Trunks are always at least 0.9 tiles apart, so there's always a way through. The far-off houses are `prop_house_<style>_<n>.gif` (8 per style, also from `scenery.py`); their window colour must match `Skyline.WINDOW`. There are no giant mushrooms: the world should look like a real place, and the only mushrooms are tiny ones on the forest floor.

To test without a phone, run `bash tools/world_preview/run.sh`. It compiles the sim and renderer on the plain JVM, soak-tests full walks (map determinism, a Tauranga region from real elevations, a wanderer who must never be ambushed and a hunter who catches beasts, the circling fight, the sidestep through the densest forests, snapshot round-trip, frame time) and writes preview PNGs to `tools/world_preview/out/`: views per region, top-down `map_*.png` overviews, weather, the horizon panorama, the fight sequence, and `sidestep.png`/`sidestep_path.png` (walking straight at a grove, and its path from above). `./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest` checks the map and a scripted fight are bit-identical on the JVM and wasm (`scripts/node-wasm.sh` lets Kotlin 1.9's wasm tests run on Node 22+).

**Roadmap (not built yet):**

1. **Multiplayer.** The groundwork is in place. Everything runs through `WorldSession`, so the next step is a `NetWorldSession` that sends `PlayerInput` (steering only) up and applies `WorldSnapshot`s down, with a host or server running the same `sim/` code. Other players already render as `spr_player_*` and show as cyan on the minimap. Since everyone shares the day's seed, only entities need syncing, not the map. Decisions still to make: host-authoritative vs. server, how encounters work when two players are near one creature, and syncing through Supabase Realtime vs. a small relay. Players only share a map if they share the region too, so the host sends its `Region.encode()` with the seed.
2. **Art:** back-view walk cycles (creatures walking away currently use the side view), reeds, a cage-opening animation, seasonal trees (pōhutukawa only flower in December), and maybe local landmarks on the horizon (Mauao for Tauranga).
3. **Tuning:** walk length, how often you meet creatures, and territory difficulty, possibly tied to Momentum or the danger slider.

## 6. UI & Rendering

* **Android:** Native dark-mode Command Center with Momentum, tasks, recovery lists, and leaderboard.
* **Windows:** Native Win32/GDI dark-mode interface with tactile custom buttons and system-tray integration.

## 7. RHC Sprite Studio V9

A local zero-dependency Python/HTML5 tool for the Gamers flavor, providing AI sprite generation, background stripping, 8-bit resizing, GIF tweening, onion skinning, and audio synthesis.

**Autogen:** `python3 sprite_studio/autogen/autogen.py` draws every sprite in the Studio matrix (all 23 rows × 10 animations, 230 GIFs) from the designs in `sprite_studio/autogen/designs.py`. Each creature is drawn once per pose (walk step, wing flap, eyes, mouth), and the animations are built from those poses, so a beast looks the same in every GIF. Use `--only cacheon,titan`, `--anims idle,attack`, `--preview sheet.png` (contact sheet), or `--skip-existing` to keep GIFs you've touched up by hand in the Studio. Designs that also draw the ¾ and back views list them with `views=ALL_VIEWS`, which adds their 3D-Wilds walk/idle GIFs and `turn` (a full 8-direction spin). A design can use a larger grid with `size=64`; cacheon is the first, redrawn in a darker, harder, less cute style that the other rows will follow. Creature frames have spare room around the art (5/32 of the grid each side, 10/32 above, none below) so lunges, dodges and hops never leave the canvas: GIFs are 84×84 for 32-grid designs and 168×168 for 64-grid ones, and autogen warns if any frame still touches the edge. The 3D renderer scales creatures by the same 42/32 (`CREATURE_CANVAS`) so they keep their world size. `--turnaround views.png` renders the 5 views side by side, and `bash tools/world_preview/run.sh` writes `turn.png`, a Cacheon turned through all 8 headings in the renderer.

## 8. Critical Blocklist Parsing Bug — FIXED 08/09/2026

A parsing mismatch caused website blocks to fail.

`MainActivity.kt` stores entries as:

`domain | date | triggers | display name`

but `ShieldRuleEngine.kt` incorrectly read index `1` as the domain, resulting in the date being used as the blocking target.

**Fix:** `ShieldRuleEngine.kt` now reads the actual domain field, and `MainActivity.kt` correctly parses application entries. This restores reliable blocking for domains such as `m.youtube.com`.




---

# BUILD / RELEASE

Run `./release.sh` from the repo root. It shows a numbered menu (`1` = Desktop +
all four Android flavors, the default if you just press Enter; then Desktop,
each of the four Android flavors, and "all four flavors" as `2`–`7`), takes
your choices as a single string of digits (e.g. `24` for Desktop + Gamers
Female Homevisits), builds each selected target, copies the resulting
apk(s)/exe to the repo root under their established filenames, and then
publishes them with `gh release create` to `frostyosty/htc-downloads-rhc`.
