package app.porchlight.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A parchment card on a dark scrim; a tap outside it calls [onOutside]. */
@Composable
private fun SheetFrame(description: String, onOutside: () -> Unit, glow: Color? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .drawBehind {
                if (glow != null) {
                    val c = Offset(size.width / 2f, size.height * 0.4f)
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), Color.Transparent), c, size.width * 0.8f), size.width * 0.8f, c)
                }
            }
            .tapToDismiss(onOutside)
            .safeDrawingPadding()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .shadow(16.dp, RoundedCornerShape(10.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(10.dp)
                .swallowTaps()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** One boost on a sheet: its mark, what it does, its price, and a brass lever to take it. */
@Composable
private fun BoostRow(boost: Boost, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (enabled || on) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphArt(boost.glyph, Craft.parchmentInk, Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(boost.title, color = Craft.parchmentInk, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold)
            Text(boost.detail, color = Craft.parchmentInkSoft, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Price(boost.price, Craft.parchmentInk)
        LeverSwitch(on, "${boost.title}, ${fireflies(boost.price)}") { if (enabled || on) onToggle() }
    }
}

/**
 * Before a round: the level, and the boosts that can be bought for it. PLAY shows what they come to
 * and what is left after; it is the one tap that buys them. Nothing is ever on by itself.
 */
@Composable
internal fun PreRoundSheet(session: Session) {
    val progress = session.store.progress
    val level = levelAt(progress.current)
    var picked by remember { mutableStateOf(emptySet<Boost>()) }
    val total = priceOf(picked)
    val balance = progress.wallet.balance
    SheetFrame("Level ${level.number}. Tap outside to close.", onOutside = { session.preRound = false }) {
        Text("LEVEL ${level.number}", style = Label, color = Craft.parchmentInkSoft)
        Text(chapterOf(level).name, style = Display, fontSize = 26.sp, color = Craft.parchmentInk, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(levelLine(level), color = Craft.parchmentInkSoft, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        val stars = progress.levelStars[level.number - 1]
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$stars of 3 stars" }) {
            repeat(3) { i -> Star(filled = i < stars, shown = true, modifier = Modifier.size(22.dp), instant = true) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Craft.parchmentShade))
        Spacer(Modifier.height(6.dp))
        Text("BOOSTS FOR THIS ROUND", style = Label, color = Craft.parchmentInkSoft, modifier = Modifier.fillMaxWidth())
        for (boost in Boost.entries.filter { it.before }) {
            val on = boost in picked
            BoostRow(boost, on, enabled = balance - total >= boost.price) {
                picked = if (on) picked - boost else picked + boost
                session.sfx.play(Sound.Flip, vary = 0.06f)
            }
        }
        Text(
            if (picked.isEmpty()) "You have ${fireflies(balance)}."
            else "${fireflies(total)} · you'll have ${balance - total} left. A boosted round earns up to ${Economy.BOOSTED_MAX_STARS} stars.",
            color = Craft.parchmentInk, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        PlayButton(
            "Play",
            session.calm,
            Modifier.fillMaxWidth(),
            sub = if (picked.isEmpty()) "No boosts" else "Boosts · ${fireflies(total)}",
            glyph = Glyph.Play,
        ) {
            if (picked.isEmpty()) {
                session.start(daily = false)
                return@PlayButton
            }
            val paid = session.store.pay { buyBoosts(it, picked, System.currentTimeMillis()) }
            if (paid is SpendResult.Spent) {
                session.sfx.play(Sound.Bought)
                session.start(daily = false, boosts = picked)
            }
        }
    }
}

/**
 * The bulb burned out, with a relight on offer: Relight (for Fireflies, or for an ad) or Try again.
 * Never more than these. The board stays face down while it is up.
 */
@Composable
internal fun BurnoutSheet(session: Session) {
    val round = session.round
    val balance = session.store.progress.wallet.balance
    val price = Boost.Relight.price
    SheetFrame("The bulb burned out. Relight, or try again.", onOutside = {}, glow = Craft.danger) {
        Text("The bulb burned out", style = Display, fontSize = 26.sp, color = Craft.parchmentInk, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(4.dp))
        Text(
            "${round.pairs} of ${round.pairCount} pairs found. Relight it for ${secondsText(Economy.BOOST_RELIGHT_MS)} more seconds; the pairs you found stay found.",
            color = Craft.parchmentInk, fontSize = TypeScale.Body, lineHeight = 20.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "A relit round earns up to ${Economy.BOOSTED_MAX_STARS} stars.",
            color = Craft.parchmentInkSoft, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        if (balance >= price) {
            PrimaryButton("Relight for $price", Modifier.fillMaxWidth(), glyph = Glyph.Bulb) { session.relightWithFireflies() }
            Text(
                "You have ${balance.grouped()} · ${(balance - price).grouped()} after",
                color = Craft.parchmentInkSoft, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        AdButton(session, Placement.Relight, "Relight for free", Modifier.fillMaxWidth()) { session.relightWithAd(it) }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Try again", Modifier.fillMaxWidth(), glyph = Glyph.Replay) { session.declineRelight(again = true) }
    }
}
