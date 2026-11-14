# Porchlight: redesign prompts

Copy-paste prompts for an AI coding agent (or a designer). Run them in this order: 0 is the master prompt and the rest break it into pieces. Each prompt stands on its own.

Ground rules for every prompt:
- Keep it a **simple** game: one tap mechanic, rounds under 60 seconds, no accounts, no ads, no IAP.
- Kotlin + Jetpack Compose, no game engine. Draw art with Compose Canvas like `Moths.kt` already does.
- Keep `Game.kt` pure and unit-tested. Add tests for every rule change.
- Must run at 60fps on a mid-range Android phone (Android 7+).

---

## 0. Master prompt (the whole overhaul)

```
You are redesigning "Porchlight", a Kotlin/Jetpack Compose memory-matching game for Android
(repo: porchlight-android). Read Game.kt, GameScreen.kt, Moths.kt, Progress.kt and Sfx.kt first.

The core idea is good: moths gather at a porch bulb, and the bulb is the clock. The execution
reads like a settings page, not a game. Rebuild the presentation and the loop around these goals:

1. SCREENS, NOT A SCROLLING PAGE. Replace the single verticalScroll column with three full-screen
   states: Porch (home), Round (play), Results. Nothing scrolls during play. The board, the bulb
   and the score must fit on a 360x640dp screen with no scrolling.
2. NIGHT, ALWAYS. The game is set at night. Drop the mint daylight palette. The play screen is a
   dark night porch; the bulb throws warm light. Light, not UI chrome, is the visual language.
3. THE BULB IS THE HERO. The bulb is large, at the top centre, and it IS the timer: its glow
   radius, brightness and colour temperature fall as time runs out; it flickers under 5 seconds;
   it pops and goes dark when time ends. Remove the red progress bar and the "34.0s" headline.
4. CARDS MUST READ AT A GLANCE. Face-down, face-up and matched states must differ in colour,
   brightness AND shape/motion. Matched moths should leave the card and fly to the bulb.
5. JUICE. Every tap gets an animation, a sound and a haptic. Streaks escalate the feedback.
6. DEPTH. Add a difficulty curve (more cards per level), a daily porch with a fixed seed,
   and a reason to come back after all six moths unlock.
7. ROBUSTNESS. Pause the round when the app goes to the background. Save the round across
   process death. Make the launcher icon adaptive.

Do the work in the order of the numbered sections in docs/REDESIGN_PROMPTS.md (1 to 9).
After each section: build, run unit tests, and describe what changed.
```

---

## 1. Structure: split into real screens

```
Porchlight currently renders everything in one scrolling Column (GameScreen.kt): title, HUD,
board, results, lead picker, perk text, button, status line, badges. Break it into three screens
driven by a sealed `Screen` state (no navigation library needed):

PORCH (home)
- Full-bleed night scene: the porch wall, the bulb (off or dim), the moth roster resting on the
  wall around the bulb. Unlocked moths are drawn in colour; locked ones are grey silhouettes
  with "LV n".
- Tap a moth to make it the lead: it flutters to a spot beside the bulb and its perk appears in
  one short line ("Long-life bulb: +4 seconds").
- One big primary button: "Light the porch". Under it: best score, level, a small badges button.
- Badges open as a bottom sheet, not an inline list.

ROUND (play)
- Top: the bulb (big, centred, about 25% of screen height) with score left and streak right.
- Middle: the board, sized to fill the remaining space. No scrolling.
- No title text, no lead picker, no disabled "Round in play" button, no badge list.
- Atlas's "Second look" becomes a small round icon button by the bulb, not a full-width button.
- The status line ("A pair of Atlases, times 4.") becomes short floating text near the action,
  or is removed and kept only as an accessibility live region.

RESULTS
- A full-screen overlay that slides up over the dimmed board.
- Order of reveal, each step 300-400 ms apart: stars one by one -> score counting up from 0 ->
  XP bar filling -> level up banner -> new moth flying in -> new badge stamped in.
- Two buttons: "Again" (primary, same lead) and "Porch" (secondary).
- Tapping anywhere skips the animation to its end state.

Keep Game.kt as is in this step. Only GameScreen.kt is split (Porch.kt, RoundScreen.kt,
Results.kt, Hud.kt). Back from Round asks "Leave this round?"; back from Porch exits.
```

## 2. Visual direction: a night porch, not a mint app

```
Restyle Porchlight. Art direction: "warm bulb at night, cozy picture book". Think a dark
blue-black wall with wood siding, a warm amber pool of light around the bulb, soft vignette at
the edges, and slow-moving dust motes in the light.

Palette (replace both Palette objects in GameScreen.kt with one night palette):
- Night wall: #0E1A24 to #16283A vertical gradient, with faint horizontal plank lines (#1C3346,
  1dp, every 28dp).
- Bulb light: radial gradient centred on the bulb, #FFD27A at 0% alpha 0.55 -> #FF9F43 alpha 0.18
  at 45% -> transparent at 100%. Its radius scales with time left.
- Card back: deep slate #1E3446 with a thin #2E4A60 border and an embossed folded moth in
  #2A465C. Cards close to the bulb are lit warmer than cards far away (sample the light gradient).
- Card face: warm paper #FFF4DA.
- Matched card: card becomes an empty, darker slot with a faint golden outline; the moth itself
  has flown to the bulb.
- Accent: amber #FFB938 for score and multipliers, ember red #FF5A3C only for danger (low time).
- Never use pure mint/teal daylight backgrounds again.

Typography: bundle one display font with personality (for example "Fredoka" or "Baloo 2",
OFL licensed, put the TTF in res/font) for numbers, titles and bursts. Keep the system sans for
small body text. Score digits use tabular figures so they don't jitter while counting.

Cards: 16dp corner radius, 3:4 ratio, soft drop shadow tinted toward #000814. Add a subtle 1dp
top highlight so they look like physical cards.

Add a vignette (radial black, 0 -> 0.45 alpha at the corners) drawn above the wall and below
the cards.

Deliver: screenshots of Porch, Round mid-play, Round with <5 s left, Results.
```

## 3. The bulb as the timer

```
Make the porch bulb the centre of the play screen and the only timer.

- Size: about 25% of screen height, centred at the top. Draw it with Canvas (extend BulbArt).
- Let t = timeLeft / roundTime.
  - Glow radius = lerp(0.6, 1.6, t) * bulb width.
  - Glass colour shifts from warm white #FFF3C4 (t=1) to amber #FFB347 (t=0.4) to deep orange
    #E8642B (t<0.15).
  - The filament glows brighter with t; at low t it pulses.
- Under 5 seconds: random flicker (glow alpha drops to 30% for 40-90 ms, 2-4 times per
  second), a soft electrical buzz sound, and the background vignette tightens.
- Time out: bright pop flash (white overlay, 120 ms), tinkling glass sound, bulb goes dark,
  all moths scatter off-screen, then results.
- Sorbet's +1.5 s per pair: the bulb visibly "breathes" brighter for 300 ms and a small "+1.5s"
  floats off it.
- Show a thin arc around the bulb as a precise readout for players who want numbers, and keep the
  seconds as a small number under the bulb (one decimal only below 10 s).
- Remove the red linear progress bar and the big "34.0s" text.

Moths that are matched fly in a curved path (cubic bezier with a little wobble) from their card to
the bulb and then circle it slowly for the rest of the round. By the end, all six pairs orbit the
light. This is the game's signature image: make it look great.
```

## 4. Cards and moth readability

```
Fix card readability in Porchlight. Today face-up and matched cards are nearly the same yellow
(tileUp #FFF7DC vs tileMatch #FFE27A), and Ember and Sorbet share pink/orange/yellow, which is
bad in a memory game.

1. Three clearly different card states:
   - Face down: dark slate back with the folded-moth emblem.
   - Face up (unmatched, 1 or 2 open): warm paper face, card lifted (scale 1.06, bigger shadow).
   - Matched: the moth flies away (see the bulb prompt); the card turns into an empty dim slot
     with a gold rim. The board visibly empties as you progress.
2. Give each moth one dominant hue that no other moth uses, so the colour alone identifies it:
   Wick = pale green, Ember = red-orange, Atlas = rust brown, Dot = white with black dots,
   Iris = violet, Sorbet = change to a strong sky blue/cyan with a yellow body (or keep pink
   and make Ember fully red/black). Check the six at 48dp side by side and in a
   deuteranopia simulation; every pair must still be distinguishable by shape or pattern.
3. Wrong pair: both cards tint red for 150 ms, shake (already exists), the moths flinch (wings
   snap closed), then flip back.
4. Flip: keep the rotationY flip but add a slight lift (translationY -6dp) and a camera distance
   so it doesn't look flat. Wings open AFTER the card faces you (they already animate; delay
   them 120 ms so it reads as the moth waking up).
5. Idle moths on face-up cards should gently flap (wing open 0.9 <-> 1.0, 1.2 s loop, random
   phase per card).
6. Touch target: the whole card; tap feedback (scale 0.96) happens on press, not on release.
```

## 5. Game feel and juice

```
Add game feel to Porchlight. Every action needs visual + sound + haptic feedback, and streaks
must escalate it.

- Flip: soft paper flip sound with random pitch +/-6%; light tick haptic.
- Match x1: chime + small gold spark burst from both cards (8-12 particles, 400 ms).
- Match x2: higher chime, bigger burst, score text "+200" pops from between the two cards
  (not over the middle of the board).
- Match x3: chime + bass note, burst + brief bulb flare, screen-edge warm glow pulse.
- Match x4 OVERDRIVE: 80 ms hit-stop, a short camera shake (4dp), the bulb flares, a gold ring
  shockwave from the pair, "OVERDRIVE" in the display font sliding in from the side, strongest
  haptic. Keep the overdrive glow on the streak counter until the streak breaks.
- Streak break: the streak counter cracks and drops back to x1, with a low "thunk".
- Score counter animates (counts up quickly) instead of jumping.
- Particles: write one small Compose Canvas particle system (pool of ~200 particles,
  position/velocity/life/colour) and reuse it everywhere. No allocations per frame.
- Burst text must never cover face-down cards the player is trying to remember. Place it
  above the matched pair and fade it within 700 ms.
- Respect a "Reduce motion" toggle (and the system animator duration scale): turn off shake,
  hit-stop and particles but keep the colour changes.
```

## 6. Sound and atmosphere

```
Upgrade Porchlight's audio. Sfx.kt synthesizes simple tones (square/triangle/sine), which sounds
like a 90s alarm clock. Keep synthesis if you want zero assets, but make it warmer:

- Ambience loop: night crickets + a very soft bulb hum, low volume, starts on the Porch screen.
  Either synthesize (filtered noise bursts for crickets) or ship one small OGG (<200 KB, CC0).
- Replace square waves in Miss and LevelUp with sine/triangle + a short low-pass envelope.
  Square waves at 1900 Hz (Tick) are harsh: use a soft wooden click (~800 Hz, 30 ms).
- Match chimes should form a rising musical scale within one key (C major pentatonic:
  C5 D5 E5 G5 A5 C6) as the streak grows, so a perfect round sounds like a melody.
- Add a gentle 2-3 note music sting for Win and a descending one for Lose.
- Add a separate Music/SFX toggle on the Porch screen. Mute state persists (already does).
- Use SoundPool or a small mixer so overlapping sounds don't cut each other off.
```

## 7. Gameplay depth and balance

```
Porchlight's round is always 12 cards, 6 pairs, ~30 s. After a few rounds it's solved, and
after level 6 there is nothing left to earn. Add depth while keeping it a simple tap game.

1. Difficulty ladder ("Nights"): Night 1 = 3x4 (6 pairs). Night 2 = 4x4 (8 pairs, needs two
   new moth designs). Night 3 = 4x5 (10 pairs). Time scales with pairs (about 5 s per pair).
   Beat a night with 2+ stars to unlock the next. Add new moths to Moths.kt in the same style.
2. Twists that appear on higher nights (one at a time, introduced with a one-line tip):
   - Wanderer: after each wrong pair, two face-down cards swap places (animated, visibly).
   - Moth-eaten card: one card is a "dud" (a dead leaf); flipping it costs 2 s.
   - Shadow: a moth is shown as a silhouette only during the peek.
3. Daily Porch: a seeded deal (seed = date) that's the same for everyone today, one scored
   attempt, a streak counter for consecutive days, and a "share" text with emoji stars.
   The deal must stay deterministic: make `deal()` accept a Random.
4. Rebalance the lead moths. Today Sorbet (+1.5 s x 6 = +9 s) beats Wick (+4 s) on time, and
   Dot's 800->400 ms flip-back barely matters. Proposed:
   - Wick: +5 s.
   - Ember: streak starts at x2, but the round is 3 s shorter.
   - Atlas: one Second look (900 ms).
   - Dot: wrong pairs fold back instantly AND the first miss doesn't break the streak.
   - Iris: peek 3.4 s.
   - Sorbet: +1 s per pair.
   Write a unit test that simulates a perfect player and a typical player (30% miss rate) for
   each lead and asserts no lead wins on both score and time by more than 15%.
5. Post-roster goals: per-moth mastery (win 3 stars with each lead -> golden variant of that
   moth for the card art), and 12+ badges with clear names and icons.
6. Keep every rule in Game.kt pure; add tests for each new rule.
```

## 8. Onboarding and UX copy

```
Add onboarding to Porchlight and tighten the copy.

- First launch: an interactive 15-second tutorial round with 4 cards (2 pairs). Hand pointer
  shows "Tap a card", then "Find its twin". No text walls. Skippable.
- Introduce each new twist and moth once, with one line and an animation, the first time it
  matters.
- Button and label copy: "Start the round" -> "Light the porch". "Change the bulb" -> "Again".
  "Round in play" (disabled button) -> remove. Keep the moth voice short and warm.
- Remove the permanent title "Six moths. One bulb. Beat the clock." from the play screen;
  show it once on the Porch screen as the tagline.
- Locked moth tiles: tapping one says "Reach level 5 to meet Iris" with the moth silhouette,
  not just a buzz sound.
- Every number on screen needs a reason to be there. During play show only: bulb (time),
  score, streak. Pairs count is shown by the board itself (matched slots).
```

## 9. Android polish and robustness

```
Fix Android-level issues in Porchlight:

1. PAUSE: the round clock uses SystemClock.elapsedRealtime and an `endsAt` timestamp, so if
   the player gets a call or switches apps, the bulb keeps burning and they come back to a loss.
   Observe the lifecycle (LifecycleEventObserver / LocalLifecycleOwner): on ON_PAUSE store
   timeLeft and freeze; on ON_RESUME show a "Paused - tap to continue" overlay, then set
   endsAt = now + timeLeft. Add a pause button to the HUD. Add Game.kt pause()/resume() with tests.
2. STATE: save the current Round with rememberSaveable (or a ViewModel + SavedStateHandle)
   so process death doesn't lose a round.
3. ICON: ic_launcher.xml is a plain vector, so Android 8+ launchers shrink it into a white
   shape. Make an adaptive icon (mipmap-anydpi-v26/ic_launcher.xml with separate background
   and foreground layers, foreground art inside the 66dp safe zone) plus a monochrome layer
   for Android 13 themed icons. Make the icon show a glowing bulb with one moth, not just a bulb.
4. SPLASH: use the androidx core-splashscreen API with the night background and the bulb.
5. EDGE TO EDGE: draw the night scene behind the status and nav bars; keep only the HUD inset.
6. SCREENS: support 360dp phones up to tablets. On tablets/landscape, put the board beside the
   bulb instead of locking portrait.
7. HAPTICS: respect the system haptic setting; add an in-game toggle.
8. PERFORMANCE: profile with a baseline profile; no allocations in draw loops; target 60fps on
   a mid-range device during Overdrive with particles.
9. STORE READINESS: real screenshots (the "peek" screenshot in docs/screens shows the board face
   down, not the peek), a 15 s gameplay video, a feature graphic, and a store description that
   leads with the bulb-as-timer hook.
```
