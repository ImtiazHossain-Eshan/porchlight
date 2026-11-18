# Phase 6: CI without a Mac

`.github/workflows/ios.yml` does all the iOS work on GitHub's `macos-latest` runners.

| Job | When | What |
|---|---|---|
| `shared-tests` | every push and PR | `./gradlew :shared:iosSimulatorArm64Test`: the shared rules tests on the iOS simulator; the report is uploaded if they fail |
| `simulator` | every push and PR | Installs XcodeGen, generates `Porchlight.xcodeproj`, builds the app for the simulator (unsigned), boots an iPhone simulator, launches the app seven times with debug arguments (`--level 12 --setXp 3000 --screen ...`) and screenshots Porch, Round, Levels, Daily, Moths, Badges and Settings. Artifact: `ios-simulator-screenshots` (with the build log and the app's log lines) |
| `testflight` | manual only: **Run workflow** with **Also send this build to TestFlight** ticked | Archives the Release build, signs it with automatic signing through the App Store Connect API key, and uploads it to TestFlight. Build number = run number |

The key is written to the runner's temp folder only for the run and deleted at the end. No key,
certificate or profile is in the repository.

## What the owner must set up

All of it is in [docs/IOS_RELEASE.md](../IOS_RELEASE.md). In short:

1. An Apple Developer Program membership.
2. The bundle id `app.porchlight.game` registered, and the app created in App Store Connect.
3. An App Store Connect API key with App Manager access.
4. Four repository secrets: `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (the `.p8` file's text), and
   `APPLE_TEAM_ID` (Xcode needs the team to sign for; the three key secrets alone can't name it).

## Small shared addition

`--screen Round` (iOS) and `--es screen round` (Android) start a round of the picked level at
launch, so the simulator can be screenshotted mid-round without a UI test. Checked on the Android
emulator: `--ei level 12 --es screen round` opens a 12-card round; `--es screen daily` opens the
daily porch.

## Checks

- The three workflow files and `iosApp/project.yml` parse as YAML.
- Android `testDebugUnitTest lintDebug assembleDebug` pass; `:shared:compileKotlinIosSimulatorArm64` passes.

## Report

- **Moved:** nothing.
- **Stubbed:** nothing.
- **Not verified:** none of `ios.yml` has run: nothing is pushed, and it needs a macOS runner. The
  first run may need small fixes (an Xcode or XcodeGen setting, the simulator name, or Compose
  resources reaching the app bundle). The TestFlight job also needs the Apple membership and secrets.
