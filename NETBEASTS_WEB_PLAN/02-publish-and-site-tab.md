# 02. Publish on release; the website tab shows it

**Needs:** [01](01-kotlin-and-web-spike.md) · **Size:** S
**Read first:** [README](README.md); `release.sh`; `CLAUDE.md` → "Build & release"
**Status:** not started

## Goal

Every RHC release also publishes the web client, and the third tab of the
website's Games page shows the newest one without the website being
redeployed. This is the answer to the user's "when I update it in RHC, can it
update the one on the website tab?"

## Where the files go

**GitHub Pages on `frostyosty/htc-downloads-rhc`**, the public repo the
releases already go to. A `gh-pages` branch, served at
`https://frostyosty.github.io/htc-downloads-rhc/`. Layout:

```
netbeasts/
  index.html            tiny loader, never cached long
  version.json          {"version":"v1.4.2","sim":"<pin hash>","built":"..."}
  v1.4.2/               immutable: the wasm, the .mjs, the sprite sheets
  v1.4.1/               the last few versions kept, so a tab that loaded
                        an older index.html still finds its files
```

`index.html` reads `version.json` (with `cache: "no-store"`) and loads that
version's folder. Putting every build in its own folder means the wasm and
the JS that goes with it can never get out of step in someone's cache, and
the folders can be cached forever. Keep the last 3 versions and delete older
ones on each publish, so the branch doesn't grow without end (the sprite
sheets are a few MB each time).

Pages has to be switched on for the repo once (Settings → Pages → branch
`gh-pages`), by the user. Tell them.

## `release.sh`

- Add a target **"Web (Netbeasts in the browser)"** to the menu, and include
  it in option `1` (everything), since the user's default is option 1.
- Build: the sprite-sheet script from 01, then
  `./gradlew :netbeasts-web:wasmJsBrowserDistribution`.
- Publish: check out `gh-pages` of `htc-downloads-rhc` into a temp dir, copy
  the build into `netbeasts/$TAG/`, write `version.json`, prune old versions,
  commit "netbeasts web $TAG", push. Use the same `gh` login the script already
  checks. It runs after the GitHub release is created, so a failed build
  never publishes half a release.
- A web-only run (only the web target picked) still bumps the tag and
  quicksaves like the others, so the version on the website always matches a
  tag in this repo.
- Follow the script's style (emoji status lines, `set -euo pipefail`) and run
  `shellcheck release.sh` after editing.

## The htc side

These changes are in the htc repo (`.../lcode/workspaces/htc`), not here.
Make them from that workspace, or hand this list to the user.

1. **`vercel.json`:** a rewrite **before** the catch-all
   `{"source": "/(.*)", "destination": "/index.html"}`:
   ```json
   { "source": "/play/netbeasts/:path*",
     "destination": "https://frostyosty.github.io/htc-downloads-rhc/netbeasts/:path*" }
   ```
   Vercel proxies it, so the browser sees the game as part of the website's
   own domain. That matters: a cross-site iframe gets partitioned or blocked
   storage (Safari especially), and same-site doesn't.
2. **`vite.config.js`:** the site is a PWA (`VitePWA`, `registerType:
   'autoUpdate'`). Its service worker answers navigations with the site's
   `index.html`, which would replace the game's page inside the iframe. Add
   `workbox: { navigateFallbackDenylist: [/^\/play\//] }` to the `VitePWA`
   options, and keep `/play/` out of the precache.
3. **Dev server:** add a `server.proxy` entry in `vite.config.js` for
   `/play/netbeasts` pointing at the same Pages URL (or at the local
   `wasmJsBrowserDevelopmentRun` server while developing the game).
4. **The tab:** `src/js/features/game/index.js` has the third button disabled
   ("Netbeasts (soon)"). Give it `data-tab="game-netbeasts"`, and in
   `handleGameSubTab` make an iframe:
   `<iframe src="/play/netbeasts/" allow="fullscreen; autoplay" style="width:100%;height:100%;border:0">`.
   Remove it when switching away (stops the game and its Aether).
   Add `{ id: 'game-netbeasts', label: 'Netbeasts', ... }` to the Games
   `subTabs` in `src/js/config/navigation.js`.
   Per open question 2 in the README, gate it to admins until fights work.
5. **Talking to the iframe:** `postMessage` with a fixed `type` prefix
   `netbeasts:`, checking `event.origin` is the site's own origin on both
   sides. To start with: the page sends `netbeasts:theme` (light/dark); the
   game sends `netbeasts:fullscreen` (ask the page to go fullscreen, which
   works better on phones than fullscreen from inside an iframe) and
   `netbeasts:ready`. Write down every message here as it's added.

## Ask

- Open question 2 (the walk-only preview: admins only, or everyone). Default
  admins only.

## Interfaces (for later steps)

- The URL is `/play/netbeasts/` on the website; GitHub Pages is only behind
  it. Nothing in the game should hard-code the Pages URL.
- `version.json`'s `sim` field: the build's determinism pins, hashed (see
  [06](06-multiplayer.md)).
- The `netbeasts:` message list above.

## Done when

- A web-only `release.sh` run (with the user's OK) puts a new version on the
  website tab within about 10 minutes (Pages caches for 10 minutes), with no
  htc deploy.
- An open tab of the old version keeps working until reloaded.
- The iframe works on desktop Chrome and on an iPhone, and the site's PWA
  doesn't hijack it.
