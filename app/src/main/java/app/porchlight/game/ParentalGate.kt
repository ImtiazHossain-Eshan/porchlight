package app.porchlight.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val ONES = listOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

/** 42 -> "forty-two", 70 -> "seventy". Two-digit numbers from 21 to 99. */
fun inWords(n: Int): String {
    require(n in 21..99)
    val tens = TENS[n / 10]
    val ones = ONES[n % 10]
    return if (ones.isEmpty()) tens else "$tens-$ones"
}

/** A fresh number for the gate: never a round ten, so the answer always takes two different digits. */
fun gateNumber(random: Random = Random.Default): Int {
    while (true) {
        val n = random.nextInt(21, 100)
        if (n % 10 != 0) return n
    }
}

/**
 * The grown-up check before anything is bought with money and before any link out of the app: a
 * number written in words, typed as digits. Everyone sees it (the game is for all ages); it is never
 * in front of a rewarded ad. Three wrong tries bring a new number.
 */
@Composable
internal fun ParentalGate(why: String, onPass: () -> Unit, onCancel: () -> Unit) {
    var number by remember { mutableIntStateOf(gateNumber()) }
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    fun press(d: Int) {
        if (typed.length >= 2) return
        typed += d
        if (typed.length < 2) return
        if (typed.toInt() == number) {
            onPass()
            return
        }
        wrong += 1
        typed = ""
        if (wrong >= 3) {
            wrong = 0
            number = gateNumber()
            message = "Here's a new one."
        } else {
            message = "Not quite. Try again."
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .tapToDismiss(onCancel)
            .semantics { contentDescription = "Ask a grown-up. Tap outside to close." },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .padding(horizontal = 24.dp)
                .shadow(14.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(8.dp)
                .swallowTaps()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ask a grown-up", style = Display, fontSize = 24.sp, color = Craft.parchmentInk, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(why, color = Craft.parchmentInkSoft, fontSize = TypeScale.Body, textAlign = TextAlign.Center, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text("Type this number in digits:", color = Craft.parchmentInk, fontSize = TypeScale.Body)
            Text(inWords(number), style = Display, fontSize = 26.sp, color = Craft.parchmentInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            // The two boxes the digits go into.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { i ->
                    Box(
                        Modifier
                            .padding(2.dp)
                            .border(1.5.dp, BrassRule, RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(typed.getOrNull(i)?.toString() ?: " ", style = Display, fontSize = 26.sp, color = Craft.parchmentInk)
                    }
                }
            }
            Text(
                message ?: " ",
                color = Craft.parchmentInkSoft, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(8.dp))
            // A keypad of small oak keys.
            for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (key in row) {
                        when (key) {
                            -1 -> Spacer(Modifier.weight(1f))
                            -2 -> SecondaryButton("Del", Modifier.weight(1f)) { typed = typed.dropLast(1) }
                            else -> PrimaryButton("$key", Modifier.weight(1f)) { press(key) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Not now", Modifier.fillMaxWidth()) { onCancel() }
        }
    }
}
