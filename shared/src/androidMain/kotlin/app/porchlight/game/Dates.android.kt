package app.porchlight.game

import java.util.Calendar
import java.util.Locale

actual fun localDay(daysAgo: Int): LocalDay {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, daysAgo)
    return LocalDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

internal actual fun shortDate(year: Int, month: Int, day: Int): String {
    val c = Calendar.getInstance()
    c.clear()
    c.set(year, month - 1, day)
    return String.format(Locale.UK, "%ta %<te %<tb", c)
}
