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

The turn time is 1.0 s. The spec's 0.7 s made rounds too strict for real players (7 to 36 seconds), so the owner asked for an easier pace; at 1.0 s the tuned rounds run 10 to 55 seconds.

Targets for the average player: chapter 1 97 to 90%, chapter 2 90 to 82%, chapter 3 85 to 74%, chapter 4 80 to 67%, chapter 5 72 to 56%; breathers 8 points easier (at most 99%), bosses 10 harder. Levels 1 to 5 also need a casual player to win 90%, and level 50 needs a strong player to win 50 to 70%. That is why levels 1 to 5 have short peeks and level 50 a long one.

## The table

| Level | Cards | Seconds | Peek | Twists | Kind | 3★ misses | Target | Casual | Average | Strong | Avg stars | Avg time left |
|---:|---:|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|
| 1 | 6 (3p) | 10 | 1.2 | - | Normal | 2 | 97% | 94.7% | 99.3% | 100.0% | 2.84 | 3.8 s |
| 2 | 8 (4p) | 15 | 0.9 | - | Normal | 2 | 96% | 91.3% | 97.0% | 98.7% | 2.40 | 5.0 s |
| 3 | 8 (4p) | 16 | 0.8 | - | Normal | 2 | 95% | 93.7% | 97.9% | 99.3% | 2.49 | 5.5 s |
| 4 | 12 (6p) | 25 | 0.7 | - | Normal | 2 | 95% | 91.1% | 94.9% | 97.3% | 1.87 | 6.2 s |
| 5 | 12 (6p) | 26 | 0.6 | - | Normal | 2 | 94% | 94.1% | 96.4% | 97.4% | 1.86 | 6.6 s |
| 6 | 12 (6p) | 15 | 3.2 | - | Normal | 2 | 93% | 54.0% | 92.1% | 94.3% | 1.33 | 2.8 s |
| 7 | 12 (6p) | 17 | 3.3 | - | Breather | 2 | 99% | 76.5% | 99.0% | 99.4% | 2.33 | 4.6 s |
| 8 | 16 (8p) | 22 | 3.5 | - | Normal | 2 | 92% | 51.8% | 91.8% | 98.0% | 1.54 | 4.4 s |
| 9 | 16 (8p) | 22 | 3.3 | - | Normal | 2 | 91% | 45.2% | 88.5% | 97.5% | 1.44 | 4.2 s |
| 10 | 16 (8p) | 22 | 3.0 | - | Boss | 1 | 80% | 39.4% | 79.7% | 95.9% | 1.19 | 3.8 s |
| 11 | 12 (6p) | 17 | 2.6 | Wanderer | Normal | 2 | 90% | 50.2% | 89.4% | 97.9% | 1.98 | 4.4 s |
| 12 | 12 (6p) | 17 | 2.6 | Wanderer | Normal | 2 | 89% | 49.8% | 90.2% | 97.6% | 2.01 | 4.3 s |
| 13 | 16 (8p) | 23 | 3.0 | - | Normal | 2 | 88% | 49.2% | 87.6% | 98.2% | 1.39 | 4.4 s |
| 14 | 16 (8p) | 27 | 3.0 | Wanderer | Normal | 2 | 87% | 53.8% | 87.6% | 97.6% | 1.88 | 7.3 s |
| 15 | 16 (8p) | 28 | 2.8 | Wanderer | Normal | 2 | 86% | 57.9% | 88.1% | 97.7% | 1.87 | 7.6 s |
| 16 | 20 (10p) | 32 | 3.2 | - | Normal | 2 | 86% | 50.8% | 86.8% | 97.3% | 1.35 | 6.0 s |
| 17 | 16 (8p) | 24 | 3.0 | - | Breather | 2 | 93% | 60.2% | 92.4% | 98.6% | 1.58 | 5.1 s |
| 18 | 20 (10p) | 38 | 3.0 | Wanderer | Normal | 2 | 84% | 48.5% | 82.5% | 95.4% | 1.40 | 8.4 s |
| 19 | 20 (10p) | 40 | 2.8 | Wanderer | Normal | 2 | 83% | 55.8% | 83.2% | 95.3% | 1.41 | 9.4 s |
| 20 | 20 (10p) | 37 | 2.8 | Wanderer | Boss | 1 | 72% | 40.5% | 73.4% | 88.4% | 1.04 | 7.3 s |
| 21 | 12 (5p+2L) | 14 | 3.0 | Dead leaves | Normal | 3 | 85% | 41.3% | 86.5% | 91.9% | 1.80 | 3.4 s |
| 22 | 16 (7p+2L) | 23 | 3.0 | Dead leaves | Normal | 3 | 84% | 38.4% | 81.2% | 95.8% | 1.41 | 4.8 s |
| 23 | 16 (7p+2L) | 24 | 2.8 | Dead leaves | Normal | 3 | 83% | 42.6% | 82.8% | 95.1% | 1.44 | 5.0 s |
| 24 | 20 (10p) | 39 | 2.8 | Wanderer | Normal | 3 | 81% | 53.1% | 81.5% | 93.2% | 1.47 | 8.7 s |
| 25 | 20 (9p+2L) | 34 | 2.8 | Dead leaves | Normal | 3 | 80% | 46.6% | 81.1% | 93.7% | 1.23 | 6.1 s |
| 26 | 20 (9p+2L) | 34 | 2.7 | Dead leaves | Normal | 3 | 79% | 45.2% | 80.4% | 92.9% | 1.16 | 5.7 s |
| 27 | 16 (8p) | 24 | 2.6 | - | Breather | 3 | 86% | 49.3% | 85.2% | 96.7% | 1.33 | 4.5 s |
| 28 | 20 (9p+2L) | 40 | 2.6 | Dead leaves, Wanderer | Normal | 3 | 76% | 51.3% | 77.6% | 91.0% | 1.29 | 8.1 s |
| 29 | 20 (8p+4L) | 35 | 2.6 | Dead leaves | Normal | 3 | 75% | 41.1% | 75.5% | 89.1% | 1.13 | 6.0 s |
| 30 | 20 (9p+2L) | 37 | 2.5 | Dead leaves, Wanderer | Boss | 2 | 64% | 32.0% | 63.3% | 80.7% | 0.92 | 6.2 s |
| 31 | 16 (8p) | 25 | 2.6 | Shadow | Normal | 3 | 80% | 50.2% | 83.4% | 94.7% | 1.22 | 4.4 s |
| 32 | 20 (10p) | 34 | 2.6 | Shadow | Normal | 3 | 79% | 50.8% | 79.6% | 91.3% | 1.08 | 5.5 s |
| 33 | 20 (10p) | 34 | 2.5 | Shadow | Normal | 3 | 77% | 48.1% | 77.5% | 90.9% | 1.03 | 5.2 s |
| 34 | 16 (8p) | 26 | 2.5 | Lookalikes | Normal | 3 | 76% | 49.6% | 77.6% | 88.4% | 1.24 | 4.9 s |
| 35 | 20 (10p) | 35 | 2.5 | Lookalikes | Normal | 3 | 74% | 45.3% | 74.5% | 87.8% | 1.07 | 5.8 s |
| 36 | 24 (12p) | 42 | 2.6 | - | Normal | 3 | 73% | 40.8% | 72.7% | 88.6% | 0.89 | 5.8 s |
| 37 | 20 (10p) | 33 | 2.4 | - | Breather | 3 | 79% | 44.3% | 77.0% | 91.0% | 1.04 | 5.2 s |
| 38 | 24 (12p) | 43 | 2.4 | Shadow | Normal | 3 | 70% | 38.9% | 68.3% | 84.4% | 0.80 | 5.5 s |
| 39 | 24 (12p) | 53 | 2.3 | Shadow, Wanderer | Normal | 3 | 68% | 49.2% | 68.3% | 81.1% | 0.85 | 8.7 s |
| 40 | 24 (12p) | 44 | 2.2 | Shadow, Lookalikes | Boss | 2 | 57% | 31.0% | 56.0% | 72.3% | 0.61 | 5.2 s |
| 41 | 16 (8p) | 29 | 2.4 | Shadow, Wanderer | Normal | 4 | 72% | 46.1% | 74.2% | 87.7% | 1.28 | 6.0 s |
| 42 | 20 (10p) | 43 | 2.3 | Lookalikes, Wanderer | Normal | 4 | 70% | 50.2% | 69.4% | 83.0% | 1.18 | 9.0 s |
| 43 | 24 (11p+2L) | 44 | 2.3 | Dead leaves | Normal | 4 | 68% | 36.7% | 65.9% | 83.2% | 0.78 | 5.5 s |
| 44 | 24 (12p) | 46 | 2.2 | Shadow, Lookalikes | Normal | 4 | 67% | 44.0% | 66.6% | 81.2% | 0.80 | 6.2 s |
| 45 | 24 (11p+2L) | 52 | 2.2 | Dead leaves, Wanderer | Normal | 4 | 65% | 42.8% | 65.3% | 79.9% | 0.87 | 8.0 s |
| 46 | 24 (12p) | 53 | 2.1 | Shadow, Wanderer | Normal | 4 | 63% | 43.5% | 64.4% | 77.0% | 0.81 | 8.2 s |
| 47 | 20 (10p) | 34 | 2.1 | - | Breather | 4 | 69% | 44.2% | 72.2% | 85.7% | 0.95 | 5.0 s |
| 48 | 24 (12p) | 55 | 2.0 | Lookalikes, Wanderer | Normal | 4 | 60% | 44.6% | 62.3% | 74.9% | 0.80 | 9.0 s |
| 49 | 24 (11p+2L) | 45 | 2.0 | Dead leaves, Shadow | Normal | 4 | 58% | 33.2% | 57.1% | 73.5% | 0.64 | 5.1 s |
| 50 | 24 (12p) | 42 | 3.8 | Wanderer, Shadow, Lookalikes | Boss | 3 | 46% | 15.7% | 45.2% | 65.4% | 0.57 | 5.7 s |
