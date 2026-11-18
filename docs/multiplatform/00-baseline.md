# Phase 0: Android baseline

Recorded on the `multiplatform` branch, cut from `levels-50` at `423f47c` (Version 0.6.6), before any change.

## Checks

| Check | Result |
|---|---|
| `./gradlew testDebugUnitTest` | 144 tests, 3 skipped (the tuner tests), 0 failures |
| `./gradlew lintDebug` | 0 errors, 11 warnings |
| `./gradlew assembleDebug` | `app-debug.apk`, 12,673,884 bytes |
| Toolchain | Gradle 9.6.0, AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, JBR 21 |

## Screenshots

Emulator `emulator-5554`, 1344 x 2992, launched with `--ei level 12 --ei setXp 3000`.
The ⅓-size copies are in [screens/android](screens/android); the full-size originals were kept
outside the repo for comparison.

| File | Screen |
|---|---|
| `01-porch.jpg` | Porch (lobby) |
| `02-round.jpg`, `02b-round-flip.jpg` | Round: the peek ending, then a card turned |
| `03-results.jpg` | Results (a loss on level 12) |
| `04-level-map.jpg` | Level map |
| `05-daily.jpg` | Daily porch |
| `06-moths.jpg` | Moths |
| `07-badges.jpg` | Badges |
| `08-settings.jpg` | Settings |
| `09-share-sheet.jpg` | Daily share sheet (post card) |

The scene is animated (moths, motes, the bulb's rays and grain), so later comparisons look for the
same layout, type, colour and effects, not identical pixels.

## Android-only APIs in use

| API | Where | Plan |
|---|---|---|
| `SharedPreferences` (`Store`) | `Progress.kt` | `Storage` interface; Android keeps the same file (`porchlight`) and keys |
| `AudioTrack` streaming mixer, `kotlin.concurrent.thread`, `@Volatile`, `synchronized` | `Sfx.kt` | PCM synthesis and mixing move to common; each platform plays the mixed buffer |
| `SensorManager` game rotation vector | `Depth.kt` | `Tilt` source per platform (CoreMotion, DeviceOrientationEvent) |
| `android.util.Log` | `Depth.kt` (`FrameWatch`) | expect/actual `log()` |
| `RuntimeShader`, `RenderEffect.createRuntimeShaderEffect` (AGSL) | `Shaders.kt` | Android unchanged; skiko `RuntimeEffect` / `ImageFilter.makeRuntimeShader` elsewhere, same source |
| `BlurEffect` with API 31 check | `Shaders.kt`, `Bloom.kt`, `Particles.kt` | capability flag |
| `Build.VERSION.SDK_INT` | `Shaders.kt`, `Cards.kt`, `Scene.kt` | capability flags on `Gpu` |
| `@RequiresApi`, `@ChecksSdkIntAtLeast` | `Shaders.kt` | stay in the Android actual |
| `Bitmap.compress`, `File`, `cacheDir`, `FileProvider`, share `Intent`, `ClipboardManager`, `ClipData` | `ShareCard.kt` | `Platform.share` / `copyText`; PNG encoding per platform |
| `GraphicsLayer.toImageBitmap().asAndroidBitmap()` | `ShareCard.kt` | `ImageBitmap` stays common; only encoding differs |
| `Settings.Global.ANIMATOR_DURATION_SCALE` | `GameScreen.kt` | `systemReduceMotion()` per platform |
| `LocalContext` | `GameScreen.kt`, `Menus.kt`, `Results.kt`, `Depth.kt`, `ShareCard.kt` | goes away; `Platform` is passed in |
| `LocalView.display.refreshRate` | `Scene.kt` | expect/actual refresh rate |
| `LocalActivity.isChangingConfigurations` | `GameScreen.kt` | Android-only detail of the lifecycle hook |
| `androidx.lifecycle` observer (`LocalLifecycleOwner`) | `GameScreen.kt`, `Depth.kt` | lifecycle-runtime-compose is multiplatform; foreground state goes through `Platform` |
| `BackHandler` (activity-compose) | `GameScreen.kt`, `LevelMap.kt`, `ShareCard.kt` | expect/actual; iOS and web get a visible way out instead |
| `SystemClock.elapsedRealtime` | `Theme.kt` (`now()`) | expect/actual monotonic clock |
| `R.font.*` with `FontVariation` | `Theme.kt` | `composeResources/font` |
| `String.format`, `Locale` | `Progress.kt`, `Tally.kt`, `Theme.kt`, `RoundScreen.kt`, `ShareCard.kt` | small common formatters |
| `java.util.Calendar` | `GameScreen.kt` (`todaySeed`), `ShareCard.kt` (`cardDate`) | expect/actual local date; weekday by arithmetic |
| `LocalHapticFeedback` / `HapticFeedbackType` | `GameScreen.kt`, `Results.kt` | multiplatform in Compose; iOS gets UIKit feedback generators |
| `ApplicationInfo.FLAG_DEBUGGABLE` + intent extras | `MainActivity.kt` | stay in the Android host |
| `installSplashScreen`, `enableEdgeToEdge`, adaptive icon, splash theme | `MainActivity.kt`, `res/` | stay in the Android host |
| `rememberSaveable` + `mapSaver` | `GameScreen.kt` | multiplatform; works on all targets |
| `SharedTransitionLayout`, `TextAutoSize`, `FlowRow` | several | in Compose Multiplatform 1.12 |

## Report

- **Moved:** nothing yet.
- **Stubbed:** nothing.
- **Not verified:** nothing; this phase only records.
