# 10. Visitors in AR

**Needs:** 05, 08 · **Size:** L · **Track:** camera, then both
**Read first:** [README](README.md); the Interfaces sections of
[02](02-ar-foundation.md), [03](03-house-map.md), [05](05-coming-back.md),
[07](07-conversations.md) and [08](08-visits-and-calendar.md);
[04](04-first-visit.md) → Interfaces, and beat 10 of its script (the tour). The
sizes in [art](art.md).

## Goal

Any visit from 08 plays out in the player's real home. Visitors:
- knock and come in;
- walk the routes the player has walked;
- notice things;
- talk with you and with each other;
- leave.

This step grows step 04's scripted Rachel into something that works for anyone.

## Characters as sprites

- **Billboards.** Characters are billboards standing on the floor, turned to face the
  camera around the vertical axis only.
- **Eight directions.** They're drawn from 8 directions using 5 drawn views, like the
  Wilds' creatures: front, ¾ front, side, ¾ back and back, with the other side
  mirrored. The view is picked from the angle between the way the character faces
  and where the camera is.
- **Real sizes.** Each character's height is in their cast sheet (Alfred is 1.75 m,
  Maya 1.62 m, and so on).
- **Canvas padding.** The sprite's canvas padding must match autogen, so it's one
  constant, `PERSON_CANVAS`, used both in the renderer and in `homevisits.py`. It's
  the same rule as `CREATURE_CANVAS` in the Wilds.
- **Grounding:**
  - a soft contact shadow on the floor;
  - a tint from ARCore's light estimate;
  - a small bob when they walk.
- **Occlusion,** on phones with depth (tier `FULL`): hide the pixels that are behind
  real things (the couch, a door frame), using the depth image. Without depth there's
  no occlusion, so keep characters on open floor (next section).

## Where they can stand and walk

- **Only on floor the player has walked.** The walks' paths (03) are floor the player
  has crossed, so there's no furniture on them.
  - Characters walk along those paths.
  - They stand within about 0.4 m of a path, or on a detected floor plane with
    nothing above it.
  - Never inside a bench, and never behind a wall.
- **Along known routes only** (README rule 3). Rough legs (03) aren't walked. The
  character says "I'll meet you there" and is waiting at the next spot when the
  player arrives.
- **Crossing a room.** Inside a room, a character walks straight only if the whole
  line stays over walked or detected floor. Otherwise they follow a walked path, or
  they're simply at the other side when the player looks back.
- **Ahead of the player, and out of their way.** On a tour, a character keeps
  1.2–2.5 m ahead along the route. They stop at each stop, and turn to look back if
  the player lags. They never stand on the player's path ahead: they keep 0.6 m from
  it.
- **Following.** If the player goes somewhere else, the visitor follows the player's
  own trail, 1.5 m behind ("Where are we going?"). That walk is recorded like any
  other. If it's a new way, it becomes a link.
- **Where to stand in a room:** spread around the spot the conversation is about,
  facing each other and the player.

## Arriving and leaving

- **Arriving.** The knock (a sound and a line), then guidance to the door along the
  known route (05 aligns the door). The visitor is on the doorstep, outside the door,
  facing in. [Open the door] lets them in.
- **Leaving.** If the door is close and the route to it is clean, they walk to the
  door along the known route. Otherwise, they wave, walk off round the nearest
  corner, and fade out.

## Noticing things

A character notices an item when it's:
- within about 4 m;
- within 60° of the way they're facing;
- in the same room.

Then:
- 06 appraises it (`saw item`);
- their head turns to it and an emote shows;
- 07 has a storylet ready.

Characters look around when they enter a room, so things get noticed naturally. They
head for things they've heard about ("Where's this painting, then?").

## Talking in AR

- **Only while standing still.** A conversation starts once the player has stopped
  (the camera moving under about 0.2 m/s for a second). If the player walks off
  mid-conversation, the visitor follows and waits ("Hang on, where are you off to?").
- **The dialogue panel.** The visitor turns to face the camera. The panel at the
  bottom of the screen shows their portrait (with the face from 06), their name,
  their line, and the choice buttons. A small emote floats over their head in AR.
- **Characters talking to each other** face each other, and their lines go in the
  panel in turn.
- **"Hold up your camera."** If the phone is pointed at the floor (tilted more than
  about 60° down) during a tour, show a gentle reminder. If a visitor is off screen,
  show an arrow at the edge of the screen with their portrait.

## Sound

- Knocks and the doorbell.
- Soft footsteps.
- Each character's voice: a short burble of pitched blips while their line types
  out. No voice acting.

The sounds come from the Studio's synthesiser ([art](art.md)).

## Endings

- **Visiting hours run out, or Nightfall starts:** the visitor wraps up within about
  20 s ("I'd better let you go").
- **A phone call, or the app closes:** the visit pauses.
  - If the player comes back within 10 minutes, it picks up where it left off
    (re-aligning through 05).
  - Otherwise, the visitor has gone and leaves a note (09).

## Budget

- 30 fps.
- At most 3 characters and 30 placed items drawn.
- Texture atlases per animation, loaded when a character arrives and freed when they
  leave.
- No allocations in the frame loop.

## Interfaces (for later steps)

- `Actor` is a character in the scene: `walk(route)`, `follow(trail)`,
  `standAt(spot, slot)`, `face(target)`, `emote(e)` and `expression(x)`.
- `Stage` arranges the actors in a room, decides who has noticed what, and reports
  `saw item` and `entered room` events to 06.
- The beats `Arrive`, `Tour`, `Talk` and `Leave` get their general versions here.
  `Talk` now runs a 07 `Conversation`.

## Done when

- **Fake-house tests:** tours on random plans never give a visitor a rough leg, or a
  leg that wasn't walked. Following the player records the walk.
- **At home,** the user plays a visit with two visitors: a tour of three rooms, a
  conversation in the kitchen, and the second knock during the first visit. They
  report:
  - whether anyone stood inside furniture or walked through a wall;
  - how the sprites looked;
  - how easy the panel was to read while standing in a hallway;
  - how much battery it used.

## Ask

- **Voices.** Default: pitched blips, different for each character. The alternative
  is silence.
