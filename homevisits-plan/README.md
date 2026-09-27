# Homevisits plan

Homevisits is the game in the female Gamers APK (flavor `gamersFemaleHomevisits`,
released as `rhc_homevisits.apk`). It takes the place that Netbeasts has in the
male APK. None of it is built yet: the steps table below says what's done. This
folder is kept apart from `PLAN.md` at the repo root, which covers Netbeasts, the
web client and multiplayer.

**How to read this folder.** Everyone reads this file. Then read your step's file,
plus only what its **Read first** line lists. When a step needs something an
earlier step built, it points at that step's **Interfaces** section. Each
Interfaces section makes sense without the rest of its file.

## The idea

From the user (2026-09-27):

- You use your phone's camera to map your home, but it doesn't feel like mapping.
  The game gives you errands instead. There's a parcel at the door: you walk to the
  front door, open it, see the parcel on the doorstep and tap it. "Let's open it on
  the bench": you carry it to the kitchen and open it, and it's a painting. "Let's
  hang it in the bedroom": you walk to the bedroom and hang it. Without noticing,
  you've shown the game your rooms and the ways between them. If the camera isn't
  allowed yet, the game asks for it as part of the story.
- Then there's a knock at the door. It's a friend, and she starts exploring. You
  hold up your camera and follow her along the path you made.
- The game has to be clever about what it doesn't know. For example, knowing where
  two rooms are doesn't mean you can walk straight from one to the other.
- An **emotions engine** works out the relationships between the characters. When
  they talk about things, your answers change those relationships, which aren't
  necessarily romantic.
- Momentum earned outside the app pays for things for your home. The user's example
  of how it should feel:

  > I earned Momentum, so I bought a new painting. Perfect: Rachel said she'd come
  > back tomorrow, and she loves paintings. I hope she arrives before Alfred,
  > though, because I know they don't like each other.

Each part of that example is built in one of the steps:

| In the example | Built in |
|---|---|
| Earning Momentum outside the app | [01](01-groundwork.md) (Momentum in this APK), [12](12-guardian-wall.md) (the block screen) |
| Buying a painting | [11](11-shop-and-deliveries.md) |
| "Rachel said she'd come back tomorrow" | A promise made in conversation ([07](07-conversations.md)) and kept by the visit planner ([08](08-visits-and-calendar.md)) |
| "She loves paintings" | Her tastes ([06](06-emotions-engine.md), [cast](cast.md)), which you learn by talking to her |
| "Before Alfred" | The order people arrive in, and what you've learned of their habits ([08](08-visits-and-calendar.md)) |
| "They don't like each other" | Relationships between characters ([06](06-emotions-engine.md)), and what you've seen of them ([08](08-visits-and-calendar.md)) |

## Where things stand today

- The flavor exists in `rhc-android/app/build.gradle.kts`. It has the id suffix
  `.homevisits`, the block-screen title "AVERT YOUR EYES." and the message "Take a
  deep breath and step away.". It's option 4 in `release.sh`.
- Apart from those two strings, the APK plays Netbeasts. Its `flavor_id`
  ("female_gamers") isn't read anywhere.
- The flavor was added on 2026-04-17 (it replaced a "Behaviour" flavor), with
  `// Placeholder until Homevisits is built!` in `MainActivity`. That comment has
  since been removed. Nothing described what Homevisits would be until the user did
  on 2026-09-27.

## Rules for every step

1. **Nothing about the home leaves the phone.** No uploads, no Cloud Anchors and no
   analytics. Save geometry and descriptors, never camera pixels. The player can
   wipe the home map at any time. Debug exports contain no images, and the player
   sends them.
2. **The story steers, and the camera confirms.** Every walk has a reason in the
   story. The game says where to go, then checks that the player got there. It
   never asks the camera to recognise a room from nothing. There's no "scan your
   room" screen and no mapping progress bar.
3. **Only walked routes exist** (agreed with the user). Two places are connected
   only after the player walks from one to the other in one unbroken session.
   Walking that route backwards is fine. Two places being close together means
   nothing, because there may be a wall between them. Every "go to X" follows known
   links. When there's no known way, the prompt is open ("show Rachel the way to
   the lounge"), and the walk that follows is recorded.
4. **Ask, don't guess, and ask in character.** When the game isn't sure where the
   player is, whether they're home, or what a room is, a character asks ("Is this
   the kitchen?") and gives buttons to answer with. A question costs less than a
   wrong guess.
5. **Every beat has a way out.** No bench? A table is fine. Rather not open the
   front door at night? The parcel was left just inside. Not the bedroom? Pick
   another room. "Not now" is always there.
6. **Safe to play.** Show one short line of text while the player walks.
   Conversations only happen while they stand still. Say "Careful on the stairs"
   on stairs. Never send the player past the doorstep. Suggest switching on a light
   when it's too dark to track. Never ask the player to go into the bathroom.
7. **A reward, not a new distraction.** Momentum pays for it, visiting hours
   (Aether) cap it, and Nightfall closes it. There are no push notifications unless
   the user asks for them. Missing a visit never makes the player feel guilty,
   because being away from the phone is the point of the app.
8. **Grounded.** Real rooms, believable neighbours, NZ English ("bench", "lounge",
   "jandals"). The only unreal thing is that the visitors aren't really there. This
   is the same spirit as the Wilds rule in `CLAUDE.md`.
9. **Pure core.** `homevisits-core` is pure Kotlin: no `android.*`, no `java.*`
   and no libraries. It's deterministic (its own seeded RNG, the time passed in,
   nothing that depends on hash-map order) and tested on the JVM. The app turns
   camera frames and taps into facts for the core, and shows what the core decides.
10. **Warm and wholesome.** RHC is a Christian app. The relationships are
    friendships, families, mentors and rivals. There's no romance unless the user
    decides otherwise (see the open questions).

## How it fits together

```
rhc-android/
  homevisits-core/                   pure Kotlin module; only the Homevisits APK uses it
    house/    spots, rooms, walks, links, routes                   step 03
    story/    the director and the beat contract                   step 04
    vision/   spot signatures, matching, alignment (plain arrays)  step 05
    mind/     the emotions engine                                  step 06
    talk/     the content language, storylets, conversations       step 07
    visits/   who comes when, promises, the calendar               step 08
    shop/     catalogue, prices, lay-by, deliveries                step 11
    save/     versioned save files (hand-written JSON)             step 03; each later step adds its part
  app/src/gamersFemaleHomevisits/    source set that only this APK builds
    AndroidManifest.xml              camera, ARCore, the activities
    java/com/rockhard/blocker/homevisits/
      HomevisitsActivity.kt          the home screen (not AR)
      ar/                            ARCore session, renderer, beat runner, spot capture
      twod/                          visits without AR
      ui/                            dialogue panel, calendar, who's who, catalogue, messages
    assets/homevisits/               content: cast, conversations, catalogue (text files)
    res/drawable-nodpi/hv_*          art from sprite_studio/autogen/homevisits.py
tools/homevisits_sim/                phone-free checks: fake houses, a 90-day soak, transcripts
```

The core decides **what** happens: which errand is next, who knocks, how Rachel
feels about the painting. The app decides **how** it happens: the camera, AR,
drawing and buttons. The app then reports back what the player did. Everything the
core decides can be tested without a phone.

## The steps

| # | Step | What it gives | Needs | Size | Status |
|---|---|---|---|---|---|
| 01 | [Groundwork](01-groundwork.md) | The APK opens its own home screen and runs on Momentum; the core module exists; the female/male bug is fixed | — | S | not started |
| 02 | [AR foundation](02-ar-foundation.md) | Camera and ARCore working past the Guardian; floors, benches and walls; sprites that stay put; tested on real phones | 01 | M | not started |
| 03 | [House map](03-house-map.md) | The home as data, and the walked-routes rule proved on generated floor plans | 01 | M | not started |
| 04 | [First visit](04-first-visit.md) | The parcel, bench, painting, knock and tour: the errands that map the home | 02, 03 | L | not started |
| 05 | [Coming back](05-coming-back.md) | The next day the painting is still on the wall, because the game finds known places again | 04 | L | not started |
| 06 | [Emotions engine](06-emotions-engine.md) | How characters feel about things, about you and about each other | 01 | L | not started |
| 07 | [Conversations](07-conversations.md) | Characters talk about things and your answers change relationships; the content language | 06 | L | not started |
| 08 | [Visits and calendar](08-visits-and-calendar.md) | Who comes when, promises, arrival order; the calendar and who's-who screens | 06, 07 | M | not started |
| 09 | [Messages and 2D visits](09-messages-and-2d.md) | Playing without AR (no ARCore, no camera, not home), and texts between visits | 07, 08 | M | not started |
| 10 | [Visitors in AR](10-visitors-in-ar.md) | Characters walking your real rooms, noticing your things and meeting each other | 05, 08 | L | not started |
| 11 | [Shop and deliveries](11-shop-and-deliveries.md) | Spending Momentum; parcels at the door; placing and moving things | 05, 06 | M | not started |
| 12 | [Guardian wall](12-guardian-wall.md) | The block screen and its rewards in Homevisits style | 01 (06 for the cameo) | S | not started |
| — | [Art](art.md) | The look: people, items, rooms, sounds. Each step lists the art it needs | any time | — | — |
| — | [Cast](cast.md) | The characters: personalities, tastes, bonds, stories | any time | — | — |

Size (S, M or L) compares the steps with each other. It isn't a time estimate.

After 01, two tracks can go side by side. The **camera track** is 02 and 03, then
04, then 05. The **people track** is 06, 07, 08, then 09. They meet at 10. Build
each step, test it, and ship it before starting the next step on the same track.

## Open questions

**Ask** marks a decision that belongs to the user. Ask it when its step starts, and
don't settle it yourself. Each one has a default to propose. When it's answered,
write the answer where the Ask is, with "(agreed with the user <date>)".

| Step | Question | Default to propose |
|---|---|---|
| [01](01-groundwork.md) | Keep Netbeasts in this APK until the first visit is playable? | **No** — switch immediately from step 01 (agreed with the user 2026-09-27) |
| [01](01-groundwork.md) | The launcher name for the game | "Homevisits" |
| [02](02-ar-foundation.md) | Phones that can't run ARCore | The 2D visits of step 09, and no camera-only mode |
| [02](02-ar-foundation.md) | Camera permission permanently denied (Android then only changes it in Settings, which the Guardian locks) | Explain, and use 2D visits |
| [04](04-first-visit.md) | What exactly did "walking the path in reverse order" mean? | **Decided (2026-09-27):** every walk is recorded. The friend uses any known path — she can detour to rooms the player walked earlier, not just retrace the most recent route. The map grows with every trip. |
| [04](04-first-visit.md) | Does the first visit use up visiting hours? | No, it's free |
| [06](06-emotions-engine.md) | Romance | None with the player, and none between characters unless the user wants it |
| [06](06-emotions-engine.md) | How much faith content | Light: a couple of churchgoing characters, never preachy |
| [06](06-emotions-engine.md) | How much drama | Gentle, with some friction |
| [08](08-visits-and-calendar.md) | Notifications when someone's at the door | None |
| [08](08-visits-and-calendar.md) | Visitors per day | Up to 2 |
| [10](10-visitors-in-ar.md) | Character voices | Short pitched blips, no voice acting |
| [11](11-shop-and-deliveries.md) | Prices, lay-by, and whether buying counts on the leaderboard | Everyday items cost less than a day's Momentum; bigger ones go on lay-by; buying doesn't count |
| [12](12-guardian-wall.md) | Any penalty for dismissing the block screen | None |
| [art](art.md) | The style for people and things | **Decided (2026-09-27):** very high-res sprites with deep rotation detail (8+ views). Warm pixel art from autogen, Rachel drawn first. |
| [cast](cast.md) | The characters besides Rachel and Alfred | The cast proposed in cast.md |

## Testing

- **Pure code:** `cd rhc-android && ./gradlew :homevisits-core:jvmTest`.
- **Without a phone:** `bash tools/homevisits_sim/run.sh` (made in step 03, and
  added to by 04 and 06–08). It draws fake houses (`house_*.png`), runs a 90-day
  soak of the emotions engine with charts, and writes readable transcripts of
  simulated visits.
- **The APK:** `./gradlew compileGamersFemaleHomevisitsReleaseKotlin` is a quick
  check. If you touch `app/src/main`, check that all four flavors compile.
  `./release.sh` commits, pushes and publishes a release, so don't run it unless
  the user asks.
- **The Codespace emulator.** It has no camera AR, but it's fine for the home
  screen, 2D visits and the Guardian. The AVD is `NetbeastPhone` (API 30). Run
  `sudo chmod 666 /dev/kvm` first and set it back to 660 afterwards. Start the
  emulator with `-read-only -no-window -no-snapshot -gpu swiftshader_indirect`.
  Use `adb root` to start activities that aren't exported. Boot takes about three
  minutes.
- **AR needs the user's phone at home.** Each AR step ends with an at-home
  checklist for the user. Step 02's debug screen shows what the game sees (no
  images) and can export a report.

## Words used in these files

- **Spot:** a place in the home where something happened, such as the front door,
  the kitchen bench or the bedroom wall. The building block of the map.
- **Room:** a named group of spots ("the kitchen"). The story names rooms; the
  game never guesses them.
- **Walk:** one recorded trip from one spot to another in an unbroken session.
- **Link:** two spots with at least one walk between them. Routes only use links.
- **Route:** a chain of links from one spot to another.
- **Placement:** an item at a position on a surface at a spot.
- **Beat:** one step of the story that the app acts out, such as "go to the door"
  or "tap the bench".
- **Director:** the core code that picks the next beat.
- **Visit, or scene:** one session with visitors, from the knock to the goodbye.
- **Storylet:** a small piece of conversation with conditions for when it can come
  up.
- **Appraisal:** working out how a character feels about an event, an action or a
  thing.
- **Visiting hours:** Homevisits' name for Aether, the daily play time.
- **Verify and align:** checking that the player really is at the spot the story
  sent them to, and working out where that spot is in today's AR session.

## Keeping this plan current

- When a step lands, mark it `done <date>` in the steps table. In the same change,
  move anything that should last (rules, constants that must match, test commands)
  into `CLAUDE.md` (a short Homevisits section, like the one for the Wilds) and
  into a new §5.2 in `rhc-android/README.md`. Keep the step's **Interfaces**
  section accurate, because later steps rely on it. Trim the rest of the step's
  file to a short note of what was built.
- When the user answers an Ask, write the answer where the Ask is.
- New ideas the user agrees to go into the step they belong to, or into a new
  numbered step. Nothing about Homevisits goes in `PLAN.md`.

## Not planned

Ask the user before building any of these:

- Real friends visiting each other's homes. That would send home data off the
  phone, which breaks rule 1.
- AI-written dialogue, or answering in free text or by voice.
- A pet that lives in your home and walks your routes.
- Seasonal decorations, such as a pōhutukawa Christmas in December.
- A floor-plan view of your home.
- Homevisits in the web client.
- Characters reacting to the Timesavers-style real-world tasks.
