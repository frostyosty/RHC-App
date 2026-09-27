# 01. Groundwork

**Needs:** nothing · **Size:** S · **Track:** both
**Read first:** [README](README.md). Nothing else.

## Goal

The Homevisits APK stops being a copy of Netbeasts. It opens its own placeholder
home screen, earns and spends Momentum instead of coins, and has the
`homevisits-core` module with a test that runs. The other three APKs don't change,
except for the female/male fix in part 1.

## 1. One place that says which flavor this is

Flavor checks are spread around the code as
`BuildConfig.FLAVOR.lowercase().contains(...)`, and `"female".contains("male")` is
true. So both female APKs think they're the male ones:

- `MainMomentumUI.populateSpendingTasks` and `MomentumActivity.populateSpendingTasks`
  show the male task list ("Chop Wood", "Workout / Lift"). The female list ("Yoga /
  Stretch", "Skincare Routine") is never seen.
- `LeaderboardEngine.getDisplayName` gives new female players the male nouns
  ("Spartan", "Wolf"). The name is saved once (`LEADERBOARD_NAME`), so fixing this
  won't rename anyone.

Add `app/src/main/java/com/rockhard/blocker/Flavor.kt`, and use it everywhere:

```kotlin
object Flavor {
    private val f = BuildConfig.FLAVOR // "gamersMaleNetbeasts", "gamersFemaleHomevisits", ...
    val isGamers = f.startsWith("gamers")
    val isFemale = f.contains("Female")
    val isHomevisitsApk = f == "gamersFemaleHomevisits"
    const val HOMEVISITS_ACTIVITY = "com.rockhard.blocker.homevisits.HomevisitsActivity"
    private const val HOMEVISITS_READY = false // true once step 04 ships (see the Ask)

    /** Homevisits is the game in this APK (before it's ready, only with the preview switch on). */
    fun homevisits(prefs: SharedPreferences) =
        isHomevisitsApk && (HOMEVISITS_READY || prefs.getBoolean("HV_PREVIEW", false))

    /** Momentum is the currency: Timesavers, and Homevisits once it's on. */
    fun usesMomentum(prefs: SharedPreferences) = !isGamers || homevisits(prefs)
}
```

Find every check with `rg -n 'BuildConfig.FLAVOR' rhc-android/app/src/main`. Today
the checks are in `MainActivity`, `MainMomentumUI`, `MomentumActivity`,
`LeaderboardEngine` and `GuardianService`. Line numbers move a lot in these files,
so go by function name.

## 2. What the Netbeasts parts do in Homevisits

Wherever "gamers" really means Netbeasts, Homevisits needs its own answer:

| Where | Netbeasts does | Homevisits does |
|---|---|---|
| `MainActivity.onCreate`, the jump when `LAUNCH_GAME_DEFAULT` is on | opens `GameActivity` | opens `HomevisitsActivity` |
| The Safari card (`llSafariCard`, `btnGame` in `activity_main.xml`) | "OPEN NETBEAST SAFARI" | "OPEN HOMEVISITS", with today's Momentum on it |
| `MomentumEngine.resetDailyIfNeeded` in `MainActivity` | isn't called | is called, as in Timesavers |
| The Momentum card (`llMomentumContainer`, the Timesavers task list) | hidden | hidden: Homevisits spends Momentum in its own catalogue |
| Settings (`openSettingsMenu`) | "Set Netbeasts as Default Home App", the 3D switch, the location button | "Set Homevisits as Default Home App"; no 3D switch or location button; the hidden "Homevisits preview" switch |
| The reward for finishing setup (the Test Filter button in `MainActivity`) | a legendary netbeast | Momentum, as in Timesavers |
| `GuardianService.handleReward` (the first time an urge is overcome) | an orphaned netbeast | Momentum, as in Timesavers |
| `GuardianService.triggerBossInvasion` | the invasion wall, where Defend starts a fight | the Timesavers "Momentum gained" wall for now (step 12 gives it its own look); with the game set as default, it opens `HomevisitsActivity` |
| The wording in `AetherEngine.nightfallReason` and `closedReason` | "The Wilds are closed", "Aether depleted" | "Visiting hours start again at 7:00", "No more visiting hours today" |
| The `GameLauncher` alias in `AndroidManifest.xml` | label "Netbeasts", icon `@mipmap/ic_game` | its own label and icon (part 3) |

Homevisits uses Aether as it is for its daily play time and calls it **visiting
hours** on screen. Each APK has only one game, so the prefs keys (`AETHER_DAY`,
`AETHER_LEFT`) don't clash.

## 3. The flavor's own source set

Everything that only Homevisits uses goes under `app/src/gamersFemaleHomevisits/`,
so the other APKs never build it:

- `AndroidManifest.xml` declares `.homevisits.HomevisitsActivity` (not exported,
  `singleTask`, portrait). The camera and ARCore are added in step 02.
- `java/com/rockhard/blocker/homevisits/HomevisitsActivity.kt` is the placeholder
  home screen: a title, today's Momentum, and "Your first visitor is on the way". It
  leaves with a toast when `AetherEngine.closedReason` says the game is closed, the
  way `GameActivity` does. It spends visiting hours only while a visit is open
  (there are none yet), not while this screen is up.
- `main` starts it by name with `Intent().setClassName(context, Flavor.HOMEVISITS_ACTIVITY)`.
  That way nothing in `main` refers to a class that only one flavor has, and the
  other flavors need no stubs.
- **The launcher alias.** In the main manifest, set the `GameLauncher` label to
  `@string/game_launcher_label`. Give every flavor a
  `resValue("string", "game_launcher_label", ...)` in `app/build.gradle.kts`
  ("Netbeasts" for the male Gamers flavor; see the Ask for Homevisits). Give
  Homevisits its own `res/mipmap-*/ic_game.png`, made with the icon generator in
  `tools/`.

## 4. The core module

- `rhc-android/homevisits-core/build.gradle.kts`: copy `world-core`'s (Kotlin
  Multiplatform), with only the `jvm` target for now. A `wasmJs` target can be
  added later if the web client ever gets Homevisits.
- `settings.gradle.kts`: `include(":homevisits-core")`.
- `app/build.gradle.kts`: `add("gamersFemaleHomevisitsImplementation", project(":homevisits-core"))`,
  so only this APK carries the module.
- `commonMain` uses no libraries at all. `tools/homevisits_sim` compiles it with the
  bare Kotlin compiler, the way `tools/world_preview` compiles `world-core`, and
  README rule 9 keeps it pure.
- The package is `com.rockhard.blocker.homevisits`. Add two basics that every later
  step uses:
  - `HomeClock(day: Int, minute: Int)`: the day number (days since a fixed date,
    local time) and the minute of the day. The app passes it in. The core never
    reads a clock.
  - `Rng`: a wrapper over `kotlin.random.Random(seed)` with named streams
    (`rng.stream("visits", day)`). That way, adding randomness to one system doesn't
    shift another system's rolls. The Wilds' coins use their own RNG for the same
    reason.
- Add one test, run with `./gradlew :homevisits-core:jvmTest`.

## 5. Docs

- `CLAUDE.md`: add `rhc-android/homevisits-core` to the table at the top.
- `rhc-android/README.md`: §1 lists the four flavors. Add a §5.2 "Homevisits
  (Gamers Female)" that says what's built, and add to it with each step.

## Ask

- **Keep Netbeasts in this APK until the first visit (step 04) is playable?**
  Decided by the user (2026-09-27): **no**. Switch to Homevisits immediately, from
  step 01 onwards. Remove the `HOMEVISITS_READY` flag and the `HV_PREVIEW` switch.
  `Flavor.homevisits(prefs)` becomes just `isHomevisitsApk`. The APK shows the
  placeholder home screen from the moment step 01 ships.

  Update the `Flavor` object accordingly:
  ```kotlin
  fun homevisits() = isHomevisitsApk
  fun usesMomentum() = !isGamers || homevisits()
  ```
  (No `prefs` parameter needed once the flag is gone.)
- **The launcher name.** Default: "Homevisits".

## Done when

- All four flavors compile:
  `cd rhc-android && ./gradlew compileGamersMaleNetbeastsReleaseKotlin compileGamersFemaleHomevisitsReleaseKotlin compileTimesaversMaleMomentumReleaseKotlin compileTimesaversFemaleMomentumReleaseKotlin`.
- `./gradlew :homevisits-core:jvmTest` passes.
- On the emulator (README, Testing), with the preview switch on:
  - the Homevisits APK opens the placeholder from the card, and as the default home
    app;
  - a blocked app gives Momentum, not an invasion.
- Timesavers Female shows the female task list.
- The male APKs behave as before.
