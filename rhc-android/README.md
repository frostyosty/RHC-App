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
* The world is open fields, tall grass (slower), trees, sand, lakes and hills. **Nothing blocks you**: trees are walk-through, water is wadeable (slower, camera drops, no drowning) and the map wraps at the edges. You can never get stuck, so you *will* run into a creature unless you deliberately walk in circles.
* Creatures patrol territories (zones). Stage 1 beasts live near the start, stage 3 on the far side of the map. If one sees you, it chases faster than you walk.
* **Cages:** your netbeasts travel in cages. When a creature reaches you, you throw your lead netbeast's cage between you. It lands, and after 1 second your netbeast emerges facing the wild one. With no netbeasts you face it yourself.
* After a short face-off, the fight currently hands off to the existing turn-based battle screen. When it ends, the walk resumes where you were. A beaten creature respawns in its territory 45s later; one you fled from goes back to patrolling.
* Everyone gets the same map on the same day (the seed is the date).

**Code:** `app/src/main/java/com/rockhard/blocker/world/`

| Part | What it does |
|---|---|
| `sim/` (`WorldMap`, `World`, `WorldSession`) | The rules. **Pure Kotlin with no Android imports.** It runs in fixed 30Hz ticks, takes player inputs only, uses its own seeded RNG and has `snapshot()`/`applySnapshot()`. |
| `render/TerrainRenderer` | A "voxel space" heightmap renderer, also pure Kotlin. It draws the terrain, fog, weather palette and billboard sprites into an `IntArray`, with a depth buffer so hills hide things behind them. |
| `WorldView`, `SpriteBank` | The Android side: the frame loop, touch steering and look, the HUD (timer, cage, minimap), and decoding the autogen GIFs into textures. |
| `WorldBridge.kt` | Glue into `GameActivity`: starting a walk, turning an encounter into `startWildBattle`, resuming, and the end-of-walk reward. |

Creatures are seen from 8 directions (45° steps) using 5 drawn views: front, fq (¾ front), side, bq (¾ back) and back; the right-facing views are mirrored for the other side. Walking uses `walk_front`, `walk_fq`, `explore` (side), `walk_bq` and `walk_back`; standing uses `idle_front`, `idle_fq`, `idle` (side), `idle_bq` and `idle_back`. A row that has no ¾/back art yet falls back to the old front/side GIFs. Scenery and the cage are `prop_*.gif` from `sprite_studio/autogen`.

To test without a phone, run `bash tools/world_preview/run.sh`. It compiles the sim and renderer on the plain JVM, soak-tests a full walk (map determinism, encounters, snapshot round-trip, frame time) and writes preview PNGs to `tools/world_preview/out/`.

**Roadmap (not built yet):**

1. **Battles in the world.** Instead of switching to the battle screen, the two beasts fight right there in the field. You help by throwing potions (and later other items) at your netbeast. The turn logic in `engines/combat/` stays the rules; the world only presents them. `WorldEvent.BattleReady` is where this plugs in.
2. **Multiplayer.** The groundwork is in place. Everything runs through `WorldSession`, so the next step is a `NetWorldSession` that sends `PlayerInput` (steering only) up and applies `WorldSnapshot`s down, with a host or server running the same `sim/` code. Other players already render as `spr_player_*` and show as cyan on the minimap. Since everyone shares the day's seed, only entities need syncing, not the map. Decisions still to make: host-authoritative vs. server, how encounters work when two players are near one creature, and syncing through Supabase Realtime vs. a small relay.
3. **Art:** back-view walk cycles (creatures walking away currently use the side view), rain and snow particles from the real weather, reeds and flowers, and a cage-opening animation.
4. **Tuning:** walk length, how often you meet creatures, and territory difficulty, possibly tied to Momentum or the danger slider.

## 6. UI & Rendering

* **Android:** Native dark-mode Command Center with Momentum, tasks, recovery lists, and leaderboard.
* **Windows:** Native Win32/GDI dark-mode interface with tactile custom buttons and system-tray integration.

## 7. RHC Sprite Studio V9

A local zero-dependency Python/HTML5 tool for the Gamers flavor, providing AI sprite generation, background stripping, 8-bit resizing, GIF tweening, onion skinning, and audio synthesis.

**Autogen:** `python3 sprite_studio/autogen/autogen.py` draws every sprite in the Studio matrix (all 23 rows × 10 animations, 230 GIFs) from the designs in `sprite_studio/autogen/designs.py`. Each creature is drawn once per pose (walk step, wing flap, eyes, mouth), and the animations are built from those poses, so a beast looks the same in every GIF. Use `--only cacheon,titan`, `--anims idle,attack`, `--preview sheet.png` (contact sheet), or `--skip-existing` to keep GIFs you've touched up by hand in the Studio. Designs that also draw the ¾ and back views list them with `views=ALL_VIEWS`, which adds their 3D-Wilds walk/idle GIFs. `--turnaround views.png` renders the 5 views side by side, and `bash tools/world_preview/run.sh` writes `turn.png`, a Cacheon turned through all 8 headings in the renderer.

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
