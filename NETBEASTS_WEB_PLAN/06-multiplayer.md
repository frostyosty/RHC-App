# 06. Multiplayer

**Needs:** [03](03-battle-rules-in-world-core.md), [05](05-save-identity-aether.md) · **Size:** L
**Read first:** [README](README.md) (decisions, rules 1-3); 03's and 05's **Interfaces**; `world-core/.../sim/WorldSession.kt`; `htc/src/js/features/cardgame/net/room.js` (a working Supabase Realtime room, for the channel setup)
**Status:** not started

This was `NETBEASTS_PLAN.md` §4.

## Goal

Players on phones and in the website's tab walk the same Wilds together, see
each other, and fight the same creatures, with fights that take any number of
sides.

## The shape (from the user's answers)

- **A server runs the sim** ("server"), and **Supabase Realtime carries the
  messages** ("supabase"). The server is a small JVM process of ours,
  `netbeasts-server` (a new module running `world-core` on the JVM), that
  joins Realtime channels like any client and is the only one whose
  snapshots count. Run it on Fly.io by default (open question 4).
- **Everything goes through `WorldSession`.** Add `NetWorldSession`: it sends
  `PlayerInput` up and applies `WorldSnapshot`s down. The views and screens
  don't change.
- **Only entities sync, not the map.** Everyone shares the day's seed, and
  players only share a map if they share the region, so a channel is per
  (day, region, sim version): `nb-<date>-<region hash>-<sim hash>`. With more
  than about 16 players in one, open a second one (`...-2`).
- Other players already render as `spr_player_*` and show cyan on the
  minimap.

## The protocol

Broadcast messages on the channel, each with a `type`:

| From | Type | What |
|---|---|---|
| client | `hello` | `player_id`, the client's JWT (checked by the server against `nb_devices`), name, party and bag for fights, sim hash |
| server | `welcome` | the client's entity id, the tick, a full snapshot |
| client | `input` | `seq`, the tick it's for, and the `PlayerInput`s for up to 3 ticks (sent ~10 times a second) |
| server | `snap` | the tick, and the entities that changed since the last `snap` (a full one every 2 s, so a missed message heals) |
| server | `event` | `WorldEvent`s that a client acts on (its encounters, fight events, coins, fight ended), addressed by player |
| any | `bye` | leaving |

- **A wire format is needed.** The snapshot types are plain data classes
  today. Use `kotlinx.serialization` (works on JVM and wasm) with compact
  field names, or a small binary codec in `world-core`. Doubles go as they
  are; the client's copy only has to look right, the server's is the truth.
- **Smooth steering.** A round trip to Sydney and back is ~60-100 ms from NZ.
  Your own steering can't wait for that: the client predicts its own player
  with the same sim code and corrects to the server's snapshot when it
  differs; other players and beasts are drawn interpolated ~100 ms behind.
- **Check the Realtime quota.** Supabase limits Realtime messages per second
  by plan, and a broadcast counts once per receiver. 10 snaps a second to 10
  players is already 100 a second. Work out the numbers against the user's
  plan before building, and lower the snap rate or split channels if needed.
  If it doesn't fit, tell the user: a WebSocket straight to the server is the
  alternative (and an iframe doesn't stop WebSockets).
- **The sim version.** The website updates on every release (02); phones
  update only when the user installs a new APK, so versions will differ. The
  sim hash (the `DeterminismTest` pins) is in the channel name, so different
  versions never share a world. Default: the server runs only the newest
  version; an older client walks alone (`LocalWorldSession`) with a "update
  the app to play with others" note. **Ask** if the user wants old phones
  kept in.
- **Trust.** The server checks who a client is (the JWT and `nb_devices`).
  For a first version it trusts what the client says about its own party and
  bag; saving still goes through 05's rules. Write down here what a cheating
  client could do, so it's a choice and not an accident.

## Fights with more than two sides

The user's words (2026-09-27, `NETBEASTS_PLAN.md` §4): "you see another human
being attacked, you can throw down a beast to attack that netbeast at the same
time! however there should be buttons for the user to press should the user
want to attack the other user's netbeast at the same time as the computers
netbeast is attacking them (sort of backstabbing another user). Computer
netbeasts can switch the focus of their attacks with each attack instance.
Since the netbeasts are circling each other, a third combatant means the
circles need to change, into a complex 3 circle system. A fourth player needs
to be able to get roped in too so this needs to be modular! If the killing
blow on a netbeast is landed by a user, the netbeast gets captured rather
than slain." And earlier: "first come first served, other player can hang
around ready to challenge the winner."

What that means for the build (03 gives the fight its list of sides):

- **Joining.** A fight in progress near you (within the engage range) shows a
  **Join** button. Tapping it and throwing a cage adds you as a side. The
  wild beast's side stays one side.
- **Targets.** A wild beast picks its target afresh each attack, from the
  sim's RNG (**Ask** whether it should prefer whoever hurt it most). A player
  picks a target with each move: the wild beast by default, or another
  player's netbeast with the **Backstab** buttons.
- **The circles.** With two sides it's today's standoff. With N sides, each
  side's netbeast takes a place round one shared ring, evenly spaced, the ring
  growing with N, and the players circle outside it behind their own
  netbeast; everyone still reverses together on the fight's swap timer. That
  works for 3, 4 or more without special cases. **Ask:** show the user preview
  PNGs from `tools/world_preview` of a 3- and 4-sided fight before building
  the real thing, since "a complex 3 circle system" may mean something
  different to them.
- **Capture.** The side whose hit makes a wild beast faint catches it, if
  that side is a player.
- **Challenge the winner.** A player waiting near a fight can challenge the
  winner when it ends: a player-vs-player fight with the same rules. **Ask**
  what the loser loses (default: nothing beyond the fight; no netbeast
  changes hands).
- **The rules that come after** (Wilds phases 4-6 in `NETBEASTS_PLAN.md`)
  were answered for many players: a chasing beast follows the closest
  player; a red-wall colossus is one player's alone, a weekly-event boss
  interrupts everyone; mounts are seen by everyone; a sky beast fights like
  any shared encounter.

## Ask

- Open question 4 (where the server runs), if not answered.
- Old phone versions (above).
- Target choice, the ring geometry (with preview PNGs), and challenging the
  winner (above).

## Tests

- `DeterminismTest`: a scripted 3-sided and 4-sided fight, bit for bit on JVM
  and wasm (with 03's).
- A headless load test: the server plus N bot clients that walk like the soak
  test's wanderer and hunter, checking tick time on the server and message
  rates against the quota.
- By hand: two browser tabs; then a phone and a tab; then two players and a
  wild beast in one fight, with a backstab and a capture.

## Done when

- Two players (a phone and a website tab) see each other walk, join one fight,
  and one of them captures the beast with the last hit.
- A third player joins and the ring rearranges.
- A client that drops and comes back picks up where the world is.
- The server runs for a day without growing memory or falling behind 30 Hz.
