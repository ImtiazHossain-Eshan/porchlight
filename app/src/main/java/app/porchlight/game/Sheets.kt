package app.porchlight.game

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
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

/**
 * One boost before a round, as a walnut tile with its brass medallion. Off, the coin bobs and waits;
 * tap it and the coin spins over, the tile lifts and lights in the boost's own colour, and a spark
 * runs round its edge. A boost the wallet can't cover sits dim and says how many more it needs.
 */
@Composable
private fun RoundBoostTile(session: Session, boost: Boost, index: Int, on: Boolean, short: Int, modifier: Modifier, onToggle: () -> Unit) {
    val clock = session.clock
    val calm = session.calm
    val enabled = on || short <= 0
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
    val lit by animateFloatAsState(if (on) 1f else 0f, if (calm) snap() else bouncy, label = "lit")
    val spin by animateFloatAsState(if (on) 360f else 0f, if (calm) snap() else spring(dampingRatio = 0.7f, stiffness = 60f), label = "spin")
    val sink by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "sink")
    val phase = index * 1.7f
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(190.dp)
            .graphicsLayer {
                cameraDistance = 12f * density
                val s = sink * (1f + 0.04f * lit)
                scaleX = s
                scaleY = s
                translationY = -6.dp.toPx() * lit
                // A small lean toward the light while it is lit.
                rotationX = if (calm) 0f else 6f * lit * kotlin.math.sin(clock.seconds * 1.1f + phase)
                rotationY = if (calm) 0f else 5f * lit * kotlin.math.sin(clock.seconds * 0.8f + phase)
                alpha = if (enabled) 1f else 0.5f
            }
            .toggleable(on, interactionSource = press, indication = null, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .semantics {
                contentDescription = "${boost.title}, ${fireflies(boost.price)}. ${boost.detail}" +
                    if (!enabled) " You need $short more." else ""
            },
    ) {
        Column(
            Modifier
                .matchParentSize()
                .plank(Wood.Walnut, corner = 16.dp, elevation = (6 + 10 * lit).dp, seed = 70 + index)
                .drawBehind {
                    val c = Offset(size.width / 2f, size.height * 0.3f)
                    val breathe = if (calm) 0.5f else 0.5f + 0.5f * kotlin.math.sin(clock.seconds * 1.8f + phase)
                    val r = size.width * (0.75f + 0.25f * lit)
                    drawCircle(Brush.radialGradient(listOf(boost.halo.copy(alpha = 0.16f + 0.1f * breathe + 0.3f * lit), Color.Transparent), c, r), r, c)
                }
                .border(1.5.dp + 1.dp * lit, if (lit > 0.5f) SolidColor(boost.halo) else BrassRule, shape)
                .drawWithContent {
                    drawContent()
                    if (lit <= 0.01f) return@drawWithContent
                    // Two sparks chasing each other round the edge.
                    val w = size.width
                    val h = size.height
                    val per = 2f * (w + h)
                    for (k in 0..1) {
                        val u = (((if (calm) 0.1f else clock.seconds * 0.22f + phase) + k * 0.5f) % 1f + 1f) % 1f * per
                        val p = when {
                            u < w -> Offset(u, 0f)
                            u < w + h -> Offset(w, u - w)
                            u < 2 * w + h -> Offset(w - (u - w - h), h)
                            else -> Offset(0f, h - (u - 2 * w - h))
                        }
                        val gr = 18.dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * lit), boost.halo.copy(alpha = 0.6f * lit), Color.Transparent), p, gr), gr, p, blendMode = BlendMode.Plus)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(84.dp).graphicsLayer {
                    cameraDistance = 10f * density
                    rotationY = spin
                },
            ) { Medallion(boost, clock, calm, phase, Modifier.matchParentSize()) }
            FitText(boost.short, Display, 19.sp, Dusk.ink)
            Spacer(Modifier.weight(1f))
            GlowPrice(boost.price, clock, calm, phase)
            Spacer(Modifier.height(6.dp))
            val (label, tint) = when {
                on -> "✓ IN THIS ROUND" to boost.halo
                !enabled -> "NEED $short MORE" to Dusk.inkSoft
                else -> "TAP TO ADD" to Dusk.inkSoft
            }
            Text(label, style = Label, color = tint, maxLines = 1)
        }
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
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Boost.entries.filter { it.before }.forEachIndexed { i, boost ->
                val on = boost in picked
                RoundBoostTile(session, boost, i, on, short = boost.price - (balance - total), modifier = Modifier.weight(1f)) {
                    picked = if (on) picked - boost else picked + boost
                    session.sfx.play(Sound.Flip, vary = 0.06f)
                }
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
