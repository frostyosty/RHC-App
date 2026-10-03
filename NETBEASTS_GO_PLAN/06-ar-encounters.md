# 06. AR encounters

**Needs:** 04, 05, and the Findings of Homevisits 02 · **Size:** L
**Read first:** [README](README.md), rules 2, 3, 7, 8 and 10;
[04](04-beasts-at-real-places.md) → "Meeting one" and Interfaces;
[05](05-ar-kit.md) → Interfaces; `HOMEVISITS_PLAN/02-ar-foundation.md` →
Build item 7 (drawing sprites) and Findings; `rhc-android/README.md` §5.1,
the paragraphs on fights and on the 8 directions a creature is seen from.

## Goal

When you tap a beast in reach, the camera opens and the beast is standing on
the real ground a few steps in front of you. You flick a net at it or throw a
cage, and the fight plays out there on the grass. On a phone without ARCore,
or with the camera switched off, the same fight shows in the Wilds view as it
did in step 04.

## The fight is still the sim

This is the idea that keeps the step small. A Wilds fight is a `World` that
ticks, and `TerrainRenderer` draws its snapshot. Here a second thing draws the
same snapshot: `go/ArFightView.kt`, over the camera. The sim, `WorldFight.kt`,
the panel of cages and moves, and the battle rules don't know which one is
drawing (rule 7).

- **Where things stand.** When the ground is found, fix one anchor on it about
  3 m ahead. That's the middle of the fight. Each entity's place in the sim,
  measured from the fight's middle, becomes metres from the anchor (1 tile is
  1 m to begin with; tune it outdoors). The sim's "toward the player" is the
  direction from the anchor to where the camera was when the fight began.
- **You are the camera.** In the Wilds you circle the beasts. Here the sim
  still circles you, but the drawing ignores it: you stand where you stand,
  and the two beasts circle each other. If you step round them you really do
  see them from another side.
- **Which side you see.** A creature is drawn from 8 directions using 5 views.
  `TerrainRenderer` picks the view from where the eye is and which way the
  creature faces. Lift that choice into something both call, and give it the
  real camera's position.
- **Lunges, hits and effects** come out of the snapshot (`perform`, `effect`)
  as they do for the Wilds. Health bars and the patience bar are drawn at the
  sprites' places on screen.
- **Switching views costs nothing**, because the fight isn't in the view. So:
  - tracking lost for 3 s, or you start walking: drop to the Wilds view and
    say why in one line;
  - a switch on the fight screen turns the camera off and on;
  - on leaving the fight, close the camera at once.

## Finding the ground

Outdoors this is easier than Homevisits' rooms: one flat patch is all it
needs.

1. Say "Stand somewhere safe, off the road", then "Point at the ground a few
   steps ahead."
2. Take the first upward-facing plane under the middle of the screen.
3. Nothing after 3 s: use Instant Placement, guessing the phone at 1.4 m above
   the ground. The beast may slide a little as ARCore learns better; let it.
4. Nothing after 8 s: the Wilds view.

Use the kit's hints with outdoor wording: "Too dark to see the ground" (and
Nightfall will have closed the game before real night), "Hold it steadier",
"Point at the ground, not the sky".

## Making it look like it's there

- A soft contact shadow on the ground under each beast.
- Tint by ARCore's light estimate, so a beast isn't bright in shade.
- Nearest-neighbour scaling, as in the Wilds.
- On phones with depth (the kit's `FULL` tier), hide the parts of a beast
  behind real things. Homevisits step 10 tunes this for rooms; use what it
  finds, or its hook if 10 isn't done.

## The net

With the net-first throw built (see [04](04-beasts-at-real-places.md)), a
swipe up from the net button throws it along the swipe. Draw its arc from the
bottom of the screen to the beast. Whether it lands is the sim's and the
rules' answer, not where the finger went: the swipe only makes it feel thrown.

## Battery and heat

The camera is on only during a fight, at 30 fps (the kit's session already
asks for that), and closed straight after. A fight is a minute or two. Check
it on the walk below.

## Ask

- **Phones that can't run ARCore.** Default: the Wilds view, which is already
  built. The alternative is a camera view held steady by the gyroscope, where
  the beast floats and can't be walked round.
- **Is the camera on by default?** Default: yes on phones that can, with the
  switch remembered.
- **How big is a beast?** Default: 1 tile is 1 m, so a stage 1 beast stands
  about knee high and a stage 3 about waist high. Tune it on the walk.

## At the park (the user, on their phone)

1. Grass, footpath, sand: how long until the beast appears on each?
2. Does it stay put when you step sideways? When you walk round it?
3. Bright sun, shade, dusk: can you see it, and does it look lit right?
4. Does the size feel right next to a bench or a bin?
5. Ten minutes of fights: how much battery, and how warm?

Write the answers under Findings.

## Interfaces (for later steps)

- `ArFightView`: `attach(session: WorldSession, ar: ArSession)`, `detach()`.
- The view choice (`viewFor(eye, creature)`), shared with `TerrainRenderer`.
- `GameActivity.goFightUsesCamera` (pref `GO_CAMERA`).

## Done when

- All four flavors compile; `bash tools/world_preview/run.sh` still passes and
  its PNGs haven't changed (lifting the view choice must not move a pixel).
- On an AR phone outdoors: a fight from tap to finish over the camera, and
  one that drops to the Wilds view halfway and still finishes.
- On the emulator (no AR): the fight opens in the Wilds view, as in step 04.

## Findings

(Filled in from the park.)
