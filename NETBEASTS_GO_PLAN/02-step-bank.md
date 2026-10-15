# 02. The step bank

**Needs:** nothing (it can ship before 01) · **Size:** S
**Read first:** [README](README.md), rules 1 and 5; `CLAUDE.md` → Conventions
(asking for a permission past the Guardian).

**Status:** built 2026-10-16. Waiting on its "Done when" checks: the Gradle
tests and the four-flavor compile in the Codespace (the session that wrote it
had no JDK), then the user's walk.

## Goal

Walking with the phone in your pocket earns something. When you next open
Netbeasts it says how far you've walked since last time and pays you for it.
It needs no location and no screen, so it's the part of this plan that fits
RHC best, and it's worth having even if nothing else here gets built.

## What was built

- **The bank** (`world-core` `world/go/StepBank.kt`, pure). Given each
  reading of the phone's step counter and a day number (the epoch day). The
  first reading ever only sets where counting starts, so the steps since the
  phone started up don't count. A reading lower than the last one means the
  phone restarted, so the reading itself is the new steps. Today's steps stop
  at the cap. `payOut()` turns unpaid steps into coins, keeps the remainder
  (under 200) banked, and gives back the steps walked since the last pay-out.
- **Healing** (`world/go/StepHealing.kt`, pure). See below.
- **The sensor** (`app/src/main/.../go/StepCounter.kt`). Registers for
  `TYPE_STEP_COUNTER` with a 5-minute report latency. `GuardianService`
  starts it when it connects and stops it in `onDestroy`; the game starts it
  too, in case the Guardian is off. It does nothing outside the Netbeasts APK.
  A pay-out first flushes the readings the phone is holding back (waiting up
  to 2 s), then pays.
- **The permission.** `ACTIVITY_RECOGNITION` (and the step counter as an
  optional feature) in `app/src/gamersMaleNetbeasts/AndroidManifest.xml`
  only. Offered once, with "Walk with me in your pocket and I'll count your
  steps", on a later opening than the location offer so the two don't come
  together. Asked through `PermissionGate`. Before Android 10 no permission is
  needed.
- **Paying out** (`GameActivity.payOutSteps`). Each time the Netbeasts screen
  opens: coins, healing, a line in the console ("👣 2,340 steps since you
  last looked: +11 coins", and "💚 The walk healed your party (n HP)") and a
  toast. The hub has no coin counter to pop, so the toast stands in for the
  Wilds' "+n" pop.
- **No step counter on the phone:** nothing is said or paid.

## Healing

The user's answer: steps heal netbeasts, "flying ones can be slow to heal,
higher levels heal slower too". Proposed by Claude 2026-10-16; check it once
it's been played.

Each beast heals in proportion to the steps walked since the last pay-out. A
beast needs `stepsToHeal` steps to go from nothing to full:

    stepsToHeal = (2,000 + 200 × level) × (2 if Flying else 1)
    HP healed   = steps × maxHp / stepsToHeal, never past full

Level is the app's `maxHp / 10`. Some examples:

| Beast | Level | Steps for a full heal | A 1,500-step walk heals |
|---|---|---|---|
| Noobit | 6 | 3,200 | about half |
| Zephyrlet (flying) | 3 | 5,200 | about 30% |
| Grindlord | 25 | 7,000 | about a fifth |
| Stratolord (flying) | 13 | 9,200 | about a sixth |

A day's capped steps (15,000) heal anything fully. Knocked-out beasts (0 HP)
heal too. The constants are `StepHealing.BASE_STEPS`, `STEPS_PER_LEVEL` and
`FLYING_FACTOR`.

## What steps buy later

Step [04](04-beasts-at-real-places.md) adds **trails**: every so many steps, a
beast has followed you and waits where you stand when you open the radar.
`StepBank` grows a `trails` count then (one more saved number). It isn't built
here.

## Ask

- **What steps buy, and the cap.** 1 coin per 200 steps, counting up to 15,000
  steps a day (75 coins) (agreed with the user 2026-10-16).
- **Should steps also heal your netbeasts?** Yes, more slowly for flying and
  higher-level ones (agreed with the user 2026-10-16). The rule above is
  Claude's design of it.

## Interfaces (for later steps)

- `StepBank(last, day, stepsToday, unpaid, walked)`: `onReading(counter, day): Int`
  (steps added), `payOut(): StepPayout(steps, coins)`, `stepsToday`.
  `StepBank.STEPS_PER_COIN = 200`, `DAILY_CAP = 15_000`.
- `StepHealing.heal(steps, hp, maxHp, flying): Int`, `stepsToHeal(level, flying)`.
- `StepCounter.start(context)`, `stop()`, `available(context)`, `allowed(context)`,
  `payOut(context) { StepPayout? -> }`, and `shouldOffer` / `offer` /
  `onPermissionResult` (request code 4218).
- Prefs: `STEP_LAST`, `STEP_DAY`, `STEP_TODAY`, `STEP_UNPAID`, `STEP_WALKED`,
  and `STEP_ASKED`.

## Done when

- `StepBank` tests pass on the JVM and wasm (`StepBankTest`): a normal day, a
  restart in the middle, the cap, two pay-outs in a row (the second pays
  nothing), the saved numbers, and healing. Run
  `cd rhc-android && ./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest`.
- All four flavors compile; only the Netbeasts APK asks for the permission
  (check the merged manifests for `ACTIVITY_RECOGNITION`).
- On the user's phone, with the Netbeasts APK:
  1. Open Netbeasts twice (the location offer comes on the first opening, the
     step offer on the second) and allow it.
  2. Note a party beast's HP, then close the game.
  3. Walk round the block with the phone in a pocket, screen off, and count
     roughly (or compare with the phone's own step count).
  4. Open Netbeasts: the console line should show about the right number of
     steps, about 1 coin per 200, and the beast's HP up.
  5. Close and open it again at once: no second pay-out.
