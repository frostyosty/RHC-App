# 02. The step bank

**Needs:** nothing (it can ship before 01) · **Size:** S
**Read first:** [README](README.md), rules 1 and 5; `CLAUDE.md` → Conventions
(asking for a permission past the Guardian).

## Goal

Walking with the phone in your pocket earns something. When you next open
Netbeasts it says how far you've walked since last time and pays you for it.
It needs no location and no screen, so it's the part of this plan that fits
RHC best, and it's worth having even if nothing else here gets built.

## Build

1. **The sensor.** `Sensor.TYPE_STEP_COUNTER` gives the steps since the phone
   last started up. It only keeps counting while something is listening, so
   one listener has to stay registered. `GuardianService` is always running:
   register there (`go/StepCounter.kt` holds the code), with a report latency
   of a few minutes so the phone can batch the steps and sleep.
2. **The permission.** From Android 10 the sensor needs
   `ACTIVITY_RECOGNITION`. Declare it in the Netbeasts flavor's manifest
   (`app/src/gamersMaleNetbeasts/AndroidManifest.xml`, which this step or
   [05](05-ar-kit.md) creates, whichever comes first), so the other APKs don't
   carry it. Ask the way `LocationEngine.requestPermission` does, or with
   05's `PermissionGate` if that exists by then. Say why first: "Walk with me
   in your pocket and I'll count your steps."
3. **The bank** (`StepBank`, pure, in `world-core` `world/go/`):
   - It's given each counter reading. A reading lower than the last one means
     the phone restarted, so the reading itself is the new steps.
   - It keeps today's steps and the steps not yet paid out, and stops adding
     at the daily cap. The cap is there so that shaking the phone isn't worth
     the bother.
   - `payOut()` turns unpaid steps into coins and leaves the remainder banked.
   - The app saves its three numbers in prefs (`STEP_LAST`, `STEP_TODAY` with
     its day, `STEP_UNPAID`).
4. **Paying out.** When the Netbeasts screen opens, pay out and show it where
   the coin counter is: "2,340 steps since yesterday: +11 coins", with the
   same "+n" pop the Wilds use for a coin.
5. **Phones with no step counter.** Say nothing and pay nothing. Step 03 lets
   metres walked with the radar open count instead.

## What steps buy later

Step [04](04-beasts-at-real-places.md) adds **trails**: every so many steps, a
beast has followed you and waits where you stand when you open the radar.
`StepBank` grows a `trails` count then. Leave room for it, and don't build it
here.

## Ask

- **What steps buy, and the cap.** Default: 1 coin per 200 steps, counting up
  to 15,000 steps a day (75 coins). For scale, the Wilds give 5 coins a walk
  plus what you pick up, and the first mount is planned at 200.
- **Should steps also heal your netbeasts?** Default: no. Keep it to coins
  until it's been played.

## Interfaces (for later steps)

- `StepBank(last, today, unpaid)`: `onReading(counter, day)`, `payOut(): Int`
  (coins), `stepsToday`.
- `StepCounter.start(service)`, `stop()`, `available(context)`.
- Prefs: `STEP_LAST`, `STEP_TODAY`, `STEP_UNPAID`.

## Done when

- `StepBank` tests pass on the JVM and wasm: a normal day, a restart in the
  middle, the cap, two pay-outs in a row (the second pays nothing).
- All four flavors compile; only the Netbeasts APK asks for the permission.
- On the user's phone: a walk round the block with the phone in a pocket shows
  up as about the right number of steps and pays out once.
