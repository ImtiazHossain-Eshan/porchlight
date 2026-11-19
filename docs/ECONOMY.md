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
