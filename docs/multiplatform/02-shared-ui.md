# Phase 2: Shared UI

Compose Multiplatform 1.12.2 is added to `:shared`, and every composable moved there. The Android app
module now holds only `MainActivity`, the manifest and its resources (splash, adaptive icon, theme,
FileProvider paths).

## What moved

All 32 UI files moved from `app/src/main/java` to `shared/src/commonMain` unchanged except for the
platform seams below. The fonts (Alfa Slab One, Fredoka) moved to `shared/src/commonMain/composeResources/font`;
on Android they ship as assets (`assets/composeResources/...`) instead of `res/font`.

| Was Android-only | Now |
|---|---|
| `R.font.*` top-level `FontFamily` | `ProvideFaces` builds them once from composeResources with the same `FontVariation` weights (400/600/700); the text styles read them through getters |
| `SystemClock.elapsedRealtime` | `expect fun now()`; Android keeps `elapsedRealtime`, iOS/web use a monotonic `TimeSource` / `performance.now()` |
| `android.util.Log` | `expect fun log()` |
| `BackHandler` (activity-compose) | `expect BackHandler`; Android unchanged, iOS/web no-op. Every place it was used also has a visible way out (Leave/Skip, Keep playing, Back, Close, Not now, tap outside) |
| `Settings.Global.ANIMATOR_DURATION_SCALE` | `expect systemReduceMotion()`; iOS reads `UIAccessibilityIsReduceMotionEnabled`, web `prefers-reduced-motion` |
| `LocalView.display.refreshRate` | `expect displayRefreshRate()` |
| `LocalActivity.isChangingConfigurations` | `expect rememberChangingConfiguration()` |
| `androidx.lifecycle` observers | unchanged code, now the multiplatform `org.jetbrains.androidx.lifecycle` |
| `SharedPreferences` in `Store` | `Store(Storage)`; Android's `Storage` wraps the same `porchlight` file and keys |
| `AudioTrack` in `Sfx` | `Sfx` (synthesis and mixing) is common; Android's `AudioOutput` runs the same AudioTrack loop on the same thread |
| `SensorManager` in `rememberTilt` | the smoothing and resting-position drift are common; Android's `TiltSensor` reads the game rotation vector as before |
| `RuntimeShader` / `RenderEffect` | `expect class FoilShader / GlowShader / LayerEffects`; Android keeps its AGSL code; the shader sources stay common |
| `Build.VERSION` checks | `Gpu.shaders` / `Gpu.blur`, set from `Platform` at launch; the `pretendApi` debug extra still works |
| `Bitmap`, `FileProvider`, share `Intent`, `ClipboardManager` | `Platform.shareImage / shareText / copyText`; the Android code is the old code |
| `LocalContext` | `LocalPlatform` |
| `Math.PI`, `Math.toRadians`, `Math.toDegrees` | `kotlin.math.PI` and identical helpers |

`MainActivity` is now a thin host: splash, edge to edge, the debug extras turned into a
`DebugLaunch`, and `setContent { PorchlightApp(AndroidPlatform(this), debug) }`.

## Platform interface

`Platform` (in `Platform.kt`) is the one object the game reaches the device through: storage, audio,
tilt, haptics, share, clipboard and the shader/blur capabilities. Android implements all of it
(`AndroidPlatform`). iOS and the web implement it in phase 3.

## Behaviour notes

- One small fix came with the move: the sound mixer is now released when the composition goes
  away. Before, a rotation started a second mixer and left the first running.
- The shared module drops the `iosX64` target: Compose Multiplatform 1.12 no longer publishes it
  (Intel-Mac simulators). `iosArm64` and `iosSimulatorArm64` remain.
- The wasm tests now run in headless Chrome (`wasmJsBrowserTest`), because Compose's renderer
  (skiko) only loads in a browser.

## Checks

| Check | Result |
|---|---|
| `testDebugUnitTest` | 151 tests, 3 skipped (all in `:shared`, run on the JVM) |
| `lintDebug` | 0 errors, 6 warnings (fewer, since the code left the app module) |
| `assembleDebug` | 12,767,372 bytes |
| `:shared:wasmJsBrowserTest` | 138 tests pass (headless Chrome) |
| `:shared:compileKotlinIosSimulatorArm64`, `compileTestKotlinIosSimulatorArm64`, `compileKotlinWasmJs` | succeed |

### Android against the phase 0 screenshots

Same emulator, same launch extras. Share of pixels (below the status bar) that differ by more than
40/255:

| Screen | Differs | Why |
|---|---|---|
| Daily, Badges, Settings, Share sheet | 0.0% | identical |
| Level map | 0.2% | fireflies |
| Results | 0.7% | the loss tip is picked per round; floating embers |
| Moths | 1.0% | moths flutter |
| Porch | 2.5% | moths and the light ray move |
| Round | 19% | captured at a different moment of the bulb (13 s left against 3.4 s), so the bulb's colour differs |

Type, layout, colours and effects match on every screen.

## Report

- **Moved:** every composable, the fonts, the sound mixer, tilt smoothing, the save, and the shader
  sources, all into `:shared`.
- **Stubbed (until phase 3):** on iOS and the web the shader classes return null (the existing
  fallbacks draw instead); there is no iOS or web `Platform` yet, so no storage, sound, tilt or
  share there.
- **Not verified:** nothing runs on iOS or the web yet; both only compile.
