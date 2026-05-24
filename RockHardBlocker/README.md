PROJECT OVERVIEW: Rock Hard Christianity (RHC)

Rock Hard Christianity (RHC) is a cross-platform (Native Android & Native Windows C++) environment protector and time-reclamation engine. It operates in two distinct build flavors:
Netbeast Safari (Gamers): Disguises a military-grade blocker inside a retro 32-bit RPG. Bypassing the blocker spawns terrifying Bosses that permanently kill the player's Netbeasts.
Momentum Core (Time-Savers): A sleek productivity dashboard that converts blocked urges into an "evaporating" time currency, incentivizing users to spend reclaimed time on real-world tasks and rank on a global leaderboard.
1. Multi-Platform Architecture & Modularization
The project uses a unified logic architecture ported across two entirely different native stacks:
Mobile (Kotlin / Android): Compiled via Gradle into 4 distinct flavors (Gamers/Timesavers, Male/Female). Features a unified "Command Center" dashboard (MainActivity.kt).
Desktop (Pure C++ / Win32): A zero-dependency, lightning-fast Windows .exe utilizing the Win32 API, GDI rendering, and Microsoft UIAutomation.
Shared Engine (rhc-common/): The C++ desktop app perfectly mirrors the Android logic by utilizing a custom DatabaseManager wrapping sqlite3.c, mimicking Android's SharedPreferences for 1:1 logic translation of the ShieldRuleEngine and MomentumEngine.
2. The Ironclad Security Stack (Mobile & Desktop)
Mobile (GuardianService & Rules Engine):
The Accessibility Ninja: Scans the DOM. Triggers on Hard words (instant drop) or Soft words (requires 3 distinct triggers).
Fuzzy App Matching: Dynamically maps user-installed apps via PackageManager and strips developer tags (e.g., com.instagram.android becomes instagram) to automatically block "Lite" and variant apps.
The Xiaomi/MIUI Nuke: Bypasses proprietary Chinese task killers by aggressively monitoring all Launchers. If an uninstall confirmation popup is detected, the Red Wall slams down instantly, overriding all setup passes.
The Watchdog Heartbeat: An AlarmManager pings every 15 minutes. If the OS kills the service, it fires a high-priority "SHIELD DOWN" lock-screen notification.
Windows Desktop (C++ System Interceptors):
UIA Scanner: Uses native Microsoft UI Automation to read browser tabs, URL bars, and window titles without triggering Antivirus heuristic flags. Injects Alt+Left Arrow via SendInput to physically back the user out of illicit sites.
Network Sinkholing: Directly parses and rewrites C:\Windows\System32\drivers\etc\hosts to instantly sever connections to user-defined blocklists.
Taskmgr Assassin: A background thread that identifies Taskmgr.exe and instantly fires a WM_CLOSE message, making the app unkillable by standard means.
Safe Mode Cloak (Dead Man's Switch): Hooks the WM_QUERYENDSESSION shutdown event. On restart, it hides the original executable and disguises itself in the startup registry as SyncServices.exe to evade Safe Mode uninstalls, uncloaking only on a normal boot.
3. The "Self-Destruct" System Override
To prevent "Lock-in Panic" without enabling 3 AM relapses, the system features a one-way mandatory cooldown for uninstallation or pausing.
Users select a 3, 5, 7, or 14-day delay using a one-way slider (cannot be decreased).
Once the countdown expires, Android allows access to the Settings app, and Windows executes a silent CMD script that physically deletes rhc_desktop.exe from the hard drive and wipes the hosts file.
4. Momentum Core (Time-Savers Engine)
Smart Daily Yields: Eliminates "Urge Farming" by awarding 0 points for hitting the Red Wall. Instead, users gain a passive daily time yield based on their blocklist (e.g., blocking TikTok = +45 mins/day, Snapchat = +15 mins/day).
Evaporation: Earned time decays. After 2 hours, 1 minute of Momentum evaporates every 5 minutes, enforcing urgent, proactive task execution.
Global Supabase Leaderboards: A serverless REST integration using Android's HttpURLConnection and Windows' native winhttp.dll. Scores and Anonymous UUIDs are synced safely using Row Level Security (RLS).
5. Netbeast Safari (Gamers RPG Engine)
The 19-Variable Netbeast String: Zero-overhead data serialization. (Name, Type, HP, MaxHP, Moves, Amulets, Traits, etc.).
Dynamic RPG Combat: CombatState.kt tracks transient statuses. Modifiers include [Vampiric], [Thick-Skinned], [Spiked], and [Elusive].
Weather & GPS Rescues: Pings Open-Meteo silently. Weather dictates combat buffs (e.g., Storm = +50% Dmg). Dead Netbeasts are captured by Poachers at real-world GPS offset suburbs; the player must physically travel there to win them back.
The Flee Penalty: Fleeing the Red Wall permanently kills the player's 3 weakest Netbeasts and enforces a 15-minute "Demon Domain" OS-level lockout where the app cannot be opened.
6. UI & Rendering
Mobile (Command Center): A unified, dark-mode dashboard housing the Evaporating Bar, Custom Spend Tasks, Overcome Lists, and Global Leaderboard in a single view. Uses native XML <layer-list> shapes for 3D tactile buttons.
Desktop (Pure Win32 GDI): Zero-bloat native Window rendering. Implements custom Window Subclassing to capture WM_MOUSEMOVE and WM_LBUTTONDOWN, creating tactile, color-shifting dark-mode buttons that physically depress when clicked. Embedded System Tray integration with a hidden Developer Backdoor.
7. Tooling: RHC Sprite Studio V9 (For Gamers Flavor)
A zero-dependency Python/HTML5 suite running locally.
Features AI Text-to-Sprite (via pollinations.ai) with mathematical white-background stripping and nearest-neighbor 8-bit resizing.
Auto-Tweening GIF timelines, Onion Skinning, and 8-bit Audio Synthesizer for combat SFX.








male momentum only

cd /workspaces/RHC-App/RockHardBlocker

./gradlew assembleTimesaversMaleMomentumRelease

cp app/build/outputs/apk/timesaversMaleMomentum/release/app-timesaversMaleMomentum-release.apk ../rhc_momentum_m.apk

cd /workspaces/RHC-App
TAG="v$(date +%Y%m%d%H%M%S)"
GITHUB_TOKEN="" GH_TOKEN="" gh release create "$TAG" \
  ./rhc_momentum_m.apk \
  --repo frostyosty/htc-downloads-rhc \
  --title "Dev Build $TAG - Flawless UI & Anti-Farming Logic" \
  --notes "Fixed the Setup Paradox, removed VPN penalties, added 60-second setup pass, and fixed Momentum UI padding."








cd /workspaces/RHC-App/RockHardBlocker

# 1. Compile all four flavors
./gradlew assembleGamersMaleNetbeastsRelease
./gradlew assembleGamersFemaleHomevisitsRelease
./gradlew assembleTimesaversMaleMomentumRelease
./gradlew assembleTimesaversFemaleMomentumRelease

# 2. Copy and rename them to match the website's JS fileMap
cp app/build/outputs/apk/gamersMaleNetbeasts/release/app-gamersMaleNetbeasts-release.apk ../rhc_netbeasts.apk
cp app/build/outputs/apk/gamersFemaleHomevisits/release/app-gamersFemaleHomevisits-release.apk ../rhc_homevisits.apk
cp app/build/outputs/apk/timesaversMaleMomentum/release/app-timesaversMaleMomentum-release.apk ../rhc_momentum_m.apk
cp app/build/outputs/apk/timesaversFemaleMomentum/release/app-timesaversFemaleMomentum-release.apk ../rhc_momentum_f.apk

# 3. Create the release and upload ALL 4 APKs at once
cd /workspaces/RHC-App
TAG="v$(date +%Y%m%d%H%M%S)"
GITHUB_TOKEN="" GH_TOKEN="" gh release create "$TAG" \
  ./rhc_netbeasts.apk \
  ./rhc_homevisits.apk \
  ./rhc_momentum_m.apk \
  ./rhc_momentum_f.apk \
  --repo frostyosty/htc-downloads-rhc \
  --title "Dev Build $TAG - Flawless UI & Anti-Farming Logic" \
  --notes "Fixed the Setup Paradox, removed VPN penalties, added 60-second setup pass, and fixed Momentum UI padding."






  # Navigate to the Desktop project directory
cd /workspaces/RHC-App/rhc-desktop

# 1. Run the build script
./build.sh

# 2. Navigate back to the root and create the GitHub Release
cd /workspaces/RHC-App
TAG="v$(date +%Y%m%d%H%M%S)-desktop"

# IMPORTANT: Make sure your GITHUB_TOKEN is set
gh release create "$TAG" \
  ./rhc-desktop/rhc_desktop.exe \
  --repo frostyosty/htc-downloads-rhc \
  --title "Desktop Build $TAG" \
  --notes "Latest build of the Win32 C++ Momentum Core. Includes all server-side logic from the Android build."