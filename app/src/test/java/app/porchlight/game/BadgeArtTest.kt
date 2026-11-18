package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** Badge pictures live with the drawing code, so this check sits beside it. */
class BadgeArtTest {
    @Test fun `every badge has its own picture`() {
        assertEquals(Badge.entries.size, Badge.entries.map { it.glyph() }.toSet().size)
    }
}
