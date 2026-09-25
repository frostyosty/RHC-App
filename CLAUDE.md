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


### D. Every attack gets an effect (TODO only half done)

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

## Wilds roadmap (temporary)

**Delete this whole section once every phase is done** (the user asked for
that). Before deleting it, move anything that should last (new rules,
constants that must match, test commands) into "Netbeasts: the 3D Wilds"
above and README §5.1.

Work through the phases in order. Build each one, soak-test it with
`bash tools/world_preview/run.sh`, and ship it before starting the next.
Later phases depend on earlier ones: a caught flee uses phase 1's get-up
and phase 2's coin counter, the colossus uses phase 3's scaling and phase 4's
chase, and mounts need phase 2's coins. Mark each item `(done <date>)` when it lands and update
README §5.1 in the same change. **Ask** marks a decision the user makes.
Ask it when that phase starts and don't settle it yourself. Where there's a
default, propose it.
Anything new in the sim follows the rules above: deterministic, the sim's own
RNG, `DetMath` trig, in `EntitySnapshot`, and `DeterminismTest` re-pinned only
on purpose.

### Phase 1. Fights that feel better (done 2026-09-25)

Built as planned below, with these details:
- The reversal is `Entity.orbitSpin` easing toward `orbitDir` by `SPIN_RATE`
  a tick inside `World.orbit`, with the timer `swapTicks` on the player. A
  swipe holds its direction for at least `SWAP_MIN_TICKS`, without using RNG.
- The hit waits in the sim, not the renderer: `World.perform` and `effect`
  hold a HIT (or a FAINT behind it) and its fx in `pendingAction`/`pendingFx`
  until the attacker's lunge peaks. The lunge reach is
  `TerrainRenderer.lungeReach`: bodies meet at `LUNGE_CONTACT` x their summed
  sizes, and a lunge at you stops `LUNGE_FACE` short.
- `EncounterOutcome.PLAYER_BEATEN` knocks you flat (`downTicks`: `LIE_TICKS`
  flat, then getting up). `WorldFight` sends it when the beast hit YOU (a lost
  last stand). Every fight then sets `recoverTicks` (`RECOVER_PACE` 0.6
  easing to 1 over 8 s). `downTicks` and `recoverTicks` are in the snapshot,
  because the camera reads them. Phase 4's "caught" uses `PLAYER_BEATEN`.
- `DeterminismTest.WALK_PRINT` was re-pinned. The preview checks the
  reversals, the held hit, the pace and the get-up, and writes
  `fight_lunge.png` and `getup_*.png`.

The plan:

1. **Circling changes direction.** Circling one way the whole time made the
   user dizzy. Each fight gets one swap timer, on the player. Everyone circles
   one way for 3 s plus up to 3 s more (sim RNG), then the orbit speed eases to
   0 over about 0.6 s, `orbitDir` flips, and the speed eases back up. The
   standoff, the watch circle and the duel all follow the timer, and the duel
   keeps turning against your circle (`b.orbitDir = -p.orbitDir`). A swipe
   still picks the direction and restarts the timer. This changes
   `DeterminismTest`'s scripted fight, so re-pin it.
2. **Lunges connect.** This is render-only, in `TerrainRenderer.drawSprites`.
   The attack lunge covers about 80% of the distance to the foe instead of a
   fixed 0.45 tiles, so the two sprites collide, and the foe's HIT knock-back
   starts at the peak of the lunge. Check `fight_attack.png`.
3. **Picking yourself up.** After every fight you get up and walk on at 60%
   pace, easing back to 100% over about 8 s. If you were knocked down (phase
   4's caught flee), the camera rises from the ground first. Store it as a
   player field (`recoverTicks`) that scales `forward` in `stepPlayer`.
4. **Item buttons.** They already exist: NET, POT and SPRY appear in the left
   panel once a netbeast is out, but only for items equipped on that
   netbeast, which makes them easy to miss. Show the row in every fight once
   a netbeast is out. Grey out items at 0, and have a tap on one say to equip
   it in the Bag.

### Phase 2. The Wilds as the start screen, coins, HUD (done 2026-09-25)

Built as planned below, with these details:
- The first frame is `WorldPreviewView` in `peaceControls`, 140dp tall.
  `WorldBridge.prepareNextWalk` makes the map, the world and its frame on a
  background thread (`walkMaker`) at `WorldView.PORTRAIT_W`. It publishes them
  as `nextWalk` only when they're done, so the main thread never shares a
  world with it. It remakes them for a new day or region (`walkKey`), or for
  new weather or a new hour (`look`). `enterWorld` sets off in `nextWalk` if
  its key matches, and otherwise makes a world on the spot.
  `WeatherEngine.fetchSilent`'s `onRegion` fires after `RegionProbe`. It took
  about 45 ms on a warm desktop JVM; it hasn't been timed on a phone yet.
- Coins aren't entities. They're `WorldMap.coins`, placed by `placeCoins`
  from their own RNG (`COIN_SALT`), so the rest of the map didn't move: the
  old fingerprints still passed with coins left out of the hash. `World`
  keeps `coinGone` (ticks until each one is back), and the snapshot carries
  only the gone ones (`CoinGone`).
- The numbers: 8 trails of 3-5 coins plus 18 singles, about 54 per map.
  `COIN_REACH` is 0.5 tiles. `COIN_RESPAWN_TICKS` is 5 minutes, longer than a
  walk. Coins aren't on the minimap. In the soak, a wanderer picks up 0-1 and
  a bot that knows where every coin is picks up nearly all of them (49-53).
  Tune `COIN_TRAILS`/`COIN_SINGLES` for the phase 6 prices.
- `MAP_PRINT`, `COAST_PRINT` and `WALK_PRINT` now hash the coins, and were
  re-pinned on purpose.
- There's no coin sound yet: `onWorldCoin` plays `sfx_coin` once that sound
  exists in `res/raw`.

The plan:

1. **The first frame instead of the button.** With 3D on, the Netbeasts tab
   shows the Wilds where `btnDispatch` is: today's map from the spawn point,
   rendered once (not ticking) with a small "drag to start walking" hint. The
   first touch calls `enterWorld()`, and the same drag already steers. When
   Aether is depleted, dim the frame and put the message on it. Text mode (3D
   off) keeps the button. Generate the map when the tab opens and reuse it
   for the walk, so the first frame and the walk are the same world. Time
   `WorldMap.generate` on a phone. If it's slow, run it off the main thread
   and show a plain placeholder until it's ready.
2. **HUD counters.** Top left, under the timer: `🪙 coins  🕸️ nets`. Keep
   them live, so coins picked up on the walk count straight away. Nets is the
   bag's `nets`. When coins are lost (getting caught in phase 4 costs 90%),
   the counter visibly plummets: it rolls down fast, flashes red, and shows a
   caption like "-540 🪙 dropped as you ran".
3. **Fewer coins, and coins to pick up.** There are no coin pickups in the
   Wilds yet. Coins come from the end-of-walk purse (5-15 in
   `finishExploration`), [Looter], StealCoins, the market and selling. The
   fast mount ("collect coins fast") assumes coins lying around. Plan:
   - Sparse `EntityKind.COIN` pickups, placed from the seed: more along tracks
     and in far territories, none in the air, and they respawn on a timer.
   - A spinning `prop_coin` from autogen.
   - A `WorldEvent.CoinPicked` that the host adds to `focusCoins`.
   - Agreed with the user: walking into a coin picks it up (a contact radius
     checked every tick, so you never have to stop or tap), and the end of a
     walk pays a flat **5 coins** plus whatever you picked up. Tune how many
     coins lie around with the phase 6 prices in mind.

### Phase 3. Fair fights: the power curve and size

1. **The first fights follow a curve.** The reference level is the average
   level of your netbeasts, leaving out legendaries (Aegis, Titan, type
   "Legendary"). In the first encounter the wild beast is a little weaker
   than that (about 0.85x HP). In the second it's a little stronger (about
   1.15x). From the third on, use the standard rule in `onWorldEncounter`:
   lead max HP x (0.6 + 0.2 x stage) x 0.85-1.15. Agreed with the user: the
   easy-then-hard pair happens **once ever** after install, not every walk,
   so count world encounters in prefs.
2. **Bigger when it outlevels you.** The wild beast's size scales with its
   level against the reference level, not by much: roughly
   `sqrt(wild / yours)`, clamped to 0.85-1.3x its stage size. Measure against
   the reference level, not the netbeast that's out, so the size doesn't
   change when you swap. The host rolls the level at `Encounter`, so the
   beast grows as it squares up (eased over about 0.5 s, so it looks like it
   rears up). Do it through a host call like `perform` (`World.scaleBeast`)
   that sets `Entity.size`, which is already in the snapshot. The renderer
   doesn't change.

### Phase 4. Fleeing

This is the biggest change to the sim. It adds player states for fleeing, up
a tree and under water, a beast `CHASE` state, and `PlayerInput` fields for
flee and the three escapes. It all goes in the snapshot.

- **FLEE** is a button in the fight panel. You turn round and auto-run,
  faster than walking and still steerable, and the beast chases you. Once
  you flee there are no more cages this fight: a netbeast that's out goes
  back in its cage, and the panel shows only the three escapes.
- **The chase.** The beast is slightly faster than you, so running alone
  doesn't lose it. It's behind you, so the minimap shows it; the chaser's
  dot pulses.
- **Climb tree** works only if you're within about a tile of a tree, the
  beast isn't Flying, and it's shorter than that tree (its height is
  `size x CREATURE_CANVAS`; trees are `PropKind.size`, 2.4-3.2). The camera
  rises into the canopy. The beast comes to the trunk, looks up for a couple
  of seconds, then turns and walks off, and you've escaped. When the climb
  can't work, the button says why: no tree near, it can fly, or it's too big.
- **Lie down** works only in water. You go under (the view goes dark blue)
  and an oxygen bar drains (about 8 s). The beast loses you, searches for a
  few seconds, then leaves. If the oxygen runs out while it's still close,
  you come up and the chase goes on. On land, lying down does nothing.
- **Turn and yell** startles the beast, and it stops for a moment before it
  chases on. Each yell stops it for less time (about 1 s, then 0.6 s, then
  0.35 s, and so on) until yelling does nothing. The effect comes back if you
  don't yell for about 10 s. The yell count is sim state.
- **Escaping** is `EncounterOutcome.PLAYER_FLED`, which already exists: the
  beast goes back to its zone.
- **Getting caught** (agreed with the user) costs **90% of your coins**. It
  also knocks you down, the fight counts as lost (like a last stand, without
  the 9,999 hit), and you get up at 60% pace (phase 1.3). The sim sends a
  `WorldEvent.Caught`, the host takes the coins off `focusCoins`, and the
  phase 2 counter plummets so you see them go. Escaping costs nothing.
- **Tests.** Add soak cases for each escape and for being caught, and a
  scripted flee in `DeterminismTest`. Add preview PNGs: running with the beast
  on the minimap, up a tree looking down at it, and under water.

### Phase 5. The red-wall colossus

When the red wall drops and you tap Defend, the invader (`isUnderAttack`,
"[Colossal]", 8000 HP, evades everything) fights you in the Wilds instead of
on the old battle screen. It's the only creature allowed to come at you, so
update the "Beasts never ambush" rule when this lands. The battle rules stay
the same.

- **Where.** If you're on a walk, it interrupts the walk. If you're not, the
  Wilds open at today's spawn point for this fight only and close after it.
- **The charge.** It appears on the horizon in front of you, about 25 tiles
  out, with the ground shaking. It charges straight in and stops towering
  over you, and the standoff circle is wider so it fits on screen and you
  look up at it. It animates at about 0.8x speed, so it looks heavy without
  being slow motion.
- **Size.** Several times the tallest tree (about 6-8 tiles against 3), so it
  looks like one hit would flatten any netbeast. It's too big to escape by
  climbing a tree. Lying down in water still works.
- **Keeping it sharp** (agreed: it needs the bigger sizes). The world
  renders at only about 200x300 px and is scaled up, so a sprite looks
  blocky only when its source has fewer pixels than the render pixels it
  covers. A 64-grid creature (84 px frames) is
  fine up to about 84 px tall. A colossus 300 px tall would be 3-4x blockier
  than everything around it. So draw it the way the trees are drawn: size
  levels at 64, 128 and 256 grid, with the renderer picking a level by
  on-screen height like `treeLevel`. Add detail as the size goes up (plate
  seams, rivets, scars and glowing veins at 256) rather than blowing up a
  small sprite. At 256 its pixels are about the size of the terrain's. The
  cacheon helpers already take `p.size`, so the silhouette carries across
  levels. Draw only the views and animations it uses (charge, idle, attack,
  roar and fade, facing you). Load them when it appears and free them after
  the fight, because 256-grid frames are about 340x340 px.
- **Art: one giant per kind of breach** (agreed with the user; general
  categories, not one per app). Only walls with a Defend button lead to a
  fight (`canDefend` in `ShieldRuleEngine`), and they come in four kinds,
  read from the reason's prefix:
  - **App**: "App Overcome:", a blocked app.
  - **Web**: "Hyperlink Overcome:" and "Explicit Input/Query:", a blocked
    link or search.
  - **Content**: "Content Guard:", adult content.
  - **Tamper**: "Anti-Tamper:", trying to switch the blocker off.

  Nightfall and the strict modes (Nuclear, Dumb Phone, No Internet, No
  Videos) lock you out with no Defend button, so they get no giant.
  `GuardianService` only passes the trigger word today, so add the kind as
  an extra on the `UNDER_ATTACK` intent. Each giant is its own row in the
  cacheon style from `sprite_studio/autogen`, with its own Kit and accent
  colour. Show the user each design before moving on to the next.
- **Tests.** Add a preview sequence of the charge and the standoff. The
  wanderer soak must still meet no normal beast that comes at it.

### Phase 6. Mounts: two ridable netbeasts and a dragon

The user swapped the vehicles for creatures you ride and kept the same
ideas: the bike is now a quick mount, the car a fast mount, and the plane a
dragon. You buy mounts; you don't catch them, and they don't fight or join
the party. Do it in this order: the shop and saving what you own, then the
quick mount (it brings the riding camera and getting on and off), then the
fast mount (stamina and no encounters), then the dragon.

- **Shop and party screen.** Quick mount 200, fast mount 1,000, dragon
  10,000, each bought once and saved in prefs. The shop shows each mount's
  animated sprite next to its price, and the party screen shows the mounts
  you own with their sprites, in their own row apart from your fighters. The
  car's fuel becomes the fast mount's stamina, refilled with feed bought in
  the shop.
  **Ask:** the feed price and how long a full stamina bar lasts, and
  whether the dragon needs feed too.
- **Who they are** (agreed with the user; the names can change later): a
  horse-like netbeast, **Gigahoof** (quick mount), an elephant-like one,
  **Teraphant** (fast mount), and a dragon, **Petadrake**. The names follow
  the tech-pun style of the others, and giga, tera, peta go up with the
  price.
- **Waiting near the start.** Every mount you own waits near the start of
  each walk (`EntityKind.MOUNT`, placed from the seed on open ground,
  idling). Walk up to it to climb on.
- **Riding camera.** On a mount the view pulls back and up (about 2 tiles
  behind, a little higher) and shows you on its back. The renderer takes a
  camera offset and stops skipping the player's own sprite.
- **Art.** Mounts are creatures, so each is a row in `designs.py` in the
  cacheon style with all 5 views. Draw the rider into the design with a
  `ride` pose, so autogen makes `ride_*` animations with the rider sitting
  right in every view, rather than stacking the player sprite on top.
- **Quick mount** (was the bike). About 1.8x walking pace, no stamina. Wild
  beasts can still engage you: you jump off to fight and climb back on after.
- **Fast mount** (was the car). About 3.5x walking pace, with a stamina bar
  in the HUD. Wild beasts can't engage you (they scatter), so it's for
  collecting coins fast. When the stamina runs out you climb off and walk,
  and it lies down where it stopped. There are still no colliders: the
  sidestep's probe grows with speed, so it swerves round trunks.
  **Ask:** what happens at water. Default: it stops at the shore and you
  climb off.
- **Dragon** (was the plane). It takes off from open ground, and you steer
  and climb by dragging. The voxel renderer draws from altitude: raise the
  eye height and the draw distance, and watch the frame time. There are no
  coins in the air, but special netbeasts fly high in the sky (new rows in
  the `FlyingKit` style) and are only found up there. Fighting one is the
  normal fight: you circle each other, in the air. Agreed with the user:
  in the sky **the dragon fights for you** (you just sit on its back), so
  there are no cages up there. You can still throw a net at a sky beast, and
  a netted one falls to the ground. The map wraps every 96 tiles, so from
  high up you'd see it repeat: cap the altitude or thicken the fog with
  height.
- **Tests.** Soak a ride (never stuck, stamina runs out, no encounters on the
  fast mount) and a flight (frame time at altitude, and a sky fight that
  circles).

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
