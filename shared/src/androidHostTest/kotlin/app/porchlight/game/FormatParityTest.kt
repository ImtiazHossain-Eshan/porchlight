package app.porchlight.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The common formatters print exactly what `String.format(Locale.US, ...)` printed before. */
class FormatParityTest {
    @Test fun `grouped matches the Java formatter`() {
        for (n in listOf(0, 7, 999, 1_000, 2_340, 65_536, 1_234_567, Int.MAX_VALUE, -1, -12_345)) {
            assertEquals(String.format(Locale.US, "%,d", n), n.grouped())
        }
    }

    @Test fun `one decimal matches the Java formatter for every round time`() {
        for (ms in 0..120_000) {
            val f = ms / 1000f
            assertEquals("at $ms ms", String.format(Locale.US, "%.1f", f), oneDecimal(f))
        }
    }

    @Test fun `the English short date matches the Java formatter`() {
        for (day in 1..28) for (month in listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12)) {
            assertEquals(shortDate(2026, month, day), englishShortDate(2026, month, day))
        }
    }
}
