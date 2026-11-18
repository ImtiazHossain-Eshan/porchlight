package app.porchlight.game

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun `numbers group in threes`() {
        assertEquals("0", 0.grouped())
        assertEquals("999", 999.grouped())
        assertEquals("1,000", 1_000.grouped())
        assertEquals("2,340", 2_340.grouped())
        assertEquals("1,234,567", 1_234_567.grouped())
        assertEquals("-12,345", (-12_345).grouped())
    }

    @Test fun `one decimal rounds half up on the float's own value`() {
        assertEquals("12.4", oneDecimal(12_400 / 1000f))
        assertEquals("0.0", oneDecimal(0f))
        assertEquals("9.9", oneDecimal(9.94f))
        assertEquals("10.0", oneDecimal(9.96f))
        assertEquals("0.3", oneDecimal(0.25f)) // exactly half: up
        assertEquals("12.4", oneDecimal(12.45f)) // 12.45f is a hair under 12.45
    }

    @Test fun `weekdays and short dates`() {
        assertEquals(0, weekday(2026, 11, 15)) // a Sunday
        assertEquals(4, weekday(2026, 11, 19))
        assertEquals(6, weekday(2000, 1, 1))
        assertEquals("Sun 15 Nov", englishShortDate(2026, 11, 15))
        assertEquals("Thu 19 Nov", cardDate(20261119))
    }
}
