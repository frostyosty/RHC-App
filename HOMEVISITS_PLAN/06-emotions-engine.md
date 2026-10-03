# 06. The emotions engine

**Needs:** 01 · **Size:** L · **Track:** people (pure code; no phone needed)
**Read first:** [README](README.md); [cast](cast.md), but only skim one character
sheet to see what the engine is fed. Nothing else.

## Goal

`homevisits-core/mind/` decides how each character feels (about things in your home,
about you, and about each other) and how that changes over time. It's fed events:
someone arrived, Rachel saw the new painting, you stuck up for Alfred. It answers
questions:
- How does Rachel feel about Alfred, and why?
- What face is she pulling right now?
- Does she want to visit tomorrow?

It's deterministic, and it runs 90 simulated days in seconds, so it can be tuned
without a phone.

## What it has to do

From the user's idea:

1. **Work out the relationships between the characters,** rather than scripting
   them. Rachel and Alfred don't get on because of things that happened, and new
   things that happen can change that.
2. **Use your answers when characters talk about things.** What you say about Alfred
   to Rachel changes how Rachel sees Alfred, and how she sees you.
3. **Cover more than romance:** friends, rivals, family, mentors, the neighbour
   you're wary of.
4. **Drive the rest of the game:** the faces and lines in conversations (07), who
   wants to visit and who avoids whom (08), and reactions to the things you buy (11).

And for it to work as a game:

5. **Legible.** The player can form a theory ("Rachel loves paintings; she and Alfred
   clash") and act on it. The engine can explain any relationship by pointing to the
   events behind it.
6. **Forgiving.** One bad answer can't wreck a friendship, and every relationship can
   be mended.

## The parts

```
cast sheet (content, fixed)   personality · tastes · standards · concerns · bonds · rhythm · voice
state (saved in game.json)    emotions (this scene) · mood · relationships · memories · knowledge · impressions of you

event ─► appraisal ─► emotions ─┬─► mood (hours) · relationships (weeks) · memories
                                └─► faces and lines now (07) · wanting to visit (08)
```

The code lives in `mind/`: `Mind.kt`, `Personality.kt`, `Tags.kt`, `Appraisal.kt`,
`Relationships.kt`, `Labels.kt`, `Memory.kt`, `Gossip.kt` and `Volition.kt`.

The cast sheets are content files, and the file format comes in step 07. Until then,
build a `Cast` in Kotlin: a `TestCast` with Rachel and Alfred from [cast](cast.md).

## Personality

Personality is seven numbers from −1 to 1, taken from the cast sheet, and they never
change. The first five are the Big Five, a well-known model that gives writers words
they already know. The last two are the ones this game's drama needs.

| Trait | High means | What it drives |
|---|---|---|
| Openness | likes new things and ideas | liking new items and changes to your home; interest in unfamiliar topics |
| Conscientiousness | reliable, tidy, punctual | keeping promises (08); reproach when others don't; liking tidiness |
| Extraversion | sociable | visiting more often; enjoying group visits; talking more per turn |
| Agreeableness | warm, forgiving, avoids conflict | bigger gains from kindness; grudges fade faster; less likely to criticise |
| Neuroticism | feels things strongly | stronger emotions, especially negative ones; moods that last longer |
| Pride | cares about respect and status | praise and slights both hit harder; jealousy |
| Discretion | keeps things to themselves | passing on less gossip; disliking people who gossip |

## Tastes

Tastes map tags to a number from −1 to 1: how much the character likes things with
that tag. One shared list of tags is used for items (11), topics (07) and places:
`painting, landscape, seascape, portrait, abstract, flowers, plants, books, music,
candles, cosy, bright, tidy, handmade, vintage, modern, photos, food, baking, garden,
sport, rugby, art, church, travel, family, news, weather`, and so on. The list lives
in `mind/Tags.kt`. Content files may only use tags from that list, and 07's validator
checks it.

Tastes are hidden from the player until they learn them. They can learn a taste by:
- asking the character about the topic;
- the character saying so;
- seeing the character react strongly to it twice.

What the player has learned is saved, and only that is shown on screen (08).

## Standards

Standards are what each character thinks is right, as weights from 0 to 1: honesty,
kindness, loyalty, generosity, reliability, modesty, tidiness and faith. Anyone's
actions (including the player's) are tagged with the standards they uphold or break.
A character reacts in proportion to how much they care about that standard.

## Concerns

Concerns are what's on a character's mind, one to three at a time, from their story
in the cast sheet: Rachel's exhibition, Alfred missing his wife. A concern has a goal,
and the cast sheet names the events that help it or hurt it.
- Helpful events bring joy and gratitude; harmful ones bring distress.
- Events the character expects bring hope or fear, and then relief or disappointment.

Concerns move on through the character's story as things happen. 07's storylets can
advance them.

## Events

Everything that happens is an `Event`, recording:
- who did it;
- who it was to, or about;
- what it involved (an item, a topic, a fact);
- where it happened;
- who was there to see it;
- when it happened.

The kinds of event:

- **Presence:** arrived, left, met (for the first time), saw an item, entered a room.
- **Social actions,** by a character or by the player. These are what 07's choices
  map to:
  - greet, thank, joke, tease, comfort, confide, apologise;
  - praise (a person, an item or a taste), criticise, agree, disagree;
  - defend someone, side with someone, credit someone (for something);
  - ask about a topic, tell a fact (news or gossip), keep a secret, change the
    subject;
  - give an item, dedicate an item ("I got it thinking of you");
  - invite, promise, keep a promise, break a promise, leave early.
- **House:** an item bought, placed, moved or removed; a gift on show or not.
- **World:** a day passed; the player's day (Momentum earned and urges overcome, from
  step 12); life events from the cast sheets (Rachel's exhibition opens).

Each social action lists the standards it touches. For example:
- `tell` (something unkind about someone): kindness −, discretion −
- `defend`: loyalty +, kindness +
- `keep promise`: reliability +
- `give`: generosity +
- `dedicate`: kindness +

## Appraisal: from events to emotions

For each character who perceives an event (they were there, or they're told about it
later), `Appraisal` works out their emotions. It follows the OCC model (Ortony,
Clore and Collins), the usual basis for emotions in game characters:
- events are judged against the character's goals;
- actions are judged against their standards;
- things are judged against their tastes.

Starting values; tune them with the soak.

| When a character… | They feel | How strongly |
|---|---|---|
| sees an item | liking or disliking, towards the item | how well its tags match their tastes × novelty (1.0 if new, 0.3 if seen before) × (1 + 0.5 × openness, if new) |
| sees an item you chose for them | gratitude to you as well | their liking × how far they believe you (their trust in you) |
| sees a gift they gave you, on show | gratitude and pride | 0.6; more if it's somewhere prominent (lounge, entry, kitchen) |
| is praised, or their things are | joy, and gratitude to whoever praised them | (0.5 + 0.5 × respect for them) × (1 + 0.5 × pride). If their trust in the praiser is below −30, nothing, or reproach ("flattery") |
| is criticised | distress, and anger at the critic | (0.5 + 0.5 × pride) × (1 + 0.5 × neuroticism) |
| sees someone uphold or break a standard they hold | admiration or reproach, towards that person | the standard's weight × how clear-cut it was |
| has a concern helped or hurt | joy and gratitude, or distress and anger | how much the concern matters |
| hears of good or bad luck for someone | happy-for or sorry-for if they like that person; resentment or gloating if they don't | how big it is × how much they care about that person |
| has a promise to them kept or broken | gratitude, or disappointment and anger | how much it mattered × (0.5 + conscientiousness) |
| sees someone they dislike turn up | distress, plus anger if there's a grudge | −affinity / 100 |
| sees someone they like turn up | joy | affinity / 100 |
| expects something | hope or fear | how likely it is × how much it matters |
| watches you spend the visit on a rival | jealousy (resentment and distress) | pride × how one-sided it was |

Mood scales the result: a bad mood makes negative emotions stronger. Neuroticism
does the same for negative emotions.

An emotion has a kind, a target (a character, you, an item or an event), an
intensity from 0 to 1, and a cause (the event).

## Emotions, mood and faces

- **Emotions** last for the scene (one visit). They're what shows right now:
  - the face: happy, sad, angry, surprised, laugh, frown, shy, touched or neutral;
  - the little emote over the head: ❤️ 💢 💦 ✨ ❓;
  - which reply variant 07 picks.
- **Mood** is three numbers from −1 to 1: pleasure, arousal and dominance (PAD).
  - Every emotion pulls the mood towards that emotion's own PAD values. Gebhard's
    ALMA model has a table of PAD values for the OCC emotions to start from.
  - Between scenes, mood drifts back to the character's resting mood. Getting
    halfway back takes about 8 hours × (1 + neuroticism).
  - Mood decides which lines fit ("grumpy Alfred"), and it colours the next
    appraisals.
- `expression(who)` returns the face: the strongest emotion of the scene if it's
  above 0.3, otherwise a face from the mood.

## Relationships

Relationships are directed: Rachel's view of Alfred isn't Alfred's view of Rachel.
Every character has a relationship with each other character they know, and with
you. Each one is four numbers:

| | Range | Means |
|---|---|---|
| affinity | −100 to 100 | how much they like them |
| trust | −100 to 100 | whether they believe them, and would trust them with a secret |
| respect | −100 to 100 | how much they look up to them |
| familiarity | 0 to 100 | how well they know them; grows with time spent together |

There are also fixed **bonds** from the cast sheet: sisters, old friends,
neighbours, workmates. Each bond has a resting point that long-term drift returns
towards.

**At the end of each scene,** its emotions settle into the relationships. The
changes, where `i` is the emotion's intensity (starting values):

| Emotion | Change |
|---|---|
| gratitude | affinity +8i, trust +3i |
| admiration | respect +8i, affinity +3i |
| reproach | respect −8i, trust −2i |
| anger | affinity −10i, trust −4i |
| joy in their company | affinity +2i |
| happy-for, sorry-for | affinity +2i |
| resentment, gloating | affinity −3i |

Each minute spent together also adds 0.3 to familiarity.

Every change is multiplied by `stability = 1 − familiarity / 200`, so old friends
don't swing as much as new acquaintances. Positive changes are also multiplied by
(1 + 0.3 × agreeableness), and negative ones by (1 + 0.3 × neuroticism).

**Caps.** No number moves more than 15 in one scene. One bad visit can't end a
friendship.

**Each day:**
- Affinity above its resting point drifts 1% of the way back, so friends drift apart
  without contact.
- Affinity below its resting point drifts (1% + 2% × agreeableness) of the way back,
  so grudges fade, and faster for forgiving people.
- Familiarity fades by 0.2 a day after two weeks apart.
- Trust and respect drift at half those rates.

**Labels,** for the screens (08) and for conditions (07), are worked out from the
numbers: close friends, friends, getting on, acquaintances, don't get on, rivals (low
affinity but some respect), can't stand each other, family, looks up to. The
thresholds are in one table in `mind/Labels.kt`.

**Why.** `why(a, b)` returns the memories that moved a's view of b the most. So the
game can say "She still hasn't forgiven him for the council complaint", and the
who's-who screen can explain a line between two people.

## Memory and knowledge

- An **event log** keeps what happened. It's capped, and the oldest trivial events
  are dropped first.
- Each character has **memories**: references to events they saw or were told
  about, each with a salience from 0 to 1 that comes from the emotion the event
  caused.
  - Salience fades, halving in about two weeks. Strong memories last longer.
  - Memories power callbacks ("Remember when you hung my painting in your
    bedroom?"), grudges, and `why`.
- **Knowledge** is who knows what.
  - A `Fact` is a claim: "Alfred said Rachel's mural is an eyesore", "Rachel is
    coming tomorrow", "there's a new lamp in the lounge".
  - A character knows a fact if they saw it happen, saw it in your home, or were told
    it. Being told records who told them, and when.
  - Characters only react to what they know.
- **The player's knowledge** is tracked the same way, and it's all the screens show.
  You don't see Rachel's view of Alfred until you've seen it or been told.

## Gossip and taking sides

This is how the user's idea works: characters talk about each other, and what you say
travels.

**Hearing a fact.** When a character hears a fact about someone (from you or from
anyone else), their view of that someone moves:

`Δaffinity(listener → subject) = k × sentiment × trust(listener → teller) / 100 × relevance × stability`

Relevance is how much the listener cares about the subject, and about the topic.

**Judging the teller.** The listener also judges whoever told them. Passing on
something unkind upsets people who value kindness or discretion (reproach). It
pleases people who dislike the subject.

**Passing it on.** Characters pass facts on when they meet, on screen or off (08).
They choose what to pass on by salience × relevance × (1 − discretion) × how close
they are to the listener. Maya is a gossip and spreads news in a day. Ruth keeps
secrets.

**Taking sides.** When Rachel complains about Alfred, your answer is a choice:
- *Agree:* Rachel's affinity to you goes up. Your stance against Alfred is now known
  to her, and to Alfred too if he was there or hears of it.
- *Defend him:* Rachel's view of Alfred softens a little, if she trusts you. How her
  view of you changes depends on her loyalty and kindness weights, and on how much
  she dislikes him.
- *Change the subject:* little changes, and the tension drops.

**Balance.** People tend to like their friends' friends, and to cool on their
enemies' friends (Heider's balance theory). Each day, look at each strong triangle
(three characters, or two characters and you) that's out of balance, and nudge its
weakest side a little: about 0.5 a day at full strength. You count as one of the
three. If Rachel likes you and knows you like Alfred, she slowly warms to him.

**Impressions of you.** Each character keeps a running score of your actions by
standard: kind, honest, loyal, generous, reliable, a gossip. It feeds their lines
("You always know what to say") and their appraisals (people tell a gossip fewer
secrets).

## What characters choose to do

In group scenes (07, 10) and off-screen meetings (08), characters choose their own
social actions. Each option is scored on:
- how well it fits their personality;
- their relationships (who they like and dislike);
- their mood;
- their concerns;
- variety (not what they just did);
- a little seeded noise.

They pick the best option, and 07 turns it into a line. For example:
- Criticising someone scores higher the lower their affinity for that person, the
  lower their agreeableness and the higher their neuroticism.
- Leaving early scores higher when someone they dislike is there and their
  extraversion is low.

The weights are in one table in `mind/Volition.kt`.

## Time and determinism

- A **scene** is one visit: `startScene`, then events, then `endScene` (the emotions
  settle into relationships and memories).
- `advanceTo(clock)` applies the mood and relationship drift for the time that has
  passed, and runs off-screen days (08 decides who met whom). It runs each day in
  full for up to 14 days, and only the drift after that, so the player can be away
  for months.
- The same seed and the same inputs always give the same history:
  - randomness only comes from `Rng` streams;
  - characters are always processed in id order;
  - nothing depends on hash-map order;
  - nothing reads the clock.

  `kotlin.math` is fine here, because being bit-exact between the JVM and wasm isn't
  needed. The engine barely needs trig anyway.

## Guardrails

- Every number is clamped to its range.
- No character leaves the game for good. The worst that happens is that they visit
  rarely until things are mended.
- Every negative has a way back: apologising, time, help with a concern, or a
  peacemaker (Ruth, in the proposed cast).
- The engine never punishes the player for being away from the phone (README
  rule 7).

## Interfaces (for later steps)

```kotlin
class Mind(cast: Cast, state: MindState, rng: Rng) {
    fun advanceTo(clock: HomeClock)
    fun startScene(present: List<CharId>, place: RoomKind)
    fun perceive(event: Event)                             // appraise it for everyone who perceives it
    fun endScene(): SceneSummary                           // what changed, for 07's closing lines and the tools
    fun choose(who: CharId, options: List<SocialAction>): SocialAction
    fun relationship(from: CharId, to: Who): Relationship  // Who is a character or the player
    fun label(from: CharId, to: Who): Label
    fun why(from: CharId, to: Who): List<Memory>
    fun expression(who: CharId): Expression
    fun mood(who: CharId): Pad
    fun knows(who: Who, fact: FactId): Boolean
    fun tasteKnownToPlayer(who: CharId, tag: Tag): Float?  // null if not learned yet
    fun wantsToVisit(who: CharId, day: Int): Float         // 08 combines it with rhythms and promises
    val state: MindState                                   // saved in game.json
}
```

`Event`, `SocialAction`, `Fact`, `Tag`, `Emotion`, `Expression` and `Label` are the
vocabulary that 07's content language uses.

## Done when

- **Unit tests** for each appraisal rule, and for how emotions settle into
  relationships.
- **Scenario tests,** written from the user's example:
  - Rachel (who loves paintings) sees a new painting, and her affinity to you rises
    by 5–15.
  - Rachel and Alfred, left alone with no help, still "don't get on" after 30 days.
  - You defending Alfred to a Rachel who trusts you softens her view of him. Three
    scenes like that, with Ruth there, can bring the two of them to "getting on"
    within about three weeks.
  - One rude answer drops a close friend by at most 15, and the friendship recovers
    within a week.
- **The 90-day soak** in `tools/homevisits_sim`, with three pretend players (kind,
  gossip and random):
  - no value goes out of range;
  - relationships spread out, rather than all ending up the same;
  - the kind player ends up with more friends than the gossip;
  - the same seed gives an identical history (compare hashes).

  The soak writes a chart per relationship over the 90 days, and a daily "what
  happened" log the user can read.

## Ask

- **Romance.** Default: none between the player and the characters, and none between
  characters. If the user wants it between characters, it's a fifth relationship
  number and a bond, off by default.
- **Faith.** Default: light. A couple of characters go to church, and it comes up
  naturally (a Sunday lunch, praying for someone). It's never preachy and never
  mocked. `faith` is a standard like the others.
- **How much drama.** Default: gentle, with some friction. The tuning knobs are the
  volition weights for criticising and leaving early, and the gossip rate.
