# Phase 8: Parity and handover

## What was added

- [`docs/PLATFORMS.md`](../PLATFORMS.md): every feature against Android, iOS and the web, marked
  works / fallback / missing, with what is not verified; the tests per platform; the side-by-side
  screenshots.
- Side-by-side screenshots: [`screens/side-by-side-a.jpg`](screens/side-by-side-a.jpg) and
  [`screens/side-by-side-b.jpg`](screens/side-by-side-b.jpg) (Android beside the web build), with the
  web captures alone in [`screens/web/`](screens/web). iOS screenshots come from CI.
- `README.md`: how the code is split now (`shared/`, `iosApp/`, `webApp/`), playing on an iPhone in
  Safari (and Add to Home Screen), building the web version, the iOS app through CI or on a Mac,
  triggering TestFlight, and running the tests on each target. File links point at their new homes.
- [`docs/IOS_RELEASE.md`](../IOS_RELEASE.md) (phase 6): the owner's one-time Apple setup.

## Final run (clean build)

```
./gradlew clean
./gradlew testDebugUnitTest lintDebug assembleDebug :shared:wasmJsBrowserTest \
  :webApp:wasmJsBrowserDistribution :shared:compileKotlinIosArm64 \
  :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64
```

| Target | Result |
|---|---|
| Android unit tests (JVM) | 151 passed, 3 skipped (the tuner), 0 failed; baseline was 144 / 3 skipped |
| Android lint | 0 errors, 6 warnings (baseline 0 / 11) |
| Android debug APK | 12,767,372 bytes (baseline 12,673,884: +93 KB for Compose Multiplatform's resources library and the shared module) |
| Shared tests on Wasm (headless Chrome) | 138 passed |
| Web distribution | built (about 4.4 MB gzipped) |
| iOS: `iosArm64` and `iosSimulatorArm64` main and test compilation | succeeds on Windows |
| iOS simulator tests, Xcode build, TestFlight | wired in `ios.yml`, not run (needs macOS) |

Android on the emulator from this build, against the phase 0 screenshots (pixels differing by more
than 40/255, below the status bar): Badges 0.0%, Settings 0.0%, Daily 0.07%, Levels 0.5%,
Moths 1.0%, Porch 2.5%. What differs is what moves: moths, fireflies, motes, the light ray.

## Branch

`multiplatform`, cut from `levels-50` at `423f47c`, one commit per phase (0 to 8). Nothing pushed,
no PR opened. The owner's uncommitted `docs/REDESIGN_PROMPTS.md` and the untracked
`porchlight-screenshots-v0.5.2.zip` were left alone.

## What the owner needs to do

1. Push `multiplatform` when ready. Then:
   - **Web:** Settings → Pages → Source: **GitHub Actions**. `web.yml` publishes on each push to
     `multiplatform`. Open the Pages address in Safari on an iPhone with iOS 18.2+.
   - **iOS:** `ios.yml` runs on its own: shared tests on the simulator, a simulator build and
     screenshots. The first run may need a small fix (it has never run).
2. For TestFlight: the Apple Developer Program, the bundle id, an App Store Connect API key and four
   secrets, as [`docs/IOS_RELEASE.md`](../IOS_RELEASE.md) describes. Then Actions → iOS → Run
   workflow → tick TestFlight.

## Report

- **Moved:** nothing in this phase; it documents and checks.
- **Stubbed:** nothing.
- **Not verified, across the whole port:** anything on a real iPhone or iPad, or in Xcode: the iOS
  build, linking, launch screen, audio, haptics, CoreMotion, share sheet, safe areas, pausing on a
  call, Dynamic Type, Reduce Motion and frame rate. On the web, iPhone Safari itself (audio unlock,
  the motion permission, sharing a file, Add to Home Screen, offline). The three workflows have not
  run.
