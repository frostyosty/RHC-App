# 03. The battle rules move into `world-core`

**Needs:** nothing (can run alongside 01 and 02) · **Size:** L
**Read first:** [README](README.md) (rules, facts, especially "The battle rules"); `rhc-android/README.md` §5.1; `CLAUDE.md` → "Netbeasts: the 3D Wilds"
**Status:** code done 2026-11-12; the APK and preview builds still need a check (see "Where it stands")

This was `NETBEASTS_PLAN.md` §2. It's the biggest step and the one everything
else waits on: a browser can't fight until it's done, and in multiplayer the
server has to run the fight.

## Goal

The fight rules run inside the sim, on the sim's ticks and RNG, with the
fight's state in the snapshot, so the phone, the browser and the server all
run the same fight. The phone plays exactly as it does now. The fight state is
built for **any number of sides** from the start, because multiplayer
([06](06-multiplayer.md)) needs three- and four-way fights and the user
doesn't want the rules rewritten twice.

## What's there now

- `app/.../engines/combat/`: `executePlayerMove`, `executeHumanPunch`,
  `armLastStandStrike` (`PlayerTurn.kt`), `triggerEnemyCounterAttack`
  (`EnemyTurn.kt`), `processEnemyVictory`, `endBattle`
  (`BattleResolution.kt`), the party traits (`PartyTraits.kt`: Herd, Hoard).
  All are `GameActivity` extension functions.
- `engines/SkillEngine.kt`: `SKILL_DATABASE`, `resolveSkill(activity, pet,
  move)`, `isAntiAir`, `isAntiFlyingDouble`. Rolls `kotlin.random.Random`.
- State: the `CombatState` object (shield, poison stacks, stun, terrify) and
  `GameActivity` fields (`currentEnemy`, `party`, `activePetIndex`,
  `battleOver`, `playerLastStand`, `playerHasTripleStrike`,
  `participatingPets`, `currentWeather`, `isUnderAttack`).
- Timing: `mainHandler.postDelayed` between the player's move and the
  counter-attack (1500 ms and others), and 7 more in `world/WorldFight.kt`.
- Show: `printLog`, `playSpriteAnim`, `AnimUtils.*`, `AudioEngine.playSfx`,
  `playFx`, `showBattleArena`, the battle timer, `vibratePhone`.
- Data: `GameModels.kt` (`Netbeast`, `GameData.beasts`, `GameData.traits`).
  No Android imports, but `Netbeast.isGrouchy()` reads
  `System.currentTimeMillis()`, and traits are parsed out of the name
  (`"[Frenzy] Noobit"`) all over the rules.
- Two front ends use the rules: the Wilds fight (`world/WorldFight.kt`) and
  the old battle screen (`gameui/BattleUI.kt`: text-mode exploring, bosses,
  the red-wall Defend). Both must run on the moved code, with no second copy
  left behind.

## What to build

- **A `battle/` package in `world-core` `commonMain`** with `Netbeast`, the
  beast and trait tables, `SkillEngine` and the turn rules. Pure Kotlin:
  `kotlin.math.abs` not `Math.abs`, no `System`, the time for `isGrouchy`
  passed in (the sim's clock or a `nowMs` the host gives the fight at its
  start, which goes in the snapshot).
- **Rolls come from the sim's RNG**, never `kotlin.random.Random`.
  Floating-point `+ - * /` and `toInt()` are the same on the JVM and wasm, so
  the damage maths can stay as it is; any `pow`, `sin` and similar go through
  `DetMath` or are replaced.
- **Time is ticks.** Each `postDelayed` becomes a tick countdown in the
  fight's state (1500 ms is 45 ticks at 30 Hz). Keep the same lengths so the
  game feels the same.
- **The fight's state lives in the `World` and in the snapshot**: a `Fight`
  with an id and a list of **sides**. A side is a wild beast, a player (with
  the netbeast they have out and their cages), or a rival/boss. Each side has
  its own shield, poison, stun, terrify, streaks, triple strike and last
  stand: what `CombatState` and the `GameActivity` fields hold today, once per
  side instead of once in total. A move has an attacker and a **target** (a
  side). With one player and one beast this is today's fight.
- **The rules talk to the screen through events.** The sim emits
  `WorldEvent`s (or a `FightEvent` inside one): a log line, an animation
  (`Action`), an effect (`fx`), a sound key, a vibration, the fight ending
  and how. The app turns those into `printLog`, `playSpriteAnim`, `playFx`,
  `AudioEngine.playSfx`, `vibratePhone` exactly as now. Today's
  `perform(role, action)` / `effect(role, fx)` hooks go away in favour of the
  sim doing it itself. Log text stays as it is (emoji and all), built in
  `world-core`.
- **Inputs, not calls.** Using a move, punching, swapping, throwing a net,
  using a potion or spray become fields on `PlayerInput` (for example
  `move: String?`, `target: Int`, `item: String?`), applied on the next tick
  like `throwCage` is now. The player's party and bag that the fight needs
  are given to the world when the fight starts (and on a server, come from the
  save, [05](05-save-identity-aether.md)).
- **What stays on the phone:** what happens to the save after a fight (coins,
  XP, catching a beast into `PARTY_DATA`, deaths into `GRAVEYARD_DATA`), done
  from the fight-ended event. In multiplayer the server sends that event to
  the player's client, which writes the save. Keep the line clear: the sim
  decides what happened, the host writes it down.
- **The killing blow captures.** Agreed with the user for multiplayer: "If the
  killing blow on a netbeast is landed by a user, the netbeast gets captured
  rather than slain." Find out with the user whether that's already how it
  works in single-player before changing it there (**Ask**). Either way the
  fight state should record which side landed the last hit.
- **Weather** (`currentWeather`) is an input to the fight, given at its
  start, in the snapshot.

## Ask

- The killing-blow rule above: multiplayer only, or single-player too.
- Anything that would change how a fight plays on the phone. The default is
  that nothing does.

## Tests

- `DeterminismTest`: a scripted fight that goes through the real rules
  (damage, traits, weather, a catch, a loss), and a scripted **three-sided**
  fight (two players and a wild beast, the beast switching targets), each
  matching bit for bit on the JVM and wasm. Re-pin `WALK_PRINT`,
  `POACHER_PRINT`, `BOSS_PRINT` on purpose and say so in the commit.
- `tools/world_preview/run.sh`: the hunter soak now fights through the moved
  rules to the end (until now the soak's fights resolve by hand).
- Unit tests for the rules that are easy to get wrong when moving them:
  Persistence/Dynamism streaks, Herd/Hoard, Elusive and Flying misses, the
  event boss's weakness, Thick-Skinned/Spiked/Vampiric, last stand.
- On a phone: a Wilds fight, a text-mode boss, and a red-wall Defend all play
  as before.

## When it lands

- Rewrite "Fights stay in the world" in `CLAUDE.md`, which says the rules
  live in `engines/combat/`.
- Update `rhc-android/README.md` §5.1 (the `perform`/`effect` hooks, the code
  table).
- Check `rhc-common` (the desktop C++) has no copy of these rules that now
  needs the same change (`CLAUDE.md` → Conventions).

## Interfaces (for later steps)

- The `PlayerInput` fight fields and the fight events: the web client (04)
  sends and shows exactly these.
- `Fight` and its sides in `WorldSnapshot`: the server (06) sends them.
- What a fight needs at its start (party, bag, weather, time) and what it
  hands back at its end (outcome, catches, deaths, coins, XP): the save
  (05) applies the latter.

## Done when

- `engines/combat/` and the old `SkillEngine` are gone from `app`, both
  front ends run on `world-core`, and nothing in the rules imports `android.*`
  or `kotlin.random.Random`.
- The tests above pass, the four flavors build, and a phone plays a fight the
  same as before.

## Where it stands (2026-11-12)

- **Done:** `world-core/.../world/battle/` (`Fight.kt`, `FightModel.kt`,
  `FightRng.kt`, `PartyTraits.kt`, `LocalFight.kt`), with `Netbeast.kt`,
  `SkillEngine.kt` and `GameData.kt` moved into world-core's commonMain
  (same package, so app imports didn't change). The World owns fights
  (`startFight`, `sideOf`, `WorldEvent.Fighting`, `WorldSnapshot.fights`);
  `PlayerInput` carries a `FightInput` instead of `throwCage`/`recall`.
  `engines/combat/`, `engines/SkillEngine.kt`, `CombatState.kt` and
  `GameModels.kt` are gone from the app; `gameui/FightHost.kt` is its host
  (sides in, events out, the save written from them) and `WorldFight.kt` the
  Wilds' panel and HUD over the World's fight.
- **Decisions:** a killing blow captures a wild beast in multiplayer only
  (`FightSetup.killingBlowCaptures`, off on the phone). The beast's own side
  reads `CAUGHT` too ("it was caught"). Single-player World fights are one
  player against one foe; the rules take any number of sides (tested three).
- **Tests:** `FightTest` (the rules) and `DeterminismTest` (walk, poacher,
  boss, a battle-screen fight, a three-sided fight) pass on the JVM and wasm
  with the same pins; MAP/COAST unchanged. WALK/POACHER/BOSS were re-pinned
  on purpose.
- **Still to check:** the four flavors compiled with `compile*ReleaseKotlin`
  but no APK was built or played. `bash tools/world_preview/run.sh` (Kotlin
  1.9 from Gradle 8.7's lib) hadn't been rerun after a 1.9 smart-cast fix
  in `Fight.kt`; its hunter soak now fights through the rules.
  `ROADMAP.md` (owned by step 01's session) still needs this step marked done.
