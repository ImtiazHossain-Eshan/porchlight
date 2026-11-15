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

- **Display:** Alfa Slab One (`res/font/alfa_slab_one.ttf`, OFL). Signs, titles, scores, the countdown. One weight.
  - `Carved` (dark with a light lower edge) on oak and brass.
  - `Gilt` (light with a dark lower edge) on dark timber.
- **Label:** system sans, bold, 11 sp, 1.4 sp tracking, capitals. Plate captions and tile names.
- **Body:** system sans, 13–16 sp. All sizes are in sp.

## Materials (`Materials.kt`)

| Modifier | Is | Used for |
| --- | --- | --- |
| `wood(Wood.Oak)` | the lightest wood | signs you press, the menu title plaque, the pause sign |
| `wood(Wood.Walnut)` | panels | menu tiles, secondary signs, boards behind lists |
| `wood(Wood.Ebony)` | the darkest wood | card tiles, number plates |
| `metal()` | brass; `copper = true`; `dull = true` is iron | round buttons, medallions, night markers, studs |
| `parchment()` | paper with stains and an inked hairline | the night map, notices, hints, the daily record, badge details |
| `velvet()` | plum, lit from the middle | lead-moth showcases only |
| `gilded()` | navy, double gold rule, corner flourishes | the scorecard only |

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
- `LevelBar`: level in a brass medallion, XP as an amber fill in a brass tube.
- `Star`: bevelled gold; an unearned star is an empty iron socket.
- `BadgeMedal`: scalloped rim, sunken face, the badge's mold; bronze, silver or gold, gold with a gem; iron until earned.
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
- Menu content is capped at 560 dp wide. Verified at 360 × 640 dp and on a tall phone, both orientations.
- Touch targets are 48 dp or larger.

## Fallbacks (`Shaders.kt`, `Gpu`)

- Below Android 13: no shaders. The glow is a gradient and the foil is a sliding sheen; state colours still reach the lamp and cards.
- Below Android 12: no blur, so no bloom and no blurred board behind the scorecard.
- If frames run long, bloom drops to half resolution, then particles thin out.

## Not done

- The concept art is painted; this is a vector rendition of it. Moths are flat shapes, not painted fur.
- The daily screen shows the player's own best days. The game has no server, so it has no ranking against other players.
