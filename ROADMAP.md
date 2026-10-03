# RHC roadmap

What to build next, across all three games. The detail is in each plan; this
file only says which step of which plan comes when, and why.

**The order below was proposed by Claude on 2026-10-03 and isn't agreed yet.**
When the user agrees or changes it, replace this paragraph with "agreed with
the user <date>".

## The plans

| Plan | Covers | Where it stands (2026-10-03) |
|---|---|---|
| `NETBEASTS_PLAN.md` | The Wilds; the battle rules moving into `world-core`; the web client; multiplayer; Wilds phases 4-6 | Wilds phases 1-3 are built. Two loose notes from the user sit near the end of the file (see 1 and 5 below) |
| `HOMEVISITS_PLAN/` | The game in the female Gamers APK, in 12 steps | Code exists for step 01 and part of step 02, and no step is signed off: the plan's table still says "not started". The APK shows a placeholder screen, so it has no game at the moment |
| `NETBEASTS_GO_PLAN/` | Netbeasts outdoors on foot, in 8 steps | Nothing built. The design is a proposal, with four questions to answer before its step 01 |

## What the games share

Build each of these once. This is where the plans would otherwise repeat each
other.

| Shared thing | Where it is today | Who needs it | Planned in |
|---|---|---|---|
| The camera and ARCore code | Homevisits' own source set (`homevisits/ar/`, about 320 lines) | Homevisits, Netbeasts Go | Go 05 moves it to where both build it |
| Drawing sprites over the camera | Not built | Homevisits, Netbeasts Go | Homevisits 02, built in the shared kit |
| Asking for an Android permission past the Guardian | `LocationEngine`, and a copy in `CameraPermission` | Location, camera, step counter | Go 05 (`PermissionGate`) |
| Decoding the autogen GIFs | `world/SpriteBank.kt` | The Wilds, both camera games | Built; the kit reuses it |
| The fight rules | `engines/combat/`, Android only | The battle screen, the Wilds, Netbeasts Go, the web, multiplayer | Netbeasts §2 moves them to `world-core` |
| Where you are | `LocationEngine`, one fix at a time | Weather, the Wilds' region, Netbeasts Go | Go 01 adds following |
| Daily play time and Nightfall | `AetherEngine` | All three games | Built |
| A pure, tested core with a phone-free tool | `world-core` + `tools/world_preview`; `homevisits-core` | All three | Go's core goes inside `world-core`; `tools/homevisits_sim` and `tools/go_sim` are still to make |
| Art | `sprite_studio/autogen` | All three | Built; Homevisits adds people and furniture (its `art.md`) |

## The order

Two things decide it. First, a step that needs the user's phone at home or on
a walk waits on the user, so those tests are batched and put early. Second,
the female APK has no game until Homevisits 04, so that comes before the new
outdoor game.

1. **Sign off what's half-built.** Small.
   - Homevisits 01: the code is there (`Flavor.kt`, the placeholder screen,
     `homevisits-core`). Go through its "Done when", finish what's missing
     (`GuardianService` still reads `BuildConfig.FLAVOR` in one place; the
     docs in its part 5), and mark the table.
   - The second loose note in `NETBEASTS_PLAN.md`: are the poacher and the
     boss right in the 3D Wilds, and pinned? They're both in
     `WorldVisitors.kt`. `DeterminismTest` pins a poacher fight
     (`POACHER_PRINT`). It has no test for a boss, which is a summoned beast.
     Check both in the game and add that pin.
2. **The shared AR kit** (Go 05). Small. Now, while there's little to move
   and before Homevisits 02 adds to it.
3. **Homevisits 02, finished on the kit, then one test outing by the user.**
   The at-home checklist in that step, plus ten minutes in the yard: does the
   ground get found on grass, and does a sprite stay put in sunlight? How
   well AR works on the user's phone is the biggest unknown in two of the
   three plans, and this is the cheapest point to find out.
4. **Homevisits 03, then 04: the first visit.** The first playable
   Homevisits, and the end of the placeholder.
5. **The net thrown while you walk** (the first loose note in
   `NETBEASTS_PLAN.md`). Small. It's a Wilds feature, and it's also how you
   catch things outdoors, so it comes before Go 04.
6. **Go 02: the step bank.** Small, needs nothing else, and pays out in the
   Netbeasts that exists today.
7. **Go 01, 03, 04: outdoors without the camera.** The first playable outdoor
   game. Each ends with a short walk by the user.
8. **Go 06: AR encounters.** By now the kit has had Homevisits 02 and 04 on
   top of it.
9. **Homevisits 05: coming back.** With this the camera track of Homevisits
   is done.
10. **Netbeasts §2, §3, §4**: the battle rules into `world-core`, the web
    client, multiplayer. The order agreed on 2026-09-27, unchanged.
11. **Homevisits 10, 11, 12**, once the people track (below) has reached 08.
12. **Netbeasts phases 4-6** (fleeing, the colossus, mounts), then **Go 07**
    (streets and caches, optional) and **Go 08** (together outdoors, which
    needs multiplayer and the colossus).

## Work that can go alongside

None of these needs a phone, and none touches the same files as the steps
above. Pick them up whenever the main line is waiting on a test.

- **Homevisits' people track: 06, 07, 08, then 09.** The emotions engine,
  conversations, visits. It's the larger half of that plan, all pure code,
  and 09 (visits without AR) gives the female APK a game on phones that can't
  do AR.
- **Netbeasts §2** (the battle rules into `world-core`) can come forward from
  10 if the web client matters sooner. Go 04 and 06 reuse the Wilds fight
  whole, so they don't depend on it and don't make it harder.
- **Battle effects and Wilds art** (`NETBEASTS_PLAN.md`, "Any time"), and
  Homevisits' art and cast.

## The judgement calls in this order

These are the places where the user might reasonably want it the other way:

- **Homevisits' first visit before the outdoor game** (4 before 7). The
  reason is the female APK's placeholder. If the outdoor game is the more
  exciting one, 5-7 can go first: they don't depend on 4.
- **The outdoor game before the web client and multiplayer** (7 and 8 before
  10). This pushes back the order agreed on 2026-09-27. The case for it: the
  outdoor game is single-player and reuses what's built, while the web client
  starts with moving all the fight rules.
- **Go 07 last.** Real streets are what most makes it look like Pokémon Go,
  but they bring an outside service, and the game works without them.

## Decisions waiting on the user

Soonest first. Each is an **Ask** in its plan, with a default there.

| Before | Decision | Where |
|---|---|---|
| 3 | Phones that can't run ARCore; camera permission denied for good | `HOMEVISITS_PLAN/02-ar-foundation.md` |
| 5 | How the net thrown while walking works: the odds, and what a miss costs | The loose note in `NETBEASTS_PLAN.md` |
| 6 | What steps buy, and the daily cap | `NETBEASTS_GO_PLAN/02-step-bank.md` |
| 7 | A mode in the Netbeasts APK or its own APK; beside the Wilds or instead; shared Aether; the name | `NETBEASTS_GO_PLAN/README.md` |
| 10 | Where the save and identity live; host or server | `NETBEASTS_PLAN.md` §3, §4 |
| 12 | Whether to use OpenStreetMap data | `NETBEASTS_GO_PLAN/07-streets-and-caches.md` |

## Keeping this current

- When a step lands, strike it here (or move the numbering on) in the same
  change that marks it done in its plan.
- A new plan gets a row in the first table and its steps a place in the
  order. Shared code it needs goes in the second table before anyone builds a
  second copy.
- This file never holds a step's detail. If a line here grows past a few
  sentences, it belongs in the plan.
