# Phase 5: Native iOS app

`iosApp/` holds a SwiftUI app that hosts the shared Compose game. The Xcode project is generated
from `iosApp/project.yml` with XcodeGen, on a Mac or on the macOS CI runner; no `.pbxproj` is
committed, since one written by hand on Windows couldn't be checked.

## Pieces

| Piece | Where | What |
|---|---|---|
| Kotlin entry | `shared/src/iosMain/.../MainViewController.kt` | `MainViewController(arguments, onImmersive)` returns `ComposeUIViewController { PorchlightApp(IosPlatform(...)) }` |
| Framework | `shared/build.gradle.kts` | `Shared.framework`, static, for `iosArm64` and `iosSimulatorArm64` |
| SwiftUI host | `iosApp/iosApp/PorchlightApp.swift` | One `WindowGroup`, the game edge to edge (`ignoresSafeArea`; the game keeps its controls in the safe area itself, as on Android) |
| Build phase | `project.yml` | `./gradlew :shared:embedAndSignAppleFrameworkForXcode` before compiling |
| Debug launch | `MainViewController.kt` | Debug builds read `--level`, `--setXp`, `--quality`, `--screen`, like Android's intent extras |

## Identity

- Bundle id `app.porchlight.game`, version 0.6.6, iOS 16.0 minimum.
- iPhone: portrait only. iPad: all four orientations (the game already lays out for landscape tablets).
- `UIRequiresFullScreen`: no Split View or Slide Over on iPad.

## Art

- `AppIcon` (1024 px, single size) from `art/icon.svg`: the Android launcher's bulb and moth on the
  plank wall, the same paths, cropped so the moth stays whole on a rounded square.
- Launch screen (`UILaunchScreen`): the wall colour `#0E1A24` with the bulb-and-moth mark centred,
  as the Android splash shows it.

## Info.plist (from `project.yml`)

| Key | Value | Why |
|---|---|---|
| `CADisableMinimumFrameDurationOnPhone` | true | ProMotion iPhones may run the game at 120 Hz |
| `UIRequiresFullScreen` | true | |
| `UIStatusBarStyle` | light content | light text on the night wall |
| `UILaunchScreen` | colour + mark | matches the Android splash |
| `ITSAppUsesNonExemptEncryption` | false | no export-compliance question on each upload |

During a round the status bar and home indicator hide and edge swipes are deferred (a first swipe
from an edge only shows the indicator). SwiftUI does this with `statusBarHidden`,
`persistentSystemOverlays(.hidden)` and `defersSystemGestures(on: .all)`, switched by a new
`Platform.setImmersive(on)` hook that the game calls when a round starts and ends. Android and the
web ignore the hook.

## Privacy

`PrivacyInfo.xcprivacy`: no tracking, no tracking domains, no collected data. Required-reason APIs:
UserDefaults (`CA92.1`, the app's own save) and system boot time (`35F9.1`, the monotonic clock the
round timer and Kotlin's `TimeSource` read).

## Small shared additions

- `Platform.setImmersive(on)` (default: nothing).
- `DebugLaunch.screen`: open a menu screen at launch, for screenshots. Android's debug build reads it
  as `--es screen daily`.

## Checks

- `:shared:compileKotlinIosArm64` and `:shared:compileKotlinIosSimulatorArm64` pass.
- Android `testDebugUnitTest assembleDebug` pass.

## Report

- **Moved:** nothing; this phase adds the iOS host around the shared game.
- **Stubbed:** nothing.
- **Not verified:** everything that needs Xcode: generating the project, linking `Shared.framework`,
  compiling the Swift, the asset catalog, launching, how the launch screen looks, the immersive
  mode, and the copying of the fonts (Compose resources) into the app bundle. Phase 6 wires all of
  that into CI on a macOS runner.
