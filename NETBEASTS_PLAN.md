# Netbeasts plan

Netbeasts work that isn't built yet, in the order it'll be built. Homevisits
is in `HOMEVISITS_PLAN/`, the outdoor game is in `NETBEASTS_GO_PLAN/`, and
`ROADMAP.md` says where this plan's steps come among theirs. What is built is
described in `CLAUDE.md` and `rhc-android/README.md`. When something here
lands, move anything that should last (new rules, constants that must match,
test commands) into `CLAUDE.md` or README §5.1 in the same change, then
delete it from here.

**Ask** marks a decision the user makes. Ask it when that work starts and
don't settle it yourself. Where there's a default, propose it.

Build each step, test it (for the Wilds, soak-test with
`bash tools/world_preview/run.sh`), and ship it before starting the next.
Anything new in the sim follows the Wilds rules in `CLAUDE.md`:
deterministic, the sim's own RNG, `DetMath` trig, in `EntitySnapshot`, and
`DeterminismTest` re-pinned only on purpose.

## The order (agreed with the user 2026-09-27)

1. **Wilds phase 3**, fair fights. Small, and fine to do first.
2. **Move the battle rules into `world-core`.** Web and multiplayer both
   need it.
3. **The web client**, single-player, with the save synced between phone
   and browser.
4. **Multiplayer.** A browser tab is the second player for testing, so it
   doesn't take two phones.
5. **Wilds phases 4-6**: fleeing, the red-wall colossus, mounts. They come
   after multiplayer so each is designed for more than one player from the
   start, and so no new fight rules get built on Android-only code.

Battle effects and Wilds art can be done at any time between steps.

Phases 1 (fights that feel better) and 2 (the Wilds as the start screen,
coins, HUD) were done 2026-09-25. Their notes are in README §5.1. Left over
from phase 2:
- `WorldBridge.prepareNextWalk` took about 45 ms on a warm desktop JVM. Time
  it on a phone.
- There's no coin sound yet: `onWorldCoin` plays `sfx_coin` once that sound
  exists in `res/raw`.

## 1. done - ignore

## 2. Move the battle rules into `world-core`

The battle rules aren't in the shared code. They're `GameActivity`
extension functions in `app/.../engines/combat/` (`executePlayerMove`,
`triggerEnemyCounterAttack`, `endBattle` and friends), timed with
`mainHandler.postDelayed`, rolling `kotlin.random.Random`, and keeping state
in the `CombatState` object and `GameActivity` fields. A browser can't run
them, and in multiplayer only the phone that started a fight could.
`GameModels.kt` and `SkillEngine.kt` have no Android imports, so the data
the rules use can move with them.

- Move the rules into `world-core` `commonMain` as sim code: they advance on
  the sim's ticks instead of `postDelayed`, roll the sim's RNG, and keep the
  fight's state in the snapshot.
- The app shows the results the way it does now, through `playSpriteAnim`,
  `playFx`, `showBattleArena`, the battle timer and `printLog`, driven by
  events out of the sim. The game should play the same as before.
- Both the Wilds fight (`WorldFight.kt`) and the old battle screen
  (`BattleUI.kt`: text-mode exploring, bosses, the red-wall Defend) use the
  rules, so both have to run on the moved code. Don't leave a second copy.
- Test it with a scripted fight in `DeterminismTest` that goes through the
  real rules (damage, traits, weather, catching) and matches bit for bit on
  the JVM and wasm. Re-pin on purpose.
- When it lands, rewrite the "Fights stay in the world" rule in `CLAUDE.md`,
  which still says the rules live in `engines/combat/`.

## 3. The web client

`world-core` (sim and renderer) already builds for `wasmJs`, and
`DeterminismTest` proves it matches the JVM bit for bit. The renderer draws
into an `IntArray`, which a browser canvas can show as `ImageData`. What the
web doesn't have yet:

- **A shell:** the frame loop, drag to steer and look, the HUD (timer, coins,
  nets, minimap) and the fight panel (cages, moves, items).
- **Sprites:** a way to load the autogen GIFs in the browser.
- **The save:** party, bag and coins are only in Android prefs today. They
  need to live somewhere both the phone and the browser reach, and a phone
  and a browser need a way to link to the same player.
- **Kotlin:** 1.9.22's `wasmJs` is experimental (`scripts/node-wasm.sh`
  works around its tests on Node 22+). Upgrading to Kotlin 2.x is probably
  part of this. It touches the Android build too, so do it as its own change
  and check all four flavors still build.

**Ask:**
- Where the save and identity live (Supabase with the leaderboard's
  anonymous UUID, or an account), and how a phone and a browser link.
- Its own page or an iframe, and where it's hosted. An iframe doesn't stop
  WebSockets, but cross-origin iframes get partitioned or blocked storage
  (Safari especially), so identity can't rely on `localStorage` there; pass
  a token in with `postMessage` or keep it server-side.
- Aether on the web. The daily playtime limit is set on the phone. A browser
  version either reads the same Aether from the server or is a way round it.

## 4. Multiplayer

Everything runs through `WorldSession`, so the next step is a
`NetWorldSession` that sends `PlayerInput` up and applies `WorldSnapshot`s
down, with a host or server running the same `sim/` code (which by now
includes the battle rules). Other players already render as `spr_player_*`
and show as cyan on the minimap. Since everyone shares the day's seed, only
entities need syncing, not the map. Players only share a map if they share
the region too, so the host sends its `Region.encode()` with the seed. Test
with a phone and a browser tab, or two tabs.

**Ask:**
- Host-authoritative (one player's device runs the sim) or a server.
- Syncing through Supabase Realtime (the leaderboard already uses Supabase)
  or a small relay.
- How encounters work when two players are near one creature.

## 5. Wilds phases 4-6

Later phases depend on earlier ones: a caught flee uses phase 1's get-up
and phase 2's coin counter, the colossus uses phase 3's scaling and phase 4's
chase, and mounts need phase 2's coins. Mark each item `(done <date>)` when
it lands and update README §5.1 in the same change.

These were written for one player. **Ask** at the start of each phase how it
works with others around: who a chasing beast follows and whether others can
step in, whether the colossus interrupts everyone's walk or only yours,
whether other players see your mount, and whose dragon fights a sky beast.

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
update the "Beasts never ambush" rule in `CLAUDE.md` when this lands. The
battle rules stay the same.

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
  cacheon style (`sprite_studio/CLAUDE.md`), with its own Kit and accent
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
  the shop. Tune how many coins lie around (`COIN_TRAILS`/`COIN_SINGLES` in
  `WorldMap`) to these prices.
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
  a netted one falls to the ground. The land is endless but made a big tile
  (96 cells) at a time as you near it, so from high up you'd see past the
  tiles that have been made: make them further ahead (`makeTilesAhead`), cap
  the altitude or thicken the fog with height.
- **Tests.** Soak a ride (never stuck, stamina runs out, no encounters on the
  fast mount) and a flight (frame time at altitude, and a sky fight that
  circles).

## Any time: every attack gets an effect (moves done)

Every move in `SkillEngine.SKILL_DATABASE` has its own `fx_<move>.gif`,
built from the families in `sprite_studio/autogen/effects.py` (`MOVES`
table) and listed in the Studio's Attacks tab. Still to do: items and other
actions (potion, repel spray, the human punch in `executeHumanPunch`) via
`playFx`, and a hand-polish pass on any effect that reads weakly in game.


can the net throw be a button you tap outide of combat (so while your walking you can just tap it and a net jsut gets thrown in the direction your facing) if it lands you mght catch the beast if it misses it triggers combat?

please check that the a poacher as well as one of those minibosses arriving on [say wednesday] are properly iplemented in the 3d world. since we brought them in, did we add a determinism test for the new sim paths (summon, rival's cages, resolving) to ensure JVM and wasm stay bit-identical, leaving the existing pins untouched since the wild-beast paths haven't changed?




## Any time: Wilds art and tuning

- **Art:** reeds, a cage-opening animation, seasonal trees (pōhutukawa only
  flower in December), and maybe local landmarks on the horizon (Mauao for
  Tauranga).
- **Tuning:** walk length, how often you meet creatures, and territory
  difficulty, possibly tied to Momentum or the danger slider.