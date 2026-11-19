package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasesTest {
    @Test fun `the products`() {
        assertEquals(listOf(100, 550, 1_200, 2_600), Product.packs.map { it.fireflies })
        assertEquals(listOf(99, 499, 999, 1_999), Product.packs.map { it.usdCents })
        assertEquals(300, Product.Starter.fireflies)
        assertEquals(199, Product.Starter.usdCents)
        assertEquals(499, Product.Supporter.usdCents)
        assertTrue(Product.packs.all { it.consumable })
        assertFalse(Product.Starter.consumable)
        assertFalse(Product.Supporter.consumable)
        assertEquals(Product.entries.size, Product.entries.map { it.id }.toSet().size)
    }

    @Test fun `the bonuses are what the prices say`() {
        val usd = Product.entries.associateWith { it.usdCents * 10_000L }
        // Against handfuls at 0.99 each, not against a round dollar: 9%, 18% and 28% more, truly.
        assertEquals(9, bonusPercent(Product.Jar, usd))
        assertEquals(18, bonusPercent(Product.Lantern, usd))
        assertEquals(28, bonusPercent(Product.Porchful, usd))
        assertEquals(0.0909, bonusOf(Product.Jar, usd)!!, 0.001)
        assertNull(bonusOf(Product.Handful, usd))
        assertNull(bonusOf(Product.Starter, usd))
        // No prices from Play, no claim.
        assertNull(bonusOf(Product.Jar, emptyMap()))
    }

    @Test fun `a purchase is granted once per token`() {
        val once = grant(Progress(), Product.Jar, "tok-1", 1)!!
        assertEquals(550, once.wallet.balance)
        assertNull(grant(once, Product.Jar, "tok-1", 2))
        val twice = grant(once, Product.Jar, "tok-2", 3)!!
        assertEquals(1_100, twice.wallet.balance)
        assertEquals(2, twice.wallet.bought.size)
        assertEquals("Bought/fireflies_jar", twice.wallet.ledger.last().source)
    }

    @Test fun `the starter pack gives fireflies and its card back`() {
        val p = grant(Progress(), Product.Starter, "s", 1)!!
        assertEquals(300, p.wallet.balance)
        assertTrue(p.owns(lookById(STARTER_LOOK)!!))
        assertTrue(p.ownsProduct(Product.Starter))
        assertFalse(p.supporter)
    }

    @Test fun `supporter doubles the daily gift and brings its porch`() {
        val p = grant(Progress(), Product.Supporter, "sup", 1)!!
        assertTrue(p.supporter)
        assertTrue(p.owns(lookById(SUPPORTER_LOOK)!!))
        assertEquals(0, p.wallet.balance)
        val (_, gift) = openToday(p, 20261119, 2, p.supporter)
        assertEquals(Economy.FIRST_OPEN * 2, gift)
    }

    @Test fun `a refund takes back what is still there, never below zero`() {
        val bought = grant(Progress(), Product.Lantern, "t", 1)!!
        val spent = (spend(bought, Cost("x", 1_000), 2) as SpendResult.Spent).progress
        assertEquals(200, spent.wallet.balance)
        val refunded = revoke(spent, Product.Lantern, "t", 3)
        assertEquals(0, refunded.wallet.balance)
        assertEquals(-200, refunded.wallet.ledger.last().amount)
        assertTrue(refunded.wallet.bought.isEmpty())
        // Taking it back twice changes nothing more.
        assertEquals(refunded, revoke(refunded, Product.Lantern, "t", 4))
        // A refunded token is not granted again by the same report...
        assertEquals(refunded.wallet.balance, revoke(refunded, Product.Lantern, "t", 5).wallet.balance)
    }

    @Test fun `a refund of an unspent pack takes it all back`() {
        val bought = grant(Progress(wallet = Wallet(balance = 40)), Product.Handful, "h", 1)!!
        assertEquals(140, bought.wallet.balance)
        assertEquals(40, revoke(bought, Product.Handful, "h", 2).wallet.balance)
    }

    @Test fun `a refunded supporter loses the porch and the double gift`() {
        val sup = wear(grant(Progress(), Product.Supporter, "sup", 1)!!, lookById(SUPPORTER_LOOK)!!)
        assertEquals(SUPPORTER_LOOK, sup.wearing("porch")!!.id)
        val back = revoke(sup, Product.Supporter, "sup", 2)
        assertFalse(back.supporter)
        assertFalse(back.owns(lookById(SUPPORTER_LOOK)!!))
        assertNull(back.wearing("porch"))
    }

    @Test fun `a reversed refund is granted again`() {
        val sup = grant(Progress(), Product.Supporter, "sup", 1)!!
        val refunded = revoke(sup, Product.Supporter, "sup", 2)
        val again = grant(refunded, Product.Supporter, "sup", 3)!!
        assertTrue(again.supporter)
    }

    @Test fun `revoking a token never granted changes nothing`() {
        val p = Progress(wallet = Wallet(balance = 500))
        assertEquals(p, revoke(p, Product.Jar, "nope", 1))
    }

    @Test fun `one-time purchases no longer owned at play are found, consumables never`() {
        var p = grant(Progress(), Product.Supporter, "sup", 1)!!
        p = grant(p, Product.Starter, "st", 2)!!
        p = grant(p, Product.Jar, "jar", 3)!!
        val gone = refundedOneTimes(p, ownedAtPlay = setOf(purchaseKey(Product.Starter, "st")))
        assertEquals(listOf(Product.Supporter to "sup"), gone)
    }

    @Test fun `purchases survive a restart, odd tokens and all`() {
        val prefs = MemoryPrefs()
        val store = Store(prefs)
        assertTrue(store.grantPurchase(Product.Jar, "a.b-c_d:e|f;g", 1))
        assertFalse(store.grantPurchase(Product.Jar, "a.b-c_d:e|f;g", 2))
        val back = Store(prefs)
        assertEquals(550, back.progress.wallet.balance)
        assertFalse(back.grantPurchase(Product.Jar, "a.b-c_d:e|f;g", 3))
        assertEquals(550, back.progress.wallet.balance)
    }

    @Test fun `money can never buy stars or a daily advantage`() {
        var p = Progress()
        for ((i, product) in Product.entries.withIndex()) p = grant(p, product, "t$i", i.toLong())!!
        assertEquals(Progress().levelStars, p.levelStars)
        assertEquals(Progress().totalStars, p.totalStars)
        val daily = Round.start(MothId.Luna, DAILY, boosts = Boost.entries.toSet())
        assertTrue(daily.boosts.isEmpty())
        assertEquals(rulesFor(MothId.Luna, DAILY), daily.rules)
    }
}
