# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Casual players of all ages, children included (a mixed audience under Google Play's Families policy, COPPA and GDPR-K), filling a spare minute on their phone. They play one-handed, in short bursts, often somewhere noisy or dim, and should never need to read a rule to start. (Confirmed by the owner, 2026-11-14.)

## Product Purpose

Porchlight is a memory-matching game. The player gets a short look at a board of moth cards, the cards turn face down, and the player finds every pair before the porch bulb burns out. A round lasts 30 to 60 seconds. Success is a player finishing a round and wanting another one.

## Positioning

The bulb is the clock. There is no timer bar: the light itself dims, changes colour, flickers and finally pops. Found moths leave the board and circle the bulb, so progress is visible as a flock around the light.

## Operating Context

- Rounds are played in one sitting; a round pauses when the app goes to the background.
- Before a round the player picks a lead moth (each bends one rule) and a night (difficulty).
- A daily porch deals the same cards to everyone on a given date, one scored try a day.
- There is no account and no server: progress and the Fireflies wallet live on the device and in Android Auto Backup.
- Fireflies are the in-game currency (since 0.7, owner decision 2026-11-19): earned by playing, spent on cosmetics, one-round boosts, progress skips and lead moths early. Every number is in `Economy.kt`; `docs/ECONOMY.md` explains it.
- Rewarded ads only (Google AdMob), and only when the player taps a button that says what it gives. No interstitials, no banners, ever. A neutral year-of-birth screen decides how ads are asked for: under 13 child-directed and rated G, under 16 never personalised.
- Fireflies packs, a starter pack and Supporter can be bought through Google Play Billing, behind a grown-up check. No loot boxes, nothing random for money, no subscriptions.
- The network is used only by the ad SDK and Google Play. Android only.

## Capabilities and Constraints

- Kotlin and Jetpack Compose, single activity, no game engine and no WebView. minSdk 24, targetSdk 36.
- All artwork is drawn in code (Compose Canvas and AGSL shaders). The owner chose this over shipped image files (2026-11-14).
- Game rules live in `Game.kt` as pure Kotlin and are unit-tested; progression lives in `Progress.kt`.
- No backend exists, so anything that compares players (leaderboards, rankings) cannot be real. Screens may show the player's own history only.
- Terms: lead moth, night, twist (Wanderer, dead leaves, Shadow), Overdrive (the ×4 streak), mastery (a gold moth), daily porch, Fireflies (the currency; `Gems` in code), boost, look (a cosmetic), Porch shop.

## Brand Commitments

- Name: Porchlight. Tagline: "The bulb is the clock."
- The owner supplied concept art and a 12-screen walkthrough (2026-11-14) and made them binding for look and content: a cozy-craft lobby with a mason-jar bulb, a steampunk night map on parchment, wooden panels, velvet-lined showcase jars for lead moths, embossed card frames, bioluminescent match bursts, golden Overdrive, a red critical-bulb state, gilded Victorian results, and metal badges.
- The owner asked for the game's content to follow the walkthrough where they differ (12 badges, a 3×2 tutorial, a lead moth named Luna).
- Voice: short, warm, plain. Buttons say what they do ("Light the porch", "Again", "Porch").

## Evidence on Hand

- Concept art: three boards supplied in conversation (not stored in the repo).
- Real screenshots and a gameplay clip: `docs/screens/`, `docs/store/`, `docs/visuals/`.
- No testimonials, player counts, ratings or rankings exist. Do not invent them.

## Product Principles

1. The board comes first. Nothing may cover a card the player still has to find.
2. Light is the interface. Time, danger and reward are shown by the bulb and what it lights, not by meters.
3. One tap, no reading. Every rule must be learnable by playing.
4. Kind by default. Pause any time, no penalties for leaving, no lives or energy, no pressure to pay or sign in. Money never buys stars (any boost caps a round at 2) or any edge on the daily porch, and every price shows the balance after it with one confirm tap: no countdowns, no fake discounts.
5. It must run smoothly on a mid-range phone; effects step down before the frame rate does.

## Accessibility & Inclusion

- Reduce motion (in-game switch and the system animation setting) turns off shake, tilt, sway, shimmer and particles but keeps colour changes.
- Every moth is distinguishable by shape or pattern under deuteranopia, protanopia and tritanopia; colour is never the only cue.
- Touch targets are at least 48 dp. Sound, ambience and vibration each have their own switch.
- Cards and controls carry spoken descriptions for screen readers.
