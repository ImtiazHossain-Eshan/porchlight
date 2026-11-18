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
- A turn takes 0.7 s on average (anywhere from half to one and a half times that), and a wrong pair shows for the game's 0.8 s before it folds back.
- The lead has no perk, so the numbers are the level's own. Luna, the first lead, adds 5 s; Iris adds 1.4 s of peek.

The turn time is the spec's 0.7 s. At that pace the simulated player clears a board quickly, so the tuned rounds are short: 7 to 36 seconds.

Targets for the average player: chapter 1 95 to 85%, chapter 2 85 to 75%, chapter 3 78 to 65%, chapter 4 70 to 55%, chapter 5 60 to 40%; breathers 10 points easier, bosses 10 harder. Levels 1 to 5 also need a casual player to win 90%, and level 50 needs a strong player to win 50 to 70%. That is why levels 1 to 5 have short peeks and level 50 a long one.

## The table

| Level | Cards | Seconds | Peek | Twists | Kind | 3★ misses | Target | Casual | Average | Strong | Avg stars | Avg time left |
|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6 (3p) | 7 | 1.2 | - | Normal | 2 | 95% | 92.3% | 99.2% | 100.0% | 2.85 | 2.7 s |
| 2 | 8 (4p) | 11 | 0.9 | - | Normal | 2 | 94% | 93.2% | 98.0% | 99.2% | 2.50 | 3.9 s |
| 3 | 8 (4p) | 11 | 0.8 | - | Normal | 2 | 93% | 91.4% | 96.6% | 98.6% | 2.36 | 3.6 s |
| 4 | 12 (6p) | 18 | 0.7 | - | Normal | 2 | 92% | 90.5% | 94.5% | 97.5% | 1.93 | 4.6 s |
| 5 | 12 (6p) | 18 | 0.6 | - | Normal | 2 | 91% | 90.4% | 93.8% | 95.5% | 1.78 | 4.2 s |
| 6 | 12 (6p) | 10 | 3.2 | - | Normal | 2 | 89% | 43.1% | 91.5% | 94.9% | 1.11 | 1.6 s |
| 7 | 12 (6p) | 12 | 3.3 | - | Breather | 2 | 98% | 78.7% | 99.3% | 99.9% | 2.45 | 3.4 s |
| 8 | 16 (8p) | 15 | 3.5 | - | Normal | 2 | 87% | 47.4% | 92.1% | 98.2% | 1.54 | 3.0 s |
| 9 | 16 (8p) | 15 | 3.3 | - | Normal | 2 | 86% | 42.3% | 88.1% | 98.5% | 1.42 | 2.8 s |
| 10 | 16 (8p) | 15 | 3.0 | - | Boss | 1 | 75% | 33.8% | 79.4% | 97.2% | 1.22 | 2.6 s |
| 11 | 12 (6p) | 11 | 2.6 | Wanderer | Normal | 2 | 85% | 42.1% | 87.8% | 97.8% | 1.51 | 2.4 s |
| 12 | 12 (6p) | 11 | 2.6 | Wanderer | Normal | 2 | 84% | 40.6% | 87.9% | 97.5% | 1.54 | 2.4 s |
| 13 | 16 (8p) | 15 | 3.0 | - | Normal | 2 | 83% | 34.1% | 79.5% | 97.1% | 1.19 | 2.6 s |
| 14 | 16 (8p) | 17 | 3.0 | Wanderer | Normal | 2 | 82% | 36.6% | 79.4% | 96.3% | 1.64 | 4.1 s |
| 15 | 16 (8p) | 18 | 2.8 | Wanderer | Normal | 2 | 81% | 41.2% | 81.4% | 96.0% | 1.66 | 4.5 s |
| 16 | 20 (10p) | 21 | 3.2 | - | Normal | 2 | 79% | 37.5% | 80.1% | 95.1% | 1.16 | 3.6 s |
| 17 | 16 (8p) | 16 | 3.0 | - | Breather | 2 | 88% | 49.7% | 88.7% | 98.5% | 1.48 | 3.2 s |
| 18 | 20 (10p) | 26 | 3.0 | Wanderer | Normal | 2 | 77% | 43.5% | 81.0% | 95.1% | 1.47 | 5.9 s |
| 19 | 20 (10p) | 26 | 2.8 | Wanderer | Normal | 2 | 76% | 40.9% | 76.2% | 91.9% | 1.33 | 5.6 s |
| 20 | 20 (10p) | 24 | 2.8 | Wanderer | Boss | 1 | 65% | 28.5% | 64.0% | 84.4% | 0.96 | 4.5 s |
| 21 | 12 (5p+2L) | 9 | 3.0 | Dead leaves | Normal | 3 | 78% | 25.9% | 78.3% | 86.2% | 1.37 | 2.0 s |
| 22 | 16 (7p+2L) | 16 | 3.0 | Dead leaves | Normal | 3 | 77% | 32.3% | 79.1% | 95.8% | 1.41 | 3.5 s |
| 23 | 16 (7p+2L) | 16 | 2.8 | Dead leaves | Normal | 3 | 75% | 27.0% | 72.1% | 93.3% | 1.23 | 3.3 s |
| 24 | 20 (10p) | 26 | 2.8 | Wanderer | Normal | 3 | 74% | 43.6% | 75.4% | 91.1% | 1.34 | 5.6 s |
| 25 | 20 (9p+2L) | 23 | 2.8 | Dead leaves | Normal | 3 | 72% | 29.2% | 71.8% | 90.7% | 1.06 | 3.9 s |
| 26 | 20 (9p+2L) | 23 | 2.7 | Dead leaves | Normal | 3 | 71% | 31.1% | 69.0% | 89.3% | 0.98 | 3.7 s |
| 27 | 16 (8p) | 16 | 2.6 | - | Breather | 3 | 79% | 39.9% | 79.5% | 94.6% | 1.20 | 2.8 s |
| 28 | 20 (9p+2L) | 27 | 2.6 | Dead leaves, Wanderer | Normal | 3 | 68% | 34.9% | 67.9% | 86.7% | 1.08 | 5.1 s |
| 29 | 20 (8p+4L) | 25 | 2.6 | Dead leaves | Normal | 3 | 66% | 31.8% | 70.3% | 88.8% | 1.07 | 4.4 s |
| 30 | 20 (9p+2L) | 25 | 2.5 | Dead leaves, Wanderer | Boss | 2 | 55% | 20.6% | 52.2% | 73.6% | 0.73 | 4.0 s |
| 31 | 16 (8p) | 16 | 2.6 | Shadow | Normal | 3 | 70% | 29.8% | 67.7% | 87.2% | 0.84 | 2.2 s |
| 32 | 20 (10p) | 22 | 2.6 | Shadow | Normal | 3 | 68% | 28.9% | 63.7% | 83.9% | 0.77 | 2.9 s |
| 33 | 20 (10p) | 23 | 2.5 | Shadow | Normal | 3 | 67% | 37.6% | 71.1% | 89.1% | 0.92 | 3.4 s |
| 34 | 16 (8p) | 17 | 2.5 | Lookalikes | Normal | 3 | 65% | 34.7% | 68.6% | 83.5% | 1.02 | 2.9 s |
| 35 | 20 (10p) | 23 | 2.5 | Lookalikes | Normal | 3 | 63% | 30.8% | 62.6% | 79.9% | 0.85 | 3.5 s |
| 36 | 24 (12p) | 28 | 2.6 | - | Normal | 3 | 62% | 28.3% | 64.8% | 84.2% | 0.80 | 3.6 s |
| 37 | 20 (10p) | 22 | 2.4 | - | Breather | 3 | 70% | 31.8% | 69.7% | 87.1% | 0.90 | 3.2 s |
| 38 | 24 (12p) | 29 | 2.4 | Shadow | Normal | 3 | 58% | 29.9% | 61.4% | 80.3% | 0.70 | 3.5 s |
| 39 | 24 (12p) | 35 | 2.3 | Shadow, Wanderer | Normal | 3 | 57% | 35.0% | 57.0% | 74.9% | 0.70 | 5.2 s |
| 40 | 24 (12p) | 29 | 2.2 | Shadow, Lookalikes | Boss | 2 | 45% | 18.0% | 41.1% | 61.3% | 0.44 | 2.9 s |
| 41 | 16 (8p) | 19 | 2.4 | Shadow, Wanderer | Normal | 4 | 60% | 34.2% | 62.7% | 83.1% | 1.01 | 3.6 s |
| 42 | 20 (10p) | 28 | 2.3 | Lookalikes, Wanderer | Normal | 4 | 58% | 34.9% | 56.7% | 75.6% | 0.90 | 5.2 s |
| 43 | 24 (11p+2L) | 30 | 2.3 | Dead leaves | Normal | 4 | 56% | 24.7% | 54.2% | 76.6% | 0.63 | 3.5 s |
| 44 | 24 (12p) | 31 | 2.2 | Shadow, Lookalikes | Normal | 4 | 53% | 34.3% | 57.6% | 75.3% | 0.68 | 3.8 s |
| 45 | 24 (11p+2L) | 35 | 2.2 | Dead leaves, Wanderer | Normal | 4 | 51% | 27.9% | 50.1% | 70.4% | 0.63 | 4.7 s |
| 46 | 24 (12p) | 34 | 2.1 | Shadow, Wanderer | Normal | 4 | 49% | 23.8% | 46.2% | 62.6% | 0.57 | 4.4 s |
| 47 | 20 (10p) | 22 | 2.1 | - | Breather | 4 | 57% | 23.5% | 56.9% | 75.6% | 0.68 | 2.7 s |
| 48 | 24 (12p) | 36 | 2.0 | Lookalikes, Wanderer | Normal | 4 | 44% | 28.5% | 49.3% | 63.4% | 0.63 | 5.1 s |
| 49 | 24 (11p+2L) | 30 | 2.0 | Dead leaves, Shadow | Normal | 4 | 42% | 14.7% | 37.6% | 55.6% | 0.41 | 3.0 s |
| 50 | 24 (12p) | 27 | 3.8 | Wanderer, Shadow, Lookalikes | Boss | 3 | 30% | 8.0% | 32.5% | 58.2% | 0.40 | 3.5 s |
