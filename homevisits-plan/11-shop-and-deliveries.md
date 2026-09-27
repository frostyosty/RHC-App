# 11. Shop and deliveries

**Needs:** 05, 06 · **Size:** M · **Track:** both
**Read first:** [README](README.md); [04](04-first-visit.md) → Interfaces, and beats
2–6 of its script (the parcel); [05](05-coming-back.md) → Interfaces;
[06](06-emotions-engine.md) → Tastes, and the appraisal table. `MomentumEngine.kt`
itself. Nothing else.

## Goal

The player spends Momentum on things for their home. Each purchase arrives as a
parcel at the door, brought by Sione the courier once he's been introduced. Choosing
where each new thing goes is how the game learns new rooms. Visitors notice and care.

## Momentum as it works today

Read `MomentumEngine` before changing anything.
- **The daily yield.** Each day starts with a "Daily Overcome Yield", worked out from
  the blocklist (TikTok 45, YouTube 40, Instagram 30, anything not in its list 15,
  and so on). It's set the first time the app opens that day. For five blocked apps
  or sites, that's roughly 75–160.
- **Urges.** Overcoming an urge adds 10–19, or 15–27 for adult content.
- **Evaporation.** Each amount earned starts to evaporate two hours later, at one
  every five minutes. Everything resets at midnight. It can't be saved up.
- **Spending.** `spendMomentum(prefs, name, cost)` spends it, if there's enough.

Step 01 made `resetDailyIfNeeded` run in this APK. Homevisits spends Momentum in its
own catalogue.

## The catalogue

- Items are defined in `assets/homevisits/shop/*.hv`, in 07's format. Each item has:
  - an id and a name;
  - tags, from 06's list;
  - a price and a size;
  - where it goes: a wall, a surface or the floor;
  - its art.
- Things are grounded and real:
  - paintings of many subjects: a harbour at dusk, Mauao, pōhutukawa, a bach on the
    beach, flowers, abstract, a portrait;
  - pot plants, and flowers in a vase;
  - lamps, cushions, a rug, books, a clock, candles, photo frames, a tea set, a
    ukulele.
- There's always a basic range, plus a few "this week" items that change every
  Monday (seeded by the week). So there's always something new, without pushing.
- Each item shows its tags as small icons. If the player knows someone likes one of
  its tags, the item says so ("Rachel would love this"). That's the link the user's
  example depends on.

## Prices and lay-by

- **Everyday items** cost less than a typical day's Momentum. Starting values: small
  things 20–40, paintings 50–120.
- **Bigger things** (a bookcase, a big painting) go on **lay-by**: the player pays in
  parts over up to seven days, and the item is delivered once it's paid off. Lay-by
  payments are how Momentum gets "saved" without changing `MomentumEngine`.
- **Buying** calls `MomentumEngine.spendMomentum(prefs, "Homevisits: <item>", price)`.
  It doesn't call `LeaderboardEngine.submitScoreAsync`, because the leaderboard
  counts time spent on real tasks (see the Ask).
- **No pressure** (README rule 7). Buying is one tap plus a confirm. No timers, and
  no "only 2 left".

## Deliveries

- **When it arrives.** The parcel is at the door the next time the player opens AR,
  or straight away, with a knock, if they're already in AR. In 2D (09), it's by the
  drawn door.
- **The parcel.** It's the first visit's parcel beats (04) again: go to the door, tap
  the parcel, carry it, and unwrap it where you like.
- **Where does it go?** "Where shall we put it?" Known places are offered by name
  ("the kitchen bench", "your bedroom wall"), along with "Somewhere new".
  - A known place is a `GoTo` along a route.
  - "Somewhere new" is a `GoNew`. It makes a spot, and a room if needed (named by
    asking "What room's this?"), and records the walk.

  This is how the map grows without it ever feeling like mapping.
- **Suggesting a room.** If the home has no room of a kind that suits the item (no
  lounge yet for the armchair), the parcel's card can suggest one ("This would suit a
  lounge"), at most once per delivery. It never insists.
- **Sione** (proposed cast) brings the parcels once he's been introduced. A short
  chat at the door makes deliveries part of the social game.

## Moving and putting away

- In AR, long-press an item (in 2D, tap it) to either:
  - move it: drag it along the same surface, or carry it somewhere else;
  - put it away: it goes into a cupboard list and can come back out later.
- Moving and putting away are events for 06. A character notices when their gift has
  moved from the lounge to a back room.

## Gifts

- Characters sometimes bring something: a thank-you, or a birthday present. It's
  free and arrives in their hands. Whether you put it on show matters to them (06).
- You can give a character something you own, and they take it home. That's a big
  appraisal event, especially for something they love, and the item leaves your
  home.

## What the emotions engine needs

Each item carries:
- its tags;
- who bought it and why (bought, a gift from X, dedicated to X);
- where it is: the room, and how prominent it is. The entry, lounge and kitchen are
  public; the bedroom is personal.

06's appraisal rules use all of these.

## Interfaces (for later steps)

- `Catalogue`, `Item`, `ItemId`, `Price`, `LayBy` and `Delivery`. The delivery queue
  is saved in `game.json`.
- Beats:
  - `Deliver(item)`: the parcel sequence;
  - `Place(item)`: choose a place and put the item there;
  - `Move(item)`.

## Done when

- **Tests:**
  - spending never goes below zero, and never spends twice;
  - lay-by delivers when it's paid off, and survives days passing;
  - a delivery to "somewhere new" makes a spot and a walk;
  - items keep their giver and their dedication.
- **At home:** buy something, get the parcel, and put it somewhere new. The next
  visitor notices it.

## Ask

- **Prices and lay-by.** Default: as above. Tune them after the user has seen a week
  of real Momentum.
- **The leaderboard.** Default: buying doesn't count, because it isn't a real-world
  task.
