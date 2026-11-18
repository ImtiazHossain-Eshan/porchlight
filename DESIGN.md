# Design

Recorded from the built app on 2026-11-15. This describes what the code draws, not an intention. The source of each rule is named so it can be checked.

## The world

A porch at night, built by hand. Every surface is a made object drawn in code: timber, brass, copper, parchment, velvet and glass. No image files ship. The owner's concept art pins this world (see `PRODUCT.md`, Brand Commitments).

**Light is state.** The lamp and everything it lights take one of five colours, set in `GameScreen.kt` as `session.hue`:

| State | Colour | Where |
| --- | --- | --- |
| Burning | warm white → amber → deep orange with time left | default |
| Cards shown | teal `Craft.bio` | the peek; the rim of every face; sparks on a found pair |
| Overdrive | gold `Craft.gold` | ×4 streak: the lamp, the streak plate's rim, the MATCH pop |
| Last 5 seconds | red `Craft.danger` | lamp, red edge on the wall, a wash on the cards, the readout |
| Off | none | lost round; menu screens |

## Colour (`Theme.kt`)

- **Wall:** `Dusk.wallTop #10171F` → `wallBottom #1A242E`, planks from `Wood.Night`.
- **Text on dark:** `Dusk.ink #F6E8CC`; secondary `Dusk.inkSoft #C4AC88` (tinted from the timber, never grey).
- **Text on parchment:** `Craft.parchmentInk #3B2A17`; secondary `parchmentInkSoft #6F5636`.
- **Text on oak:** `Dusk.paperInk #2E2014`.
- **Numbers and highlights:** `Dusk.amber #FFC24A`.
- Metals: `Craft.brass`, `brassLight`, `brassDark`; `copper`, `copperLight`, `copperDark`; `iron` for anything locked or off.

## Type

Everything is in `Theme.kt`. Nothing is set outside the scale, and no card or panel uses more than three sizes.

- **Display:** Alfa Slab One (`res/font/alfa_slab_one.ttf`, OFL), only for big words: screen titles, the Results headline, the score, the level number, OVERDRIVE, the peek count and GO!. ALL CAPS only here.
- **Body / UI:** Fredoka (`res/font/fredoka.ttf`, OFL), a rounded sans at 400, 600 and 700. Every label, line and button caption. It is provided app-wide, so a bare `Text` is already right.
- **Numbers:** tabular figures everywhere (`tnum`), so a changing number never jitters.
- **Scale (`TypeScale`):** Hero 56 (the score, the peek count), Title 28 (headlines, screen signs), Heading 18, Body 15, Label 11 (capitals, +1.5 sp tracking), Micro 9 (the share card only).
- **Treatments, one per surface:**
  - `TextCarved`: painted on wood (screen signs, plank buttons). Dark ink with a light edge below; amber paint with a dark cut above on dark walnut.
  - `TextGlow`: lit by the bulb (the score, OVERDRIVE, the Results headline). Amber gradient #FFE08A → #F2A93B with a soft glow, never a black shadow. Pass no colour with it. `glowOf(colour)` lights another colour the same way.
  - `TextEngraved`: cut in metal (the level number, badge text, seals). A light edge below; the emblem also cuts a dark edge above.
  - `TextInk`: on paper. Plain ink.
  - `FitText`: one line that steps down only as far as it must (a big font scale on a narrow phone). Signs and plank buttons.
- **Copy:** sentence case for sentences, capitals for labels. Numbers grouped ("3,240"). Never "none", "+0" or "0 of 0": hide the line. Say what's next ("172 XP to level 12", "2 badges to go", "312 short of your best").
- **The name (`TitleSigns.kt`):** PORCHLIGHT is cut into a dark board and gilded, hung on two chains, swinging gently with a glint; two small moths (Sulphur and Rose) loop round it and now and then perch on its top edge. Still under Reduce motion.
- **Motion (`TextMotion.kt`):** `CountUp` (odometer digits), `PunchIn` (scale 1.3 → 1), `NeonOn` / `NeonOff` (letters light up; a loss flickers and cools to ember), `Stamp` (1.6 → 1 with a 3 dp shake). All fall back to a plain fade with reduce motion.

## Materials (`Materials.kt`)

| Modifier | Is | Used for |
| --- | --- | --- |
| `wood(Wood.Oak)` | the lightest wood | signs you press, the menu title plaque, the pause sign |
| `wood(Wood.Walnut)` | panels | menu tiles, secondary signs, boards behind lists |
| `wood(Wood.Ebony)` | the darkest wood | card tiles, number plates |
| `metal()` | brass; `copper = true`; `dull = true` is iron | round buttons, medallions, night markers, studs |
| `parchment()` | paper with stains and an inked hairline | the night map, notices, hints, the daily record, badge details |
| `velvet()` | plum, lit from the middle | lead-moth showcases only |
| `gilded()` | navy, double gold rule, corner flourishes | not used now: the scorecard is full screen, on ebony plates and walnut boards with brass |

`plank()` adds the shadow and clips. Ornaments: `gear()`, `rivet()`, `pipe()`.

Rules that hold across the app:
- The primary action on a screen is one oak sign (`PrimaryButton`). Secondary actions are walnut (`SecondaryButton`).
- Elevation is a real shadow with an offset; panels do not also carry a soft halo.
- Card and panel corners are 8–16 dp. Only round buttons and medallions are circles.
- `velvet` and `gilded` are each reserved for one place. Do not reuse them for ordinary panels.

## Icons (`Icons.kt`)

One set, `Glyph`: a 24-unit box, one 2-unit round stroke. No text glyph or emoji stands in for an icon anywhere.

## Components (`Hud.kt`)

- `plate()`: ebony with a brass rule. Score, streak, daily figures, the tutorial's Skip.
- `BrassButton`: a 48 dp brass disc with a glyph; iron when disabled.
- `Emblem` (`Emblem.kt`): the level as a moth medallion: a metal moth with its wings spread and the number on a struck disc over its body. Bronze for levels 1–4, brass with amber eye-spots 5–9, gold with swallow tails 10–19, bright gold with rays and a shine every 4 s from 20. XP is warm light filling the wings outward from the disc, with a glowing edge. Every few seconds it gives one soft wingbeat; the level-up spins it once in 3D, spreads the wings open, flips the number split-flap style and throws sparks. Used in the top bar (`LevelPlate`, swaying slowly beside a brass XP gauge), Results, the share card and the Moths screen.
- `Star`: bevelled gold; an unearned star is an empty iron socket.
- `BadgeMedal` (`Emblem.kt`): scalloped rim, sunken face, and each badge's own mark (no two share one). Bronze is warm copper, silver is cool with a bright shine line, gold is warm with a glow. Not yet earned it is a dark outline only. A new badge stamps in, a shine crosses it and its `TierRibbon` unfurls; on the Badges screen it wears a glowing dot until seen.
- `LeverSwitch`: a brass knob in an ebony slot that glows amber when on. It is a switch to screen readers.

## The lamp (`Bulb.kt`)

- `Lamp.Jar`: the lobby. A mason jar with a filament, hung by a wire.
- `Lamp.Works`: the round. An Edison bulb with two turning gears and a pressure gauge whose needle is the time left; the gauge's last sixth is red.

## Screens

| Screen | File | Structure |
| --- | --- | --- |
| Lobby | `Porch.kt` | level bar and settings; logo; jar with the leads around it; lead line; oak sign; four tiles |
| Six nights | `Menus.kt` | parchment map over gears; copper pipe to the nights reached; detail panel; oak sign |
| Daily porch | `Menus.kt` | calendar leaf; a week of studs; three figures; the player's own best days; oak sign |
| Lead moths | `Menus.kt` | one velvet showcase per lead: jar, name, pips for time, look and streak, perk |
| Badges | `Menus.kt` | 3 × 4 castings on walnut; the picked one explained on parchment |
| Settings | `Menus.kt` | a walnut switchboard of four levers; replay the tutorial |
| Round | `RoundScreen.kt`, `Cards.kt` | lamp, score and streak plates, the band, the board |
| Scorecard | `Results.kt` | gilded panel: stars, score, bonus table, level gauge, rewards strip, two signs |

Menu screens share `MenuFrame`: a brass back button and the title on an oak plaque.

**The band** under the lamp shows one thing at a time: READY 3·2·1 in the peek, SWAP on a Wanderer move, OVERDRIVE at ×4. Nothing in the band, and no effect, may cover a card the player still has to find.

## Cards (`Cards.kt`)

- **Face down:** an ebony tile, embossed brass frame, four studs, a brass folded moth, a faint gold foil.
- **Face up:** a parchment sheet set into the frame; lifted, with the moth flapping.
- **Found:** an empty, recessed socket with only a dull trace of the frame. It must recede.

## Motion

- Everything touched moves on a spring: signs and buttons squeeze, cards tilt toward the finger, flips overshoot and settle.
- Screens change with a 220 ms fade through the wall.
- Authored moments: the lobby light flickering on; the scorecard springing up with the level number flipping; the pause sign dropping on its chains; found moths rising out of their cards and circling the lamp, passing behind it on the far side.
- Reduce motion (the in-game switch or the system's animation setting) removes sway, tilt, shimmer, the colour-split pulse, the swap swirl and particles. Colour changes stay.

## Layout

- Edge to edge; content keeps to the safe area.
- Portrait: lamp above the board. Landscape and wide screens: lamp beside the board; lobby stage beside its controls.
- Menu content and the lobby's sign and tiles are capped at 560 dp wide.
- Beside the board, the round's header is a tall column: the lamp grows to fill it and the plates and band sit under it.
- Lobby moths scale with the stage, up to 150 dp for the lead on a tablet.
- Verified at 360 × 640 dp, on a tall phone, and at 800 × 1280 dp (tablet), each in both orientations.
- Touch targets are 48 dp or larger.

## Fallbacks (`Shaders.kt`, `Gpu`)

- Below Android 13: no shaders. The glow is a gradient and the foil is a sliding sheen; state colours still reach the lamp and cards.
- Below Android 12: no blur, so no bloom and no blurred board behind the scorecard.
- If frames run long, bloom drops to half resolution, then particles thin out.

## Not done

- The concept art is painted; this is a vector rendition of it. Moths are flat shapes, not painted fur.
- The daily screen shows the player's own best days. The game has no server, so it has no ranking against other players.
