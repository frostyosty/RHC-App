# 03. The house map

**Needs:** 01 · **Size:** M · **Track:** camera (pure code; no phone needed)
**Read first:** [README](README.md), especially rules 2–4. Nothing else.

## Goal

`homevisits-core/house/` holds the home as the game knows it: spots, rooms, walks,
links and placements. It also holds the rules that stop the game assuming a way it
hasn't seen (the user's "don't assume they can get from room A to C"). It saves and
loads. A fake-house harness proves the rules on hundreds of generated floor plans,
and `tools/homevisits_sim` draws them.

## The model

```kotlin
// homevisits-core/house/
enum class SpotKind { START, FRONT_DOOR, BENCH, TABLE, SEAT, BED, WALL, FLOOR }
enum class RoomKind { ENTRY, KITCHEN, DINING, LOUNGE, BEDROOM, HALL, STUDY, OUTSIDE, OTHER } // never a bathroom
enum class SurfaceKind { FLOOR, BENCH, TABLE, SEAT, WALL }

data class Pose4(val x: Float, val y: Float, val z: Float, val yaw: Float) // y is up; yaw turns about y
data class Surface(val kind: SurfaceKind, val centre: Pose4, val halfWidth: Float, val halfDepth: Float)

data class Spot(
    val id: SpotId,
    val kind: SpotKind,
    val room: RoomId?,           // null until the story names it
    val storey: Int,             // 0 is the front door's storey
    val floorY: Float,           // the floor, in this spot's frame
    val surfaces: List<Surface>, // in this spot's frame
    val made: HomeClock,
    val lastConfirmed: HomeClock,
)
data class Room(val id: RoomId, val kind: RoomKind, val name: String) // as the story put it: "your bedroom"

data class Walk(
    val from: SpotId, val to: SpotId, val at: HomeClock, val seconds: Int,
    val path: List<Vec3>,        // in from's frame, a point about every 25 cm
    val gaps: List<IntRange>,    // stretches of the path where tracking was lost
    val climb: Float,            // net change in height
)
data class Link(
    val a: SpotId, val b: SpotId,
    val walks: Int, val lastWalked: HomeClock,
    val path: List<Vec3>,        // the best walk's path, in a's frame
    val bInA: Pose4?,            // where b is, seen from a (averaged over walks); null if unknown
    val length: Float,
    val rough: Boolean,          // no gap-free walk yet
    val blockedUntil: HomeClock?,
)
data class Placement(val item: ItemId, val spot: SpotId, val pose: Pose4, val on: SurfaceKind)
```

**Each spot has its own frame.** Its origin is where the phone was when the spot was
made. Its y axis points up, and its yaw is the way the camera faced.

- Surfaces and placements are stored in their spot's frame, never in a whole-house
  frame.
- A walk's path is stored in the frame of the spot it started from.
- A link keeps where its far end is, seen from its near end.

So drift in one part of the house never moves things in another part, and step 05
can align one spot at a time.

## The rules

This is the part to get right.

1. **A walk counts only if it's unbroken.** It starts when the player is 1.5 m away
   from spot A. It ends when they're confirmed at B, in one of three ways: B is
   verified (05), the player taps a surface the story asked for, or they answer
   "yes, I'm here". It becomes part of the map only if all of these hold:
   - the app stayed in front the whole time (no pause, screen-off or call);
   - it took at most 3 minutes;
   - tracking was good at both ends.

   Losing tracking in the middle is allowed: the link is real but **rough**. A
   broken walk is thrown away, and the game says nothing about it.
2. **Links only come from walks.** There is no other way to create one.
3. **Backwards is the same link.** Links have no direction: you can always walk back
   the way you came. A door that only opens one way is handled by rule 6, once the
   player says so.
4. **Distance means nothing.** Chaining links from A to B to C can put C a metre
   from A. That says nothing about a way from A to C, because there may be a wall.
   Chained positions are used for only three things:
   - drawing the home (in the tools);
   - predicting where a spot should appear before it's aligned (05);
   - "it's roughly that way".

   They are never used for routes.
5. **Rough links are real, but visitors can't walk them.** The player got through,
   but the path has a gap, and a straight line across the gap might go through a
   wall. Visitors don't walk rough legs; they "meet you there" instead (10). A later
   gap-free walk replaces the path and clears `rough`.
6. **Blocked.** When the player says a way is blocked ("the door's shut", "someone's
   asleep"), it stays blocked until the end of the day. A way blocked three times
   in two weeks stays blocked until the player walks it again.
7. **Inside a room, you can get anywhere.** Spots in the same room can be reached
   from each other without a walk, but there's no path between them (a straight
   line across a kitchen can go through the island). Step 10 decides how visitors
   cross a room.
8. **Storeys.** A walk that rises or falls more than 2 m changes storey. Routes can
   avoid storey changes. That's a player setting, and it also applies to
   characters with bad knees.
9. **The story names rooms; the game never guesses.** A spot made when the story
   said "the kitchen bench" is in the kitchen. If the player used a table instead,
   a character asks later ("Is this the kitchen?").
10. **Never a bathroom.** There's no room kind for it, and nothing ever sends the
    player there.

## Routes

- `route(from, to, avoidStairs = false): Route?` runs Dijkstra over the links,
  skipping blocked ones. A leg costs its length, × 1.3 if it's rough, and × 1.2 if
  it's been walked only once, so well-known ways win.
- A `null` route means there's **no known way**. The caller must not pretend
  otherwise. It uses an open beat instead ("Show Rachel the way to the lounge"), and
  the walk that follows becomes a link.
- `nextWaypoint(route, playerPosition)` says where the on-screen arrow points: the
  next point along the stored path, never straight at the destination.

## What the game never assumes

1. That there's a way between two known places (rules 2–4).
2. Where the player is when the app opens. They may be in any room, or out. The
   first beat of a session always has a known destination, and it checks the player
   got there (05).
3. That the player did what was asked. They may go somewhere else, stop, or put the
   phone down. Record what happened, not what was asked.
4. That places stay the same. Furniture moves, lights change, doors get shut, and
   people move house (05).
5. That the home has a kitchen bench, a separate bedroom, a doorstep, only one
   storey, or a door that opens outwards. Every beat has a way out (README rule 5).
6. That the camera can track everywhere. Dark rooms, blank hallways, mirrors and
   glass confuse it.
7. That the player lives alone. Nothing is saved as an image (README rule 1), and
   any beat can be put off.
8. That the bedroom is fine to go into. The story asks first and offers another
   room.
9. That time runs smoothly. The player may be away for weeks (06, 08).

## Saving

- The map is saved in `filesDir/homevisits/home.json`, with `version: 1`.
- `save/Json.kt` is a small hand-written JSON reader and writer (objects, arrays,
  strings, numbers, booleans and null). It uses no library, so the core still
  compiles with the bare Kotlin compiler.
- `HomeStore` writes `home.json.tmp` and then renames it, so a crash can't leave
  half a file. It saves after each finished beat.
- A file from a newer version is left alone rather than overwritten, and the game
  says it needs an update.
- **Forget my home** (in Homevisits' settings) deletes `home.json` and `spots/`. The
  next visit is moving day (05).

## Fake houses

These go in `homevisits-core/src/commonTest/.../house/`.

**`FakeHouse`** reads floor plans drawn in ASCII:

```
#############
#K....#B....#
#..b..#...w.#
#.....#.....#
###.#####.###
#H..........#
#.......D...#
########=####
```

- `#` is a wall, `.` is floor, and `=` is the front door.
- A capital letter starts a room: K is the kitchen, B the bedroom, H the hall.
- A lower-case letter is where one of the story's spots goes: b is the bench and w
  is the bedroom wall. D is the doormat inside the front door.

Here the kitchen and the bedroom are back to back, with no door between them. That's
the case the user worried about: the only way between them is through the hall.

**`FakePlayer`** walks the plan with A*, so real walks go through doorways. It adds
the problems a real player has:
- drift of 1–2% of the distance walked;
- tracking drop-outs, more often in rooms the plan marks as dark;
- taking a different way from the one suggested, 20% of the time;
- stopping, refusing, and being interrupted.

**`PlanGen`** makes random plans with 4–9 rooms. They include hallways, open-plan
kitchens and dining rooms, dead-end rooms, and back-to-back rooms with no door.
Sometimes there's a second storey up a flight of stairs, and sometimes the whole
home is a single-room studio.

**`FakeRunner`** acts out beats (04) against a fake player. This step only needs
walks and making spots.

The tests:

- **No invented links.** Across 500 random plans with scripted sessions, every link
  joins two spots that have a real way between them in the plan. No link ever joins
  two spots that don't.
- **Routes can be walked.** Every route follows real doorways in the plan, including
  when some links are blocked.
- **Back to back.** The kitchen and the bedroom, a metre apart through a wall, are
  only ever routed through the hall.
- **Backwards is free.** Walking a link backwards needs no new walk.
- **Broken walks leave nothing.** Interrupted and over-long walks make no link.
- **Rough legs.** No route handed to a visitor contains one.
- **Saving.** Save then load gives back the same map, for random maps. A file from a
  newer version is refused.

## The tool

Create `tools/homevisits_sim/run.sh`, modelled on `tools/world_preview/run.sh`. It:

1. compiles `homevisits-core`'s `commonMain`, plus the fakes, with the Kotlin
   compiler in the Gradle distribution;
2. runs `HomevisitsSim.kt`;
3. writes to `tools/homevisits_sim/out/`.

This step's output is `house_<n>.png` for a handful of generated plans. Each picture
shows:

- the plan;
- the spots;
- the links in green (rough ones dashed);
- in red, spots that are close together but not linked, which the game must never
  route between.

Steps 04 and 06–08 add their own outputs.

## Interfaces (for later steps)

- The types above, plus `Vec3`, `SpotId`, `RoomId` and `ItemId`.
- `HouseMap`:
  - what's in it: `spots`, `rooms`, `links`, `placements`;
  - changing it: `addSpot`, `nameRoom`, `recordWalk`, `block`, `place`, `move`,
    `remove`;
  - asking it: `route`, `nextWaypoint`, `spotsIn(room)`, `freeWallSpace(room)`.

  Everything that changes the map goes through `HouseMap`, which enforces the rules
  above.
- `save/Json.kt` and `HomeStore` (load, save and forget).
- `FakeHouse`, `FakePlayer`, `PlanGen` and `FakeRunner`, for tests.

## Done when

- `./gradlew :homevisits-core:jvmTest` passes with the tests above.
- `bash tools/homevisits_sim/run.sh` writes the house pictures, and the user has
  looked at a few.
