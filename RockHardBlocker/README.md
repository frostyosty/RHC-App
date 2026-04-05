PROJECT OVERVIEW: Rock Hard Christianity (RHC) & Netbeast Safari

1. The Core Concept
Rock Hard Christianity (RHC) is a native Android application designed to completely eradicate pornographic material, illicit apps, and time-wasting websites from a user's phone.
However, instead of relying on guilt or "accountability partners" (which cause shame and friction), it uses Loss Aversion and Positive Reinforcement. The blocker is gamified through a built-in retro RPG called Netbeast Safari. The user collects, trains, and battles digital pets (Netbeasts). If they try to bypass the blocker, terrifying Boss monsters invade their game and permanently kill their pets and steal their items. It rewires the brain's dopamine loop to protect their digital ecosystem instead of destroying it.

2. The Ironclad Defense Stack (The Guardian)
We built a multi-layered, tamper-proof security system that does not require a PC to install.
The Accessibility Ninja (Screen Reader): A background service that reads the screen at 60fps using virtually zero battery. It features a 10-Language NSFW Dictionary (English, Chinese, Spanish, Russian, etc.). If it sees illicit words, it physically forces the phone to scroll away or hit the "Back" button.
The Red Wall Overlay: If the user tries to open Android Settings, alter the VPN, or remove Device Admin privileges, the Guardian instantly hijacks the screen with a massive, un-closeable red overlay.
Custom "Overcome" Lists: Users can permanently ban specific websites (e.g., youtube.com) or specific Android Apps (e.g., WeChat, TikTok, Tinder). The Guardian is context-aware: it only blocks websites if the word is found in a clickable link or URL address bar, preventing "friendly fire" if the user is just reading a book.
Anti-ADB & Battery Guard: Blocks the "Developer Options" menu so users can't uninstall it via a PC. It also detects Chinese OEM phones (Xiaomi, Huawei, Oppo) and forces the user to enable "Autostart" so the phone's battery-saver doesn't accidentally kill the blocker.
Free IP-Level DNS: Integrates with CleanBrowsing’s Adult Filter to block illicit domains at the hardware level.

3. The RPG Game Engine (Netbeast Safari)
Hidden inside the app is a fully modular, text-adventure-style RPG that reacts to the real world.
Real-World Weather & Terrain: The app silently pings an IP-Geolocation and Open-Meteo API in the background. It reads the user's real-world City, Elevation, and Humidity to mathematically calculate their Terrain (e.g., Coastal Lowlands or Mountainous) and live Weather (Rain, Snow, Storms), adjusting the game's ecosystem without ever asking for scary GPS permissions.
Habit-Based Spawning: The Guardian secretly tracks how many seconds the user spends on Social Media, Streaming, Gaming, or Tech apps. When a wild Netbeast spawns, it matches their exact real-world habits (e.g., watching YouTube spawns a Bufferoo, scrolling Twitter spawns a Chirplet).
URL Scavenger Hunts: If it is currently raining in the real world, the user can "Infuse" their Netbeast with Rain energy. If they browse healthy websites that contain the word "rain" in the URL, the Netbeast absorbs it and permanently gains damage multiplier stacks!
Concurrent Expeditions: Users can deploy their Netbeasts on 3-minute background expeditions. They scavenge for items or trigger 5-second Quick Time Events (QTEs) where wild beasts ambush them.
Offline Resolution: If the user closes the app while a Netbeast is exploring, the game calculates the math in the background. When they return, they might be greeted with an "OFFLINE AMBUSH" and thrown straight into a battle.

4. Deep Combat Mechanics
David vs Goliath Scaling: Wild Netbeasts dynamically scale their HP based on your active pet. Furthermore, a Party Penalty adds +15 HP to wild monsters for every extra beast you hoard in your party, forcing players to be strategic. Beating higher-level monsters yields massive "Goliath Bonus" payouts.
Tactical Weather & Traits: Rain makes Netbeasts slip and miss attacks. Netbeasts spawn with randomly generated traits (e.g., Slick, Glitchy, Battle-Hardened, Veteran) that alter their HP.
Potions & Gene Splicing: Potions grant dynamic buffs (Insta-Kill, Guaranteed Evasion, or Permanent Max HP increases). The "Move Tutor" allows players to pay 50 coins to rip a move off one beast and teach it to another.
The "Last Stand": If all your Netbeasts die in a boss fight, the human player is forced to step in. You get a "THROW PUNCH" button, deal 2 damage, and the Boss obliterates you for 9,999 damage.
The Flee Penalty: If a user triggers a Red Wall block, they can hit "FLEE" to escape the boss. But when they reopen the app... their camp is devastated. The boss permanently kills their 3 weakest Netbeasts and steals 50% of their inventory items.

5. The Economy & Frictionless Multiplayer
The Safari Lodge: A local market where users can buy and sell Nets, Sprays, and Potions using "Focus Coins."
The Grouchy Debuff: If you buy a Netbeast from the market, it remembers. It receives a 24-hour [Grouchy] status effect where it has a 30% chance to cross its arms and disobey your attack commands in battle.
Ghost Multiplayer Setup: The moment the app is opened, it silently generates a secure UUID and a retro Hacker Name (e.g., Neon_Paladin_42). This lays the groundwork for our upcoming Cloud Market, where users can buy and sell beasts with other real players in their city without ever needing to create an account or log in.

6. UI / UX Polish & Engineering
Custom XML Dialogs: We stripped out all ugly Android system popups. Everything runs on beautifully styled, dark-mode Native XML modals.
Street Fighter Arena: Battles feature dual health bars and colored placeholders that physically slide onto the screen, shake when hit, lunge when attacking, evade diagonally, and fade out when killed.
Haptic Feedback: Native hardware vibration is tied to combat. Small buzzes for hits, heavy buzzes for crits, and massive vibrations when a Netbeast is killed.
Dynamic App Icons: Users can toggle a setting that physically morphs their phone's home screen icon from the gritty "RHC" logo into a bright orange "Netbeasts" gaming logo.
Master Debug Console: A dedicated developer UI that outputs raw API location data, live battery health/voltage, exact habit tracking percentages, and a 40-character contextual readout of exactly why the Guardian triggered a block.
Modular Architecture: We took a massive "God Class" file and successfully refactored the codebase into distinct, professional engines (CombatEngine.kt, ExploreEngine.kt, WeatherEngine.kt, SaveManager.kt, GameUI.kt) to ensure lightning-fast compile times and zero memory leaks.



🗂️ THE MASTER ARCHITECTURE LIST
✅ GameModels.kt -> The Data. Contains Netbeast class and GameData lists (Beasts, Traits, Bosses).
✅ SaveManager.kt -> The Hard Drive. Handles all SharedPreferences string parsing.
✅ DialogUtils.kt -> The Modals. The sleek XML pop-up generator.
✅ AnimUtils.kt -> The Graphics. View shakes, lunges, and fades.
✅ WeatherEngine.kt -> The Network. Fetches IP Geolocation and Open-Meteo data silently.
✅ CombatEngine.kt -> The Math. Handles executePlayerMove, triggerEnemyCounterAttack, and endBattle.
✅ ExploreEngine.kt -> The World Clock. Handles performGlobalTick, spawnWildQTE, and checkOfflineExpeditions.
✅ GameUI.kt -> The Screen. Handles updatePartyScreen, updateBagScreen, setUIState, setupShop, and setupEquipPanel.
⏳ GameState.kt (NEW) -> The Memory. Handles loadSaveData, wipeCorruptSave, processFleePenalty, and generateMarket.
⏳ GameSetup.kt (NEW) -> The Wires. Extracts the massive button click-listeners out of onCreate.
⏳ GameActivity.kt (SLIMMED) -> The Hub. Will now only hold the variables and the onCreate lifecycle.




cd /workspaces/RHC-App/RockHardBlocker
./gradlew assembleRelease
cp app/build/outputs/apk/rhc/release/app-rhc-release.apk ../rhc.apk

cd /workspaces/RHC-App
TAG="v$(date +%Y%m%d%H%M%S)"
GITHUB_TOKEN="" GH_TOKEN="" gh release create "$TAG" ./rhc.apk \
  --repo frostyosty/htc-downloads-rhc --title "Dev Build $TAG - Link Restoration" \
  --notes "message"