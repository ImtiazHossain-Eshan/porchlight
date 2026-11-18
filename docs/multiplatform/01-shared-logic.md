# Phase 1: Shared game logic

A Kotlin Multiplatform module, `:shared`, now holds the game rules. Targets: Android
(`com.android.kotlin.multiplatform.library`), `iosArm64`, `iosSimulatorArm64`, `iosX64` and `wasmJs`.
The Android app depends on it; its package stays `app.porchlight.game`, so nothing else changed name.

## What moved to `shared/src/commonMain`

| File | What it holds |
|---|---|
| `Game.kt` | Levels, chapters, rules, the round engine, deals, daily seed, flight log encode/decode, saved round state |
| `Peek.kt` | Peek timings and the countdown |
| `Tally.kt` | Results tally, loss notes, star hints |
| `Progress.kt` | Badges, `Progress`, `settleRound`, XP, stars, `DailyCard`, `dailyShare`, the six-nights migration |
| `MothId.kt` | The moth list (split out of `Moths.kt`, whose drawing stays in the app) |
| `Format.kt` (new) | `grouped()` and `oneDecimal()`, replacing `String.format(Locale.US, ...)` |
| `Dates.kt` (new) | `todaySeed()` and `cardDate()`, with an `expect` local date per platform |

`Store` (the SharedPreferences save) stayed in the app as `Store.kt`; it moves behind a storage
interface in phase 3. `String.format` and `Locale` are gone from the shared code and from
`RoundScreen.kt`, `Tally.kt` and `Theme.kt`. `Calendar` now lives only in the Android `actual`.

`Math.floorMod` became Kotlin's `mod`, which gives the same answer for the shadow moth and the loss tip.

## Platform pieces

| | Android | iOS | Web |
|---|---|---|---|
| `localDay()` | `Calendar` (as before) | `NSCalendar.currentCalendar` | JS `Date` |
| `shortDate()` | `String.format(Locale.UK, "%ta %<te %<tb")` (as before) | English names, weekday by arithmetic | same as iOS |

Android keeps its own date formatter on purpose: Java's UK locale writes September as "Sept", and
the share card should not change by a letter.

## Tests

| Where | Runs on | Tests |
|---|---|---|
| `shared/src/commonTest` (kotlin.test) | Android JVM, iOS simulator, wasm (Node) | 137: Game, Peek, Progress, Tally, FlightLog, Balance, and a new FormatTest |
| `shared/src/androidHostTest` (JUnit) | JVM only | 13: LevelCurveTest, LevelTuner (3 skipped unless `PORCH_TUNE` is set), Simulator, and a new FormatParityTest that checks the new formatters against `String.format` for every tenth of a second up to two minutes |
| `app/src/test` | JVM | 1: BadgeArtTest (the badge-picture check, which needs the drawing code; it was an assertion in ProgressTest) |

`./gradlew testDebugUnitTest` still runs everything on the JVM: `:shared` has a task of that name that
runs `testAndroidHostTest`. Total 151, 3 skipped (was 144, 3 skipped).

Test names lost their commas (Kotlin/Native doesn't allow them in names), and JUnit's
message-first assertions became kotlin.test's message-last ones. One check used Java reflection to
list `LogEntry.Match`'s fields; it now checks the data class's printed form, which lists the same.

## Build changes

- `settings.gradle.kts`: `:shared`, and Ivy repositories for Node.js, Yarn and Binaryen (the wasm
  toolchain), since modules may not declare repositories.
- `build.gradle.kts`: the multiplatform plugins (Kotlin 2.4.21, AGP 9.4.1), and a hook that stops the
  Kotlin plugin from adding those repositories itself.
- `gradle.properties`: `kotlin.native.ignoreDisabledTargets=true` (iOS test runs are skipped on Windows).
- `.github/workflows/ios.yml` (new): `:shared:iosSimulatorArm64Test` on `macos-latest`.
- `.github/workflows/android.yml`: uploads the shared test report on failure too.

## Report

- **Checks:** `testDebugUnitTest lintDebug assembleDebug` pass: 151 tests (3 skipped), lint 0 errors /
  11 warnings, APK 12,735,485 bytes. `:shared:wasmJsNodeTest` passes 137 tests.
  `:shared:compileKotlinIosArm64`, `compileKotlinIosX64` and `compileTestKotlinIosSimulatorArm64`
  succeed on Windows. The installed debug build kept its save and shows the same daily card
  ("Thu 19 Nov", "8/8 pairs · 19.0 s left · 6 misses").
- **Stubbed:** nothing.
- **Not verified:** the iOS simulator test run (`:shared:iosSimulatorArm64Test`) needs macOS; it is
  wired into `ios.yml` and has not run yet.
