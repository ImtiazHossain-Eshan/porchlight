package app.porchlight.game

import kotlin.math.abs
import kotlin.math.floor

/**
 * Number text without `String.format`, so it reads the same on every platform. Each matches what
 * `String.format(Locale.US, ...)` printed before, digit for digit.
 */

/** 1234567 -> "1,234,567" (`"%,d"`). */
fun Int.grouped(): String = toLong().grouped()

fun Long.grouped(): String {
    val digits = abs(this).toString()
    val out = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
        out.append(c)
    }
    return if (this < 0) "-$out" else out.toString()
}

/**
 * 12.36f -> "12.4" (`"%.1f"`). Rounds half up on the float's exact value, as Java's formatter does:
 * a float widened to a double and multiplied by ten is exact, so nothing is rounded twice.
 */
fun oneDecimal(value: Float): String {
    val tenths = floor(abs(value.toDouble()) * 10 + 0.5).toLong()
    val text = "${tenths / 10}.${tenths % 10}"
    return if (value < 0f) "-$text" else text
}

/** Degrees to radians, as `Math.toRadians` did. */
internal fun toRadians(degrees: Double): Double = degrees * (kotlin.math.PI / 180.0)

/** Radians to degrees, as `Math.toDegrees` did. */
internal fun toDegrees(radians: Double): Double = radians * (180.0 / kotlin.math.PI)
