package app.porchlight.game

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun localDay(daysAgo: Int): LocalDay {
    val calendar = NSCalendar.currentCalendar
    val date = calendar.dateByAddingUnit(NSCalendarUnitDay, daysAgo.toLong(), NSDate(), 0u) ?: NSDate()
    val parts = calendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, date)
    return LocalDay(parts.year.toInt(), parts.month.toInt(), parts.day.toInt())
}

internal actual fun shortDate(year: Int, month: Int, day: Int): String = englishShortDate(year, month, day)
