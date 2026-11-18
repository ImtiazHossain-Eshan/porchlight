# Phase 3: Platform services

Every service the game needs now goes through the one `Platform` object handed to
`PorchlightApp(platform)`. Android's implementation came over unchanged in phase 2; this phase adds
`IosPlatform` (`shared/src/iosMain`) and `WebPlatform` (`shared/src/wasmJsMain`), and real SkSL
shaders for both (`shared/src/skikoMain`, a source set shared by iOS and the web).

| Service | Android | iOS | Web |
|---|---|---|---|
| Storage | SharedPreferences `porchlight`, same keys | NSUserDefaults, same keys (string sets as string arrays) | localStorage, same keys under `porchlight:`; in memory if storage is blocked |
| Sound | PCM synthesis and mixing in common `Sfx`; AudioTrack stream | AVAudioEngine + AVAudioSourceNode pulling the same mix; session category Ambient; restarts after an interruption | Web Audio script processor pulling the same mix; unlocked by the first tap; suspended while the page is hidden |
| Ambience loop | yes | yes (same mixer) | yes (same mixer) |
| Haptics | Compose `LocalHapticFeedback`, as before | UINotificationFeedbackGenerator (Confirm, Reject), UIImpactFeedbackGenerator (LongPress heavy, SegmentTick light, others medium) | none |
| Tilt | game rotation vector | CoreMotion device motion at 60 Hz | DeviceOrientationEvent; Safari's permission is asked on the first tap after a round starts listening |
| Shaders | AGSL `RuntimeShader` (Android 13+) | SkSL `RuntimeEffect` through skiko, same source | same as iOS (WebGL) |
| Blur | `BlurEffect` (Android 12+) | `BlurEffect` | `BlurEffect` |
| Share card | ImageBitmap → `Bitmap.compress` PNG → FileProvider → share intent | ImageBitmap → Skia PNG → temp file → UIActivityViewController (popover on iPad) | ImageBitmap → Skia PNG → `navigator.share({files})`; otherwise the PNG downloads and the text is copied |
| Clipboard | ClipboardManager | UIPasteboard | `navigator.clipboard`, with a textarea fallback |
| Device checks | `Build.VERSION` → `Platform.shaders` / `blur` (`pretendApi` still works) | both true | both true |

## How the pieces fit

- `Sfx` is an `AudioEngine`: `prepare()` synthesizes every sound, `render(buffer)` mixes the next
  block (voices, ambience bed, soft limiter). Android's output multiplies by 30,000 into 16-bit PCM
  exactly as before; iOS and the web hand the floats straight to the system.
- Sounds are built off the main thread on Android (its audio thread) and iOS (a global dispatch
  queue). The web has one thread, so it builds them 300 ms after start, once the first frames are up.
- A Skia shader is an immutable snapshot of its uniforms, so on iOS and the web each `update()`
  makes a new shader. The draw code now reads `brush` after `update()`; on Android that is the same
  object as before, so nothing changes there.
- iOS and web tilt readings are flipped to turn the same way as Android's pitch and roll.
- If a shader fails to compile on a device, `create()` returns null and the existing non-shader
  fallback draws, as on old Android phones.

## Checks

| Check | Result |
|---|---|
| `testDebugUnitTest lintDebug assembleDebug` | pass: 151 tests (3 skipped), lint 0 errors / 6 warnings |
| `:shared:compileKotlinIosSimulatorArm64` | succeeds (type-checks every UIKit, AVFAudio and CoreMotion call against the iOS SDK) |
| `:shared:compileKotlinWasmJs` | succeeds |
| Android on the emulator | Porch, Moths and the Round look as in phase 0, foil and glow included |

## Report

- **Moved:** the shader sources are shared by all three platforms; the sound synthesis and mixer are
  shared; storage, haptics, tilt, share and clipboard have an implementation on each platform.
- **Stubbed:** web haptics (no API on iPhone). Nothing else.
- **Not verified:** nothing on iOS has run (no Mac): audio, haptics, CoreMotion, the share sheet and
  NSUserDefaults compile but are untested. The web services are exercised in phase 4, once there is
  a page to load them. The tilt directions on iOS and the web are worked out from the platform docs,
  not checked on a device.
