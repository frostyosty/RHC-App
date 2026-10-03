# 03. The radar

**Needs:** 01 · **Size:** M
**Read first:** [README](README.md), rules 1-3 and 9; [01](01-groundwork.md) →
Interfaces; `rhc-android/README.md` §5.1, the table of the Wilds' Android
parts (`WorldView`, `WorldBridge`), because this screen sits beside them.

## Goal

The outdoor screen. You're in the middle with north at the top, rings mark 25,
50 and 100 m, and things around you are dots at their true direction and
distance. It's built to be glanced at. This step has nothing to show as dots
yet except a test dot, and it carries the rules about moving.

It's a radar and not a map on purpose: the game doesn't know your streets
until [07](07-streets-and-caches.md), and a made-up landscape drawn over a
real town would be wrong about every wall and road.

## Build

1. **Where it lives.** `go/GoView.kt` is a view inside `GameActivity`, shown
   in place of the Wilds window the way `WorldView` is (`showWorld` /
   `hideWorld` in `WorldBridge.kt` are the model; put the glue in
   `go/GoBridge.kt`). That way the party, the bag and the fight panel are the
   ones the game already has.
2. **What it draws** (an ordinary `Canvas`; no renderer needed):
   - you, as the arrow the minimap uses, turned to your heading;
   - the three rings, a north mark, and a faint disc as wide as the fix is
     unsure;
   - dots, coloured the way the minimap colours what it shows; a dot in reach is ringed and
     pulses;
   - the weather's sky colour behind it, from the same look the Wilds use, so
     it darkens in rain and toward evening.
3. **The glance line.** Big text under the radar says the one thing worth
   knowing: "Chirplet, 60 m north-east", or "Nothing close. Try a walk." A
   glance has to be enough (rule 1).
4. **Movement** (`Movement`, pure, in `world-core` `world/go/`), from the last
   several fixes:

   | State | When | What the screen does |
   |---|---|---|
   | `STILL` | under 1 m/s for 3 s | everything works |
   | `WALKING` | 1-4 m/s | the radar and the glance line only; nothing can be tapped |
   | `TOO_FAST` | over 4 m/s for 5 s; it lifts after 10 s under | the dots go; "You're moving too fast to play." |
   | `LOST` | no fix for 10 s, or none better than 30 m | the dots dim; "Looking for the sky…" |

   A single fix that jumps must not flip the state: `tools/go_sim`'s jumping
   walk checks that.
5. **Aether and Nightfall.** The radar counts as being in the game, so Aether
   runs down while it's on screen (it's `GlobalTick` that decides when the
   Wilds and the explore screen drain it). Nightfall closes it the way it
   closes the Wilds.
6. **Metres with the radar open** go to the step bank's pay-out on phones with
   no step counter ([02](02-step-bank.md), part 5), at 0.75 m a step.
7. **A test dot.** Behind the debug toasts setting (`DEBUG_UI_TOASTS`), place
   a dot 60 m north of where the radar opened. It's what the walk below uses.

## Ask

- **North at the top, or the way you're facing?** Default: north at the top,
  like the Wilds' minimap. Facing-up is easier to follow on foot but spins
  when the compass is unsure.

## On a walk (the user, on their phone)

1. Open the radar outside. How long until it stops saying "Looking for the
   sky…"?
2. Walk to the test dot. Is it where the radar said, to within the reach
   ring? Does the arrow point the way you're walking?
3. Does it go quiet while you walk and wake when you stop?
4. In a car as a passenger: does it say you're too fast?
5. Could you read the glance line in sunlight?

Write the answers under Findings.

## Interfaces (for later steps)

- `Movement.state(fixes): MoveState` (`STILL`, `WALKING`, `TOO_FAST`, `LOST`).
- `GoView.setDots(list of GoDot(id, point, stage, label))`,
  `onDotTapped(id)` (only fires for a dot in reach while `STILL`).
- `GameActivity.showRadar()`, `hideRadar()`.
- `REACH_M` (40 to start with).

## Done when

- `Movement` tests pass on the JVM and wasm for the four fake walks.
- All four flavors compile; the radar opens only in the Netbeasts APK with the
  switch on.
- On the emulator, a fake walk moves the test dot toward the middle.

## Findings

(Filled in from the walk.)
