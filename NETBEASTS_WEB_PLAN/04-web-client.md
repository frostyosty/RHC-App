# 04. The web client, single-player

**Needs:** [01](01-kotlin-and-web-spike.md), [03](03-battle-rules-in-world-core.md) · **Size:** L
**Read first:** [README](README.md); 01's and 03's **Interfaces**; `rhc-android/README.md` §5.1 ("How it plays")
**Status:** not started

## Goal

The whole Wilds walk in the browser, playing like the phone: the auto-walk,
coins, creatures, visitors, and fights with cages, moves and items. Saved
locally for now; [05](05-save-identity-aether.md) moves the save to Supabase.

## What to build

The Android side of the Wilds is the reference. Match it piece by piece,
in `netbeasts-web`, and keep every rule in `world-core`:

| Phone (`app/.../world/`) | Web |
|---|---|
| `WorldView`: frame loop, touch steering and look, the HUD (timer, coin and net counters, minimap, health bars over fighters, the beast's patience, captions) | Canvas for the 3D frame; HTML/CSS over it for the HUD, so text is sharp at any size |
| `WorldFight`: cages down the left, the three move buttons, items, the patience timer | An HTML panel driven by the fight state in the snapshot and the fight events from 03 |
| `WorldCages`: cages ride down the left on a walk, drag to reorder, tap to let out | Same, pointer events |
| `WorldBridge`: making the walk, starting it, coins, resuming after a fight, end-of-walk reward | A small `WebGame` class |
| `WorldVisitors`: poachers and the invasion boss | Comes with the save in 05 (who's due depends on the save). Leave out until then |
| `SpriteBank` | The sprite sheets from 01 |
| `RegionProbe` (Open-Meteo elevation) and `WeatherEngine` | The same two Open-Meteo calls from the browser (it allows CORS), from the browser's location. Ask permission only when the player taps a "use my location" button; without it use the generic region and the clear-day weather |
| Sounds (`AudioEngine`, `res/raw`) | Web Audio; mp3/ogg copies of the sounds the fight events name. Muted until the first tap (browsers block autoplay) |

- **Controls:** touch and mouse drag as on the phone, keyboard on desktop
  (steer, look, 1-3 for moves, number keys or Q/E for cages). Holding a move
  button shows what it does, as on the phone; on desktop, hovering does.
- **Portrait and landscape:** the phone's 200-wide portrait frame and 320-wide
  landscape frame both exist; the iframe on a desktop is landscape.
- **A fullscreen button** that asks the website to go fullscreen
  (`netbeasts:fullscreen`, see 02).
- **Pausing:** when the tab is hidden or the website switches sub-tab, the
  walk pauses (the phone holds Aether in the background; do the same).
- **The starter save** for a new browser: the same starter party, nets,
  sprays and potions as `MainActivity` gives a new phone (find
  `starterParty` there). Stored with the same string format as
  `SaveManager` so 05 can move it as-is.
- **Not on the web:** the red wall, the Guardian, invasions from blocked apps,
  Momentum. Those are the phone's. The web plays the Wilds only.

## Ask

- Open question 1 in the README (visitors without the app) before this step
  starts. It decides whether a new browser gets a starter save at all.
- Which phone screens the web needs besides the Wilds: Party, Bag, Shop?
  Default: Party (see and reorder your netbeasts) and Bag (equip items) yes,
  Shop and Market later.

## Interfaces (for later steps)

- `WebGame` takes a `WorldSession`, so 06 swaps `LocalWorldSession` for the
  network session without touching the UI.
- The local save behind one small interface (`load()`, `save()`), so 05
  swaps it for Supabase.

## Done when

- A full walk with a catch, a loss, a let-out Looter and coins plays in the
  browser like it does on the phone, in Chrome, Firefox and Safari, on a
  desktop and on a phone.
- Published through 02 and visible in the website's tab (to admins, or to
  everyone if the user said so).
