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
* **Aether** is the daily playtime: 20 minutes on install day, one less each day down to 10, plus up to 5 minutes of remnants for days you skipped. It's set the first time the game opens each day and what's left is saved (`AETHER_DAY`/`AETHER_LEFT`), so closing and reopening doesn't refill it. At 0 the game closes and won't open again until tomorrow. It only runs down while the game is on screen and you're in the Wilds, the explore screen or the Bag/Shop: it holds on the Party tab and in the background, and defending against an invasion shows `00:00` and spends none. **Tap the Aether timer** for a one-way slider (like the System Override one) that lowers your own daily Aether, down to 4 minutes (`AETHER_DAILY_LIMIT`); it can't be dragged back up, and today's Aether drops to the new limit straight away.
* **Nightfall** closes the game too: it won't open in the Nightfall hours and shuts itself when they start (after any fight in progress), unless the shield is Paused (System Override). A Defend fight from the red wall still goes ahead, on a spent day as well; the game closes when it's over.
* Fleeing the Red Wall can permanently kill three weakest Netbeasts and trigger a 15-minute Demon Domain lockout.

### 5.1 The Wilds (3D exploring)

Exploring is a first-person walk through an open 3D field. It's on by default. Settings (⚙️ on the main screen, so never mid-walk) has **"Explore Netbeasts in 3D"** to switch back to the text-log expeditions.

**How it plays (built):**

* Where the Explore button was, the Netbeasts screen shows **the first frame of today's walk**, a window onto the Wilds made in the background (so it shows today's map, your region and the real weather). **Touch it to set off**: the walk starts on that very frame, and the same drag already steers. With Aether depleted it's dimmed and says so. It's a 3-minute walk. You **auto-walk the whole time and can't stop or leave** until the timer runs out. Aether keeps draining as usual.
* You only steer. Drag sideways to turn, and drag up or down to look. Looking gets stiffer near its limits and drifts back to level when you let go, so you can't end up staring at the sky or your feet and steering stays easy on a phone.
* The world is open meadows (wildflowers, almost no trees), small dense forests (a leaf-litter floor, ferns, tiny mushrooms), groves in between, sand, mud (on wet shores, tidal flats and in hollows), water and hills, with dirt walking tracks running out from the start and on across the land. Your pace depends on the ground: a track is 10% quicker, sand 10% slower, forest 10% slower, tall grass 15% slower, mud 20% slower and wading 45% slower. **Nothing blocks you**: there are no colliders. Instead the auto-walk **sidesteps** trunks: when one is in the 1.2 tiles ahead, you ease sideways around it (up to 0.9 tiles/s) and slow to no less than 70% pace, without your heading changing, and the view leans a little into it. Between two trunks you head for the middle of the gap. If you still clip one you walk through it (it's a billboard and dithers away), and leaves flick past as you go under a canopy. Water is wadeable (camera drops, no drowning) and the land has no edges. You can never get stuck.
* **The land is endless.** It comes in big tiles, each 96 × 96 of the small tiles (cells) that distances here are measured in. You start in the middle of the first one, and the tiles around it are made as you walk toward them, so there's new country in every direction and you never come back round to where you began. A walk in a straight line gets about 350 cells from the start, three or four big tiles away. The joins don't show: the ground, the forests and the tracks carry on from one tile into the next. On the coast the sea is one band across your way (its middle about 30 cells out, toward the real sea), and if you wade over it the land goes on beyond. **The minimap (top right) keeps you in the middle**: the land half a tile each way scrolls under your arrow, north at the top, and a tile that's still being made is blank for a moment.
* **It looks like where you are.** From your location (see the next point), one Open-Meteo elevation request (36 points in rings out to 12 km; the sea reads as exactly 0 m) gives the map a `Region`: on the coast a big sea or harbour on the real compass side (Tauranga gets its harbour to the north), hills from how much the land rises, the local trees (NZ: pōhutukawa on the coast, cabbage trees in the paddocks, ponga and nīkau in the bush, some pine plantation) and, painted along the far horizon, a row of houses in the country's style (NZ weatherboard under corrugated iron, European brick, US clapboard, Nordic timber, tropical concrete). Their windows light up at night. The region is cached in prefs, so it works offline; before the first lookup the Wilds are generic temperate land with no houses.
* **Coins lie about the Wilds**: short trails of 3-5 along the walking tracks, and single coins scattered over dry land, fewer near the start (about 50 to a big tile, none in water). **Walk into one and it's yours**, one coin each, and it's gone for the rest of the walk. They spin and bob a little above the ground and aren't on the minimap, so you have to spot them. At the end of a walk you get 5 coins on top of whatever you picked up. Your coins and the bag's nets are counted under the timer (top left): a coin you pick up pops a "+1", and coins you lose make the counter plummet, flashing red.
* **Where you are** is the phone's location (`LocationEngine`). The first time the Netbeasts screen opens it asks for it, and Settings > **"📍 Use My Location for the Weather"** asks again later. It takes a fix from the last 30 minutes if any provider has one, else asks for a new one (waiting up to 8 s), and remembers the last fix, so it still works with location switched off. Without permission it falls back to IP geolocation, which is often a city away (NZ connections tend to read as Auckland). Open-Meteo only gets the spot rounded to about 1 km. The weather report in the log says which it used (`Loc Source`). Android's prompt comes from the permission controller, which the Guardian blocks as anti-tamper, so `ShieldRuleEngine` lets that one package through while the app is asking (`ALLOW_PERMISSION_PROMPT_UNTIL`); Settings stays locked.
* **The real weather:** rain streaks and ripples on the water, snow, storms with lightning, fog, and night.
* Creatures patrol territories (zones). Stage 1 beasts live near the start, with a ring of stage 2 round them. Further out the country is tame in places and wild in others (about 45% of it is stage 3), and the dead snags and wire-trees show you where the wild country is. **They never ambush you.** One that notices you (within 8 tiles) walks off at 1 tile/s, well under your pace, stepping out of your path rather than straight ahead of you, so a fight is always your choice: head for one and keep steering at it until you're within about 2.4 tiles with it in front of you. One at your side or behind you just shies off. (A 3-minute random walk in the soak test meets 0-1 beasts; one that heads for them meets dozens.)
* **Fights happen right there in the world.** When you catch up to a creature, you square up a couple of tiles apart and circle each other, face to face. Nobody circles one way for long: every 3-6 seconds everyone slows to a stop and circles back the other way (swiping picks the direction and holds it for a few seconds). You can't run away. Your netbeasts' cages appear down the left of the screen (sprite, level against the wild one, type, health): tap one to throw it. It lands, your netbeast comes out, and the two beasts circle each other while you keep circling them both. Shout moves at your netbeast with the three buttons on the left (hold one for what it does). Its nets, potions and sprays are always under them: an item it has none of equipped is dimmed, and tapping it says to equip some in the Bag. Tapping another cage swaps (it costs a turn). Health bars float over both beasts. An attack lunges right into the other beast, and the hit (and its effect) lands when the lunge does.
* **Don't dawdle.** The wild beast's patience shows under its health bar (7s). Before you've thrown a cage it lunges at you and knocks one loose: that netbeast has to fight and takes the first hit. After that it gets a free hit on your netbeast. With no netbeasts left, it goes for you.
* The rules are the normal battle code (`engines/combat/`), so damage, traits, weather infusions, catching and so on work as on the battle screen. When the fight ends the walk goes on from where you were, at 60% pace for a moment, easing back to full over 8 seconds ("catching your breath…"). If it clobbered you (a lost last stand), you're knocked flat first: the view drops to the ground, tipped on its side, and rises as you pick yourself up. A beaten creature faints and lies there for 8s (`FADE_TICKS`), then respawns in its territory 45s later; your own fallen netbeasts stay where they fell for the rest of the fight and 8s after (`BODY_TICKS`: `leaveBody` unlinks the fainted companion instead of removing it). With no netbeasts left, punches take no turn: tap as fast as you like, and the beast strikes you 3s after it turns on you (`LAST_STAND_MS`; `LAST_STAND_STRIKE_MS` on the battle screen). While you walk, your cages ride down the left edge in throwing order (top first, `WorldCages.kt`); drag one up or down to change it. The walk order is saved (`WALK_ORDER`, by `boughtAt`) and carries over to the next walk; it's separate from the party's lead, which a fight puts back when it ends; a caught one is gone; one you sprayed away goes back to patrolling.
* **Tap a cage while you walk and you let that netbeast out.** The cage is tossed down a few steps ahead, opens a second later, and the netbeast roams near you for the rest of the walk: it trots to a spot ahead of you and off to one side, stands looking around while you walk past, then trots on to another (a green dot on the minimap). That's all it does. It never fights, wild beasts take no notice of it, and during a fight it potters about a few tiles clear of it. Its cage is gone from the column and from the fight panel until the walk ends, so with every netbeast out roaming you fight with your fists. **A `[Looter]` goes after coins while it's out**: any coin within 3 tiles of it (and 6 of you) it runs to, and that coin is yours (about 10-20 a walk in the soak).
* Everyone in the same area gets the same land on the same day, however far and whichever way they walk (the seed is the date; the region is part of the map's identity too).

**Code:** `sim/` and `render/` are in `world-core/src/commonMain/kotlin/com/rockhard/blocker/world/` (Kotlin Multiplatform, built for the JVM and wasm); the Android side is in `app/src/main/java/com/rockhard/blocker/world/`.

| Part | What it does |
|---|---|
| `sim/` (`WorldMap`, `World`, `WorldSession`, `Region`, `DetMath`) | The rules. **Pure Kotlin with no Android imports.** It runs in fixed 30Hz ticks, takes player inputs only (steering, throwing a cage in a fight, and letting a netbeast out on a walk), uses its own seeded RNG and has `snapshot()`/`applySnapshot()`. Its trig is `DetMath` (only `+ - * /`, `floor`, `sqrt`), because `kotlin.math`'s `sin`/`cos`/`atan2` differ in the last bit between the JVM and wasm and a circling fight would drift apart. The host's battle rules show their results with `perform(role, action)` (attack, hit, faint, victory poses) and `effect(role, fx)`, which go into the snapshot. |
| `render/TerrainRenderer` | A "voxel space" heightmap renderer, also pure Kotlin. It draws the terrain, fog, weather palette and billboard sprites into an `IntArray`, with a depth buffer so hills hide things behind them. |
| `WorldView`, `SpriteBank` | The Android side: the frame loop, touch steering and look, the HUD (timer, coin and net counters, minimap, health bars over the fighters, the beast's patience, battle captions), and decoding the autogen GIFs into textures. |
| `WorldPreviewView` | The first frame of today's walk on the Netbeasts screen, instead of the Explore button. Touching it sets off. |
| `WorldBridge.kt` | Glue into `GameActivity`: making the next walk ahead of time in the background (`prepareNextWalk`: map, world and first frame, remade for a new day, region, weather or hour), starting it, coins, resuming after a fight, and the end-of-walk reward. |
| `WorldFight.kt` | The in-world fight: the cage and move panel on the left (`game_world.xml`), the patience timer, and the hooks the battle code calls (`playSpriteAnim`, `playFx`, `showBattleArena`, the battle timer, `printLog`). |
| `RegionProbe.kt` | The elevation lookup that builds the `Region`, called from `WeatherEngine` after the weather. |

Creatures are seen from 8 directions (45° steps) using 5 drawn views: front, fq (¾ front), side, bq (¾ back) and back; the right-facing views are mirrored for the other side. Walking uses `walk_front`, `walk_fq`, `explore` (side), `walk_bq` and `walk_back`; standing uses `idle_front`, `idle_fq`, `idle` (side), `idle_bq` and `idle_back`. A row that has no ¾/back art yet falls back to the old front/side GIFs. Scenery, the cage and the spinning coin are `prop_*.gif` from `sprite_studio/autogen` (`autogen.py --only coin` writes just the coin; a prop can set its own frame count and timing with `@prop(name, frames=, ms=)`).

Trees come in 13 kinds (oak, pine, birch, willow, palm, cypress, maple, dead snag, the glitched wire-tree, and the NZ pōhutukawa, cabbage tree, ponga and nīkau), each hand-drawn by `sprite_studio/autogen/scenery.py` at 10 distance levels, `prop_tree_<kind>_d0`–`d9` (128 px down to 8 px). The renderer draws the level closest to the tree's on-screen height instead of rescaling one sprite, so near trees keep their detail and far ones don't shimmer. `TerrainRenderer.TREE_LOD` must match `D` in `scenery.py`. `WorldMap` fills forest tiles with trees and places single-kind groves elsewhere, picking kinds by terrain and the region's flora (willows or pōhutukawa by water, pines on temperate hills, snags and wire-trees only in stage-3 country). Trunks are always at least 0.9 tiles apart, so there's always a way through. The far-off houses are `prop_house_<style>_<n>.gif` (8 per style, also from `scenery.py`); their window colour must match `Skyline.WINDOW`. There are no giant mushrooms: the world should look like a real place, and the only mushrooms are tiny ones on the forest floor.

To test without a phone, run `bash tools/world_preview/run.sh`. It compiles the sim and renderer on the plain JVM, soak-tests full walks (map determinism, tiles that come out the same whatever order they're made in and join without a seam, straight walks that cross several tiles and give the same sim whichever tiles were made beforehand, a Tauranga region from real elevations, a wanderer who must never be ambushed, a hunter who catches beasts and a coin seeker who heads for the nearest coin, a netbeast let out to roam (it must stay near you all walk and out of every fight, and only a forager brings coins), the circling fight (it has to turn round every few seconds, a hit waits for the lunge, and you walk on slowly or get up off the ground after), the sidestep through the densest forests, snapshot round-trip, frame time) and writes preview PNGs to `tools/world_preview/out/`: views per region, top-down `map_*.png` overviews (the first tile and the eight around it, with the territories ringed by stage), `*_join_*.png` (looking across a join), weather, the horizon panorama, the fight sequence (`fight_lunge.png` is a lunge at its peak, `getup_*.png` getting up after being clobbered), `coins.png` (a coin trail on a track), `roamer_*.png` (a cage tossed down on a walk and the netbeast roaming ahead), `first_frame.png` (the Netbeasts screen's window onto the walk), and `sidestep.png`/`sidestep_path.png` (walking straight at a grove, and its path from above). `./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest` checks three tiles of the map (made in two different orders) and a scripted fight are bit-identical on the JVM and wasm (`scripts/node-wasm.sh` lets Kotlin 1.9's wasm tests run on Node 22+).

**How some of it is built:**

* **Circling reversals.** `Entity.orbitSpin` eases toward `orbitDir` by `SPIN_RATE` a tick inside `World.orbit`. Each fight has one swap timer, `swapTicks` on the player (3 s plus up to 3 s from the sim RNG), and the standoff, the watch circle and the duel all follow it. The duel turns against your circle (`b.orbitDir = -p.orbitDir`). A swipe picks the direction and holds it for at least `SWAP_MIN_TICKS`, without using RNG.
* **The hit waits for the lunge in the sim, not the renderer.** `World.perform` and `effect` hold a HIT (or a FAINT behind it) and its fx in `pendingAction`/`pendingFx` until the attacker's lunge peaks. The reach is `TerrainRenderer.lungeReach`: bodies meet at `LUNGE_CONTACT` × their summed sizes, and a lunge at you stops `LUNGE_FACE` short.
* **Getting up.** `EncounterOutcome.PLAYER_BEATEN` knocks you flat (`downTicks`: `LIE_TICKS` flat, then getting up). `WorldFight` sends it when the beast hit you (a lost last stand). Every fight then sets `recoverTicks` (`RECOVER_PACE` 0.6 easing to 1 over 8 s), which scales the forward speed in `stepPlayer`. Both are in the snapshot because the camera reads them.
* **The first frame.** `WorldBridge.prepareNextWalk` makes the map, the world and its first frame on a background thread (`walkMaker`) at `WorldView.PORTRAIT_W`, and publishes them as `nextWalk` only when they're done, so the main thread never shares a world with it. It remakes them for a new day or region (`walkKey`), or for new weather or a new hour (`look`). `enterWorld` sets off in `nextWalk` if its key matches, and otherwise makes a world on the spot. `WeatherEngine.fetchSilent`'s `onRegion` fires after `RegionProbe`.
* **The endless land.** `WorldMap.tile(tx, ty)` makes a `MapTile` (`WorldMap.TILE` = 96 cells square: its terrain, heights, props, zones and coins) the first time anyone asks for it. `make` builds a tile from the seed, the region and its own coordinates only, never from its neighbours or from what was made before, because the sim, the renderer, the minimap and the background tile-maker all ask at different times and must get the same tile. What crosses a join comes from world coordinates: the ground, woodland, lush, wet and wildness fields are value noise over hashed lattice points (`noise`, `hash`), and the tracks join hashed nodes, one per 48 × 48 square, with a track between about 65% of neighbours (`layPaths`). The water level, hill height and forest and meadow cut-offs are measured once on the first tile (in `WorldMap`'s `init`), so that tile has the water and woods the region asks for and the rest carries on from it. A tile is made with an apron of `PAD` = 3 cells so it can look at its neighbours' cells, and it keeps the apron's heights for the bilinear ground and the slope shading. Trunks keep 0.45 in from a tile's edge and territories 6, so the spacing rules hold across joins. A zone's or coin's id is its tile and index packed into an Int (`WorldMap.pack`). **Beasts wake by where players are, not by which tiles exist:** `World.wake` brings out a tile's beasts when a player is within `WAKE_RANGE` (40 cells) of it, and `sleep` puts them away once everyone is `SLEEP_RANGE` (72) from it; the snapshot carries the woken tiles (`awake`). The renderer bakes its ground texture a 16-cell page at a time as pages come into view (`TerrainRenderer.page`). `WorldView.makeTilesAhead` has tiles within half a tile of you made on a background thread and handed over with `WorldMap.adopt`, so a new tile doesn't stall a frame (about 4 ms each on the desktop JVM).
* **Coins aren't entities.** They're each tile's `MapTile.coins`, placed by `placeCoins` from their own RNG (`COIN_SALT`), so adding them didn't move anything else on the map. `World` keeps `coinGone` (a coin's id to the ticks until it's back, `COIN_RESPAWN_TICKS` = 5 minutes, longer than a walk; a coin that isn't in it is lying there), and the snapshot carries those (`CoinGone`). `COIN_REACH` is 0.5 tiles. There are `COIN_TRAILS` = 8 trails of 3-5 plus `COIN_SINGLES` = 18 singles, about 50 a tile. In the soak a wanderer picks up 0-5 and a bot that heads for the nearest coin picks up 45-50. `DeterminismTest`'s `MAP_PRINT`, `COAST_PRINT` and `WALK_PRINT` hash the coins too.
* **Roaming netbeasts are in nobody's fight.** `PlayerInput.letOut` (with `forage` for a Looter) tosses a CAGE with no `link`; it opens into a COMPANION in `EntityState.ROAM`, also with no `link`, found by `ownerId`. Everything a fight looks up goes by `link` (`companionOf`, `cageOf`, `resolveEncounter`), so none of it sees a roamer. `stepRoamer` aims at a spot that moves with you (`heelF` ahead, `heelS` to your right) at `ROAM_SPEED`, a little over your best pace, and `ROAM_RUN` from further back. A forager's coin comes back as `CoinPicked` with its `finderId`. The app keeps who's out in `GameActivity.worldRoaming`; `walkOrder()` leaves them out, and `noCages()` is what decides a last stand in the Wilds.
* **Dragging a cage only slides views.** `cageTouch` moves the cages with `translationY` and rebuilds the column after you let go. Taking the view under your finger out of its parent mid-drag makes Android cancel its touch (`ViewGroup.removeView` cancels the touch target), which is why reordering used to do nothing.

What isn't built yet (fleeing, the red-wall colossus, mounts, multiplayer, the web client) is in `NETBEASTS_PLAN.md` at the repo root.

## 6. UI & Rendering

* **Android:** Native dark-mode Command Center with Momentum, tasks, recovery lists, and leaderboard.
* **Windows:** Native Win32/GDI dark-mode interface with tactile custom buttons and system-tray integration.

## 7. RHC Sprite Studio V9

A local zero-dependency Python/HTML5 tool for the Gamers flavor, providing AI sprite generation, background stripping, 8-bit resizing, GIF tweening, onion skinning, and audio synthesis.

**Autogen:** `python3 sprite_studio/autogen/autogen.py` draws every sprite in the Studio matrix (all 23 rows × 10 animations, 230 GIFs) from the designs in `sprite_studio/autogen/designs.py`. Each creature is drawn once per pose (walk step, wing flap, eyes, mouth), and the animations are built from those poses, so a beast looks the same in every GIF. Use `--only cacheon,titan`, `--anims idle,attack`, `--preview sheet.png` (contact sheet), or `--skip-existing` to keep GIFs you've touched up by hand in the Studio. Designs that also draw the ¾ and back views list them with `views=ALL_VIEWS`, which adds their 3D-Wilds walk/idle GIFs and `turn` (a full 8-direction spin). A design can use a larger grid with `size=64`; cacheon is the first, redrawn in a darker, harder, less cute style that the other rows will follow. Creature frames have spare room around the art (5/32 of the grid each side, 10/32 above, none below) so lunges, dodges and hops never leave the canvas: GIFs are 84×84 for 32-grid designs and 168×168 for 64-grid ones, and autogen warns if any frame still touches the edge. The 3D renderer scales creatures by the same 42/32 (`CREATURE_CANVAS`) so they keep their world size. `--turnaround views.png` renders the 5 views side by side, and `bash tools/world_preview/run.sh` writes `turn.png`, a Cacheon turned through all 8 headings in the renderer.

**🔄 REGENERATE (a new drawing):** a design is code in `designs.py`, written by Claude Code, so it is drawn the same way every time and no image generator is involved. A creature row's REGENERATE opens a panel with the prompt to paste into the Claude Code chat (add what you want different in the box above it), which asks Claude to rewrite that creature's design and redraw its GIFs. When Claude has finished, 🔁 CLAUDE IS DONE: RELOAD ROW shows the result.

**Looks (🎨 RECOLOUR on a creature row, 🔄 REGENERATE on the Attacks tab):** the button moves the row to its next *look* and redraws it: for a creature the same drawing in new colours (the armour re-toned to bronze, navy, moss and so on, and the glow nudged along the colour wheel within its line's family, with the angry-eye red left alone), and for a move effect a new roll of its random parts (sparks, shards, bolts). ◀ BACK and ↩ ORIGINAL step back, and shift-click redraws the row in the look it already has, to pick up an edit to `designs.py`. The player, the poacher and the moves with no random parts are drawn one way, so their button is a plain 🔁 REDRAW. Each row's look number is kept in `sprite_studio/autogen/looks.json` (missing when every row is on the original), so a full `autogen.py` run keeps the looks you chose; from the command line it's `autogen.py --only cacheon --look next|prev|original`. The rules are in `sprite_studio/autogen/looks.py`. A look never changes a creature's shape: that takes a redraw in `designs.py`.

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
