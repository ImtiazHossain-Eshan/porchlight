package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.min
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Play: the bulb on top with score and streak beside it, the board below. Nothing scrolls. */
@Composable
internal fun RoundScreen(session: Session) {
    val round = session.round

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = session.shake.value * density }
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (maxWidth > maxHeight) {
                // Landscape and wide tablets: the bulb beside the board.
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Header(session, Modifier.fillMaxHeight().weight(0.4f))
                    Board(session, Modifier.fillMaxHeight().weight(0.6f))
                }
            } else {
                Column {
                    Header(session, Modifier.fillMaxWidth().weight(0.29f))
                    Board(session, Modifier.fillMaxWidth().weight(0.71f))
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

        FlockLayer(session)
        FxLayer(session)
        Pops(session)
        if (round.tutorial) TutorialHints(session)
        if (round.paused) Paused(session)
        session.intro?.let { NightIntro(it, session) }
    }
}

/** Sorbet's top-up: "+1.5s" floats up off the bulb each time a pair lands. */
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
        style = Display,
        fontSize = 18.sp,
        color = Dusk.glow,
        modifier = modifier.graphicsLayer {
            alpha = 1f - rise.value
            translationY = -44.dp.toPx() * rise.value
            translationX = 34.dp.toPx()
        },
    )
}

/** The round is frozen. Tap anywhere to light it again. */
@Composable
private fun Paused(session: Session) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { session.resume() }
            .semantics { contentDescription = "Paused. Tap to continue." },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Paused", style = Display, fontSize = 40.sp, color = Dusk.ink)
            Text("Tap to continue", color = Dusk.inkSoft, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            SecondaryButton("Leave round") { session.leaving = true }
        }
    }
}

/** The bulb with score on one side and streak on the other. */
@Composable
private fun Header(session: Session, modifier: Modifier) {
    val round = session.round
    val low = round.phase == Phase.Play && round.left <= LOW_MS
    BoxWithConstraints(modifier) {
        val bulbH = min(maxHeight * 0.8f, maxWidth * 0.5f / 0.7f)
        ScoreReadout(round.score, Modifier.align(Alignment.CenterStart))
        StreakReadout(
            multiplierAt(if (round.running) round.streak else 0, round.rules),
            session.calm,
            Modifier.align(Alignment.CenterEnd),
        )
        OverdriveBanner(session.overdrives, session.calm, Modifier.align(Alignment.BottomCenter).offset(y = 40.dp))

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Bulb(
                clock = session.clock,
                timeLeft = { session.timeLeft },
                power = session.power,
                showArc = true,
                modifier = Modifier
                    .size(bulbH * 0.7f, bulbH)
                    .tracksBulb(session)
                    .semantics { contentDescription = "${(round.left + 999) / 1000} seconds left" },
            )
            // A small number for players who want one; a decimal only under ten seconds.
            val seconds = round.left / 1000f
            Text(
                if (seconds < 10f) String.format(Locale.US, "%.1f", seconds) else "${seconds.toInt()}",
                style = Display,
                fontSize = 14.sp,
                color = if (low) Dusk.ember else Dusk.inkSoft,
            )
        }

        if (round.rules.pairBonusMs > 0) {
            TopUp(session.topUps, round.rules.pairBonusMs, Modifier.align(Alignment.TopCenter).padding(top = bulbH * 0.2f))
        }

        if (round.phase == Phase.Play && !round.tutorial) {
            RoundIconButton(
                label = "II",
                description = "Pause",
                enabled = !round.paused,
                size = 40.dp,
                modifier = Modifier.align(Alignment.TopStart),
                onClick = session::pause,
            )
        }

        if (round.phase == Phase.Play && round.rules.flashes > 0) {
            RoundIconButton(
                label = "2×",
                description = if (round.flashes > 0) "Second look" else "Second look used",
                enabled = round.flashes > 0 && !round.flashing,
                modifier = Modifier.align(Alignment.BottomEnd),
                onClick = session::flash,
            )
        }
    }
}
