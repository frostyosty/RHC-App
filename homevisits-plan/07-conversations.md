# 07. Conversations

**Needs:** 06 · **Size:** L · **Track:** people (pure code; no phone needed)
**Read first:** [README](README.md); [06](06-emotions-engine.md) → Events, Gossip and
taking sides, and Interfaces; the voices in [cast](cast.md). Nothing else.

## Goal

Characters talk about things, with each other and with you. Your answers are social
actions, and the emotions engine turns them into changes in relationships. All the
words live in text files that a writer can edit without touching code, and a
validator in the tests checks them.

## How a conversation runs

A visit (08, 10) opens a `Conversation` for the people present. It goes like this:

1. **Hello.** A greeting that fits: a first meeting, old friends, a grudge, a
   promised visit kept.
2. **Two to five topics.** Each topic is a storylet (see below). Some are about the
   moment (the new painting, who else is here). Some are what's on the character's
   mind (their concerns). Some are characters talking about each other.
3. **Goodbye.** Often with a plan ("I'll pop round Saturday"), which becomes a
   promise (08).

Each turn is either a line (speaker, face, text) or a choice (2–4 answers). The
player taps to go on. Nothing moves on by itself while the player is walking (README
rule 6). In AR, a conversation only starts once the player stands still (10).

## Storylets

A storylet is a small piece of conversation with conditions for when it can come up.
Fallen London and many other narrative games are built this way. Each storylet says:
- who's in it;
- when it fits;
- how often it can come up;
- its lines;
- its choices, and what they do;
- the replies.

The game picks among the storylets that fit right now. So the same visit never plays
out the same way twice, and new content drops in without rewiring anything.

## The content language

The content lives in `app/src/gamersFemaleHomevisits/assets/homevisits/`:
- `cast/*.hv`: character sheets (see [cast](cast.md));
- `talk/*.hv`: storylets;
- `shop/*.hv`: items (11).

It's one small line-based format, indented by two spaces:

```
# Comments start with #.

storylet rachel.sees_new_painting
  who A = rachel
  when A sees new item tagged painting
  when A likes painting > 0.5
  weight 3
  A happy: Oh! Is that new? {item} is gorgeous.
  A: Where did you find it?
  choice "I got it thinking of you."
    do dedicate item to A
    A if pleased, touched: You did? That's so sweet of you.
    A if doubtful, laugh: Ha. Sure you did.
  choice "It was on special."
    do praise item
    A laugh: Even better. You've got an eye.
  choice "Alfred helped me choose it."
    needs you know alfred
    do credit alfred for item
    A if displeased, frown: Alfred? Huh. Well, it's still lovely.
    A: Tell him he's got taste. Just this once.
```

**`who`** binds roles. A role is either a named character (`A = rachel`) or anyone
present who fits (`B = anyone here where A dislikes B`). `you` is the player.

**`when`** gives a condition. Every `when` line must hold. Conditions read the engine
(06) and the house, for example:
- `A likes painting > 0.5`
- `A affinity B < -20`
- `A trusts you > 30`
- `A mood low`
- `A remembers council_complaint`
- `A knows fact new_lamp`
- `A sees new item tagged plants` (this also binds `{item}`)
- `B is here`
- `you know alfred`
- `home has item tagged painting in lounge`
- `weekend`, `evening`
- `raining` (the app passes the real weather in)
- `flag first_visit_done`
- `not ...` in front of any of these

**`weight`, `cooldown 3d` and `once`** control how often a storylet comes up.

**A line** is `speaker [if outcome,] [face]: text`.
- Faces: happy, sad, angry, surprised, laugh, frown, shy, touched, neutral. They map
  to art in [art](art.md).
- Slots: `{item}`, `{room}`, `{you}` (the player's name), and `{B}` for any role.

**`choice "text"`** is followed by that choice's lines.
- `needs` hides the choice unless its condition holds.
- `do` is a social action from 06's list:
  - `praise B`, `praise item`, `defend B`, `side with A against B`, `agree`,
    `disagree`, `credit B for item`
  - `tell fact <id> to A`, `keep secret`, `ask A about <topic>`, `change subject`
  - `comfort A`, `tease A`, `thank A`, `apologise to A`
  - `dedicate item to A`, `give item to A`
  - `promise visit A tomorrow`, `invite A saturday morning`
- There are also a few plain effects: `set flag x`, `advance concern
  rachel.exhibition`, and `learn A likes painting` (the player learns a taste).

**Replies depend on how the action landed.** After a `do`, the engine appraises the
action for everyone present. A reply line can then say `if pleased`, `if displeased`,
`if hurt`, `if doubtful` or `if neutral`, worked out from the emotion the action
caused in that speaker. The first line that matches is used, and a line with no `if`
catches everything else.

**`then <storylet>`** chains to another storylet, and **`end`** finishes the
conversation.

**`channel text`** (for messages, 09) and **`channel wall`** (for the block-screen
cameo, 12) mark storylets that aren't for face-to-face visits.

**Characters talk to each other** in the same format: lines from A and B in turn,
with a choice for the player when it's their moment ("Well? You tell him."). If B is
present, B hears everything that's said about B.

The parser, the condition evaluator and the runner are in `homevisits-core/talk/`.
They're pure and small, a few hundred lines. Existing formats such as ink (by Inkle)
don't fit. They would be a library in the core, which README rule 9 rules out. They
are also built for branching scripts, not for picking pieces by conditions on the
engine's state.

## Choosing what comes up

Each turn:

1. Collect the storylets whose `who` binds and whose `when` conditions all hold.
2. Score each one: its weight × fit × freshness × mood fit.
   - Fit is ×3 if the storylet was triggered by something that happened this visit,
     like seeing the painting.
   - Freshness is lower for storylets used lately (and respects `cooldown` and
     `once`).
3. Pick by seeded weighted chance among the top few, so conversations vary but stay
   sensible.

What was picked, and when, is saved, so tomorrow's visit doesn't repeat today's.

## Talking about stuff: the user's idea in practice

- **Topics.** Characters bring up what they care about: tastes above 0.5, and their
  concerns (Alfred's tomatoes, Rachel's exhibition, Maya's exams). Your answer
  (agree, disagree gently, ask more) tells them what you think. That moves their view
  of you, and teaches you their tastes, which then show on the calendar and on who's
  who (08).
- **About each other.** These are the storylets that make relationships move: one
  character talking about another, who's either absent or standing right there. Your
  answer is a social action with a target (defend, side with, change the subject,
  pass it on later), and 06's gossip and balance rules do the rest. Write plenty of
  these for every pair in [cast](cast.md) with a strong bond or a grudge.
- **Passing it on.** Anything you're told becomes a fact you know. Later storylets
  can offer "Tell Rachel what Alfred said" as a choice, with the consequences 06
  describes.

## Writing rules

- NZ English, in each character's voice from [cast](cast.md).
- At most 90 characters a line, and at most three lines before the player gets to
  tap or choose. It's a phone screen, and the player may be standing in a hallway.
- Every choice is a real choice: at least two of the answers must lead to different
  social actions.
- Kind by default. Characters can be grumpy, jealous or hurt, but not cruel. They
  never talk about the player's real life, because the game can't see it.

## Validator and transcripts

**The validator.** A test in `homevisits-core` (`ContentTest`) loads every file under
`assets/homevisits/`. It fails on any of these:
- a parse error;
- an unknown character, tag, fact, flag, face or action;
- a line over 90 characters;
- a choice with no reply;
- a storylet that no cast member could ever satisfy (a rough check);
- a slot that won't be filled.

**Transcripts.** `tools/homevisits_sim` prints transcripts of simulated visits: who
said what, which choice the pretend player took, and what changed. The user can read
how visits flow without a phone.

## The first visit's lines

Step 04 keeps its lines in a Kotlin object. Move them into `talk/first_visit.hv` in
this step. Rachel's introduction and her reaction at the painting become storylets.
The answer the player gave at the painting in 04 (saved as a flag) is where Rachel's
feelings start.

Also move the cast sheets from 06's `TestCast` into `cast/*.hv`.

## How much to write

Start with Rachel and Alfred:
- about 30 storylets each;
- 15 about the two of them;
- 40 shared ones (greetings, goodbyes, the weather, reactions to item tags, plans).

That's about 115. After that, add about 30 per new character as they're introduced
(08). The validator's reachability check and the soak's "never picked" list show
where the content is thin.

## Interfaces (for later steps)

- The content language: this file is its spec.
- `ContentPack.load(files)`.
- `Conversation(pack, mind, scene)`, with `next(): Turn` (a `Line`, a `Choice` or
  `End`) and `choose(i)`. Promises and plans that come out of a conversation are
  reported to 08.
- `channel text` and `channel wall` storylets, for 09 and 12.

## Done when

- The validator passes on the Rachel and Alfred content.
- **Scenario tests:**
  - Rachel's story beats fire when they should. For example, a new painting she'd
    like brings up `rachel.sees_new_painting`.
  - Choices change the right relationships.
  - A promise made in a goodbye reaches 08.
- The soak's transcripts read sensibly. The user reads a few.
