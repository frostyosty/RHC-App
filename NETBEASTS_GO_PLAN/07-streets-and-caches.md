# 07. Streets and caches

**Needs:** 03, 04 · **Size:** L · **Optional:** the game is whole without it
**Read first:** [README](README.md), rules 3, 5 and 6; [03](03-radar.md) and
[04](04-beasts-at-real-places.md) → Interfaces.

## Goal

The radar becomes a map: your real streets, parks and shoreline are drawn
under the dots. Beasts keep off roads and out of the water, and gather in
parks. Real landmarks become **caches** that give coins or nets once a day.

Start with the Ask. This step brings in an outside service that the game
depends on, which nothing else in this plan does.

## Where the streets come from

OpenStreetMap, through the Overpass API. It needs no key and no account.

- **What's asked for:** the roads and paths (with their kind), parks,
  reserves, playgrounds, beaches, water, and places of worship in one square.
- **Which square:** the 1 km square you're in, snapped to a fixed grid, and
  its neighbours as you near an edge. The service learns which square, not
  where in it, which is what the weather lookup already gives away (rule 5).
- **How often:** each square is kept on the phone for 30 days, so a player
  asks a handful of times a month. Send a `User-Agent` that names the app.
- **Credit:** "© OpenStreetMap contributors" on the map screen. The licence
  requires it.
- **When it fails:** the radar of step 03, exactly as before. The game never
  waits on it.
- **Its limits.** The public Overpass servers are run by volunteers and limit
  how much one address may ask. That's fine for a few players. If the game
  ever has thousands, it needs its own copy of the data, which is a server to
  run. Say so to the user when asking.

`go/StreetMap.kt` fetches and stores squares (plain files in app storage). The
parsing and everything after it is pure, in `world-core` `world/go/`
(`StreetSquare`: lists of lines and shapes in `GeoPoint`s), so `tools/go_sim`
can load a saved square of Tauranga and draw it.

## Drawing it

Under the dots, in the Wilds' colours: paths and quiet streets as the Wilds'
dirt tracks, busy roads darker and wider, parks as meadow, water as water.
No buildings and no names: it's there to show you the way, and a glance still
has to be enough (rule 1).

## Beasts that know the streets

`GoSpawns` gets an optional `StreetSquare`:

- a spawn in water, or within 15 m of a motorway, trunk or primary road, is
  moved to the nearest path or park within its cell, or dropped;
- cells in a park get about twice as many;
- rule 3 still holds: the game never tells you to go to the spot.

**This bends rule 6.** Two phones with squares fetched at different times can
disagree about a spawn near a road that was edited in between. It'll be rare.
Without a square the spawns are the plain ones of step 04, so a player with no
data and a player with it can also differ. Step [08](08-together.md) settles
it for people playing together: the host's list wins.

## Caches

- A cache is a park, reserve, playground, lookout or church (see the Ask) from
  the square. Its id is OpenStreetMap's.
- On the map it's a small marker. Within reach and standing still, tap it:
  a few coins, and sometimes a net or a potion, rolled from a hash of the
  cache and the day. Each cache pays once a day (`GO_CACHES` in prefs).
- A big park is one cache, not one per path.

## Ask

- **Use OpenStreetMap at all?** Default: yes, as above. It's the first outside
  service the game would lean on besides the weather.
- **Are churches caches?** Default: yes. It suits the app, and they're
  landmarks people know. Tapping one needs you near it, not in it.
- **What a cache gives.** Default: 3-6 coins, and one time in five a net.

## On a walk (the user, on their phone)

1. Is the map your streets? Is anything badly wrong or missing?
2. Did any beast sit on a main road or in the harbour?
3. Which caches are near home? Are any somewhere you wouldn't want to stand?
4. With mobile data off: does the radar still work?

## Interfaces (for later steps)

- `StreetSquare` (pure): `paths`, `roads`, `parks`, `water`, `landmarks`.
- `StreetMap.squareFor(point): StreetSquare?` (never blocks; null until it has
  one).
- `GoSpawns.around(..., streets: StreetSquare?)`.
- `GoCaches.around(cell, streets, day)`; prefs `GO_CACHES`.

## Done when

- The parser's tests pass on a saved square, on the JVM and wasm.
- `tools/go_sim` draws that square with its spawns and caches to a PNG, and no
  spawn is in water or on a main road.
- With the network off the game plays as it did after step 04.

## Findings

(Filled in from the walk.)
