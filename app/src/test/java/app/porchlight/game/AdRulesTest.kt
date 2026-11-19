package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRulesTest {
    private val day = 20261119L

    // ---------------------------------------------------------------------------------------------
    // Age

    @Test fun `no year, no audience, no ads`() {
        assertNull(audienceFor(0, 2026))
        assertNull(audienceFor(2030, 2026))
    }

    @Test fun `the age counts from the youngest the player could be`() {
        // Born 2013: 12 or 13 this year. Treated as 12, a child.
        assertEquals(Audience.Child, audienceFor(2013, 2026))
        assertEquals(Audience.Teen, audienceFor(2012, 2026))
        // Born 2010: 15 or 16. Treated as 15.
        assertEquals(Audience.Teen, audienceFor(2010, 2026))
        assertEquals(Audience.Adult, audienceFor(2009, 2026))
        assertEquals(Audience.Child, audienceFor(2026, 2026))
        assertEquals(Audience.Adult, audienceFor(1950, 2026))
    }

    @Test fun `a child gets child-directed, G-rated, never personalised ads and no consent form`() {
        val child = policyFor(Audience.Child)
        assertTrue(child.childDirected)
        assertTrue(child.underAgeOfConsent)
        assertEquals("G", child.maxRating)
        assertFalse(child.personalisedIfConsented)
        assertFalse(child.consentCheck)
    }

    @Test fun `a teen is under the age of consent and never personalised`() {
        val teen = policyFor(Audience.Teen)
        assertFalse(teen.childDirected)
        assertTrue(teen.underAgeOfConsent)
        assertEquals("PG", teen.maxRating)
        assertFalse(teen.personalisedIfConsented)
    }

    @Test fun `nobody gets more than PG`() {
        assertTrue(Audience.entries.all { policyFor(it).maxRating in setOf("G", "PG") })
        assertTrue(policyFor(Audience.Adult).consentCheck)
    }

    // ---------------------------------------------------------------------------------------------
    // Caps

    @Test fun `the caps`() {
        assertEquals(5, Placement.Shop.perDay)
        assertEquals(3, Placement.Double.perDay)
        assertEquals(3, Placement.Relight.perDay)
        assertEquals(10, Economy.AD_SHOP_REWARD)
    }

    @Test fun `the shop ad pays ten, five times a day`() {
        var p = Progress()
        repeat(5) { p = rewardShopAd(p, day, it.toLong())!! }
        assertEquals(50, p.wallet.balance)
        assertEquals(0, adsLeft(p, Placement.Shop, day))
        assertNull(rewardShopAd(p, day, 9))
        // Tomorrow, the count starts again.
        assertEquals(5, adsLeft(p, Placement.Shop, day + 1))
        assertEquals(60, rewardShopAd(p, day + 1, 10)!!.wallet.balance)
        assertEquals("Ad", p.wallet.ledger.last().source)
    }

    @Test fun `each placement counts on its own`() {
        val p = watched(watched(Progress(), Placement.Shop, day), Placement.Relight, day)
        assertEquals(4, adsLeft(p, Placement.Shop, day))
        assertEquals(2, adsLeft(p, Placement.Relight, day))
        assertEquals(3, adsLeft(p, Placement.Double, day))
    }

    @Test fun `doubling pays the round's fireflies again, three times a day`() {
        val report = Report(3, 100, 1, 1, emptyList(), emptyList(), false, earnings = listOf(Earning.NewStar(1), Earning.NewStar(2)))
        assertEquals(8, report.fireflies)
        var p = Progress()
        repeat(3) { p = rewardDouble(p, report, day, 1)!! }
        assertEquals(24, p.wallet.balance)
        assertNull(rewardDouble(p, report, day, 1))
        assertEquals("Doubled", p.wallet.ledger.last().source)
    }

    @Test fun `a round that paid nothing has nothing to double`() {
        val empty = Report(0, 10, 1, 1, emptyList(), emptyList(), false)
        assertNull(rewardDouble(Progress(), empty, day, 1))
        assertEquals(3, adsLeft(Progress(), Placement.Double, day))
    }

    @Test fun `free relights are capped at three a day`() {
        var p = Progress()
        repeat(3) { p = relightByAd(p, day)!! }
        assertNull(relightByAd(p, day))
        assertEquals(0, p.wallet.balance)
    }

    @Test fun `age and ad counts survive a restart`() {
        val prefs = MemoryPrefs()
        Store(prefs).change { watched(it.copy(birthYear = 2011), Placement.Shop, day) }
        val back = Store(prefs).progress
        assertEquals(2011, back.birthYear)
        assertEquals(4, adsLeft(back, Placement.Shop, day))
    }
}
