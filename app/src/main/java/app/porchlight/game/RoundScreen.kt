package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

/** Play: the bulb and its works on top with score and streak beside it, the board below. Nothing scrolls. */
@Composable
internal fun RoundScreen(session: Session) {
    val round = session.round

    // A Wanderer swap sets a swirl turning between the two cards.
    LaunchedEffect(round.swaps) {
        val swap = round.swap
        if (round.swaps > 0 && swap != null && !session.calm) {
            session.swirlFrom = session.cardAt[swap.first]
            session.swirlTo = session.cardAt[swap.second]
            session.swirl.snapTo(0f)
            session.swirl.animateTo(1f, tween(700))
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Moths on the far side of their ring pass behind the bulb.
        FlockLayer(session, front = false)
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = session.shake.value * density }
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (maxWidth > maxHeight) {
                // Landscape and wide tablets: the bulb beside the board.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Header(session, Modifier.fillMaxHeight().weight(0.4f))
                    Board(session, Modifier.fillMaxHeight().weight(0.6f))
                }
            } else {
                Column {
                    Header(session, Modifier.fillMaxWidth().weight(0.31f))
                    Board(session, Modifier.fillMaxWidth().weight(0.69f))
                }
            }

            // The status line is spoken, not shown.
            Box(
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = round.say
                },
            )
        }

        FlockLayer(session, front = true)
        FxLayer(session)
        Pops(session)
        if (round.tutorial) TutorialHints(session)
        if (round.paused) Paused(session)
        session.intro?.let { NightIntro(it, session) }
    }
}

/** The bulb in its works, with score on one side and streak on the other, and the band under it. */
@Composable
private fun Header(session: Session, modifier: Modifier) {
    val round = session.round
    val low = round.phase == Phase.Play && !round.paused && round.left <= LOW_MS
    BoxWithConstraints(
        modifier.graphicsLayer {
            // Overdrive's colour-split pulse: this layer is everything above the board, so it never
            // touches the cards.
            val a = session.aberration.value
            renderEffect = if (a > 0.01f && !session.calm) {
                session.effects.aberration(session.bulbAt.x, size.height * 0.45f, a * 9.dp.toPx())
            } else null
        },
    ) {
        val bulbH = min(maxHeight * 0.66f, maxWidth * 0.42f / 0.7f)
        ScoreReadout(round.score, Modifier.align(Alignment.BottomStart).padding(bottom = 44.dp))
        StreakReadout(
            multiplierAt(if (round.running) round.streak else 0, round.rules),
            session.calm,
            Modifier.align(Alignment.BottomEnd).padding(bottom = 44.dp),
        )

        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Bulb(
                clock = session.clock,
                timeLeft = { session.timeLeft },
                power = session.power,
                lamp = Lamp.Works,
                hue = { session.hue },
                smoke = { low },
                still = session.calm,
                modifier = Modifier
                    .size(bulbH * 0.7f, bulbH)
                    .tracksBulb(session)
                    .graphicsLayer {
                        // Under five seconds the air over the bulb shimmers with heat.
                        renderEffect = if (low && !session.calm) {
                            session.effects.shimmer(size.width, size.height, session.clock.seconds, 2.5f.dp.toPx())
                        } else null
                    }
                    .semantics { contentDescription = "${(round.left + 999) / 1000} seconds left" },
            )
            // The time, in the light's own colour. In the last seconds it is the biggest thing here.
            val seconds = round.left / 1000f
            Text(
                when {
                    low -> String.format(Locale.US, "%02ds", (round.left + 999) / 1000)
                    seconds < 10f -> String.format(Locale.US, "%.1f", seconds)
                    else -> "${seconds.toInt()}"
                },
                style = Gilt,
                fontSize = if (low) 30.sp else 20.sp,
                color = when {
                    low -> Craft.danger
                    seconds < 10f -> Dusk.glowDeep
                    else -> Dusk.glow
                },
            )
        }

        // One thing at a time in the band between the bulb and the board: never over the cards.
        Band(session, Modifier.align(Alignment.BottomCenter))

        if (round.rules.pairBonusMs > 0) {
            TopUp(session.topUps, round.rules.pairBonusMs, Modifier.align(Alignment.TopCenter).padding(top = bulbH * 0.2f))
        }

        if (round.phase == Phase.Play && !round.tutorial) {
            BrassButton(Glyph.Pause, "Pause", enabled = !round.paused, modifier = Modifier.align(Alignment.TopStart), onClick = session::pause)
        }

        if (round.phase == Phase.Play && round.rules.flashes > 0) {
            BrassButton(
                Glyph.Eye,
                if (round.flashes > 0) "Second look" else "Second look used",
                enabled = round.flashes > 0 && !round.flashing,
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = session::flash,
            )
        }
    }
}

/**
 * The band under the bulb. During the peek it counts READY 3, 2, 1; when a Wanderer moves cards it
 * warns SWAP; when the streak hits its cap it says OVERDRIVE.
 */
@Composable
private fun Band(session: Session, modifier: Modifier) {
    val round = session.round
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(round.phase, round.paused) {
        if (round.phase == Phase.Peek && !round.paused) {
            for (n in 3 downTo 1) {
                count = n
                delay(round.rules.peekMs / 3)
            }
        }
        count = 0
    }
    val swap = remember { Animatable(0f) }
    LaunchedEffect(round.swaps) {
        if (round.swaps > 0) {
            swap.snapTo(1f)
            delay(650)
            swap.animateTo(0f, tween(250))
        }
    }
    Box(modifier.height(40.dp), contentAlignment = Alignment.Center) {
        when {
            round.phase == Phase.Peek && count > 0 -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("READY!", style = Display.copy(shadow = Shadow(Craft.bioDeep, Offset.Zero, 22f)), fontSize = 26.sp, color = Craft.bio)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 3 downTo 1) {
                        Text(
                            "$n",
                            style = Display.copy(shadow = if (n == count) Shadow(Craft.bioDeep, Offset.Zero, 18f) else null),
                            fontSize = 22.sp,
                            color = if (n == count) Color.White else Craft.bio.copy(alpha = if (n > count) 0.25f else 0.55f),
                        )
                    }
                }
            }
            swap.value > 0.01f -> Text(
                "SWAP!",
                style = Display.copy(shadow = Shadow(Craft.danger, Offset.Zero, 20f)),
                fontSize = 28.sp,
                color = Color(0xFFFFE9D6),
                modifier = Modifier.graphicsLayer {
                    alpha = swap.value
                    val s = 0.9f + 0.1f * swap.value
                    scaleX = s
                    scaleY = s
                },
            )
            else -> OverdriveBanner(session.overdrives, session.calm)
        }
    }
}

/** Sorbet's top-up: "+1s" floats up off the bulb each time a pair lands. */
@Composable
private fun TopUp(count: Int, bonusMs: Long, modifier: Modifier) {
    val rise = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count > 0) {
            rise.snapTo(0f)
            rise.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
    }
    val text = String.format(Locale.US, "+%.1fs", bonusMs / 1000f).replace(".0s", "s")
    Text(
        text,
        style = Gilt,
        fontSize = 18.sp,
        color = Dusk.glow,
        modifier = modifier.graphicsLayer {
            alpha = 1f - rise.value
            translationY = -44.dp.toPx() * rise.value
            translationX = 38.dp.toPx()
        },
    )
}

/** A sheet of dark over the round, for the pause sign and notices. Tapping it does [onTap]. */
@Composable
private fun Hush(description: String, onTap: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The round is frozen: an oak sign swings down on two chains. Tap anywhere to light the porch again. */
@Composable
private fun Paused(session: Session) {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    Hush("Paused. Tap to continue.", onTap = session::resume) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    translationY = (drop.value - 1f) * 160.dp.toPx()
                    rotationZ = if (session.calm) 0f else (1f - drop.value) * 6f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The chains.
            Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                for (x in listOf(size.width * 0.22f, size.width * 0.78f)) {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Craft.brassDark, Offset(x, y), Offset(x, y + 9.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
                        drawLine(Craft.brassLight, Offset(x, y + 2.dp.toPx()), Offset(x, y + 7.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                        y += 12.dp.toPx()
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .plank(Wood.Oak, corner = 12.dp, elevation = 12.dp, seed = 21)
                    .border(1.5.dp, BrassRule, RoundedCornerShape(12.dp))
                    .padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("PAUSED", style = Carved, fontSize = 34.sp, color = Dusk.paperInk)
                Text("Tap to continue", color = Color(0xFF3A2612), fontSize = 15.sp)
            }
            Spacer(Modifier.height(22.dp))
            SecondaryButton("Leave round", Modifier.fillMaxWidth()) { session.leaving = true }
        }
    }
}

/** A notice pinned over the round: leaving loses it. */
@Composable
internal fun LeaveNotice(session: Session) {
    Hush("Leave this round? Tap outside to keep playing.", onTap = { session.leaving = false }) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 28.dp)
                .shadow(14.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(8.dp)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Leave this round?", style = Display, fontSize = 22.sp, color = Craft.parchmentInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "The bulb goes out and this round won't count.",
                color = Craft.parchmentInk,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Keep playing", Modifier.fillMaxWidth()) { session.leaving = false }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Leave", Modifier.fillMaxWidth()) { session.toPorch() }
        }
    }
}
