package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    @Test fun `xp needed per level`() {
        assertEquals(listOf(0, 200, 500, 900, 1_400, 2_000), (1..6).map(::xpFor))
    }

    @Test fun `level boundaries`() {
        assertEquals(1, levelFor(0))
        assertEquals(1, levelFor(199))
        assertEquals(2, levelFor(200))
        assertEquals(3, levelFor(500))
        assertEquals(6, levelFor(2_000))
        assertEquals(6, levelFor(2_699))
        assertEquals(7, levelFor(2_700))
    }

    @Test fun `each level up unlocks the next moth`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), MothId.entries.map { it.unlockLevel })
    }

    @Test fun `a new player only has wick`() {
        val fresh = Progress()
        assertTrue(fresh.unlocked(MothId.Wick))
        assertFalse(fresh.unlocked(MothId.Ember))
    }

    @Test fun `level six unlocks the whole porch`() {
        val veteran = Progress(xp = 2_000)
        assertTrue(MothId.entries.all { veteran.unlocked(it) })
    }

    @Test fun `a lost round earns no stars`() {
        assertEquals(0, starsFor(Round(phase = Phase.Lost, left = 0)))
    }

    @Test fun `a slow win with misses earns one star`() {
        assertEquals(1, starsFor(Round(phase = Phase.Won, left = 4_000, misses = 3)))
    }

    @Test fun `ten seconds left earns a star`() {
        assertEquals(2, starsFor(Round(phase = Phase.Won, left = 10_000, misses = 1)))
    }

    @Test fun `no wrong pairs earns a star`() {
        assertEquals(2, starsFor(Round(phase = Phase.Won, left = 2_000, misses = 0)))
    }

    @Test fun `a fast clean win earns three stars`() {
        assertEquals(3, starsFor(Round(phase = Phase.Won, left = 20_000, misses = 0)))
    }

    @Test fun `streak multiplier caps at four`() {
        val rules = Rules()
        assertEquals(listOf(1, 2, 3, 4, 4, 4), (0..5).map { multiplierAt(it, rules) })
    }

    @Test fun `every badge has a title and a rule`() {
        Badge.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.detail.endsWith("."))
        }
    }

    @Test fun `every moth has a perk`() {
        MothId.entries.forEach {
            assertTrue(it.perkName.isNotBlank())
            assertTrue(it.perk.endsWith("."))
        }
    }
}
