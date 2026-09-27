# 09. Messages and 2D visits

**Needs:** 07, 08 · **Size:** M · **Track:** people
**Read first:** [README](README.md); [03](03-house-map.md) → Interfaces;
[04](04-first-visit.md) → Interfaces (the beat contract); [07](07-conversations.md)
→ Interfaces; [08](08-visits-and-calendar.md) → Interfaces. The 2D rooms in
[art](art.md).

## Goal

Homevisits works without AR, for:
- phones that can't run ARCore (step 02's tier `NONE`);
- players who won't allow the camera;
- players who can't walk around, or would rather not (a setting: "Visit without
  walking");
- players who aren't home (they said so in step 05).

Between visits, characters send the odd text. This step is also the fastest way to
play-test the people track on the emulator, before AR visits exist.

## 2D visits

- **A drawn home.** There's one illustrated room per room kind: an entry with the
  front door, a kitchen, a lounge and a bedroom. They're cosy and generic, from
  [art](art.md).
- **The player's own things** appear in slots in these rooms: paintings on wall
  slots, plants on the bench, and so on. If the player also has an AR home, each item
  shows in the drawn room of the same kind.
- **`VirtualHouse`** builds a fixed `HouseMap` (03) for the drawn home: the door, a
  bench, a couch and a bedroom wall, all linked. Everything in the core runs
  unchanged: the director, the mind, conversations and visits.
- **`twod/TwoDRunner`** runs the same beats as the AR runner (04's contract):

  | Beat | In 2D |
  |---|---|
  | `GoTo` | tap a room tab (with a short walking transition) |
  | `FindSurface` | tap a slot |
  | `Show`, `Unwrap`, `Hang` | happen in the room |
  | `Arrive` | the visitor at the door |
  | `Tour` | moves room to room |

- **Characters** stand in the room (front and ¾ views), with the same dialogue panel
  as in AR (10).
- **The first visit in 2D** is the same story without the walking: the parcel at the
  drawn door, the bench, hanging the painting, the knock, and Rachel's tour.

## Messages

- A texting screen inside Homevisits, with one thread per character, that looks like
  a phone's messages app.
- Characters text when something happens:
  - plans ("See you tomorrow! 🎨");
  - notes from visits the player missed (08);
  - news from off-screen days;
  - reactions ("Maya says you got a lamp?!").

  At most three texts a day across everyone, so it never becomes a feed.
- The player answers with choices. These are storylets with `channel text` (07), with
  the same engine underneath. Invitations and rescheduling (08) happen here too.
- No notifications (08's Ask). A badge on the Messages tab shows when there's
  something new.

## Not home

When the player says they're out (05), there are no visits, because a visit is to
your home: "Oh, you're out? Text me when you're back." Messages still work.

## Settings

Homevisits has its own settings:
- "Visits: with the camera / without the camera";
- "Avoid stairs" (03);
- "Forget my home" (03, 05).

## Interfaces (for later steps)

- `VirtualHouse`, `TwoDRunner`, and the slot layout of the drawn rooms (11 places
  items in the slots).
- `Messages`: threads, and `send(storylet)` for the planner and off-screen days to
  use. Replies are choices that go through `Conversation`.

## Done when

- On the emulator, a phone with no ARCore goes straight to 2D.
- The whole loop works in 2D:
  - the first visit;
  - a promised return visit;
  - a second arrival;
  - a note for a missed day;
  - messages with replies.
- Items bought in 11 appear in the drawn rooms.
