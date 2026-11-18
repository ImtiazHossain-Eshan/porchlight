# Phase 7: iPhone polish

| Item | How | State |
|---|---|---|
| Safe areas on every screen | Every screen already pads by `safeDrawingPadding()` (the Android app runs edge to edge): Porch, Round, Results, every menu through `MenuFrame`, the tutorial and the share sheet; the level prompt is centred with margins. Compose Multiplatform feeds iOS's safe area into the same insets, so the notch, Dynamic Island and home bar are kept clear the same way. The web build insets the whole game with `env(safe-area-inset-*)` instead | iOS not verified on a device; web checked in headless Chrome |
| Cards answer on touch down | The card's gesture acts on `awaitFirstDown`, before the finger lifts, on every platform | shared code, as on Android |
| No edge swipes during a round | iOS: `defersSystemGestures(on: .all)` and a hidden home indicator while a round is live (`Platform.setImmersive`). Web: the page has no history to swipe back to, and `touch-action: none` | iOS not verified |
| Pause on background or an incoming call | iOS: `AppLifecycle` (new) drives the game's lifecycle from UIKit's own notifications: resign active (call, Control Center, app switcher) → `ON_PAUSE`, which pauses the round and stops tilt, exactly as on Android; background → `ON_STOP`; active → `ON_RESUME`. Audio restarts after an interruption ends | compiles; not verified on a device |
| Bundled fonts with Fredoka's weights | Fredoka is variable; each weight sets its axis (400/600/700) through Compose Resources. iOS and the web both draw text with Skia; on the web the bold and regular weights render as on Android (checked side by side on the Daily porch) | web verified; iOS uses the same Skia text code |
| Reduce Motion | iOS reads `UIAccessibilityIsReduceMotionEnabled` and now follows changes live (`UIAccessibilityReduceMotionStatusDidChangeNotification`); it sets `session.calm` together with the in-game toggle, as Android's animator scale does | not verified on a device |
| Dynamic Type | Compose Multiplatform scales text with the iOS text size. The game has no cap of its own; Android allows up to 2×, so iOS is capped at 2× too (`MAX_FONT_SCALE`). The layouts already shrink-to-fit for large text on Android | not verified on a device |
| Haptics | The UIKit feedback generators are prepared ahead of each use, so a match or miss buzzes without the Taptic Engine's warm-up delay | not verified |
| 60/120 fps | `CADisableMinimumFrameDurationOnPhone` lets ProMotion iPhones run at 120 Hz; the game's own `FrameWatch` steps quality down on slow frames, using the screen's real refresh rate (`UIScreen.maximumFramesPerSecond`) | **not verified**: needs a real iPhone |

## Checks

- `:shared:compileKotlinIosSimulatorArm64` passes with the new lifecycle, Reduce Motion and haptics code.
- Android `testDebugUnitTest lintDebug assembleDebug` pass.

## Report

- **Moved:** nothing.
- **Stubbed:** nothing.
- **Not verified:** everything on a real iPhone: safe areas on notched and Dynamic Island phones,
  deferred edge swipes, pausing on a call, Reduce Motion, Dynamic Type at the largest sizes, haptics,
  and the frame rate (60/120 fps). The CI simulator screenshots (phase 6) will show the safe areas
  and fonts once the workflow runs.
