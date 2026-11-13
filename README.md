# Porchlight

A small Android memory game. Six moths gather at a porch bulb, and the bulb is the clock: find all six pairs before it burns out.

- 12 cards, 6 pairs, a short peek, then 30 seconds.
- Six moths, each with one perk. New moths unlock as you level up.
- XP and levels, star ratings, streak bonuses and six badges.
- Sound effects are synthesized in the app, so there are no audio files.
- Progress is saved on the device only.

Built with Kotlin and Jetpack Compose. Package name: `app.porchlight.game`.

## Build an APK locally

You need the Android SDK. Android Studio installs it.

1. Open this folder in Android Studio, or create `local.properties` with your SDK path:

   ```
   sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
   ```

2. Build:

   ```bash
   ./gradlew assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

3. Install it on a connected phone or emulator:

   ```bash
   ./gradlew installDebug
   ```

Gradle needs Java 17 to 25. On Windows, Android Studio's bundled Java works:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

## APK builds on GitHub

`.github/workflows/android.yml` runs on every push to `main`, every pull request, and on demand from the Actions tab.

- **Every run** builds a debug APK. Download it from the run page under **Artifacts** (`porchlight-debug-apk`).
- **Tags starting with `v`** (for example `v0.2.0`) also create a GitHub Release with the APK attached. The tag becomes the app's version name, and the run number becomes its version code.

### Signed release APKs (optional)

Without signing secrets, a release gets the debug APK. That installs fine for testing, but it can't go on the Play Store, and every build is signed with a different debug key, so a new version won't install over an older one from another machine.

To sign releases with your own key:

1. Create a keystore once and keep it safe. If you lose it, you can't publish updates under the same key.

   ```bash
   keytool -genkeypair -v -keystore porchlight-release.jks -alias porchlight -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Turn it into base64. On Windows PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("porchlight-release.jks")) | Set-Clipboard
   ```

3. In the GitHub repo, open **Settings → Secrets and variables → Actions** and add:

   | Secret | Value |
   | --- | --- |
   | `PORCHLIGHT_KEYSTORE_BASE64` | the base64 text from step 2 |
   | `PORCHLIGHT_KEYSTORE_PASSWORD` | the keystore password |
   | `PORCHLIGHT_KEY_ALIAS` | `porchlight` (or the alias you chose) |
   | `PORCHLIGHT_KEY_PASSWORD` | the key password |

Never commit the `.jks` file. `.gitignore` already excludes it.

## Releasing a version

```bash
git tag v0.2.0
git push origin v0.2.0
```

## Project layout

| Path | What it holds |
| --- | --- |
| `app/src/main/java/app/porchlight/game/Game.kt` | Round rules: dealing, matching, timer, scoring |
| `app/src/main/java/app/porchlight/game/Progress.kt` | XP, levels, unlocks, stars, badges, saving |
| `app/src/main/java/app/porchlight/game/Sfx.kt` | Synthesized sound effects |
| `app/src/main/java/app/porchlight/game/Moths.kt` | The moth, card-back and bulb drawings |
| `app/src/main/java/app/porchlight/game/GameScreen.kt` | The game screen |
| `.github/workflows/android.yml` | CI build and releases |
