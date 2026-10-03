# Netbeasts Go plan

Netbeasts played outdoors, on foot: the beasts are at real places near you, you
walk to them, and you can catch them through the camera. "Netbeasts Go" is a
working title (see the open questions). None of it is built.

This folder is kept apart from `NETBEASTS_PLAN.md` (the Wilds, the web client,
multiplayer) and `HOMEVISITS_PLAN/`. `ROADMAP.md` at the repo root says where
these steps come among the other plans' steps.

**How to read this folder.** Everyone reads this file. Then read your step's
file, plus only what its **Read first** line lists. When a step needs something
an earlier step built, it points at that step's **Interfaces** section. Each
Interfaces section makes sense without the rest of its file.

## The idea

From the user (2026-10-03): "a version of Netbeasts that is more like Pokémon
Go", which "would probably involve some duplicate code from" the Homevisits
plan (the camera and AR).

**That sentence is all the user has said so far.** Everything else here was
proposed by Claude on 2026-10-03 and isn't agreed. The choices that shape the
whole plan are in the open questions: ask them before step 01 starts.

What "like Pokémon Go" comes to, piece by piece:

| The piece | What RHC already has | Built in |
|---|---|---|
| The game knows where you are, and follows you | `LocationEngine` takes one fix for the weather | [01](01-groundwork.md) |
| Walking is rewarded, with the phone in your pocket | nothing | [02](02-step-bank.md) |
| A screen that shows what's around you | the Wilds' minimap | [03](03-radar.md) |
| Creatures at real places, the same ones for everybody | The Wilds are the same for everyone in an area on a day (the date's seed and the `Region`) | [04](04-beasts-at-real-places.md) |
| Catching by throwing something at it | Nets, inside a fight. A net thrown outside a fight is a loose note in `NETBEASTS_PLAN.md` | [04](04-beasts-at-real-places.md) |
| The creature standing in your real surroundings | Homevisits' AR code (camera, ARCore session) | [05](05-ar-kit.md), [06](06-ar-encounters.md) |
| A map of real streets, and stops at real landmarks | nothing | [07](07-streets-and-caches.md) |
| Other players, and big fights together | Planned for the Wilds (`NETBEASTS_PLAN.md` §4, phase 5) | [08](08-together.md) |

## Why it suits RHC, and where it pulls against it

RHC blocks distractions and hands back time. A game that gets you out of the
house on foot fits that. A game that keeps you staring at a screen while you
walk doesn't, and it's unsafe near roads. So the design leans one way: **the
walk needs no screen**. You glance, pocket the phone, walk, and take it out
again when you've arrived. The first rule below is the one to keep when the
others are in doubt.

## Rules for every step

1. **Pocket first.** Nothing the game wants from you needs the screen on while
   you move. Steps count with the phone in your pocket. A glance at the radar
   tells you which way and how far. Aether drains only while the game is on
   screen, as it does now, so looking less costs less.
2. **Stand still to play.** Fights, catching and anything with reading or
   buttons only start when you're standing still (under about 1 m/s). Above
   about 4 m/s (a bike or a car) the radar empties and says why. While you
   walk the screen shows one short line of text at most.
3. **Never send the player to an exact spot.** The game can't know that a spot
   is public or safe. Reach is generous (about 40 m), so a beast can be met
   from the footpath, and the game never says "go there". Before the camera
   opens it says "Stand somewhere safe, off the road."
4. **Beasts never ambush.** The Wilds rule from `CLAUDE.md`. A beast in reach
   waits to be tapped. No notifications unless the user asks for them.
5. **Where you are stays on the phone.** What's around you is worked out on
   the phone. No track of your walk is saved: only the last fix (which
   `LocationEngine` already saves as `LAST_LOCATION`) and totals. Anything
   sent to a service is rounded to about 1 km, as the weather lookup is today.
6. **The same world for everybody, with no server.** Whatever is placed in the
   real world comes from a hash of the place, the time and the region, in
   whole numbers. Two players standing together see the same beast because
   their phones did the same sum. That code is pure Kotlin in `world-core` and
   follows the sim rules in `CLAUDE.md`: `DetMath` trig, no wall-clock time
   (the time is passed in), pinned on the JVM and wasm.
7. **One set of fight rules, one fight.** An outdoor encounter is a Wilds
   fight: the same sim (`World`), the same panel (`WorldFight.kt`), the same
   battle rules. The camera view is another way of drawing that fight. Don't
   fork any of it.
8. **Camera code is shared, and nothing it sees leaves the phone.** The AR
   code is one kit that Homevisits and this game both build
   ([05](05-ar-kit.md)). No Cloud Anchors and no Geospatial API: both send
   camera images to Google, and Geospatial needs a billed key.
9. **A daytime game, capped like the rest.** Nightfall closes it and Aether
   caps it. Beasts are the only strange thing out there.
10. **It degrades gently.** No ARCore: the fight shows in the Wilds view. No
    step sensor: distance counts only while the radar is open. No location
    permission: the outdoor game stays off and the Wilds are as before.

## How it fits together

```
rhc-android/
  world-core/.../world/go/           pure Kotlin, with the sim
    GeoGrid, GoClock                 places and time as whole numbers         step 01
    StepBank                         steps into trails and coins              step 02
    Movement                         still, walking, too fast                 step 03
    GoSpawns                         which beast is where, and when           step 04
  app/src/main/.../go/               the outdoor game (Netbeasts APK only)
    GoTracker.kt                     following fixes and the compass          step 01
    StepCounter.kt                   the phone's step sensor                  step 02
    GoView.kt, GoBridge.kt           the radar, and its glue into GameActivity step 03
    GoEncounter.kt                   from a tap on the radar into a fight     step 04
    ArFightView.kt                   the fight drawn over the camera          step 06
    StreetMap.kt                     streets, parks and caches                step 07
  app/src/ar/                        the AR kit, built by both Gamers APKs    step 05
tools/go_sim/                        phone-free checks: fake walks through a fake town
```

The core decides **what** is there and what you may do. The app finds out where
you are, draws it and reports what you tapped. Everything the core decides can
be tested without a phone or a walk.

## The steps

| # | Step | What it gives | Needs | Size | Status |
|---|---|---|---|---|---|
| 01 | [Groundwork](01-groundwork.md) | The game follows where you are while it's open; places and time as whole numbers; fake walks to test with | — | M | not started |
| 02 | [Step bank](02-step-bank.md) | Walking with the phone in your pocket earns coins, and later trails | — | S | not started |
| 03 | [The radar](03-radar.md) | The outdoor screen: you, north, rings, dots; the standing-still and too-fast rules | 01 | M | not started |
| 04 | [Beasts at real places](04-beasts-at-real-places.md) | Beasts around you that everyone sees; walk into reach, tap, fight in the Wilds view; trails | 01, 02, 03 | M | not started |
| 05 | [The AR kit](05-ar-kit.md) | Homevisits' camera code moved to where both games build it; one way to ask for permissions | Homevisits 01 | S | not started |
| 06 | [AR encounters](06-ar-encounters.md) | The beast on your real ground through the camera; flicking a net at it | 04, 05, Homevisits 02's findings | L | not started |
| 07 | [Streets and caches](07-streets-and-caches.md) | Real streets and parks under the radar; caches at landmarks; beasts kept off roads and water | 03, 04 | L | not started; optional |
| 08 | [Together outdoors](08-together.md) | Other players nearby, shared fights, the colossus as something you gather for | 04; `NETBEASTS_PLAN.md` §4 and phase 5 | L | not started; a sketch only |

Size (S, M or L) compares the steps with each other. It isn't a time estimate.

02 and 05 need nothing from the other steps here, so either can go first or
alongside. 01, 03 and 04 are the line to the first playable outdoor game, with
no camera. 06 puts the camera on top.

## Open questions

**Ask** marks a decision that belongs to the user. Ask it when its step starts,
and don't settle it yourself. Each one has a default to propose. When it's
answered, write the answer where the Ask is, with "(agreed with the user
<date>)".

The first four shape everything, so ask them before step 01.

| Step | Question | Default to propose |
|---|---|---|
| all | A mode inside the Netbeasts APK, or its own APK? | A mode in `gamersMaleNetbeasts`: same party, bag and coins, and no fifth APK to build and release |
| all | Does it replace the 3-minute Wilds walk or sit beside it? | Beside it. The Wilds are for indoors, rain and the evening |
| all | Does outdoor play share the day's Aether with the Wilds? | Yes. The step bank is the part with no cap, because it needs no screen |
| all | The name | "Netbeasts Go" until the user picks one |
| [02](02-step-bank.md) | What steps buy, and the daily cap | 1 coin per 200 steps, up to 15,000 steps a day; from step 04, a trail per 1,000 steps |
| [03](03-radar.md) | North at the top, or the way you're facing? | North at the top, like the Wilds' minimap |
| [04](04-beasts-at-real-places.md) | How far reach is, and how many beasts are about | 40 m; about 8 within 150 m |
| [04](04-beasts-at-real-places.md) | Is a net thrown before the fight part of this? | Yes, once it's built for the Wilds (`NETBEASTS_PLAN.md`, the loose note) |
| [06](06-ar-encounters.md) | Phones that can't run ARCore | The Wilds view. The alternative is a camera view steadied by the gyroscope |
| [06](06-ar-encounters.md) | Is the camera on by default for a fight? | Yes on phones that can, with a switch on the fight screen |
| [07](07-streets-and-caches.md) | Use OpenStreetMap data at all? It's a new outside service | Yes, asked for by the 1 km square and kept for 30 days |
| [07](07-streets-and-caches.md) | Are churches caches? | Yes, with parks, reserves, playgrounds and lookouts |
| [08](08-together.md) | Seeing other players means sharing roughly where you are | Off unless switched on; never finer than a fight's reach |

## Testing

- **Pure code:** `cd rhc-android && ./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest`.
  Step 04 adds a pin for the spawns next to the map's in `DeterminismTest`.
- **Without a phone:** `bash tools/go_sim/run.sh` (made in step 01, added to by
  02-04 and 07). It plays fake walks: a stroll, standing still with a jittery
  fix, a car, a fix that jumps 300 m.
- **The APK:** `./gradlew compileGamersMaleNetbeastsReleaseKotlin`. If you
  touch `app/src/main` or the AR kit, check that all four flavors compile.
  `./release.sh` commits, pushes and publishes, so don't run it unless asked.
- **The Codespace emulator.** It can't do AR, but it can fake a walk:
  `adb emu geo fix <lon> <lat>` in a loop. The emulator's network provider
  never answers, so a test build has to put GPS first. The AVD is
  `NetbeastPhone`; `HOMEVISITS_PLAN/README.md` (Testing) has how to start it.
- **Outdoors needs the user's phone and a walk.** Steps 03, 04 and 06 each end
  with a short checklist for the user, and their answers go under Findings in
  that step's file.

## Words used in these files

- **Fix:** one reading of where the phone is, with how sure it is.
- **Cell:** a square of ground about 50 m across, named by two whole numbers.
- **Slot:** half an hour of the day, named by a whole number. Beasts change
  with the slot.
- **Reach:** how close you must be to a beast or a cache to use it.
- **Radar:** the outdoor screen. It isn't a map until step 07.
- **Step bank:** the steps counted since you last opened the game.
- **Trail:** what banked steps turn into from step 04: a beast that followed
  you, waiting where you stand.
- **Cache:** a real landmark that gives coins or nets once a day (step 07).
- **AR kit:** the camera and ARCore code that Homevisits and this game share.

## Keeping this plan current

- When a step lands, mark it `done <date>` in the steps table. In the same
  change, move anything that should last (rules, constants that must match,
  test commands) into `CLAUDE.md` and a new section of `rhc-android/README.md`
  next to §5.1. Keep the step's **Interfaces** section accurate. Trim the rest
  of the step's file to a short note of what was built. Update `ROADMAP.md`.
- When the user answers an Ask, write the answer where the Ask is.
- New ideas the user agrees to go into the step they belong to, or a new
  numbered step.

## Not planned

Ask the user before building any of these:

- Finding where you are while the game is closed, or telling you a beast is
  near. Both need background location, which costs battery and privacy.
- Eggs, gyms, trading, and anything bought with real money.
- A server that decides what's where. Rule 6 is the reason there isn't one.
- The outdoor game in the Homevisits or Timesavers APKs. (Steps counting
  toward Momentum in Timesavers would be a small spin-off of step 02.)
- The outdoor game in the web client.
