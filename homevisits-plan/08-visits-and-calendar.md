# 08. Visits and the calendar

**Needs:** 06, 07 · **Size:** M · **Track:** people
**Read first:** [README](README.md); [06](06-emotions-engine.md) → Interfaces;
[07](07-conversations.md) → Interfaces; the rhythms in [cast](cast.md). Nothing else.

## Goal

`homevisits-core/visits/` decides who comes round and when. It works from:
- their habits;
- the promises they've made;
- how they feel about you;
- what they've heard about your home;
- who they'd rather not bump into.

The home screen shows what the player knows of all this: who's coming, what they
like, and who gets on with whom. This is where the user's "I hope she arrives before
Alfred" happens.

## Visiting hours

Homevisits uses Aether (`AetherEngine`) as its daily play time, called visiting hours
on screen.
- Visiting hours are spent only while a visit or a walk-around is open, not on the
  home screen.
- When they run out, the visit ends gracefully ("Oh, look at the time") within about
  20 seconds, and never mid-sentence.
- Nightfall closes a visit the same way.

## Who wants to come

Each day, for each character who has already been introduced, start from 06's
`wantsToVisit`, then add:

- **Rhythm** (from the cast sheet): the days and times they tend to drop by. Alfred:
  mornings, on Tuesday, Thursday and Saturday. Rachel: after work on weekdays, and
  Saturday afternoons.
- **Promises:** a promise to come ("I'll come back tomorrow") adds a lot:
  (0.6 + 0.4 × conscientiousness). Alfred is never late. Rachel usually turns up,
  though not always on time.
- **Pull:** something they'd like that they've heard about. Maya told Rachel about
  the new painting.
- **Need:** a concern they want to talk about.
- **Avoidance:** if they expect someone they dislike to be there (because they know
  that person's plans or habits), subtract fear × 0.5. Rachel stays away on Saturday
  mornings once she's learned that's Alfred's time.
- **Rest:** less if they came yesterday.
- A little seeded noise.

A character above a threshold will come, in a window from their rhythm. At most two
visitors come a day (see the Ask), and at most three are there at once.

## When the app opens

Visits can only happen while the player is in the app, and the game must not train
people to open their phone at set times (README rule 7). So:

- **When a visit is due.** A visit is due from the start of its window until the end
  of the day. The first time the player opens Homevisits while it's due, there's a
  knock: "🚪 Someone's at the door" on the home screen, and the knock sound. [Answer
  the door] starts AR, or a 2D visit (09).
- **Two at once.** If two visits are due, they come in window order. The second
  visitor knocks partway through the first visit, about 1½ to 3 minutes in. That's
  the "before Alfred" moment: Rachel sees the painting first, then Alfred knocks.
- **Missed days.** If the day ends and the player never came, the visitor leaves a
  note (a message, 09): "Popped round but you were out. Hope you were somewhere nice!
  — R". The promise counts as kept, and there's no penalty. If the player earned
  Momentum that day, the note mentions it ("off your phone, I bet. Good on you").
- **Changing the order.** The player can invite someone for a time ("Come for
  morning tea Saturday?"), in a conversation or a message, or ask someone to come on
  another day. Whether they agree depends on the relationship and on their
  conscientiousness.

## When rivals meet

When someone arrives and someone they dislike is already there:
- 06 appraises it;
- 07 picks storylets for the pair;
- volition (06) decides whether they stay, needle each other, or leave early.

The player's answers matter most in those moments: mediate, take a side, or change
the subject. Ruth (proposed cast) makes peace more likely when she's there, so
inviting Ruth along with both of them is a strategy the player can discover.

## Off-screen life

Characters live their lives between visits. For each day that passes (06
`advanceTo`):

- **Off-screen meetings.** Characters who share a rhythm meet off screen: the
  churchgoers on Sunday, the neighbours in the street, Maya and Rachel for coffee.
  Each meeting quietly runs a few volition turns and gossip exchanges.
- **Life events** from the cast sheets happen on their days. Rachel's exhibition
  opens.
- **News.** Both of these produce facts, which come up in later conversations ("Did
  you hear Alfred and Rachel had words at the fence?") and in messages.

## Introducing the cast

Characters arrive over the first few weeks. Each one has an introduction storylet
that fires when its conditions hold:
- Rachel on day 1 (the first visit);
- Alfred around day 2 or 3;
- Sione with the first parcel you buy (11);
- Maya around day 5;
- Ruth in week 2;
- the rest later.

See [cast](cast.md) for who they are and why they come. A new character never
arrives on the same day as a promised visit from someone the player already knows.

## The home screen

The home screen isn't AR. The placeholder `HomevisitsActivity` from 01 grows into
these parts:

- **Today:**
  - today's Momentum;
  - who's at the door or expected ("Rachel said she'd come today");
  - a [Walk around] button to see and rearrange your things in AR.
- **Calendar:** the next seven days, but only as far as the player knows:
  - "said she'd come" (a promise);
  - "usually comes Saturday mornings" (a habit, shown once the player has seen it
    twice);
  - what the player has learned each person likes ("loves paintings").
- **Who's who:**
  - everyone the player has met;
  - lines between the pairs whose feelings the player has seen or heard about. Each
    line is warm, cool or tense, with a label ("old friends", "don't get on").
  - Tapping a line shows why, from memories the player shares (06 `why`, filtered to
    what the player knows).
- **Catalogue** (11) and **Messages** (09).

## Interfaces (for later steps)

- `VisitPlanner.planDay(day): DayPlan` (who, in what window, and why),
  `due(clock): List<Arrival>`, and `secondArrivalDelay(...)`.
- `Promise(who, day, window?, madeIn)` and `Plan` (an invitation or a rescheduling).
  Both are saved in `game.json`.
- `Calendar.view(mind): List<CalendarEntry>` and `WhosWho.view(mind): Graph`, both
  filtered to what the player knows.
- Off-screen days: `OffScreen.run(day)`, called from 06's `advanceTo`.

## Done when

- **Planner tests:**
  - a promise for tomorrow is due tomorrow;
  - two due visits come in window order, and the second visitor knocks during the
    first visit;
  - a character who knows the habits of someone they dislike avoids overlapping with
    them at least 70% of the time over the soak;
  - a missed day produces a note and no loss.
- **The 90-day soak** (tools):
  - visits per day stay between 0 and 2, averaging about 1;
  - everyone who's been introduced visits at least every 10 days, unless they're
    upset with the player;
  - the transcripts show second arrivals and scenes between rivals.
- **On the emulator in 2D (09), or on a phone:** open the app on a day with two
  visits due, and see the second one knock.

## Ask

- **Notifications** when someone's at the door. Default: none. The knock is waiting
  when the player opens the app.
- **Visitors per day.** Default: up to 2.
