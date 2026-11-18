# Porchlight for iPhone and iPad

The native iOS app: SwiftUI hosting the shared Compose game (`MainViewController` in
`shared/src/iosMain`). The Xcode project is generated from `project.yml` with
[XcodeGen](https://github.com/yonaskolb/XcodeGen), so nothing in it has to be hand-merged.

## On a Mac

```sh
brew install xcodegen
cd iosApp && xcodegen          # writes Porchlight.xcodeproj and iosApp/Info.plist
open Porchlight.xcodeproj      # Run on a simulator; for a phone, pick your team under Signing
```

The build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, so a JDK 17+ must be
on the PATH (or set `JAVA_HOME`).

Debug builds take launch arguments like the Android debug extras:
`--level 12 --setXp 3000 --screen Daily` (Levels, Daily, Moths, Badges, Settings, or Round to start the picked level) and `--quality 2`.

## Without a Mac

`.github/workflows/ios.yml` builds the simulator app, runs the shared tests on the simulator and
uploads screenshots on every push. Its manual job sends a build to TestFlight; see
`docs/IOS_RELEASE.md` for the one-time Apple setup.

## What is where

| File | Purpose |
|---|---|
| `project.yml` | Targets, settings, Info.plist keys, the Gradle build phase |
| `iosApp/PorchlightApp.swift` | The app, the immersive mode during rounds |
| `iosApp/PrivacyInfo.xcprivacy` | No tracking, no data collected; UserDefaults (CA92.1) and system boot time (35F9.1) declared |
| `iosApp/Assets.xcassets` | App icon and launch mark (rendered from `art/` by `scripts/render-icons.ps1`), launch colour |
