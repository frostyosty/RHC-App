# 04. The first visit

**Needs:** 02, 03 · **Size:** L · **Track:** camera
**Read first:** [README](README.md); [02](02-ar-foundation.md) → Interfaces;
[03](03-house-map.md) → The rules, and Interfaces. The art this step needs is listed
in [art](art.md).

## Goal

The user's onboarding, playable at home. There's a parcel at the door; you open it
on the kitchen bench and hang the painting inside; there's a knock; and a friend
tours the path you walked. By the end, the game knows the front door, the kitchen,
one more room and the ways between them. The player just thinks they've had a nice
time with their new neighbour.

This step also builds the machinery that every later step uses:
- the beat contract between the core and the app;
- the director;
- the recording of spots and walks.

## The beat contract

The core never touches the camera. Its **director** hands the app one **beat** at a
time. The app acts it out and hands back a **result**. While a beat runs, the app
also streams observations to the core: the camera pose, the tracking state, surfaces
found and taps. The core turns those into walks and spots (03).

```kotlin
// homevisits-core/story/Beat.kt
sealed interface Beat {
    data class Say(val who: CharId?, val text: String, val answers: List<String>) : Beat
    data class Sound(val name: String) : Beat
    data class GoTo(val spot: SpotId, val route: Route?, val line: String) : Beat        // a known place
    data class GoNew(val want: SpotKind, val room: RoomKind?, val line: String) : Beat    // somewhere not known yet
    data class FindSurface(val kinds: Set<SurfaceKind>, val heights: ClosedFloatingPointRange<Float>?,
                           val line: String, val hints: List<Hint>) : Beat                // tap a surface
    data class Show(val item: ItemId, val at: Where) : Beat                               // an item appears
    data class Hold(val item: ItemId?) : Beat                                             // what the player carries
    data class Unwrap(val item: ItemId) : Beat
    data class Hang(val item: ItemId) : Beat
    data class Arrive(val who: CharId, val at: SpotId) : Beat
    data class Tour(val who: CharId, val route: Route, val stops: List<SpotId>) : Beat
    data class Talk(val lines: List<Line>) : Beat          // step 07 replaces this with a Conversation
    data class Leave(val who: CharId) : Beat
    data class AlignByTap(val spot: SpotId, val item: ItemId?) : Beat
}
sealed interface BeatResult {
    data class Done(val answer: Int? = null, val tap: SurfaceTap? = null, val spot: SpotId? = null) : BeatResult
    object Declined : BeatResult      // the player took the way out ("Not now", "Somewhere else")
    data class Failed(val why: String) : BeatResult
    object Interrupted : BeatResult   // the app was paused mid-beat
}
```

- A `Hint` is a line, plus how many seconds to wait before showing it. These are the
  escalating help in each beat below.
- The app runs beats in `ar/BeatRunner.kt`. Step 09 adds `twod/TwoDRunner.kt`, and
  the tests use `FakeRunner` (03's fake houses).

## The director

`story/Director.kt` decides the next beat from the save: the house map, the story
flags, who's visiting and what's being delivered. In this step it only knows the
first visit (`story/FirstVisit.kt`), written as a script with branches. The script's
text lives in one Kotlin object (`FirstVisitLines`) until step 07 moves it into
content files.

## The script

The first visit takes about 6–8 minutes. Each beat below lists what the player sees
and does, what's recorded, and the ways out. The timings and heights are starting
values.

### 1. The doorbell (on the home screen)

- A doorbell sound, then: "Ding-dong! Sounds like a parcel. Shall we go and see?"
  [Go and see] [Not now]
- If the camera isn't allowed yet, the game asks for it as part of the story (the
  user's idea): "We'll look through your camera. Nothing it sees is saved as a photo
  or leaves your phone." [OK] Then Android's prompt, past the Guardian the way 02
  does it, and ARCore is installed if needed.
- If the player refuses, or the phone can't run ARCore, the first visit happens in
  2D (step 09). Until 09 exists: "No worries, we'll do this another time."

### 2. To the front door

- AR opens: "Head to your front door. 📦". The first time only, also: "Hold your
  phone up like you're filming. Watch your step."
- ARCore starts tracking as the player walks. It needs movement to start, and the
  walk gives it that.
- The player taps [🚪 I'm at the door].
- **Recorded:**
  - a START spot, where tracking began;
  - the FRONT_DOOR spot, here, with its yaw facing out through the door (the player
    is facing the door);
  - the walk from START, if it was unbroken (03, rule 1).

### 3. The parcel on the doorstep

- "Open the door and have a look." The app looks for the floor 0.5–1.5 m ahead. When
  it finds it, the parcel appears there, bouncing slightly: "There it is!"
- The player taps the parcel. It lifts into their hands: a carried parcel at the
  bottom of the screen that bobs as they walk.
- **Recorded:** the door's floor height and surfaces, and what the spot looks like
  facing out and (as the player turns) facing in. That look is step 05's signature,
  once 05 exists.
- **Ways out:**
  - No floor found after 8 s: "Look down at the doorstep."
  - Still none after 15 s: "Oh, they left it just inside!" The parcel appears on the
    floor inside the door.
  - [I'd rather not open the door] goes straight to "just inside".
  - After dark (by the clock, or when the light estimate is low), the game doesn't
    suggest opening the door at all.

### 4. To the bench

- "Heavy! Let's open it on the kitchen bench."
- When the player stops walking, surfaces at bench height (0.8–1.0 m above the
  floor) glow: "Tap the bench to put it down."
- **Recorded:** the BENCH spot, in a KITCHEN room; the bench top; the walk from the
  door.
- **Ways out:**
  - After standing for 15 s: "Step back a little so I can see the top."
  - After 30 s: "Any table's fine." Heights from 0.55 to 1.1 m now count.
  - After 45 s: "Just tap where you'd like to put it." (Instant Placement)
  - If it wasn't a bench, the game doesn't assume the room. A character asks about
    it later ("Is this the kitchen?").

### 5. Unwrapping

- The player taps the parcel three times, or swipes, to tear the paper off. Inside
  is a painting, "Harbour at dusk", shown big.
- There's a card in the box: "Welcome to the street! Something for your walls. I'll
  pop round and see where you put it. — Rachel, next door". This makes Rachel the
  first visitor, and ties her to paintings from the start.

### 6. Hanging it

- "It'd look lovely in your bedroom. Hang it there?" [Bedroom] [Somewhere else].
  Somewhere else shows the room list: lounge, hallway, kitchen, other.
- The player carries the painting there. At a wall: "Tap the wall where you want
  it." The painting appears where they tapped, at hanging height (its centre about
  1.5 m up), and they can drag it up or down. [Looks good]
- **Recorded:** the WALL spot, in the room they chose; the wall surface; the
  painting's placement; the walk from the bench.
- **Ways out for plain walls** (from 02's list):
  1. depth;
  2. a vertical plane;
  3. a plane fitted to feature points near the tap;
  4. "Stand about a step back from the wall, facing it", then Instant Placement at
     arm's length.

### 7. A moment

- A sparkle on the painting, and "Perfect." Then about five seconds with nothing
  asked of the player.

### 8. The knock

- Three knocks: "Someone's at the door!"
- An arrow at the edge of the screen points along the **known** way back: towards
  the next point on the stored path (bedroom → bench → door, the reverse of the
  walks). It never points straight at the door through a wall (03, Routes).
- If the player takes another way, that's fine. It's recorded, as a new link.
- At the door: [Open the door].
- **Recorded:** the walk back to the door.

### 9. Rachel

- Rachel is on the doorstep, 1 m out from the door, facing in and waving.
- The talk is scripted for now (07 later turns it into a storylet):
  1. "Hi! I'm Rachel, from next door. Did my parcel turn up?" [It did, thank you!]
     [Oh, that was you?]
  2. "And you are…?" The player can type a first name, or skip ("Just call me
     neighbour").
  3. "Can I see where you put it?" [Come in!]

### 10. The tour

- Rachel walks the known route from the door to the painting: door → bench → wall,
  forwards along the recorded paths. She keeps 1.5–2.5 m ahead of the player and
  waits at each stop. The player follows with the camera up.
- At the bench, she looks round the kitchen: "Ooh, I like your kitchen."
- At the painting, she stops in front of it, with hearts over her head: "You hung it
  in your bedroom! I'm honoured."
- The player gets one choice: [I love it] [It's perfect there] [Honestly? I wasn't
  sure where to put it]. The answer is saved as a flag, and 06 and 07 use it as the
  starting point for Rachel's feelings.
- On a rough leg (tracking was lost on that walk), Rachel says "I'll meet you
  there!" and is waiting at the next stop.
- If the player goes somewhere else, Rachel follows their trail, 1.5 m behind.

### 11. Goodbye

- "I'd better get back. I'll come round again tomorrow!" This is the first promise,
  saved for step 08.
- She waves and walks off towards the door, fading out after a few steps.
- AR closes. The home screen shows a card: "Rachel's coming back tomorrow. She loves
  paintings. 🎨" and "Earn Momentum by resisting distractions, then treat your
  home." Onboarding is done.

## What the game knows afterwards

Usually:
- the spots FRONT_DOOR (in ENTRY), BENCH (in KITCHEN), WALL (in the room the player
  chose) and a START spot;
- links door–bench, bench–wall and wall–door (or whichever way they walked back),
  all with paths;
- the painting on the wall.

Later steps add rooms through deliveries (11) and visits (10).

## Interruptions

**The app is paused** (a call, or the screen goes off). When it resumes, ARCore may
recover tracking in the same frame. If it does, carry on. If it doesn't, the frame is
new, and earlier spots can't be found yet (step 05 does that). Until then:
- The walk that was interrupted doesn't count (03, rule 1).
- Resume at the current beat.
- If the next beat needs an earlier spot (the knock needs the door; the tour needs
  the painting), use `AlignByTap`: "Help me find the painting: tap where it's
  hanging." One tap on the wall gives the painting's position, and the wall's
  direction gives the rest (05 explains why that's enough). "Tap your front door"
  works the same way.
- Tours skip any leg they can't place. Rachel meets you at the next stop instead.

**The app is closed mid-visit.** Next time, the doorbell rings again, and the visit
carries on from the first unfinished beat, with the same rules.

**Visiting hours** are ignored during the first visit (see the Ask). The first visit
can't start during Nightfall.

## Safety

README rule 6 applies to every beat:
- one line of text while walking;
- "Careful on the stairs" on stairs (02 detects them);
- nothing to read during the tour except at stops;
- opening the door is optional.

## Interfaces (for later steps)

- **The beat contract** above (`story/Beat.kt`): beats, results and hints. Add to it;
  don't make a second one. Steps 05, 10 and 11 add beats (`AlignAt`, `Deliver`,
  `Place` and others).
- **Observations.** While a beat runs, the app sends `Observation`s to
  `story/SessionLog`: the pose and tracking state about five times a second, plus
  surfaces found and taps. `SessionLog` turns them into `Walk`s and new spots
  through `HouseMap` (03). Runners never write to the map themselves.
- **The director.** `Director.next(save, session): Beat` and
  `Director.done(beat, result)`. Later steps add their own scripts next to
  `FirstVisit`: visits in 10, deliveries in 11.
- **What the first visit leaves in the save:**
  - `firstVisit.done`;
  - the player's name, if they gave one;
  - their answer at the painting (`firstVisit.paintingAnswer`);
  - Rachel's promise to come tomorrow, which 08 reads.
- **The tour code** is the start of step 10's `Actor`: a visitor who walks a route
  ahead of the player, waits at stops, and follows the player if they stray.

## Done when

- **Fake-house tests.** Run the first visit on 500 generated plans with the fake
  player. Sometimes the fake player refuses, has no bench, lives in a studio, gets
  interrupted, or takes another way. In every run:
  - the visit finishes or ends cleanly;
  - the knock's arrow and the tour use only walked links;
  - broken walks record nothing.
- **The tool.** `tools/homevisits_sim` draws `first_visit_<n>.png`: the plan, what
  was walked, the spots made, and the tour's route.
- **At home.** The user plays it through and reports:
  - how long it took;
  - whether it felt like errands or like mapping;
  - where it stalled (which beat, which hint);
  - whether the painting stayed on the wall;
  - whether Rachel walked through a wall or furniture.

## Ask

- **"Walking the path in reverse order."** Agreed with the user (2026-09-27): the
  friend follows the accumulated footprint map, not just the most recent walk. Every
  walk the player makes is recorded. When Rachel knocks, the player walks back to
  the door however they like — that walk is recorded too. Rachel's tour then uses
  whatever known path takes her from the door through the house. She can ask "Mind
  if I use the bathroom?" and walk to the bathroom along a route the player walked
  earlier, even if it wasn't the route to the painting. The map grows with every
  trip, and Rachel can use any of it.

  The script above (beat 8 and 10) already records the walk back to the door. Beat
  10 follows the path forwards to the painting. Rachel can deviate to any known spot
  on the way ("Mind if I use the bathroom?" works because there's a known link to the
  bathroom from the walk the player made earlier). If the player takes a new way to
  the door in beat 8, that's a new link Rachel can also use.
- **Visiting hours.** Default: the first visit doesn't use any.
- **The first parcel is from Rachel.** Default: yes, as above.
- **The player's name.** Default: Rachel asks for it. First name only, optional,
  and kept on the phone.
