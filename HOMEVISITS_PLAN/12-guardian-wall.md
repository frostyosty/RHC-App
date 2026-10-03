# 12. The Guardian wall

**Needs:** 01 (and 06 for the cameo) · **Size:** S · **Track:** either
**Read first:** [README](README.md); `CLAUDE.md` → Conventions. Then
`GuardianService.handleReward` and `GuardianService.triggerBossInvasion` themselves.
Nothing else in this folder.

## Goal

When the Guardian blocks something in the Homevisits APK, the block screen (the
"wall") and its rewards feel like Homevisits: Momentum gained, a word from a friend,
no invasion and no losses. After step 01 it's the Timesavers wall. This step gives
it its own look.

## Today

`triggerBossInvasion` shows a small slide-in first. Tapping the slide-in opens the
full overlay (`overlay_guard.xml`).

**Netbeasts:**
- The slide-in is red: "⚔️ INVASION: <boss>".
- The full overlay's title is "INVASION DETECTED", with a three-minute timer.
- [Defend] opens `GameActivity` with `UNDER_ATTACK`.
- Fleeing sets a 15-minute lockout (`FLEE_LOCKOUT_UNTIL`), and netbeasts can die.
- `handleReward` gives an orphaned netbeast.

**Timesavers:**
- The slide-in is green: "MOMENTUM GAINED: +N MINS".
- [Claim Momentum] adds Momentum and opens `MainActivity`. [Dismiss] goes back.

**Every branch overwrites the title.** The overlay's title view is set to
`@string/overlay_title` in the layout, which is "AVERT YOUR EYES." in this flavor.
But `triggerBossInvasion` sets the title in every branch, so today that string is
never seen. The Homevisits wall uses it.

## The Homevisits wall

- **The slide-in:** "⚡ +14 Momentum · you stepped away", in this flavor's own warm
  colour, not the invasion red.
- **The full screen:**
  - the flavor's title ("AVERT YOUR EYES.") and message ("Take a deep breath and
    step away.");
  - how much Momentum it's worth;
  - a **cameo**: a portrait of the character who likes the player most, with one
    line. For example: "Rachel: Good on you. Come show me that painting later?"
- **The buttons:**
  - [Claim Momentum] adds the Momentum, as Timesavers does, and opens Homevisits'
    home screen, with today's Momentum and the catalogue;
  - [Not now] dismisses the wall.

  No lockout and no losses (see the Ask).
- **Nightfall, the strict modes and anti-tamper:** nothing to claim, just the
  "secured" message, as in Timesavers.

## The cameo, without the core

`GuardianService` is in `main`, and it must not use `homevisits-core`. So Homevisits
writes the cameo ahead of time. Whenever the mind's state changes (the end of a
scene, a new day):
1. Pick the character with the highest affinity to the player (ties go by id).
2. Pick a line from their storylets marked `channel wall` (07).
3. Save `HV_WALL_CAMEO` in prefs, as "name|portrait resource|line".

The Guardian reads and shows the cameo. If it's empty (before anyone has been met),
the wall shows no cameo.

## The first time an urge is overcome

`handleReward` in Homevisits:
- gives Momentum, as in Timesavers;
- queues a small surprise parcel for the next visit: a free item from a "thank-you"
  list. It goes through a prefs flag that the Homevisits side reads when it opens,
  and then into 11's delivery queue.

## Characters notice

Once a day, the app sends the core a `PlayerDay` event (06): the Momentum earned and
the urges overcome that day. Characters who value reliability, or who care about the
player, can mention it ("You've been off your phone heaps. Good on you."). Never the
other way round: nothing is ever said about slips (README rule 7).

## Done when

On the emulator, with the Guardian on:
- a blocked app shows the Homevisits wall, with a cameo;
- claiming adds Momentum and opens Homevisits;
- Nightfall and the strict modes show the "secured" message;
- the male Gamers APK still gets invasions;
- the Timesavers APKs are unchanged.

## Ask

- **Any penalty for dismissing the wall.** Netbeasts has the lockout and dead
  netbeasts. Default: none.
