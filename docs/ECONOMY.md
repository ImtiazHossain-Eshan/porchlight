# The Fireflies economy

Fireflies are Porchlight's currency (`Gems` in code). This is how they are earned, what they buy,
and the rules that keep them fair. Every number lives in one table,
`app/src/main/java/app/porchlight/game/Economy.kt`; change it there and nowhere else.

## The rules it keeps

- **Playing is always free and unlimited.** No lives, no energy, no waiting.
- **Rewarded ads only, and only when the player taps** a button that says what it gives. No
  interstitials, no banners, ever.
- **Money can't buy stars.** Any boost (bought, or watched for) caps a round at 2 stars; the third
  star needs a clean, boost-free clear. Skipping a level gives it no stars.
- **The daily porch is the same deal for everyone.** It takes no boosts, and nothing bought changes it.
- **Nothing random for money.** Every pack says exactly what it gives. No loot boxes, no subscriptions.
- **No pressure.** Prices are shown with the balance after them, and one confirm tap buys. No
  countdowns, no fake discounts; seasonal looks are only *shown* in their season, and once bought
  they are kept for good.
- **A free player is never blocked.** Every chapter gate opens with stars alone, and the balance
  check in `EconomyBalanceTest` fails the build if that ever stops being true.

## Earning

| What | Fireflies |
|---|---|
| A level's 1st, 2nd and 3rd star, the first time each is earned | 3, 5, 8 |
| A win that earns no new star (a replay) | 1 |
| A boss beaten for the first time | 25 |
| A chapter complete (all ten levels cleared) | 50 |
| The daily porch, played | 10, +5 per streak day, at most 30 |
| A badge | 20 |
| A level up | 15 |
| The first open of the day (Supporter: doubled) | 5 |
| Rewarded ad in the shop (5 a day) | 10 |
| Rewarded ad on a win's results (3 a day) | the round's Fireflies again |

Stars can't be farmed: only a star better than the level's best pays, once. Practice rounds of the
daily porch (after the scored one) pay nothing.

## Spending

| What | Fireflies |
|---|---|
| +10 s on the bulb (before a round) | 15 |
| Longer peek, +1.5 s (before a round) | 10 |
| Second look: every card open for 0.9 s (pause screen) | 20 |
| Light a pair: one pair found for you (pause screen) | 25 |
| Relight: +15 s after the bulb burns out, found pairs kept (or one free ad, 3 a day) | 30 |
| Open a waiting chapter early | 10 per missing star |
| Skip a level, after 3 losses in a row on it (no stars; replayable) | 50 |
| A lead moth before its level | 150 |
| Card backs | 200 to 400 |
| Jar and bulb styles | 300 |
| Moth colours (porch and Moths page only; never on the board) | 150 |
| Porch themes (wall, railing, light colour) | 500 |
| Seasonal sets (Frost: Dec-Feb, Harvest: Sep-Nov) | 300 a back, 500 a porch |

At most one of each boost per round. "Start over" from the pause screen keeps the pre-round boosts
already paid for (Second look and Light a pair, once used, are gone).

## Buying Fireflies (Google Play)

| Product | Fireflies | US price |
|---|---|---|
| Handful | 100 | $0.99 |
| Jar | 550 | $4.99 |
| Lantern | 1,200 | $9.99 |
| Porchful | 2,600 | $19.99 |
| Starter pack (once) | 300 + Firefly lacquer card back | $1.99 |
| Supporter (once) | daily gift doubled + Golden hour porch | $4.99 |

The shop shows Play's local price and a bonus worked out from those real prices, rounded down
(on US prices: 9%, 18%, 28% more than buying handfuls). A grown-up check comes before every purchase
screen and every link out of the app, for everyone; never before an ad.

### How a purchase is handled

1. Play reports the purchase. A **pending** payment waits; nothing is granted.
2. When it is **purchased**, its signature is checked on the phone (if a licence key was built in).
3. It is **granted exactly once**, by its purchase token, which the wallet keeps; the wallet is saved.
4. Only then is it finished at Play: packs are **consumed**, the starter pack and Supporter are
   **acknowledged**. If finishing fails, Play hands the purchase back next time and the token stops
   a second grant.
5. Each time the app comes back to the front it asks Play what the player owns: purchases are
   **restored**, and a one-time purchase that is no longer owned (refunded or voided) is **taken
   back** as far as it is still there: its looks are removed, its Fireflies come off the balance,
   but never below zero.

Refunds of consumable packs can't be seen on the phone (Play forgets a consumed purchase); catching
those needs a server and Play's Voided Purchases API.

## Keeping Fireflies safe

The whole save, wallet and purchase tokens included, is in one SharedPreferences file that
**Android Auto Backup** copies to the player's Google account backup (`backup_rules.xml` and
`data_extraction_rules.xml`). It comes back on a reinstall or a new phone. **A factory reset with no
backup loses unspent Fireflies**: the one-time purchases (starter pack, Supporter) come back from
Play, but Fireflies already granted from packs can't be restored without a server. Settings shows
parents what was bought ("Fireflies bought: 3 purchases") and Play keeps the order history.

## Cheating

There is **no server**, so everything is checked on the phone. On a rooted phone a player could edit
the save file, or patch the app to skip the signature check, and give themselves Fireflies or looks.
That is accepted, by design, because **cheating only affects the cheater**: there are no
leaderboards, no trading, no gifting and nothing shared between players except the daily porch's
share card (which carries a score, not a wallet, and a boost can't touch the daily porch). The
signature check stops casual fakes; a determined cheater only cheats themselves.

## The ledger

The wallet keeps its last 200 movements (what, how many, when) for support and debugging, along
with lifetime totals earned and spent.

## A month of a free player

`EconomySim` (in `EconomyBalanceTest.kt`) plays an average free player through 30 days: no money, no
boosts, no skips. Each day: the daily gift, the daily porch, **two ads** in the shop and **19 more
rounds** (20 in all). They play their newest level; waiting at a chapter's star gate they replay the
easiest level still short of three stars, and after half a day of that they open the gate with
**Fireflies they earned**. Whenever they hold enough for a typical look (300) and aren't saving for
a gate, they buy the cheapest look they don't own. Regenerate the table with
`PORCH_ECONOMY=docs ./gradlew testDebugUnitTest --tests '*EconomyBalanceTest*'`.

What it shows:

- **Every chapter opens without money.** Chapters 2 to 4 open on stars alone. At chapter 5's gate
  (80 stars) this player is still a few stars short on day 3 after ten replays, and opens it with
  Fireflies earned by playing (10 a missing star; about 70 in this run). Those last stars come later
  anyway: 90 stars by day 4. `a free player is never blocked from progress` runs five different seeds and
  fails if any spends a whole day stuck at a gate, or doesn't reach level 50 within the month.
- **About one look a week, or a little better.** Early on, new stars, bosses, chapters, badges and
  level ups pay a lot (600 on day 1). Once the levels run out, a day is worth 80 to 110 (daily
  porch, gift, ads, replays), which buys a 300 look every three or four days. The test checks at
  least one look in every week of the month.
- **At 20 rounds a day this player finishes all 50 levels in four days.** That is the spec's
  "average player" pace; most players play far fewer rounds, which slows both progress and earning
  in step.

<!-- economy-sim:start -->
| Day | Earned | Spent | Balance | Newest level | Stars | Chapters open | Looks owned | Rounds at a gate | Gates opened with Fireflies |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 612 | 450 | 162 | 18 | 41 | 3 | 3 | 0 | 0 |
| 2 | 418 | 300 | 280 | 36 | 65 | 4 | 5 | 0 | 0 |
| 3 | 309 | 370 | 219 | 42 | 81 | 5 | 7 | 10 | 1 |
| 4 | 258 | 250 | 227 | 50 | 90 | 5 | 8 | 0 | 0 |
| 5 | 126 | 300 | 53 | 50 | 92 | 5 | 9 | 0 | 0 |
| 6 | 110 | 0 | 163 | 50 | 92 | 5 | 9 | 0 | 0 |
| 7 | 113 | 0 | 276 | 50 | 92 | 5 | 9 | 0 | 0 |
| 8 | 114 | 300 | 90 | 50 | 92 | 5 | 10 | 0 | 0 |
| 9 | 113 | 0 | 203 | 50 | 92 | 5 | 10 | 0 | 0 |
| 10 | 96 | 0 | 299 | 50 | 92 | 5 | 10 | 0 | 0 |
| 11 | 92 | 300 | 91 | 50 | 92 | 5 | 11 | 0 | 0 |
| 12 | 81 | 0 | 172 | 50 | 92 | 5 | 11 | 0 | 0 |
| 13 | 95 | 0 | 267 | 50 | 92 | 5 | 11 | 0 | 0 |
| 14 | 96 | 300 | 63 | 50 | 92 | 5 | 12 | 0 | 0 |
| 15 | 96 | 0 | 159 | 50 | 92 | 5 | 12 | 0 | 0 |
| 16 | 96 | 0 | 255 | 50 | 92 | 5 | 12 | 0 | 0 |
| 17 | 80 | 300 | 35 | 50 | 92 | 5 | 13 | 0 | 0 |
| 18 | 95 | 0 | 130 | 50 | 92 | 5 | 13 | 0 | 0 |
| 19 | 80 | 0 | 210 | 50 | 92 | 5 | 13 | 0 | 0 |
| 20 | 97 | 0 | 307 | 50 | 92 | 5 | 13 | 0 | 0 |
| 21 | 79 | 0 | 386 | 50 | 92 | 5 | 13 | 0 | 0 |
| 22 | 80 | 400 | 66 | 50 | 92 | 5 | 14 | 0 | 0 |
| 23 | 97 | 0 | 163 | 50 | 92 | 5 | 14 | 0 | 0 |
| 24 | 82 | 0 | 245 | 50 | 92 | 5 | 14 | 0 | 0 |
| 25 | 97 | 0 | 342 | 50 | 92 | 5 | 14 | 0 | 0 |
| 26 | 82 | 0 | 424 | 50 | 92 | 5 | 14 | 0 | 0 |
| 27 | 83 | 500 | 7 | 50 | 92 | 5 | 15 | 0 | 0 |
| 28 | 94 | 0 | 101 | 50 | 92 | 5 | 15 | 0 | 0 |
| 29 | 81 | 0 | 182 | 50 | 92 | 5 | 15 | 0 | 0 |
| 30 | 82 | 0 | 264 | 50 | 92 | 5 | 15 | 0 | 0 |
<!-- economy-sim:end -->
