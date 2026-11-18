package app.porchlight.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Badge pictures live with the drawing code; each badge has its own. */
class BadgeArtTest {
    @Test fun `every badge has its own picture`() {
        assertEquals(Badge.entries.size, Badge.entries.map { it.glyph() }.toSet().size)
    }
}
