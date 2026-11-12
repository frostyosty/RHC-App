# Netbeasts web plan

The 3D Wilds playable in a browser, inside the third tab of the Games page on
the user's website (the **htc** repo, `frostyosty`'s site on Vercel), with
multiplayer, and updating itself every time RHC releases.

**Priority (agreed with the user 2026-11-12):** this now comes first. It
replaces steps §2, §3 and §4 of `NETBEASTS_PLAN.md` (the battle rules into
`world-core`, the web client, multiplayer), which now point here, and it moves
to the top of `ROADMAP.md`. Wilds phases 4-6 still come after it.

**How to read this folder.** Everyone reads this file. Then read your step's
file, plus only what its **Read first** line lists. A step that needs
something an earlier step built points at that step's **Interfaces** section.

## What the user asked for

From the user (2026-11-12), in the htc repo: "the third game in the game tab
is netbeasts ... please figure out how to incorporate the 3d version of the
game into this third tab, multiplayer etc. When I update it in RHC, can it
update the one on the website tab?" Then: "we are going to prioritise this
website-ready version now."

The answer agreed in that conversation: yes. RHC builds the web client as
static files and publishes them on every release. The website's tab is an
iframe of a path on the website's own domain that Vercel proxies to those
files, so an RHC release updates the tab without redeploying the website.

## Decisions already made by the user

These were answered in `NETBEASTS_PLAN.md` (2026-09-27 to 2026-10) and are
copied here so nobody asks again. The user's own words are in quotes.

| Question | Answer |
|---|---|
| Where the save and identity live | Supabase, no account: "yes supabase is fine, no account, just qr sync" |
| Its own page or an iframe, and where it's hosted | "iframe is good, hosted on my website on a page" |
| Aether on the web | "has to read this from the server so need to move that to supabase etc." |
| Host-authoritative or a server | "server" |
| Supabase Realtime or a small relay | "supabase" |
| Two or more players near one creature | See [06](06-multiplayer.md), "Fights with more than two sides". In short: anyone can throw a netbeast into a fight they see, wild beasts can switch who they attack each turn, players can attack each other's netbeasts ("backstabbing"), the circling has to work for 3, 4 or more sides, and **a killing blow from a player captures the wild beast instead of killing it** |
| Who a chasing beast follows (phase 4, later) | The closest player, swapping if another player gets closer |
| Colossus with others around (phase 5, later) | A red-wall colossus is yours alone; a weekly-event boss interrupts everyone |
| Others see your mount (phase 6, later) | Yes: needs the player sprite sitting, and the mount sprites fleshed out |

"Server" and "Supabase" together mean: **Supabase Realtime carries the
messages, and a small server process of ours runs the authoritative sim** as
one more client on those channels. Supabase on its own can't run the sim:
Realtime only relays messages, and Edge Functions only answer requests and
then stop. See [06](06-multiplayer.md).

## How it fits together

```
RHC-App (this repo)                           htc (the website, other repo)
-------------------                           -----------------------------
world-core  (sim + renderer + battle rules,   Games tab -> 3rd sub-tab "Netbeasts"
             pure Kotlin, jvm + wasmJs)         <iframe src="/play/netbeasts/">
   |                                                    |
   +-- netbeasts-web (new: wasm shell,           vercel.json rewrite
   |     canvas, input, HUD, fight panel)        /play/netbeasts/*  ->  GitHub Pages
   |        |                                            ^
   |   release.sh builds it, pushes the static           |
   |   files to gh-pages of htc-downloads-rhc  ----------+
   |
   +-- netbeasts-server (new: JVM, runs World for each shared map,
         joins Supabase Realtime channels as the authority)

Supabase: save + identity + Aether (tables, RPCs), Realtime channels for multiplayer
Phone app: same save + Aether through Supabase, same multiplayer channels
```

## The steps

| Step | What | Needs | Size | Status |
|---|---|---|---|---|
| [01](01-kotlin-and-web-spike.md) | Kotlin 2.x, then a bare web page that draws the Wilds and walks | nothing | M | built 2026-11-12 (desktop Chrome; Firefox, Safari and a phone still to try) |
| [02](02-publish-and-site-tab.md) | `release.sh` publishes the web build; the website tab shows it | 01 | S | not started |
| [03](03-battle-rules-in-world-core.md) | The battle rules move into `world-core`, built for any number of sides | nothing (can run alongside 01-02) | L | not started |
| [04](04-web-client.md) | The full single-player web client: HUD, fights, sprites, controls | 01, 03 | L | not started |
| [05](05-save-identity-aether.md) | The save, the QR link between phone and browser, Aether and Nightfall in Supabase | 04 for the web side; the phone side can start any time | M | not started |
| [06](06-multiplayer.md) | The server, the Realtime protocol, fights with more than two sides | 03, 05 | L | not started |

01 and 02 come first because they're small and put something real on the
website early, which proves the hard plumbing (wasm in browsers, the publish
pipeline, the iframe on the site) before the big refactor in 03. 03 can be
done in parallel by another agent since it touches different files.

## Rules for every step

1. **The Wilds rules in `CLAUDE.md` still hold**, and matter more now that
   the web and a server run the same sim: pure Kotlin in `world-core`, fixed
   30 Hz ticks, inputs only, the sim's own seeded RNG, `DetMath` for trig, and
   anything another player must see goes in `EntitySnapshot`.
2. **One copy of the rules.** The phone, the browser and the server all run
   the same `world-core` code. Never re-implement a rule in JavaScript, and
   never leave the old Android copy behind once something moves.
3. **`DeterminismTest` is the contract between devices.** Run
   `./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest` after any sim
   change. Re-pin only on purpose. The pins also make a good **sim version**
   for the network (see [06](06-multiplayer.md)): two builds with different
   pins must not share a map.
4. **All four Android flavors keep building.** Check with
   `./gradlew compileGamersMaleNetbeastsReleaseKotlin` and the other three
   (`GamersFemale…`, `TimesaversMale…`, `TimesaversFemale…`; see
   `app/build.gradle.kts` for exact names) after any change to shared code or
   the Gradle setup.
5. **The website is someone else's repo.** Changes to htc (the tab, the
   Vercel rewrite) are listed in [02](02-publish-and-site-tab.md) under
   "The htc side". Make them in the htc workspace
   (`.../lcode/workspaces/htc`), not from here, and tell the user they're
   needed if you can't.
6. **Nothing secret in the web build.** It's public static files. The
   Supabase anon key is fine (it's public by design); the service-role key,
   the release token and anything that lets a client write another player's
   save are not. Use Row Level Security and RPCs (see [05](05-save-identity-aether.md)).
7. **Don't run `release.sh` or `zz_quicksave.txt` unless asked** (from
   `CLAUDE.md`). Step 02 adds a web-only option; that still publishes, so it
   still needs asking.

## Facts you'd otherwise have to dig for

Checked 2026-11-12.

- **Kotlin is 2.4.21** (since 01; root `rhc-android/build.gradle.kts`),
  Gradle 8.7, AGP 8.5.2. `world-core` declares `wasmJs { browser(); nodejs() }`
  and `netbeasts-web` is the browser shell. The root build turns off Kotlin's
  own Node/Yarn downloads (they clash with `FAIL_ON_PROJECT_REPOS`) and uses
  the Node and Yarn on PATH; Binaryen comes from a repository declared in
  `settings.gradle.kts`. `scripts/node-wasm.sh` is gone. `GameData` (the beast
  and trait tables) moved to `world-core` in 01.
- **`world-core` today** (`world-core/src/commonMain/.../world/`):
  `sim/World.kt` (~1050 lines), `sim/WorldMap.kt`, `sim/WorldSession.kt`
  (the `WorldSession` interface + `LocalWorldSession`), `sim/Region.kt`,
  `sim/DetMath.kt`, `render/TerrainRenderer.kt`, `go/StepBank.kt`,
  `go/StepHealing.kt`.
- **The renderer** is `TerrainRenderer(w, h, map, fovDeg)`. It draws into
  `fb: IntArray` (ARGB, one Int per pixel) with
  `render(world, cam, pitch, palette, sprites, timeMs)`. Sprites come through
  `fun interface SpriteSource` returning `Texture(w, h, px: IntArray)`. On
  Android the frame is 200 px wide in portrait (`WorldView.PORTRAIT_W`) and
  320 in landscape, scaled up. A browser canvas wants RGBA bytes, so the web
  shell converts ARGB to RGBA each frame.
- **The sim's API** for a client: `PlayerInput(lookDelta, throwCage, recall,
  letOut, forage)` per player per tick, `World.step(inputs)` returning
  `WorldEvent`s, and `World.snapshot()` / `applySnapshot()` with
  `WorldSnapshot(tick, entities, coinsGone, awake)`. Nothing is serialisable
  yet: these are plain data classes with no wire format.
- **The battle rules** are in `app/.../engines/combat/` (`PlayerTurn.kt`,
  `EnemyTurn.kt`, `BattleResolution.kt`, `PartyTraits.kt`, `CombatUtils.kt`,
  ~380 lines) as `GameActivity` extension functions, plus
  `engines/SkillEngine.kt` (~310 lines, also rolls `kotlin.random.Random`),
  `CombatState.kt` (a global object) and `GameModels.kt` (`Netbeast`,
  `GameData` beast and trait tables). They call `kotlin.random.Random`,
  `mainHandler.postDelayed`, `findViewById`, `AnimUtils`, `AudioEngine`,
  `prefs` and `printLog` (about 57 such calls). `Netbeast.isGrouchy()` reads
  `System.currentTimeMillis()`. Traits live inside the name string
  (`"[Vampiric] Cacheon"`). Callers: `GameActivity.kt`, `GameSetup.kt`,
  `gameui/BattleUI.kt`, `world/WorldBridge.kt`, `world/WorldFight.kt`.
- **The art** is autogen GIFs in `app/src/main/res/drawable-nodpi/`: 589
  files, 8.2 MB (326 `spr_*`, 180 `prop_*`, 83 `fx_*`). Android decodes them
  in `world/SpriteBank.kt`. Browsers can't reliably pull single GIF frames
  (Safari's `ImageDecoder` support is patchy), so 01/04 turn them into PNG
  sprite sheets at build time.
- **The save is Android prefs.** `GameState.kt` (`loadSaveData`) reads
  `PARTY_DATA`, `GRAVEYARD_DATA`, `FORSALE_DATA`, `MARKET_DATA` (through
  `SaveManager`), `COINS`, `NETS`, `POTIONS`, `SPRAYS`, `TRANSFUSERS`,
  `SMALL_HP_POTS`, `LARGE_HP_POTS`, `ACTIVE_PET_INDEX`, `TOTAL_EXPEDS`,
  `WALK_ORDER`, and the identity `PLAYER_ID` (a random UUID) and
  `PLAYER_NAME`. **`guardian/GuardianService.kt` also writes `PARTY_DATA`**
  (the red wall adds and kills netbeasts) while the game isn't open, so the
  save has two writers on the phone already. [05](05-save-identity-aether.md)
  deals with that.
- **Aether** is `engines/AetherEngine.kt`: `AETHER_DAY`, `AETHER_LEFT`,
  `AETHER_DAILY_LIMIT`, `CURRENT_REMNANTS`, `LAST_PLAYED_TIME`,
  `INSTALL_TIME`, and the shield pause `SYSTEM_PAUSE_UNTIL` for Nightfall.
- **Supabase today:** the Momentum leaderboard (`LeaderboardEngine.kt`, and
  `rhc-common/src/LeaderboardEngine.cpp`) writes to project
  `oannlpewujcnmbzzvklu`, table `momentum_leaders`, keyed by its own
  `device_id` UUID (a different UUID from `PLAYER_ID`). The htc website
  also uses Supabase (it gets its URL and anon key from its `/api/config`),
  and its TCG already runs two-player rooms over Supabase Realtime broadcast
  (`htc/src/js/features/cardgame/net/room.js`): a working example of the
  channel setup. **Whether htc's project is the same as RHC's is not
  checked**; see the open questions.
- **Releases** go to the public repo `frostyosty/htc-downloads-rhc`
  (`RELEASE_REPO` in `release.sh`). The website reads its releases through
  `htc/api/_utils/releases.js` for the downloads page.
- ~~**A failing test, found while writing this plan:** `BOSS_PRINT` was
  `0L`.~~ Pinned 2026-11-12 to `452232951521158191L`; JVM and wasm agree.

## Open questions

Ask each when its step starts, and propose the default.

1. **Visitors without the app** (before 04). Most people on the website
   won't have RHC on a phone. Can they play? Default: yes, as a **guest**
   with a starter party and a browser-only save, and a daily Aether of the
   install-day amount (20 minutes) tracked on the server against the guest
   id. Linking a phone later replaces the guest save with the phone's (after
   a confirm). The alternative is "link a phone to play", which keeps
   RHC's purpose (the game is the reward for the blocker) but shuts out
   everyone else.
2. **The walk-only preview from 02** (before 02). Show it to everyone or
   only to the site's admins until fights work? Default: admins only (the
   htc site already marks admin controls with `admin-button`), with a
   "coming soon" card for everyone else.
3. **Which Supabase project** (before 05). Use RHC's
   (`oannlpewujcnmbzzvklu`, which the phone already talks to) or the
   website's? Default: RHC's, since the save and Aether belong to the phone
   app; the website only needs the URL and anon key, which the iframe page
   can carry in its own config.
4. **Where the server runs** (before 06). Default: Fly.io (the user already
   deploys Beancounters there), one small always-on JVM machine near NZ
   (`syd`). It costs money while it runs.
5. **Website logins** (any time, optional). The website has its own users.
   Should a logged-in website user's Netbeasts save follow their website
   login? The user said "no account, just qr sync", so default: no; the
   iframe ignores website logins.

## Testing

- **Sim and rules:** `bash tools/world_preview/run.sh` (soak + preview PNGs)
  and `./gradlew :world-core:jvmTest :world-core:wasmJsNodeTest`.
- **Web client:** `./gradlew :netbeasts-web:wasmJsBrowserDevelopmentRun`
  (from 01) serves it locally; check Chrome, Firefox and Safari (or an
  iPhone). Wasm with garbage collection needs Chrome/Edge 119+,
  Firefox 120+, Safari 18.2+; the page says so instead of going blank on
  older browsers.
- **In the website:** after 02, the htc dev server proxies
  `/play/netbeasts/` the same way, so the iframe can be tested locally.
- **Multiplayer:** two browser tabs first, then a phone and a tab (the plan
  has always wanted this so it doesn't take two phones).
- **The phone:** all four flavors build; play a full walk and a fight on a
  phone after 03 and again after 05.

## Keeping this plan current

- When a step lands, set its Status in the table above (and in its file) to
  `built <date>`, move anything that should last (rules, constants that must
  match, commands) into `CLAUDE.md` or `rhc-android/README.md` §5.1 in the
  same change, and update `ROADMAP.md`.
- When the user answers an open question, write the answer in, with the date,
  and strike the question.
