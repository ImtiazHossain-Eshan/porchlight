package app.porchlight.game

/** Year, month (1..12) and day of the browser's local date, [offset] days from today. */
private fun jsLocalDay(offset: Int): JsString = js(
    "(function(o){ var d = new Date(); d.setDate(d.getDate() + o); return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate(); })(offset)",
)

actual fun localDay(daysAgo: Int): LocalDay {
    val (y, m, d) = jsLocalDay(daysAgo).toString().split("-").map { it.toInt() }
    return LocalDay(y, m, d)
}

internal actual fun shortDate(year: Int, month: Int, day: Int): String = englishShortDate(year, month, day)
