# 04. Beasts at real places

**Needs:** 01, 02, 03 · **Size:** M
**Read first:** [README](README.md), rules 3, 4, 6 and 7;
[01](01-groundwork.md) → Interfaces; [03](03-radar.md) → Interfaces;
`rhc-android/app/.../world/WorldVisitors.kt` (how a poacher or a boss is put
in front of you in the Wilds: an outdoor encounter goes in the same way).

## Goal

The radar has beasts on it. They're at real places, the same ones for anybody
standing there, and they change every half hour. Walk until one is in reach,
stop, tap it, and you fight or catch it. The fight is shown in the Wilds view;
the camera comes in [06](06-ar-encounters.md). After this step the outdoor
game is playable.

## Which beast is where (`GoSpawns`, pure, in `world-core` `world/go/`)

`GoSpawns.around(cell, clock, region, weather): List<GoSpawn>` looks at the
cells within 3 of yours (about 150 m). For each cell:

1. **Is there one?** A hash of the cell and the clock's day and slot. About
   one cell in six has a beast, which puts about 8 on the radar.
2. **Where in the cell?** Two more hashes place it, kept 5 m in from the
   edges.
3. **How wild is this spot?** A slow noise over cells (the Wilds' wildness
   field, on real ground, through `GoHash`), which doesn't change with the
   clock. So some streets are always tame and some are always wild, and
   players learn their own town. Most ground is stage 1 or 2, with pockets of
   stage 3.
4. **Which species?** From the stage, as the Wilds choose, then nudged by the
   real weather (rain brings out the water kinds, and so on, using the types
   `WeatherEngine` already favours) and by the `Region` (coastal or not).
5. **Its id** is the cell, day and slot packed together, so the app can
   remember which ones you've already met.

A `GoSpawn` is `(id, point, species, stage)`. No RNG object is kept between
calls: everything is a hash, so the answer is the same in any order, on any
phone (rule 6). Pin it: a `SPAWN_PRINT` in `DeterminismTest` over a block of
cells for one clock, matching on the JVM and wasm.

The app remembers the ids you've beaten, caught or sprayed away this slot
(`GO_DONE` in prefs, cleared when the slot changes) and leaves them off the
radar.

## Meeting one (`go/GoEncounter.kt`)

1. The radar shows each `GoSpawn` as a dot. One within `REACH_M` is ringed.
2. Tap a ringed dot while standing still. (The radar only passes the tap on
   then: [03](03-radar.md).)
3. The Wilds open on today's land, with your real weather and region, and the
   beast is put a few tiles in front of you with `World.summon`
   (`EntityKind.BEAST`, a short `ahead`). You walk the last steps into it and
   square up. From there it's an ordinary Wilds fight: `WorldFight.kt`, the
   cages, the moves, the nets, the same battle rules. Make the wild beast the
   way `WorldFight` makes one, from the species and stage.
4. When the fight is resolved, the Wilds close and the radar comes back. This
   walk has no 3-minute timer: it lasts as long as the fight.
5. Winning, losing and catching pay what they pay in the Wilds.

Nothing new goes into the fight rules, and nothing here is a second copy of
them (rule 7). If opening the Wilds for one fight needs something the sim
doesn't have, add the smallest thing that does it, and pin it.

## The net, thrown first

`NETBEASTS_PLAN.md` has a loose note from the user: a net button while you
walk, which catches the beast if it lands and starts the fight if it misses.
That's the catch this game wants. When it's built for the Wilds, an outdoor
encounter opens on it: the beast is in front of you, and the net button is
right there. Don't build a separate version here. If it isn't built yet, the
encounter just starts as a fight.

## Trails

Banked steps turn into trails ([02](02-step-bank.md)): one for every 1,000
steps, up to 5 kept. When the radar opens with a trail banked and nothing in
reach, one trail is spent and a beast "has followed your trail": a spawn in
your own cell, good for this slot. It waits to be tapped like any other
(rule 4). This one is yours alone, so it's hashed with your player id and
isn't one that others see.

Trails are what make the game work when you've walked somewhere with nothing
about, and they're the reward for leaving the phone in your pocket.

## Fake town

`tools/go_sim` gains: a walk through 2 km of cells that counts how many
beasts came into reach (aim for one every 150-250 m); two players on the same
path a minute apart, who must see the same ids; a wild pocket that's in the
same place the next day.

## Ask

- **Reach and how many.** Default: 40 m, and about 8 within 150 m. In town a
  fix is often 10-20 m out, so a much shorter reach would be frustrating.
- **The net thrown first** (above). Default: yes, when the Wilds have it.
- **Should a beaten beast stay gone for the day, not just the slot?** Default:
  the slot.

## On a walk (the user, on their phone)

1. How many beasts were on the radar at home? At the park?
2. Could you get one in reach without leaving the footpath? Every time?
3. Was the one you fought the one the radar named?
4. With a friend's phone beside yours: the same beasts?
5. After a walk with the game shut, was a trail waiting?

## Interfaces (for later steps)

- `GoSpawns.around(cell, clock, region, weather): List<GoSpawn>`;
  `GoSpawn(id, point, species, stage)`; `GoSpawns.wildness(cell): Int`.
- `GameActivity.startGoEncounter(spawn)`, and `onGoEncounterEnded(spawn,
  outcome)`.
- Prefs: `GO_DONE`; `StepBank.trails`.

## Done when

- `SPAWN_PRINT` matches on the JVM and wasm; the existing pins are unchanged.
- `tools/go_sim`'s three checks pass.
- `bash tools/world_preview/run.sh` still passes (the Wilds soak).
- On the emulator, a fake walk brings a dot into reach, a tap opens the fight,
  and the radar comes back after it with that dot gone.

## Findings

(Filled in from the walk.)
