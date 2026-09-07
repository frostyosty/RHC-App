# PROJECT OVERVIEW: Rock Hard Christianity (RHC)

Rock Hard Christianity (RHC) is a cross-platform environment protector and time-reclamation engine with native Android (Kotlin) and Windows (C++/Win32) implementations.

## 1. Architecture

* **Android:** Kotlin/Gradle, four flavors — Gamers/Timesavers × Male/Female — centered around `MainActivity.kt`.
* **Windows:** Native C++/Win32 using Win32 API, GDI, Microsoft UI Automation, SQLite, and WinHTTP.
* **Shared Logic:** `rhc-common/` mirrors Android persistence and engine behavior through a custom SQLite-based `DatabaseManager`.

## 2. Protection & Blocking

### Android

* `GuardianService` and `ShieldRuleEngine` scan accessibility/DOM content for hard and soft block triggers.
* Dynamically detects installed apps and normalizes package names to catch variants such as Lite editions.
* Monitors launchers and uninstall dialogs to enforce protection.
* A 15-minute watchdog heartbeat detects service termination and issues a **SHIELD DOWN** notification.

### Windows

* Native UI Automation scans browser tabs, URLs, and window titles.
* `SendInput` navigates away from blocked sites.
* Hosts-file sinkholing blocks configured domains.
* A background watchdog monitors Task Manager.
* Shutdown/startup protection maintains the blocker across restarts.

## 3. Mandatory Cooldown / Self-Destruct

Users can select a one-way 3, 5, 7, or 14-day cooldown before disabling protection.

After expiration:

* Android permits access to protected settings.
* Windows removes the blocker executable and restores the hosts file.

## 4. Momentum Core

Momentum converts blocked distractions into reclaimed time rather than rewarding repeated blocking.

* Daily yields are based on the user's blocklist.
* Urge farming produces no points.
* Earned Momentum gradually evaporates after two hours.
* Anonymous UUIDs and scores sync to Supabase using RLS through Android `HttpURLConnection` and Windows `winhttp.dll`.

## 5. Netbeast Safari — Gamers

* Retro RPG layer built around serialized 19-variable Netbeast data.
* Dynamic combat with traits such as Vampiric, Thick-Skinned, Spiked, and Elusive.
* Weather/GPS systems influence combat and recovery mechanics.
* Fleeing the Red Wall can permanently kill three weakest Netbeasts and trigger a 15-minute Demon Domain lockout.

## 6. UI & Rendering

* **Android:** Native dark-mode Command Center with Momentum, tasks, recovery lists, and leaderboard.
* **Windows:** Native Win32/GDI dark-mode interface with tactile custom buttons and system-tray integration.

## 7. RHC Sprite Studio V9

A local zero-dependency Python/HTML5 tool for the Gamers flavor, providing AI sprite generation, background stripping, 8-bit resizing, GIF tweening, onion skinning, and audio synthesis.

## 8. Critical Blocklist Parsing Bug — FIXED 08/09/2026

A parsing mismatch caused website blocks to fail.

`MainActivity.kt` stores entries as:

`domain | date | triggers | display name`

but `ShieldRuleEngine.kt` incorrectly read index `1` as the domain, resulting in the date being used as the blocking target.

**Fix:** `ShieldRuleEngine.kt` now reads the actual domain field, and `MainActivity.kt` correctly parses application entries. This restores reliable blocking for domains such as `m.youtube.com`.




---

# BUILD / RELEASE COMMANDS

## Male Momentum Only

cd /workspaces/RHC-App/RockHardBlocker
./gradlew assembleTimesaversMaleMomentumRelease
cp app/build/outputs/apk/timesaversMaleMomentum/release/app-timesaversMaleMomentum-release.apk ../rhc_momentum_m.apk

cd /workspaces/RHC-App
unset GITHUB_TOKEN GH_TOKEN
TAG="v$(date +%Y%m%d%H%M%S)"

gh release create "$TAG" ./rhc_momentum_m.apk \
  --repo frostyosty/htc-downloads-rhc \
  --title "Dev Build $TAG - Flawless UI & Anti-Farming Logic" \
  --notes "Fixed the Setup Paradox, removed VPN penalties, added 60-second setup pass, and fixed Momentum UI padding."






## Desktop

cd /workspaces/RHC-App/rhc-desktop
./build.sh

cd /workspaces/RHC-App
unset GITHUB_TOKEN GH_TOKEN
TAG="v$(date +%Y%m%d%H%M%S)-desktop"

gh release create "$TAG" ./rhc-desktop/rhc_desktop.exe \
  --repo frostyosty/htc-downloads-rhc \
  --title "Desktop Build $TAG" \
  --notes "Latest build of the Win32 C++ Momentum Core."


## All Four Android Flavors

cd /workspaces/RHC-App/RockHardBlocker

./gradlew assembleGamersMaleNetbeastsRelease
./gradlew assembleGamersFemaleHomevisitsRelease
./gradlew assembleTimesaversMaleMomentumRelease
./gradlew assembleTimesaversFemaleMomentumRelease

cp app/build/outputs/apk/gamersMaleNetbeasts/release/app-gamersMaleNetbeasts-release.apk ../rhc_netbeasts.apk
cp app/build/outputs/apk/gamersFemaleHomevisits/release/app-gamersFemaleHomevisits-release.apk ../rhc_homevisits.apk
cp app/build/outputs/apk/timesaversMaleMomentum/release/app-timesaversMaleMomentum-release.apk ../rhc_momentum_m.apk
cp app/build/outputs/apk/timesaversFemaleMomentum/release/app-timesaversFemaleMomentum-release.apk ../rhc_momentum_f.apk

cd /workspaces/RHC-App
unset GITHUB_TOKEN GH_TOKEN
TAG="v$(date +%Y%m%d%H%M%S)"

gh release create "$TAG" \
  ./rhc_netbeasts.apk \
  ./rhc_homevisits.apk \
  ./rhc_momentum_m.apk \
  ./rhc_momentum_f.apk \
  --repo frostyosty/htc-downloads-rhc \
  --title "Dev Build $TAG - Flawless UI & Anti-Farming Logic" \
  --notes "Fixed the Setup Paradox, removed VPN penalties, added 60-second setup pass, and fixed Momentum UI padding."
