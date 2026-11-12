# 05. The save, the QR link, Aether and Nightfall in Supabase

**Needs:** [04](04-web-client.md) for the web side; the phone side can start any time · **Size:** M
**Read first:** [README](README.md) ("The save is Android prefs", "Aether", "Supabase today", open questions 1, 3, 5); `engines/AetherEngine.kt`; `GameState.kt`; `SaveManager`
**Status:** not started

## Goal

A player's Netbeasts (party, bag, coins) and their daily Aether live in
Supabase, so the phone and the browser play the same save, and the browser
honours the same daily limit and Nightfall the phone sets. No accounts, as the
user asked: a phone and a browser are joined by scanning a QR code.

## Identity

**Default: Supabase anonymous sign-ins.** Each device signs in anonymously
(a real Supabase user with no email, and a JWT), so Row Level Security can
use `auth.uid()`. A player is the phone's existing `PLAYER_ID`; a table maps
devices to players:

```
nb_devices(auth_uid uuid primary key, player_id uuid, kind text /* phone|web */, linked_at timestamptz)
```

Every other table's RLS is "rows whose `player_id` is one of my devices'
players". Anonymous sign-ins have to be switched on in the project
(Authentication settings), by the user. The phone talks to Supabase with
plain HTTP, like `LeaderboardEngine.kt` does; no SDK needed.

If anonymous sign-ins can't be used, the fallback is a per-player secret
(made on the phone, stored hashed) and `security definer` RPCs that check it.
More code, same result.

The website's own logins are ignored (open question 5).

## The QR link

1. The browser (in the iframe) shows **"Link your phone"**: a QR code and the
   same thing as a 6-character code, valid 10 minutes, single use
   (`nb_link_codes(code, web_auth_uid, expires_at)`).
2. On the phone, Netbeasts → Settings → **"Link a browser"** scans it with the
   camera (the Gamers APKs have camera access through `PermissionGate`) or
   lets you type the code.
3. The phone calls an RPC `link_device(code)` that adds the browser's
   `auth_uid` to the phone's player in `nb_devices`.
4. The browser hears about it (Realtime on its own row, or polling every
   2 s), loads the save and starts.

A desktop can't scan, which is why the browser shows the code and the phone
scans it, not the other way round. The phone can list and remove linked
browsers.

## The save

```
nb_saves(player_id uuid primary key, data jsonb, version int, updated_at timestamptz,
         holder uuid /* auth_uid of the device playing now */, held_until timestamptz)
```

- `data` holds what `GameState.loadSaveData` reads (README facts list), with
  netbeasts in `SaveManager`'s existing string format so nothing about a
  netbeast's encoding changes.
- **One device plays at a time.** Starting a walk takes the hold
  (`holder`, `held_until` = now + 60 s, renewed every 30 s while playing).
  The other device shows "You're playing on your phone right now" and waits.
  A hold that isn't renewed lapses, so a closed tab doesn't lock you out.
- **Writes check the version** (`update ... where version = $expected`), so a
  stale device can't overwrite newer progress.
- **The phone works offline**, as now: prefs stay its working copy. When it's
  online it pulls before a walk and pushes after. If the server's version moved
  on while the phone was offline (you played on the web), the server's save
  wins for party/bag/coins; the phone's local changes since its last sync are
  only the Guardian's (next point).
- **The Guardian writes `PARTY_DATA` too** (`GuardianService.kt`: the red wall
  adds netbeasts and kills the weakest three when you flee). Those are events,
  not a new save: record them as events (`nb_party_events`), apply them on the
  server with an RPC, and re-apply any not yet uploaded on top of a pulled
  save. That way a red-wall death can't be undone by playing on the web.

## Aether and Nightfall

The user's answer: the browser "has to read this from the server".

```
nb_aether(player_id uuid primary key, tz text, install_time timestamptz,
          daily_limit int, day date, seconds_left int, remnants int, last_played timestamptz,
          nightfall_start time, nightfall_end time, pause_until timestamptz)
```

- **One copy of the Aether rule.** The daily amount (20 minutes on install
  day, one less a day down to 10, up to 5 minutes of remnants, the one-way
  daily limit; `AetherEngine.kt`) goes into a Postgres function
  `aether_today(player_id)` that sets up the day the first time it's asked on
  a new day (in the player's time zone). The phone calls it when online and
  falls back to its own `AetherEngine` when offline, taking the smaller of the
  two when it reconnects.
- **Spending:** while time runs (same rules as the phone: in the Wilds,
  Bag/Shop; not on Party or in the background), the playing device calls
  `spend_aether(seconds)` every 15 s. The function caps the spend by the real
  time since the last call, so a client can't spend less than it played.
  At 0 the game closes on both devices, as on the phone.
- **The one-way slider** on the phone writes `daily_limit` (only ever down).
- **Nightfall:** the phone uploads its Nightfall hours and the shield pause
  (`SYSTEM_PAUSE_UNTIL`); the browser won't start during Nightfall unless
  paused, and stops when it begins (after any fight in progress), as on the
  phone.
- **Guests** (if the user agreed to open question 1): a browser with no
  linked phone gets its own player and its own `nb_aether` row with the
  install-day rule from the guest's first visit. Linking a phone later asks
  which save to keep.

## The region

A linked browser uses the phone's last `Region` (store `Region.encode()` in
`nb_saves`), so you walk your own home's Wilds on the web, and so phone and
browser land on the same map for multiplayer.

## Where the SQL lives

A `supabase/migrations/` folder in this repo, one numbered `.sql` file per
change, applied by the user (Supabase dashboard SQL editor or the Supabase
CLI). Every table has RLS on from its first migration.

## Ask

- Open question 3 (which Supabase project) and open question 1 (guests),
  if not answered in 04.
- The save-conflict rule above (server wins, Guardian events re-applied).
  It's the default; confirm it.
- Whether the website should show anything about a linked player outside
  the iframe (for example on their website profile). Default: no.

## Interfaces (for later steps)

- `nb_devices` and the JWT: the multiplayer server (06) trusts a client's
  `player_id` only through these.
- The fight-ended event from 03 is applied to `nb_saves` by the device that
  holds the save.

## Done when

- Link a browser to a phone with the QR code; play a walk on the web; the
  phone shows the new coins and catches; play on the phone; the web shows
  them.
- Aether spent on one device is gone on the other; the slider on the phone
  limits the web; the web won't open in Nightfall.
- A red-wall death on the phone while the web holds the save still happens.
- RLS checked: a device can't read or write another player's rows (try it
  with a second anonymous user).
