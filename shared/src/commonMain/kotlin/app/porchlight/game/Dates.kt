package app.porchlight.game

/** A day on the calendar, in the phone's own time zone. [month] runs 1..12. */
data class LocalDay(val year: Int, val month: Int, val day: Int)

/** Today, [daysAgo] days back (negative goes back: -1 is yesterday), in the phone's time zone. */
expect fun localDay(daysAgo: Int = 0): LocalDay

/** Today's daily seed, or [daysAgo] days from it: 2026-11-14 -> 20261114. */
fun todaySeed(daysAgo: Int = 0): Long = localDay(daysAgo).let { dailySeed(it.year, it.month, it.day) }

/** 20261115 -> "Sun 15 Nov", as the share card prints it. */
fun cardDate(day: Long): String = shortDate((day / 10_000).toInt(), (day / 100 % 100).toInt(), (day % 100).toInt())

/** "Sun 15 Nov" for a date. Android keeps its own formatter so its card reads exactly as before. */
internal expect fun shortDate(year: Int, month: Int, day: Int): String

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Day of the week, 0 Sunday .. 6 Saturday, for a Gregorian date (Sakamoto's method). */
fun weekday(year: Int, month: Int, day: Int): Int {
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    return (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day).mod(7)
}

/** The plain English "Sun 15 Nov", built by hand, for platforms without Java's formatter. */
fun englishShortDate(year: Int, month: Int, day: Int): String =
    "${WEEKDAYS[weekday(year, month, day)]} $day ${MONTHS[(month - 1).coerceIn(0, 11)]}"
