<div align="center">

<img src="docs/banner.svg" alt="Six moth cards gathered around a lit porch bulb" width="100%">

# Porchlight

**A thirty-second memory game for Android.**<br>
Moths gather at a porch bulb, and the bulb is the clock. Find every pair before it burns out.

<a href="https://github.com/ImtiazHossain-Eshan/porchlight/actions/workflows/android.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/ImtiazHossain-Eshan/porchlight/android.yml?branch=main&style=flat-square&label=build&labelColor=15181a&color=2c6f6c"></a>
<img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-f4c20d?style=flat-square&logo=android&logoColor=white&labelColor=15181a">
<img alt="Kotlin with Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-f4c20d?style=flat-square&logo=kotlin&logoColor=white&labelColor=15181a">
<img alt="88 tests passing" src="https://img.shields.io/badge/tests-88%20passing-f4c20d?style=flat-square&logo=junit5&logoColor=white&labelColor=15181a">
<img alt="Sound synthesized in app" src="https://img.shields.io/badge/sound-synthesized-f4c20d?style=flat-square&labelColor=15181a">
<a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-f4c20d?style=flat-square&labelColor=15181a"></a>

[Game](#the-game) · [Screens](#screens) · [The moths](#the-moths) · [Nights](#nights-and-the-daily-porch) · [Progression](#progression) · [Architecture](#architecture) · [Get started](#get-started) · [Tests](#tests) · [Releases](#builds-and-releases)

<sub>Designed and built by Imtiaz Hossain</sub>

</div>

---

## The game

You get a two-second look at the cards. Then they fold shut, and the porch bulb starts to burn down.

1. **Peek.** Every card opens for two seconds. Remember where the moths landed.
2. **Match.** Turn two cards. A pair leaves the board: both moths fly up and circle the bulb.
3. **Chain.** Pairs in a row multiply: ×2, ×3, then ×4 *Overdrive*. One wrong pair breaks the chain.
4. **Beat the bulb.** Finish early and every second left is worth 20 more points.

There is no timer bar. **The bulb is the timer.** It burns warm white with time to spare, turns amber as it runs down, and goes deep orange near the end. In the last five seconds it flickers and buzzes. At zero it pops, the moths scatter, and the porch goes dark. A thin arc around the bulb and a small number under it are there if you want the exact time.

Pick a lead moth before each round. Each one bends the rules its own way.

## Screens

<table>
  <tr>
    <td align="center" width="20%"><img src="docs/screens/1-home.png" alt="The lobby: a lit mason jar with moths resting around it, an oak sign reading Light the porch, and four menu tiles" width="100%"></td>
    <td align="center" width="20%"><img src="docs/screens/2-peek.png" alt="The opening peek: every card face up under a teal bulb, with READY 3 2 1 counting down" width="100%"></td>
    <td align="center" width="20%"><img src="docs/screens/3-streak.png" alt="Overdrive: the bulb glows gold in its brass works, found moths circle it, and MATCH pops rise off the pairs" width="100%"></td>
    <td align="center" width="20%"><img src="docs/screens/5-low.png" alt="The last seconds: the bulb is red and smoking, the readout says 05s, and the remaining cards are washed red" width="100%"></td>
    <td align="center" width="20%"><img src="docs/screens/4-result.png" alt="The scorecard: a gilded navy panel with stars, the score, a bonus table and the level gauge" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><sub><b>The lobby</b><br>A jar, the moths, one sign</sub></td>
    <td align="center"><sub><b>The peek</b><br>Ready, 3, 2, 1</sub></td>
    <td align="center"><sub><b>Overdrive</b><br>The light turns gold</sub></td>
    <td align="center"><sub><b>Last seconds</b><br>Red, smoke, 05s</sub></td>
    <td align="center"><sub><b>Scorecard</b><br>Stars and bonuses</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screens/6-nights.png" alt="Six nights: a parchment map with a copper pipe running between six brass night markers" width="100%"></td>
    <td align="center"><img src="docs/screens/9-daily.png" alt="Daily porch: a calendar leaf with today's date, the week's studs, and your own best days" width="100%"></td>
    <td align="center"><img src="docs/screens/7-moths.png" alt="Lead moths: each moth in a glass jar on velvet, with its strengths as brass pips" width="100%"></td>
    <td align="center"><img src="docs/screens/8-badges.png" alt="Badges: twelve cast medals in bronze, silver and gold on a walnut board" width="100%"></td>
    <td align="center"><img src="docs/screens/10-settings.png" alt="Settings: a walnut switchboard with four brass lever switches" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><sub><b>Six nights</b><br>A map on parchment</sub></td>
    <td align="center"><sub><b>Daily porch</b><br>One scored try a day</sub></td>
    <td align="center"><sub><b>Lead moths</b><br>Specimens on velvet</sub></td>
    <td align="center"><sub><b>Badges</b><br>Twelve castings</sub></td>
    <td align="center"><sub><b>Settings</b><br>Brass levers</sub></td>
  </tr>
</table>

A 15-second gameplay clip is in [`docs/store/gameplay.mp4`](docs/store/gameplay.mp4).

## The moths

### Six leads

You start with Luna. Each level up brings the next lead to the porch.

| | Moth | Role | Perk | Unlocks |
| :---: | --- | --- | --- | :---: |
| <img src="docs/moths/luna.svg" width="56" alt=""> | **Luna** | The night owl | **Long-life bulb.** The round starts with 5 extra seconds. | Level 1 |
| <img src="docs/moths/wick.svg" width="56" alt=""> | **Wick** | The sprinter | **Hot streak.** Streaks start at ×2, but the bulb is 3 seconds shorter. | Level 2 |
| <img src="docs/moths/atlas.svg" width="56" alt=""> | **Atlas** | The mapmaker | **Second look.** Once a round, flash every card open for a moment. | Level 3 |
| <img src="docs/moths/dot.svg" width="56" alt=""> | **Dot** | The counter | **Quick hands.** Tap straight past a wrong pair, and your first miss keeps the streak. | Level 4 |
| <img src="docs/moths/iris.svg" width="56" alt=""> | **Iris** | The watcher | **Long look.** The opening peek lasts 3.4 seconds instead of 2. | Level 5 |
| <img src="docs/moths/sorbet.svg" width="56" alt=""> | **Sorbet** | The sweet tooth | **Top-up.** Every pair you find adds a second to the bulb. | Level 6 |

A unit test plays every lead with a perfect player and with one who misses 30% of the time, and checks that no lead beats the rest by more than 15% on both score and time.

### Four more on later nights

| | | | |
| :---: | :---: | :---: | :---: |
| <img src="docs/moths/sulphur.svg" width="56" alt=""><br>**Sulphur** | <img src="docs/moths/rose.svg" width="56" alt=""><br>**Rose** | <img src="docs/moths/midnight.svg" width="56" alt=""><br>**Midnight** | <img src="docs/moths/moss.svg" width="56" alt=""><br>**Moss** |

Every moth has one main colour no other moth uses, and a wing shape or pattern of its own, so you can tell any two apart at a glance.

## Nights and the daily porch

Beat a night with two stars or more to open the next. Each night gives about five seconds a pair.

| Night | Board | Bulb | Twist |
| :---: | --- | :---: | --- |
| 1 | 6 pairs, 3 × 4 | 30 s, then 24 s once you have won it | None |
| 2 | 8 pairs, 4 × 4 | 40 s | Sulphur and Rose join |
| 3 | 10 pairs, 4 × 5 | 50 s | Midnight and Moss join |
| 4 | 10 pairs | 50 s | **Wanderer:** after a wrong pair, two face-down cards swap places |
| 5 | 9 pairs and 2 leaves | 46 s | **Dead leaves:** turning one costs 2 seconds |
| 6 | 10 pairs | 50 s | **Shadow:** one moth is only a silhouette during the peek |

Each new night and twist is introduced once, in one line with a small animation, the first time you play it.

**Daily porch.** The deal comes from today's date, so everyone gets the same porch. You get one scored try a day. Play on consecutive days to build a streak, and share your result:

```
Porchlight daily 20261114 ★★☆ 2,340
```

## Progression

<table>
<tr>
<td valign="top" width="50%">

**XP and levels**

| Source | XP |
| --- | ---: |
| Score | score ÷ 10 |
| Win (or finish) | 50 (10) |
| Each star | 20 |

Levels need 200, 500, 900, 1,400 and 2,000 XP in total.

**Stars**

| Star | For |
| :---: | --- |
| ★ | Finding every pair |
| ★ | A third of the bulb or more left |
| ★ | Not a single wrong pair |

**Mastery.** Win three stars with a lead on night 3 or later and that moth turns gold on every card.

**Pacing.** A round hands over at most two unlocks. A new night and a new lead always arrive at once; a gold moth and badges that don't fit wait and arrive in later rounds.

</td>
<td valign="top" width="50%">

**Badges**

| Badge | How to earn it |
| --- | --- |
| First light | Finish a round. |
| Clean sweep | Win without a single wrong pair. |
| Quick wings | Win a board of 8 pairs or more with 15 seconds left. |
| Overdrive | Reach a ×4 streak. |
| Melody | Find six pairs in a row on a board of 8 pairs or more. |
| Night shift | Play 10 rounds. |
| Full roster | Win once with every lead. |
| Wanderers | Beat night 4. |
| Last light | Beat night 6. |
| Regular | Play the daily porch three days running. |
| Golden wing | Master a moth: three stars with it leading, on night 3 or later. |
| Gilded porch | Master all six leads. |

</td>
</tr>
</table>

### Feel

- Every tap answers at once: the card dips under your finger, turns in 3D, lifts off the board, and the moth wakes up and flaps.
- A wrong pair flashes red, shakes, and both moths snap their wings shut.
- Streaks build. ×1 throws a few sparks. ×2 throws more. ×3 adds a bass note, a bulb flare and a warm glow at the screen edge. ×4 *Overdrive* freezes the frame for an instant, shakes the porch, sends a gold shockwave out from the pair and slides OVERDRIVE across.
- When a streak breaks, the counter cracks and drops back to ×1 with a low thunk.
- The score counts up instead of jumping.
- All particles come from one pool of 200 that is allocated once.
- **Reduce motion**, from the settings or the system's animation setting, turns off the shake, freeze and sparks but keeps the colour changes.

### Look

The porch is built by hand, and every surface is a made thing drawn in code: night-stained planks, walnut and oak panels with grain and bevels, brass and copper plates with rivets, gears and pipes, parchment, plum velvet and glass. No image files ship.

The light's colour is the round's state: amber while it burns, teal while the cards are shown and on every found pair, gold in Overdrive, and red in the last five seconds.

- **Lobby:** a mason jar with a filament inside, hung by a wire.
- **Round:** an Edison bulb in brass works. Two gears turn while it burns, and a pressure gauge's needle falls with the time left.
- **Cards:** ebony tiles in embossed brass frames. Faces are parchment; a found pair leaves an empty, recessed socket.
- **Signs:** the main action is always an oak sign with carved lettering. Quieter actions are walnut with gilt lettering.


Modern effects without a game engine or a WebView: Compose `graphicsLayer` for the 3D, and [AGSL](https://developer.android.com/develop/ui/views/graphics/agsl) shaders in [`Shaders.kt`](app/src/main/java/app/porchlight/game/Shaders.kt) for the GPU work.

- **Cards are thick tiles.**
  - They tilt up to 12° toward your finger, sway slowly when idle, and flip on an overshooting spring.
  - Mid-flip they rise from 2 to 18 dp of shadow, then land with a small squash.
  - A specular highlight slides opposite the tilt, and cards near the bulb catch warm light on their top edge.
- **Foil.** Card backs have a faint holographic foil that shifts with the tilt. Mastered moths get the same foil in gold, only on the moth.
- **The bulb's light.**
  - A shader draws a soft volumetric glow with slow god rays and film grain, and cools from warm white to deep orange as time runs out.
  - In the last five seconds the air above the bulb shimmers with heat.
- **Bloom.** The bulb's core, the sparks and the gold rims are drawn again, blurred, and added on top. The OVERDRIVE word has its own bloom.
- **Depth.**
  - Tilting the phone tilts the board up to 4°. The wall (0.3×), the light (0.6×), the board (1×) and the flying moths (1.4×) slide at different depths.
  - Fifty dust motes drift at different depths and glow when they cross the cone of light under the bulb.
  - The tilt sensor runs only while the game is on screen.
- **Sparks** add their light where they overlap and leave short trails. Overdrive sends a 150 ms colour-split pulse through the area above the board.
- **Scorecard.** The results spring up as a gilded panel over a board that blurs behind it.

**Fallbacks.** Shaders need Android 13; blur needs Android 12.
- **Android 12 (no shaders):** a gradient glow replaces the god rays, and a sliding sheen replaces the foil.
- **Below Android 12 (no blur):** there is no bloom, and the board does not blur behind the scorecard.

**Performance.** If frames start running long, the game halves the bloom's resolution, then thins the particles.

**Reduce motion** turns off the sway, the press tilt, the device tilt, the moving foil, the shimmer and the colour pulse.

A before-and-after of every screen is in [`docs/visuals/before-after.png`](docs/visuals/before-after.png), with a 10-second clip of a round in [`docs/visuals/round-10s.mp4`](docs/visuals/round-10s.mp4).

<details>
<summary><b>Debug launch switches</b> (debug builds only)</summary>

<br>

To check the fallbacks and the parallax on any device:

```bash
adb shell am start -n app.porchlight.game/.MainActivity --ei pretendApi 32           # act like Android 12L: no shaders
adb shell am start -n app.porchlight.game/.MainActivity --ei pretendApi 30           # act like Android 11: no shaders, no blur
adb shell am start -n app.porchlight.game/.MainActivity --ef tiltX 1 --ef tiltY -0.5 # a fixed tilt instead of the sensor
adb shell am start -n app.porchlight.game/.MainActivity --ei quality 2               # half-size bloom and fewer particles
```

</details>

### Sound

Every sound is synthesized in the app. The APK ships no audio files. A small mixer plays everything through one stream, so overlapping sounds never cut each other off.

| Moment | Sound |
| --- | --- |
| On the porch and in play | Crickets and a soft bulb hum (Night sounds) |
| Card turns | A soft paper flip, its pitch varied slightly each time |
| Pair found | A chime that climbs C major pentatonic with your streak, so a perfect round plays a tune |
| ×3 and up | A bass note under the chime |
| Wrong pair / streak breaks | A low buzz / a dull thunk |
| Last 5 seconds | A wooden tick each second, and an electric buzz with every flicker |
| Bulb pops | A pop and tinkling glass |
| Win / burn out | A rising sting / the same notes falling |

Sound effects, night sounds, vibration and reduce motion each have their own switch in the settings.

## Architecture

A single-activity Compose app: a lobby with five screens off it, the round, and its scorecard. No navigation library. The game rules are plain Kotlin with no Android dependencies, which is what makes them testable.

```mermaid
flowchart LR
    A[MainActivity] --> S[GameScreen<br/><i>Compose UI</i>]
    S -- taps --> R[Round<br/><i>immutable game state</i>]
    R -- new state --> S
    S -- round over --> P[Store<br/><i>XP, levels, badges</i>]
    P --> SP[(SharedPreferences)]
    S -- events --> X[Sfx<br/><i>synthesized audio</i>]
    S --> M[Moths<br/><i>vector artwork</i>]
    S -- rotation, process death --> V[(Saved state)]
```

| File | What it holds |
| --- | --- |
| [`Game.kt`](app/src/main/java/app/porchlight/game/Game.kt) | Round rules: nights, twists, dealing, peek, matching, streaks, timer, pause, scoring, and saving a round. Every move returns a new immutable `Round`. |
| [`Progress.kt`](app/src/main/java/app/porchlight/game/Progress.kt) | XP, levels, unlocks, stars, mastery, badges, the daily streak, and saving progress. |
| [`GameScreen.kt`](app/src/main/java/app/porchlight/game/GameScreen.kt) | The session: which screen is up, the player's taps, feedback, pausing and saved state. |
| [`Porch.kt`](app/src/main/java/app/porchlight/game/Porch.kt), [`RoundScreen.kt`](app/src/main/java/app/porchlight/game/RoundScreen.kt), [`Results.kt`](app/src/main/java/app/porchlight/game/Results.kt) | The lobby, the round and the scorecard. |
| [`Bulb.kt`](app/src/main/java/app/porchlight/game/Bulb.kt), [`Flock.kt`](app/src/main/java/app/porchlight/game/Flock.kt), [`Scene.kt`](app/src/main/java/app/porchlight/game/Scene.kt) | The bulb that is the timer, the moths that orbit it, and the night wall it lights. |
| [`Shaders.kt`](app/src/main/java/app/porchlight/game/Shaders.kt), [`Bloom.kt`](app/src/main/java/app/porchlight/game/Bloom.kt), [`Depth.kt`](app/src/main/java/app/porchlight/game/Depth.kt) | The AGSL shaders and what the phone can draw, the bloom layer, and the tilt sensor and frame watch. |
| [`Cards.kt`](app/src/main/java/app/porchlight/game/Cards.kt), [`Particles.kt`](app/src/main/java/app/porchlight/game/Particles.kt), [`Hud.kt`](app/src/main/java/app/porchlight/game/Hud.kt) | The board, sparks and shockwaves, score pops, and the small pieces of UI. |
| [`Onboarding.kt`](app/src/main/java/app/porchlight/game/Onboarding.kt) | The first-launch tutorial (a 3 × 2 board) and the one-time night introductions. |
| [`Menus.kt`](app/src/main/java/app/porchlight/game/Menus.kt) | The screens off the lobby: the night map, the daily porch, lead moths, badges and settings. |
| [`Materials.kt`](app/src/main/java/app/porchlight/game/Materials.kt), [`Icons.kt`](app/src/main/java/app/porchlight/game/Icons.kt) | Wood, metal, parchment, velvet, gilded panels, gears, rivets and pipes; and the drawn icon set. |
| [`Moths.kt`](app/src/main/java/app/porchlight/game/Moths.kt) | Ten moths and a dead leaf, drawn as vector paths on a Compose `Canvas`. |
| [`Sfx.kt`](app/src/main/java/app/porchlight/game/Sfx.kt) | The synthesizer and the mixer. |
| [`Theme.kt`](app/src/main/java/app/porchlight/game/Theme.kt) | The night palette and the type. |

**Stack:** Kotlin · Jetpack Compose (Material 3) · Android Gradle Plugin 9 · Gradle 9.6 · JUnit 4. Minimum Android 7.0 (API 24). No network, no analytics, no permissions. Progress stays on the device.

**On Android:**
- A round pauses when the app goes to the background, and continues when you tap.
- A round in play survives rotation and the system killing the app.
- The launcher icon is adaptive, with a themed (monochrome) layer for Android 13.
- The app opens on a night-porch splash screen.
- The game draws edge to edge.
- Phones and tablets work in both orientations; on wide screens the board sits beside the bulb.
- A baseline profile compiles the game ahead of time.

**Type:** [Alfa Slab One](https://fonts.google.com/specimen/Alfa+Slab+One), a wood-type slab, for signs, titles and numbers, under the SIL Open Font License ([`licenses/AlfaSlabOne-OFL.txt`](licenses/AlfaSlabOne-OFL.txt)).

## Get started

### Install the APK

- **Latest build:** open the [Actions tab](https://github.com/ImtiazHossain-Eshan/porchlight/actions/workflows/android.yml), choose the newest green run, and download **porchlight-debug-apk** under *Artifacts*.
- **Releases:** tagged versions are on the [Releases page](https://github.com/ImtiazHossain-Eshan/porchlight/releases).

Copy the APK to your phone and open it. Android will ask you to allow installing from that source once.

### Build it yourself

You need the Android SDK, which Android Studio installs, and Java 17 to 25. Android Studio's bundled Java works.

```bash
git clone https://github.com/ImtiazHossain-Eshan/porchlight.git
cd porchlight
./gradlew assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew installDebug       # install on a connected phone or emulator
```

Or open the folder in Android Studio and press **Run**.

If Gradle can't find the SDK, create `local.properties`:

```properties
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
```

## Tests

88 unit tests cover the rules, the progression system and the balance between leads. They run on the JVM in a few seconds, with no emulator.

```bash
./gradlew testDebugUnitTest
```

| Suite | Covers |
| --- | --- |
| [`GameTest`](app/src/test/java/app/porchlight/game/GameTest.kt) | Dealing and seeded deals, nights, every lead's perk, peek and clock, matching, streaks, misses, time bonus, burn-out, Second look, the three twists, pause and resume, saving and restoring a round, the tutorial |
| [`ProgressTest`](app/src/test/java/app/porchlight/game/ProgressTest.kt) | XP curve, levels, unlocks, stars, opening nights, mastery, the daily porch and its streak, the share line, badges |
| [`BalanceTest`](app/src/test/java/app/porchlight/game/BalanceTest.kt) | Simulated perfect and typical players with every lead: none may beat the rest by more than 15% on both score and time |

CI runs them on every push. A failing test stops the build and uploads the HTML report.

## Builds and releases

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on every push to `main`, every pull request, and on demand:

1. Runs the unit tests.
2. Builds a debug APK and uploads it as an artifact.
3. On a `v*` tag, publishes a GitHub Release with the APK attached. The tag becomes the version name and the run number the version code.

```bash
git tag v0.2.0
git push origin v0.2.0
```

<details>
<summary><b>Signed release APKs (optional)</b></summary>

<br>

Without signing secrets, a release carries the debug APK. That's fine for testing, but it can't go on the Play Store, and an update built on another machine won't install over it.

**Quick way:** run the setup script once. It creates the key outside the repo and uploads the four secrets with the GitHub CLI:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-signing.ps1
```

**By hand:**

1. Create a keystore once and back it up. Losing it means you can't ship updates under the same key.

   ```bash
   keytool -genkeypair -v -keystore porchlight-release.jks -alias porchlight -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Encode it. In Windows PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("porchlight-release.jks")) | Set-Clipboard
   ```

3. In **Settings → Secrets and variables → Actions**, add:

   | Secret | Value |
   | --- | --- |
   | `PORCHLIGHT_KEYSTORE_BASE64` | the base64 text |
   | `PORCHLIGHT_KEYSTORE_PASSWORD` | the keystore password |
   | `PORCHLIGHT_KEY_ALIAS` | `porchlight` |
   | `PORCHLIGHT_KEY_PASSWORD` | the key password |

Tagged releases are then signed with your key. `.gitignore` keeps `*.jks` files out of the repo.

</details>

## Store listing

[`docs/store/`](docs/store/) holds what a Play Store listing needs:
- the store description, which leads with the bulb-as-timer hook
- a 1024 × 500 feature graphic
- five full-size phone screenshots
- the 15-second gameplay clip

## License

[MIT](LICENSE) © 2026 Imtiaz Hossain. The moths, artwork and sounds are original to this project. Alfa Slab One is © The Alfa Slab One Project Authors, under the SIL Open Font License.

<div align="center">
<br>
<img src="docs/moths/wick.svg" width="40" alt="">
<br>
<sub>Leave the light on.</sub>
</div>
