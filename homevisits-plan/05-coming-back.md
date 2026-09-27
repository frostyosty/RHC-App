# 05. Coming back

**Needs:** 04 · **Size:** L · **Track:** camera
**Read first:** [README](README.md); [02](02-ar-foundation.md) → Interfaces;
[03](03-house-map.md) → Interfaces; [04](04-first-visit.md) → Interfaces, and
Interruptions. Nothing else.

## Goal

Tomorrow, when Rachel knocks, the painting is still on the bedroom wall, exactly
where it was. Each time AR opens, the game works out where the known spots are in
this session. It starts with the spot the story sent the player to, and if it can't
find it, it asks. It also copes with rooms changing, and with moving house.

## Why this is needed

ARCore starts every session with a new origin. Its way of saving places between
sessions is Cloud Anchors, which upload what the camera sees to Google, and README
rule 1 rules them out. So the game keeps its own small description of each spot, on
the phone, and matches against that.

## What's saved for each spot (no pixels)

A spot's signature is saved in `spots/<id>.sig` (binary, versioned). It has two
parts:

- **Keyframe summaries.** Up to 16 per spot, taken from different directions and at
  different times of day. Each one is a whole-frame gradient histogram (HOG-style)
  on a coarse grid of the camera's grey image at 160 × 120: about 200 numbers,
  normalised. A summary says "this looks like the view from the door". It can't be
  turned back into a picture.
- **Landmarks.** Up to 800 points from ARCore's point cloud, stored in the spot's
  frame. Each point has a 256-bit binary descriptor (BRIEF-style), worked out from
  the camera's grey image around where the point appears.
  - The sampling pattern is turned by the camera's roll, so a tilted phone still
    matches. Descriptors are computed at two scales.
  - Keep points that were seen in several frames. Drop points that haven't been seen
    for 60 days.

Summaries and landmarks are computed in `homevisits-core/vision/` from plain arrays
(the grey image as a `ByteArray`, the points as a `FloatArray`), so they're tested
on the JVM. The app only copies ARCore's CPU image (`Frame.acquireCameraImage`, the
Y plane) and the point cloud across, on a background thread, a few times a second.

## Close enough? (a quick check)

Comparing the current frame's summary with a spot's keyframes (cosine similarity)
tells you whether the player is probably near that spot. It's cheap, so it runs
twice a second while the player walks. It checks the spot the story sent them to,
and the spots along the route. It never decides anything on its own: it only says
when to try aligning.

## Aligning (the proof)

1. Match the current landmarks' descriptors to the spot's, by Hamming distance with
   a ratio test.
2. ARCore knows which way is down, so only four numbers are unknown: where the spot
   is (x, y, z) and which way it faces (yaw). Two matched points are enough to guess
   all four. Use RANSAC:
   1. guess from a random pair of matches;
   2. count how many other matches agree with the guess to within 5 cm;
   3. repeat, and keep the best guess;
   4. refine it with least squares on the matches that agree.
3. Accept the result when all of these hold:
   - at least 20 points agree;
   - they're at least a quarter of all the matches;
   - the RMS error is under 6 cm.

   Keep refining while the player stands there.

Repetitive places, such as a row of identical cupboards, can give two answers that
fit about equally well. If the second-best answer is close to the best, don't
accept either; ask instead.

These thresholds are starting values; tune them at home.

## Aligning by tap

When automatic alignment fails at a spot with something on a wall, the player can
align it with one tap: "Help me find the painting: tap where it's hanging." The tap
hits the wall, which gives a point (the painting's centre) and the direction the
wall faces (the yaw). With gravity from ARCore, that's all four numbers.

The front door works the same way ("Tap your front door"). Spots with only a bench
or a table can't be aligned with one tap. They align from a linked wall or door spot
instead.

## Starting a session

1. **The first beat always has a known destination** (README rule 2). A knock or a
   parcel sends the player to the front door. "Walk around" sends them to the room
   they name ("Where are you?", with their rooms as buttons). The game never tries
   to work out where the player is from nothing.
2. While the player walks there, ARCore starts tracking, and the quick check watches
   for the destination and for the spots on the way.
3. At the destination, align. Visitors are patient in the meantime: Rachel knocks
   again.
4. **Aligned:** the session is anchored.
   - Every other spot gets a predicted position through the links (03). Predicted
     positions are used for routes, arrows and visitors' walks.
   - Items at a spot are shown only once that spot itself has aligned. Each spot
     aligns quietly as the player reaches it.
5. **Not aligned after about 20 s:** ask. "Is this the front door?" [Yes] [No]
   - **Yes:** align by tap, and add this view to the spot's signature. The place has
     changed: a coat on the hook, or different light.
   - **No:** "Where are we?", with the rooms as buttons, and [I'm not home], which
     goes to 09's not-home mode.

## Keeping spots up to date

- Every time the player is confirmed at a spot, refresh its landmarks. Add a
  keyframe too, if the view is different enough: another direction or other light.
  Evening and daylight keyframes are both kept.
- Spots made in step 04 before this step shipped have no signature. Capture it the
  first time the player is confirmed there (by asking).

## Rearranged, or moved house

- **Rearranged.** A spot fails twice in a row, but the player says they're there:
  "Hmm, it looks different. Did you move things around?" [Yes] leads to "Where does
  the painting go now?" (a tap), and the spot's signature is replaced.
- **Moved house.** Nothing aligns for two sessions, and the player says they're home:
  "Things look different. Did you move house?"
  - [New house!] starts **moving day**:
    1. The items go into boxes (parcels) at the new front door.
    2. The old map is cleared.
    3. A short first visit runs with the player's own boxes. Unpacking each one maps
       the new home.

    Characters notice ("You moved! Where's my painting going?"). Relationships are
    unaffected.
  - [No] treats it as rearranged.

## Pitfalls

- Mirrors and glass doors create fake landmarks. RANSAC's geometry check rejects
  most of them. Don't lower the thresholds to force a match.
- People and pets move through the frame. Points on them won't match anything saved,
  and they're rejected as outliers.
- At night, dim rooms have few landmarks. Ask for a light (02's hints) before giving
  up.
- Never show an item at a predicted position. A painting that jumps 30 cm when its
  spot aligns breaks the spell more than one that fades in.

## Privacy

Summaries and descriptors aren't images, but they do describe the inside of someone's
home. They stay in the app's private storage (backups are already off:
`android:allowBackup="false"`), and **Forget my home** deletes them along with the
map.

## Interfaces (for later steps)

- `SpotSignature`, `SignatureStore` (read, write and forget), `vision/Summary`,
  `vision/Landmarks` and `vision/Align4Dof`.
- `SessionAnchors`: which spots are aligned in this session, and where. For the
  rest, `predicted(spot)`.
- `HomeStatus`: `ANCHORED`, `SEARCHING` or `NOT_HOME`.
- New beats added to 04's contract: `AlignAt(spot)`, `AlignByTap(spot, item)` and
  `AskWhere`.

## Done when

- **Unit tests on synthetic data:**
  - Take a random 3D point cloud and move it by a known yaw and offset, with 3 cm of
    noise and 60% wrong matches. The move is recovered within 3 cm and 1.5° in 99%
    of trials.
  - A grid of identical points (the cupboard case) is rejected, not guessed.
  - Summaries of the same synthetic scene stay close when the light is brighter or
    the view shifts a little. Summaries of different scenes don't.
- **At home.** After the first visit, close the app. Come back that evening, and
  again the next morning. The painting should be within about 5 cm of where it was.
  Report:
  - how long aligning took;
  - how often it needed a tap;
  - anything that appeared in the wrong place.
