# Sending Porchlight to TestFlight without a Mac

The `iOS` workflow (`.github/workflows/ios.yml`) builds, signs and uploads the iPhone app on
GitHub's macOS runners. It runs on every push (tests, a simulator build and screenshots), and on
demand it sends a build to TestFlight. This page is the one-time setup that the TestFlight part
needs. Nothing here goes into the repository: the keys live only in GitHub's encrypted secrets.

## What you need

- An **Apple Developer Program** membership (99 USD a year), in your name or your organisation's.
  Apple won't sign or accept an iOS app without it. Enrolling takes a day or two.
- Admin rights on the GitHub repository (to add secrets).

## 1. Register the app id

1. Sign in at <https://developer.apple.com/account> → **Certificates, Identifiers & Profiles** →
   **Identifiers** → **+** → **App IDs** → **App**.
2. Description: `Porchlight`. Bundle ID: **Explicit**, `app.porchlight.game`.
3. No capabilities are needed. **Continue** → **Register**.

## 2. Create the app in App Store Connect

1. <https://appstoreconnect.apple.com> → **Apps** → **+** → **New App**.
2. Platform **iOS**, name `Porchlight` (or another free name), primary language, bundle ID
   `app.porchlight.game`, SKU `porchlight`. **Create**.

## 3. Make an App Store Connect API key

1. App Store Connect → **Users and Access** → **Integrations** → **App Store Connect API** →
   **Team Keys** → **+** (generate the first key if asked).
2. Name `GitHub Actions`, access **App Manager**. **Generate**.
3. Note the **Key ID** (10 characters) and the **Issuer ID** at the top of the page.
4. **Download API Key**: a file `AuthKey_XXXXXXXXXX.p8`. Apple lets you download it once; keep it
   somewhere safe and never commit it.

## 4. Find your team id

<https://developer.apple.com/account> → **Membership details** → **Team ID** (10 characters).

## 5. Add four repository secrets

GitHub → the repository → **Settings** → **Secrets and variables** → **Actions** →
**New repository secret**, one for each:

| Secret | Value |
|---|---|
| `ASC_KEY_ID` | the Key ID from step 3 |
| `ASC_ISSUER_ID` | the Issuer ID from step 3 |
| `ASC_KEY_P8` | the whole contents of the `.p8` file, including the `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----` lines |
| `APPLE_TEAM_ID` | the Team ID from step 4 |

`APPLE_TEAM_ID` tells Xcode which team to sign for; the other three let it sign in to Apple.

## 6. Send a build

GitHub → **Actions** → **iOS** → **Run workflow** → pick the branch (`multiplatform`), tick
**Also send this build to TestFlight** → **Run workflow**.

The run tests the shared code on the simulator, builds and screenshots the simulator app, then
archives the release build, signs it (Xcode makes the distribution certificate and profile itself
through the API key) and uploads it. The build number is the run number, so every upload is new.

After 5 to 30 minutes Apple finishes processing and the build appears in App Store Connect →
your app → **TestFlight**. Add yourself under **Internal Testing**, install the **TestFlight** app on
the iPhone, and accept the invite.

## When something fails

| Message | Fix |
|---|---|
| `Secret ... is missing` | Add it (step 5); names are case-sensitive |
| `No Accounts` / `Authentication failed` | Check the key id, issuer id and the full `.p8` text; the key must have App Manager access |
| `No profiles for 'app.porchlight.game' were found` | Register the bundle id (step 1) under the same team as `APPLE_TEAM_ID` |
| `The bundle version must be higher` | Re-run: each run gets a new number; or bump `MARKETING_VERSION` in `iosApp/project.yml` |
| A missing-compliance warning in TestFlight | Shouldn't happen: `ITSAppUsesNonExemptEncryption` is false in the Info.plist |

To release on the App Store later, the same build is submitted from App Store Connect, with the
store listing, screenshots (the workflow's simulator screenshots are a start) and the privacy
answers: the app collects no data and does no tracking (see `iosApp/iosApp/PrivacyInfo.xcprivacy`).
