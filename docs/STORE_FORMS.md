# Google Play forms: notes for the owner

How to fill in the Play Console forms for Porchlight 0.7 (the Fireflies economy). These are notes
from the code as built; check them against the current wording of each form before submitting.

## Store listing

- **Contains ads: Yes.** (Rewarded ads only, opt-in.)
- **In-app purchases: Yes.** Price range per item as set in Play Console.
- The full description's "Made to be kind" paragraph has been updated in `docs/store/listing.md`.
- **Privacy policy URL:** where `docs/PRIVACY.md` is hosted. Build release APKs with
  `PRIVACY_POLICY_URL` set to the same address so the in-app link matches.

## App content

### Target audience and content

- **Target age groups:** all of them, including **under 5, 6-8, 9-12** and 13+. This is a
  **mixed audience** app (children are one of the audiences, not the only one).
- **Appeals to children:** yes.
- **Neutral age screen:** yes. A year-of-birth picker on first launch, nothing chosen and nothing
  suggested; the answer only decides how ads are requested.
- Join **Families policy** requirements: the app meets them as built (see the questionnaire below).

### Families policy questionnaire (answers as built)

| Question | Answer |
|---|---|
| Ads SDKs | Google Mobile Ads SDK (AdMob) 25.5.0 and User Messaging Platform 4.0.0: both on Google Play's **Families self-certified ads SDK** list. No other ad or analytics SDK. |
| Ad formats | **Rewarded only**, shown only when the player taps a button that states the reward. No interstitials, no banners, no app-open ads. |
| Ads to children | Under 13: `tagForChildDirectedTreatment = true`, `tagForUnderAgeOfConsent = true`, `AgeRestrictedTreatment.CHILD`, max content rating **G**, personalisation **disabled**. |
| Ads to everyone else | Max content rating **PG**. 13-15: under age of consent, personalisation disabled. 16+: UMP consent where required (EEA/UK); elsewhere non-personalised. |
| Interest-based ads / remarketing to children | None. |
| Purchases | Behind a grown-up check (a number in words typed as digits) before every purchase screen and every link out of the app. Never in front of a rewarded ad. |
| Loot boxes / random paid rewards | None. Every pack states exactly what it gives. |
| Account / login | None. |
| Location, contacts, camera, microphone | None requested. |
| `AD_ID` permission | Merged in by the ads SDK (mixed audience). It is not used for children: their requests are child-directed. If Play asks, declare it is used for **advertising**, for users 13+ only. |

### Content rating (IARC)

Answer the questionnaire as a puzzle/card game with: no violence, no gambling, **users can make
digital purchases** (yes), **ads** (yes), no user interaction or chat, no location sharing.

### Ads declaration

**Yes, the app contains ads.**

### Data safety

No data is collected by Porchlight itself; what is declared below is collected by the Google SDKs it
uses. Data is **encrypted in transit** (the SDKs use HTTPS). Users **can request deletion** by
uninstalling or clearing app storage (there is no account to delete).

| Data type | Collected | Shared | Optional | Purpose | Notes |
|---|---|---|---|---|---|
| Device or other IDs (advertising ID) | Yes | Yes (Google, for ads) | Yes: only when the player taps to watch an ad | Advertising or marketing, Fraud prevention | AdMob. For players under 13 requests are child-directed and never personalised. |
| App interactions (ad interactions) | Yes | Yes (Google) | Yes | Advertising, Analytics (ad measurement) | AdMob's own measurement of the ads shown. |
| Diagnostics (crash logs from the ads SDK) | Yes | No | Yes | App functionality | AdMob SDK. |
| Approximate location (from IP) | Yes | Yes (Google) | Yes | Advertising, Fraud prevention | Derived by Google from the IP address of ad requests. |
| Purchase history | Yes | No | Yes: only if the player buys something | App functionality | Google Play purchase tokens, kept on the device only to grant each purchase once and restore it. |
| Personal info, financial info, messages, photos, contacts, location (precise) | No | No | | | |

Notes:
- **Data is not sold.**
- The year of birth stays on the device and is not collected or shared.
- Progress, the Fireflies wallet and settings stay on the device (and in Android Auto Backup).

### Government apps, news, health, financial features

Not applicable.

## In-app products (Monetize > Products > In-app products)

Create these **one-time products** (product IDs must match `Product` in `Purchases.kt`). Set the US
price below and let Play convert to local price tiers.

| Product ID | Name | Type in code | US price | Gives |
|---|---|---|---|---|
| `fireflies_handful` | Handful | Consumable | $0.99 | 100 Fireflies |
| `fireflies_jar` | Jar | Consumable | $4.99 | 550 Fireflies |
| `fireflies_lantern` | Lantern | Consumable | $9.99 | 1,200 Fireflies |
| `fireflies_porchful` | Porchful | Consumable | $19.99 | 2,600 Fireflies |
| `starter_pack` | Starter pack | One-time (acknowledged, not consumed) | $1.99 | 300 Fireflies + Firefly lacquer card back |
| `supporter` | Supporter | One-time (acknowledged, not consumed) | $4.99 | Daily gift doubled + Golden hour porch |

The shop shows Play's own local prices. The "% more than handfuls" it shows is worked out from those
prices and rounded down, so it is always true (on US prices: 9%, 18% and 28%).

### Testing purchases

Debug builds can't buy from Play unless the app is on a Play track (internal testing is enough) and
the tester's account is a **licence tester** (Play Console > Settings > License testing). Then the
real product IDs above complete with test cards and no charge. On an emulator or a build that isn't
on a track, Play answers `DEVELOPER_ERROR`; `--ez fakeStore true` (debug builds only) swaps in a
stand-in till that runs the same grant path without charging.

Set `PLAY_LICENSE_KEY` (Monetize > Monetization setup > Licensing) when building releases so each
purchase's signature is checked on the phone.

## AdMob

- Create the app in AdMob, then one **Rewarded** ad unit. Build releases with `ADMOB_APP_ID` and
  `ADMOB_REWARDED_ID` set; without them the release falls back to Google's test ids (safe, but earns
  nothing). Debug builds always use Google's test ids.
- In AdMob > App settings, mark the app as **designed for families / mixed audience** and set the
  **max ad content rating to PG** (the app asks G for children itself).
- In AdMob > Privacy & messaging, create a **GDPR (EEA and UK) consent message** for this app; the
  UMP SDK shows it.
