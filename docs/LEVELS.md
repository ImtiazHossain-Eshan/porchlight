# Porchlight levels

Fifty levels in five chapters. The table is `LEVELS` in `Game.kt`; this file is written from it by
`PORCH_TUNE=docs ./gradlew testDebugUnitTest --tests '*LevelTuner*'`, and `LevelCurveTest` checks the curve
on every build.

| Chapter | Name | Scene | Stars to open |
|---|---|---|---|
| 1 | First Light | The porch | 0 |
| 2 | Wanderers | The garden | 15 |
| 3 | Dead Leaves | The woods | 35 |
| 4 | Shadows | The rooftops | 55 |
| 5 | Last Light | The last porch | 80 |

## How the win rates are measured

A simulated player (`Simulator.kt`, test code) plays the real round engine, 2,000 seeded games per level per skill:

- From the peek it takes in each card with chance p = clamp(peek per card / 260 ms, 0.15, 0.95), times its skill: casual 0.6, average 1.0, strong 1.3. The Shadow moth's cards can't be taken in.
- It sees every card it turns, and forgets 4% of what it knows per second.
- It always plays a pair it knows; otherwise it turns an unknown card, then its partner if known, or another unknown card. It avoids dead leaves it knows.
- A lookalike is taken for its twin 15% of the times it is seen. When the Wanderer swaps two cards, it loses track of both.
- A turn takes 1.0 s on average (anywhere from half to one and a half times that), and a wrong pair shows for the game's 0.8 s before it folds back.
- The lead has no perk, so the numbers are the level's own. Luna, the first lead, adds 5 s; Iris adds 1.4 s of peek.

The turn time is 1.0 s. The spec's 0.7 s made rounds too strict for real players (7 to 36 seconds), so the owner asked for an easier pace; at 1.0 s, with the look growing with the board, the tuned rounds run 10 to 38 seconds.

Targets for the average player: chapter 1 97 to 90%, chapter 2 90 to 82%, chapter 3 85 to 74%, chapter 4 80 to 67%, chapter 5 72 to 56%; breathers 8 points easier (at most 99%), bosses 10 harder. Levels 1 to 5 also need a casual player to win 90%, and level 50 needs a strong player to win 50 to 70%. The look grows with the board: 2.5 s plus 0.12 s a card, from 3.2 s for six cards to 5.4 s for twenty-four. Levels 1 to 5 may come out easier than their targets so a casual player wins nine times in ten.

## The table

| Level | Cards | Seconds | Peek | Twists | Kind | 3★ misses | Target | Casual | Average | Strong | Avg stars | Avg time left |
|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6 (3p) | 10 | 3.2 | - | Normal | 2 | 98% | 96.7% | 99.9% | 100.0% | 2.96 | 4.0 s |
| 2 | 8 (4p) | 13 | 3.5 | - | Normal | 2 | 98% | 94.2% | 99.9% | 99.9% | 2.92 | 4.9 s |
| 3 | 8 (4p) | 12 | 3.5 | - | Normal | 2 | 97% | 90.9% | 99.6% | 99.9% | 2.73 | 4.0 s |
| 4 | 12 (6p) | 19 | 3.9 | - | Normal | 2 | 97% | 91.1% | 99.6% | 100.0% | 2.75 | 6.6 s |
| 5 | 12 (6p) | 19 | 3.9 | - | Normal | 2 | 96% | 91.8% | 99.8% | 99.9% | 2.76 | 6.6 s |
| 6 | 12 (6p) | 16 | 3.9 | - | Normal | 2 | 96% | 66.5% | 97.3% | 98.0% | 1.82 | 3.7 s |
| 7 | 12 (6p) | 17 | 3.9 | - | Breather | 2 | 99% | 76.5% | 99.0% | 99.4% | 2.33 | 4.6 s |
| 8 | 16 (8p) | 21 | 4.4 | - | Normal | 2 | 95% | 49.9% | 94.5% | 96.0% | 1.53 | 4.1 s |
| 9 | 16 (8p) | 22 | 4.4 | - | Normal | 2 | 94% | 61.0% | 95.6% | 97.5% | 1.79 | 4.9 s |
| 10 | 16 (8p) | 20 | 4.4 | - | Boss | 1 | 86% | 38.8% | 87.4% | 91.7% | 1.14 | 3.3 s |
| 11 | 12 (6p) | 16 | 3.9 | Wanderer | Normal | 2 | 94% | 53.3% | 93.7% | 96.5% | 1.82 | 3.8 s |
| 12 | 12 (6p) | 16 | 3.9 | Wanderer | Normal | 2 | 93% | 54.1% | 93.3% | 96.3% | 1.80 | 3.8 s |
| 13 | 16 (8p) | 21 | 4.4 | - | Normal | 2 | 93% | 51.3% | 93.3% | 95.2% | 1.47 | 4.1 s |
| 14 | 16 (8p) | 23 | 4.4 | Wanderer | Normal | 2 | 92% | 49.9% | 91.7% | 94.7% | 2.02 | 5.8 s |
| 15 | 16 (8p) | 23 | 4.4 | Wanderer | Normal | 2 | 91% | 49.1% | 92.2% | 94.2% | 2.03 | 5.9 s |
| 16 | 20 (10p) | 27 | 4.9 | - | Normal | 2 | 91% | 46.1% | 91.5% | 94.6% | 1.36 | 4.8 s |
| 17 | 16 (8p) | 22 | 4.4 | - | Breather | 2 | 96% | 62.7% | 95.4% | 97.5% | 1.79 | 4.9 s |
| 18 | 20 (10p) | 30 | 4.9 | Wanderer | Normal | 2 | 89% | 41.7% | 88.4% | 92.1% | 1.82 | 7.2 s |
| 19 | 20 (10p) | 30 | 4.9 | Wanderer | Normal | 2 | 89% | 41.6% | 88.1% | 92.5% | 1.73 | 7.0 s |
| 20 | 20 (10p) | 28 | 4.9 | Wanderer | Boss | 1 | 80% | 28.5% | 79.0% | 84.8% | 1.40 | 5.7 s |
| 21 | 12 (5p+2L) | 15 | 3.9 | Dead leaves | Normal | 3 | 90% | 49.7% | 91.5% | 95.1% | 2.13 | 4.2 s |
| 22 | 16 (7p+2L) | 21 | 4.4 | Dead leaves | Normal | 3 | 89% | 43.1% | 89.7% | 92.6% | 1.73 | 4.9 s |
| 23 | 16 (7p+2L) | 21 | 4.4 | Dead leaves | Normal | 3 | 88% | 44.1% | 90.3% | 92.7% | 1.73 | 4.9 s |
| 24 | 20 (10p) | 29 | 4.9 | Wanderer | Normal | 3 | 87% | 37.7% | 86.0% | 90.1% | 1.59 | 6.3 s |
| 25 | 20 (9p+2L) | 27 | 4.9 | Dead leaves | Normal | 3 | 86% | 35.4% | 84.9% | 90.0% | 1.37 | 5.3 s |
| 26 | 20 (9p+2L) | 27 | 4.9 | Dead leaves | Normal | 3 | 84% | 34.8% | 84.7% | 90.6% | 1.41 | 5.4 s |
| 27 | 16 (8p) | 20 | 4.4 | - | Breather | 3 | 89% | 40.5% | 88.6% | 90.7% | 1.16 | 3.3 s |
| 28 | 20 (9p+2L) | 28 | 4.9 | Dead leaves, Wanderer | Normal | 3 | 82% | 27.1% | 80.4% | 86.0% | 1.45 | 6.0 s |
| 29 | 20 (8p+4L) | 27 | 4.9 | Dead leaves | Normal | 3 | 81% | 30.2% | 83.3% | 89.4% | 1.52 | 6.1 s |
| 30 | 20 (9p+2L) | 27 | 4.9 | Dead leaves, Wanderer | Boss | 2 | 72% | 21.2% | 72.3% | 80.4% | 1.21 | 5.4 s |
| 31 | 16 (8p) | 21 | 4.4 | Shadow | Normal | 3 | 86% | 36.4% | 82.8% | 88.8% | 1.11 | 3.2 s |
| 32 | 20 (10p) | 27 | 4.9 | Shadow | Normal | 3 | 85% | 33.6% | 82.3% | 87.9% | 1.02 | 4.0 s |
| 33 | 20 (10p) | 27 | 4.9 | Shadow | Normal | 3 | 83% | 32.7% | 84.2% | 89.6% | 1.04 | 3.9 s |
| 34 | 16 (8p) | 22 | 4.4 | Lookalikes | Normal | 3 | 82% | 42.0% | 80.7% | 84.6% | 1.37 | 4.4 s |
| 35 | 20 (10p) | 28 | 4.9 | Lookalikes | Normal | 3 | 81% | 38.3% | 79.7% | 82.7% | 1.21 | 5.1 s |
| 36 | 24 (12p) | 33 | 5.4 | - | Normal | 3 | 79% | 31.1% | 80.0% | 91.4% | 0.96 | 4.7 s |
| 37 | 20 (10p) | 26 | 4.9 | - | Breather | 3 | 84% | 34.3% | 85.0% | 90.6% | 1.09 | 4.0 s |
| 38 | 24 (12p) | 34 | 5.4 | Shadow | Normal | 3 | 77% | 29.0% | 78.6% | 89.5% | 0.93 | 4.6 s |
| 39 | 24 (12p) | 38 | 5.4 | Shadow, Wanderer | Normal | 3 | 75% | 26.9% | 73.5% | 86.5% | 1.10 | 6.5 s |
| 40 | 24 (12p) | 35 | 5.4 | Shadow, Lookalikes | Boss | 2 | 66% | 26.2% | 68.1% | 81.1% | 0.82 | 4.7 s |
| 41 | 16 (8p) | 23 | 4.4 | Shadow, Wanderer | Normal | 4 | 80% | 35.0% | 80.4% | 85.2% | 1.37 | 4.6 s |
| 42 | 20 (10p) | 32 | 4.9 | Lookalikes, Wanderer | Normal | 4 | 78% | 39.1% | 78.2% | 81.6% | 1.55 | 7.6 s |
| 43 | 24 (11p+2L) | 34 | 5.4 | Dead leaves | Normal | 4 | 76% | 26.7% | 78.2% | 90.7% | 1.08 | 5.3 s |
| 44 | 24 (12p) | 36 | 5.4 | Shadow, Lookalikes | Normal | 4 | 75% | 32.4% | 75.5% | 84.6% | 0.99 | 5.4 s |
| 45 | 24 (11p+2L) | 37 | 5.4 | Dead leaves, Wanderer | Normal | 4 | 73% | 24.3% | 71.9% | 87.7% | 1.14 | 6.8 s |
| 46 | 24 (12p) | 38 | 5.4 | Shadow, Wanderer | Normal | 4 | 71% | 24.2% | 72.6% | 86.4% | 1.07 | 6.5 s |
| 47 | 20 (10p) | 25 | 4.9 | - | Breather | 4 | 75% | 26.8% | 77.4% | 84.9% | 0.90 | 3.5 s |
| 48 | 24 (12p) | 38 | 5.4 | Lookalikes, Wanderer | Normal | 4 | 68% | 24.1% | 67.3% | 77.8% | 1.05 | 6.9 s |
| 49 | 24 (11p+2L) | 34 | 5.4 | Dead leaves, Shadow | Normal | 4 | 66% | 18.3% | 67.7% | 82.8% | 0.84 | 4.7 s |
| 50 | 24 (12p) | 38 | 5.4 | Wanderer, Shadow, Lookalikes | Boss | 3 | 56% | 18.1% | 57.9% | 69.0% | 0.82 | 6.1 s |
