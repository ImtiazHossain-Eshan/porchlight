# Phase 4: Web build

A new Gradle module, `:webApp` (Kotlin/Wasm), shows the shared game full screen in a page. It is
built for Safari on iPhone (iOS 18.2+) and runs in any current browser.

```
./gradlew :webApp:wasmJsBrowserDevelopmentRun    # local server with reload
./gradlew :webApp:wasmJsBrowserDistribution      # the site: webApp/build/dist/wasmJs/productionExecutable
```

## Pieces

| File | What it does |
|---|---|
| `webApp/src/wasmJsMain/kotlin/Main.kt` | `ComposeViewport("porchlight") { PorchlightWeb(WebPlatform()) }` |
| `shared/src/wasmJsMain/.../PorchlightWeb.kt` | Waits for both fonts before the first frame, then runs `PorchlightApp` under a lifecycle that follows the page (hidden → paused) |
| `webApp/src/wasmJsMain/resources/index.html` | Browser check, loading screen, safe area, no zoom/selection/callout/overscroll, 100dvh, portrait notice |
| `.../manifest.webmanifest` | PWA: standalone, portrait, wall colour, `any` and `maskable` icons |
| `.../sw.js` | Service worker: network first, cache as fallback, so it plays offline after one visit |
| `.../icons/` | 192/512 `any` and `maskable`, the 180 px apple-touch-icon, a favicon |
| `art/*.svg`, `scripts/render-icons.ps1` | The icons, drawn from the Android launcher vectors (same paths), rendered with headless Chrome |
| `.github/workflows/web.yml` | On pushes to `multiplatform`: runs the shared tests in headless Chrome, builds the site, publishes it to GitHub Pages |

## Screen

- `viewport-fit=cover`, and `#porchlight` sits inside `env(safe-area-inset-*)`; the wall colour fills
  the notch and home-bar strips.
- No zoom: `maximum-scale=1, user-scalable=no`, plus `gesturestart` blocked (iOS ignores the meta
  tag), and `touch-action: none` (no double-tap zoom).
- No text selection, long-press callout, tap highlight, context menu or rubber-band overscroll.
- `100dvh`, and a fixed body, so Safari's toolbars never leave a gap or a scroll.
- A phone on its side (landscape under 500 px tall) sees "Turn your phone upright to play".
- Old browsers: a one-line WebAssembly GC check plus the iOS version from the user agent. Below
  iOS 18.2 (or without Wasm GC) the page shows "This porch needs a newer browser ... update to
  iOS 18.2 or later" instead of loading the game.

## Audio and pausing

- The Web Audio context starts suspended (Safari's rule) and resumes on the first tap, key or
  pointer press anywhere.
- It suspends while the page is hidden and resumes when it is shown again (`visibilitychange`,
  `pageshow`).
- The page lifecycle drives the same `ON_PAUSE` the Android activity sends, so hiding the tab pauses
  the round (cards face down, PAUSED sign), exactly as backgrounding the Android app does.

## Debug switches

`?noshaders` and `?noblur` in the address turn those effects off, like Android's `pretendApi`.

## Deploy

`web.yml` publishes to `https://<owner>.github.io/<repo>/` (all paths in the site are relative).
The owner has to switch it on once: repository **Settings → Pages → Source: GitHub Actions**.

## Checks

Tested in headless Chrome driven over the DevTools protocol, at 393 x 852 with touch:

| What | Result |
|---|---|
| First launch | Tutorial, then the porch; both fonts, the bulb glow, card foil |
| A round | Deal, peek ("REMEMBER 3 2 1"), fold, GO!, flips on touch, loss → Results |
| Save | 28 keys in localStorage under `porchlight:`, same names as Android; survives a reload |
| Hiding the page mid-round | Round pauses (PAUSED sign), audio context suspends; showing it resumes audio |
| Daily porch with a seeded result | Same layout as Android; share sheet card reads "Thu 19 Nov", "8/8 pairs · 19.0 s left · 6 misses" |
| Share image | `navigator.share` gets `porchlight-daily-20261119.png` (500 KB) and "Porchlight daily 20261119 ★★☆ 2,080"; Copy text copies the same line |
| iOS 17.5 user agent | The "update to iOS 18.2" page |
| Landscape 852 x 393 | The "turn your phone upright" notice |
| Android `testDebugUnitTest lintDebug assembleDebug` | pass: 151 tests (3 skipped), lint 0 errors |

Download size: about 4.4 MB gzipped (12 MB of wasm uncompressed, most of it Skia).

One finding while testing: in a browser tab that is hidden but not reported as hidden (Claude's own
preview pane when collapsed), the browser starves the page of animation frames, and cards can stay
mid-animation. A real hidden tab reports `hidden` and pauses the round, so players don't meet this.

## Report

- **Moved:** nothing new; the web host only adds the page around the shared game.
- **Stubbed:** haptics (none on the web).
- **Not verified:** on a real iPhone: Safari 18.2's Wasm support, audio unlock, the
  DeviceOrientation permission prompt, Add to Home Screen, the share sheet with a file (Safari
  needs the share to follow a tap closely; if it refuses, the PNG downloads instead), offline play,
  and frame rate. The Pages deploy has not run (nothing is pushed). Headless Chrome renders in
  software at about 20 fps, so it says nothing about speed.
