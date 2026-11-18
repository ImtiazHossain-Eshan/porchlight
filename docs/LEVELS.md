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
- It sees every card it turns, except while a Blackout has the bulb out, and forgets 4% of what it knows per second.
- It always plays a pair it knows; otherwise it turns an unknown card, then its partner if known, or another unknown card. It avoids dead leaves it knows.
- A lookalike is taken for its twin 15% of the times it is seen. When the Wanderer swaps two cards, it loses track of both.
- A turn takes 1.3 s on average (anywhere from half to one and a half times that), and a wrong pair shows for the game's 0.8 s before it folds back.
- The lead has no perk, so the numbers are the level's own. Luna, the first lead, adds 5 s; Iris adds 1.4 s of peek.

The spec's 0.7 s turn made every round of the starting table a near-certain win, and tuning to the targets at that pace gave 5 to 30 second rounds. The turn time is the one calibration constant, set to a human pace so the tuned seconds stay near the starting table.

Targets for the average player: chapter 1 95 to 85%, chapter 2 85 to 75%, chapter 3 78 to 65%, chapter 4 70 to 55%, chapter 5 60 to 40%; breathers 10 points easier, bosses 10 harder. Levels 1 to 5 also need a casual player to win 90%, and level 50 needs a strong player to win 50 to 70%. That is why levels 1 to 5 have short peeks and level 50 a long one.

## The table

| Level | Cards | Seconds | Peek | Twists | Kind | 3★ misses | Target | Casual | Average | Strong | Avg stars | Avg time left |
|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6 (3p) | 13 | 1.2 | - | Normal | 2 | 95% | 94.6% | 99.5% | 100.0% | 2.83 | 5.0 s |
| 2 | 8 (4p) | 18 | 1.3 | - | Normal | 2 | 94% | 90.7% | 98.2% | 99.9% | 2.47 | 6.3 s |
| 3 | 8 (4p) | 19 | 1.0 | - | Normal | 2 | 93% | 91.5% | 97.1% | 99.1% | 2.38 | 6.3 s |
| 4 | 12 (6p) | 33 | 0.7 | - | Normal | 2 | 92% | 92.5% | 95.3% | 97.8% | 1.87 | 8.5 s |
| 5 | 12 (6p) | 33 | 0.6 | - | Normal | 2 | 91% | 91.4% | 95.0% | 96.1% | 1.73 | 8.0 s |
| 6 | 12 (6p) | 19 | 3.2 | - | Normal | 2 | 89% | 46.4% | 87.5% | 90.8% | 1.13 | 3.2 s |
| 7 | 12 (6p) | 22 | 3.3 | - | Breather | 2 | 98% | 74.4% | 98.3% | 99.0% | 2.17 | 5.6 s |
| 8 | 16 (8p) | 28 | 3.5 | - | Normal | 2 | 87% | 41.5% | 86.9% | 94.0% | 1.28 | 4.8 s |
| 9 | 16 (8p) | 29 | 3.3 | - | Normal | 2 | 86% | 45.2% | 85.9% | 96.3% | 1.33 | 5.2 s |
| 10 | 16 (8p) | 28 | 3.0 | - | Boss | 1 | 75% | 31.3% | 72.0% | 91.4% | 0.98 | 4.3 s |
| 11 | 12 (6p) | 19 | 3.0 | Wanderer | Normal | 2 | 85% | 37.9% | 83.9% | 88.4% | 1.11 | 3.3 s |
| 12 | 12 (6p) | 19 | 3.0 | Wanderer | Normal | 2 | 84% | 38.2% | 83.0% | 88.8% | 1.06 | 3.3 s |
| 13 | 16 (8p) | 30 | 3.0 | - | Normal | 2 | 83% | 46.3% | 85.1% | 96.8% | 1.29 | 5.4 s |
| 14 | 16 (8p) | 34 | 3.0 | Wanderer | Normal | 2 | 82% | 45.5% | 82.5% | 94.1% | 1.62 | 8.1 s |
| 15 | 16 (8p) | 35 | 2.8 | Wanderer | Normal | 2 | 81% | 49.1% | 80.5% | 93.6% | 1.56 | 8.3 s |
| 16 | 20 (10p) | 41 | 3.2 | - | Normal | 2 | 79% | 44.1% | 80.8% | 94.5% | 1.13 | 6.8 s |
| 17 | 16 (8p) | 31 | 3.0 | - | Breather | 2 | 88% | 56.9% | 89.0% | 97.8% | 1.45 | 6.1 s |
| 18 | 20 (10p) | 50 | 3.0 | Wanderer | Normal | 2 | 77% | 48.8% | 79.1% | 93.3% | 1.26 | 10.7 s |
| 19 | 20 (10p) | 50 | 2.8 | Wanderer | Normal | 2 | 76% | 46.3% | 76.0% | 90.1% | 1.18 | 10.3 s |
| 20 | 20 (10p) | 47 | 2.8 | Wanderer | Boss | 1 | 65% | 34.6% | 66.9% | 83.6% | 0.89 | 8.4 s |
| 21 | 12 (5p+2L) | 17 | 3.0 | Dead leaves | Normal | 3 | 78% | 29.8% | 75.2% | 81.3% | 1.28 | 3.4 s |
| 22 | 16 (7p+2L) | 29 | 3.0 | Dead leaves | Normal | 3 | 77% | 32.6% | 74.6% | 92.2% | 1.17 | 5.3 s |
| 23 | 16 (7p+2L) | 30 | 2.8 | Dead leaves | Normal | 3 | 75% | 36.7% | 75.1% | 91.0% | 1.18 | 5.5 s |
| 24 | 20 (10p) | 49 | 2.8 | Wanderer | Normal | 3 | 74% | 43.4% | 73.0% | 88.8% | 1.21 | 9.8 s |
| 25 | 20 (9p+2L) | 42 | 2.8 | Dead leaves | Normal | 3 | 72% | 35.1% | 70.7% | 87.3% | 0.93 | 6.2 s |
| 26 | 20 (9p+2L) | 42 | 2.7 | Dead leaves | Normal | 3 | 71% | 33.7% | 68.5% | 86.3% | 0.88 | 5.9 s |
| 27 | 16 (8p) | 30 | 2.7 | - | Breather | 3 | 79% | 39.2% | 76.8% | 93.2% | 1.08 | 5.0 s |
| 28 | 20 (9p+2L) | 49 | 2.6 | Dead leaves, Wanderer | Normal | 3 | 68% | 38.5% | 68.0% | 83.0% | 1.04 | 8.7 s |
| 29 | 20 (8p+4L) | 43 | 2.6 | Dead leaves | Normal | 3 | 66% | 32.4% | 66.5% | 83.6% | 0.89 | 6.2 s |
| 30 | 20 (9p+2L) | 46 | 2.5 | Dead leaves, Wanderer | Boss | 2 | 55% | 26.6% | 53.8% | 73.6% | 0.73 | 6.8 s |
| 31 | 16 (8p) | 31 | 2.6 | Shadow | Normal | 3 | 70% | 36.8% | 72.9% | 88.2% | 0.95 | 4.6 s |
| 32 | 20 (10p) | 43 | 2.6 | Shadow | Normal | 3 | 68% | 40.5% | 68.2% | 86.0% | 0.86 | 6.0 s |
| 33 | 20 (10p) | 43 | 2.5 | Shadow | Normal | 3 | 67% | 37.0% | 67.7% | 84.1% | 0.84 | 5.9 s |
| 34 | 16 (8p) | 32 | 2.5 | Lookalikes | Normal | 3 | 65% | 37.7% | 66.0% | 81.8% | 0.94 | 5.2 s |
| 35 | 20 (10p) | 44 | 2.5 | Lookalikes | Normal | 3 | 63% | 35.5% | 63.7% | 79.6% | 0.82 | 6.4 s |
| 36 | 24 (12p) | 53 | 2.6 | - | Normal | 3 | 62% | 27.4% | 59.3% | 79.2% | 0.67 | 6.0 s |
| 37 | 20 (10p) | 42 | 2.4 | - | Breather | 3 | 70% | 35.1% | 67.9% | 84.9% | 0.87 | 6.0 s |
| 38 | 24 (12p) | 55 | 2.4 | Shadow | Normal | 3 | 58% | 31.6% | 59.7% | 76.5% | 0.67 | 6.3 s |
| 39 | 24 (12p) | 65 | 2.3 | Shadow, Wanderer | Normal | 3 | 57% | 32.9% | 55.9% | 69.3% | 0.65 | 8.8 s |
| 40 | 24 (12p) | 56 | 2.2 | Shadow, Lookalikes | Boss | 2 | 45% | 23.7% | 45.2% | 60.7% | 0.48 | 6.0 s |
| 41 | 16 (8p) | 31 | 2.4 | Blackout | Normal | 4 | 60% | 29.1% | 61.2% | 81.4% | 0.80 | 4.5 s |
| 42 | 20 (10p) | 44 | 2.3 | Blackout | Normal | 4 | 58% | 28.1% | 56.1% | 76.1% | 0.71 | 5.8 s |
| 43 | 24 (11p+2L) | 55 | 2.3 | Dead leaves | Normal | 4 | 56% | 27.9% | 54.0% | 71.8% | 0.60 | 6.0 s |
| 44 | 24 (12p) | 58 | 2.2 | Blackout | Normal | 4 | 53% | 26.8% | 53.4% | 68.8% | 0.62 | 6.8 s |
| 45 | 24 (11p+2L) | 63 | 2.2 | Dead leaves, Wanderer | Normal | 4 | 51% | 27.9% | 51.5% | 67.7% | 0.63 | 8.3 s |
| 46 | 24 (12p) | 59 | 2.1 | Shadow, Blackout | Normal | 4 | 49% | 25.9% | 50.4% | 64.3% | 0.55 | 6.4 s |
| 47 | 20 (10p) | 42 | 2.1 | - | Breather | 4 | 57% | 28.0% | 55.5% | 74.7% | 0.65 | 4.9 s |
| 48 | 24 (12p) | 66 | 2.0 | Lookalikes, Wanderer | Normal | 4 | 44% | 26.7% | 44.2% | 58.1% | 0.52 | 8.8 s |
| 49 | 24 (11p+2L) | 58 | 2.0 | Dead leaves, Blackout | Normal | 4 | 42% | 19.8% | 40.3% | 58.2% | 0.44 | 5.9 s |
| 50 | 24 (12p) | 54 | 3.8 | Wanderer, Shadow, Blackout | Boss | 3 | 30% | 9.3% | 31.1% | 54.9% | 0.37 | 6.6 s |
