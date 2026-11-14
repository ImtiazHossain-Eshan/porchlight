package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The tutorial's pointing hand and its two words of advice, plus a way out. */
@Composable
internal fun TutorialHints(session: Session) {
    val round = session.round
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            "Skip",
            style = Label,
            fontSize = 13.sp,
            color = Dusk.inkSoft,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { session.endTutorial() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }

    val (target, words) = when {
        round.phase == Phase.Peek -> null to "Remember them"
        round.phase != Phase.Play || round.miss -> return
        round.open.isEmpty() -> round.deck.indices.firstOrNull { !round.matched[it] } to "Tap a card"
        else -> {
            val first = round.open.first()
            round.deck.indices.firstOrNull { it != first && round.deck[it] == round.deck[first] } to "Find its twin"
        }
    }
    val bob = rememberInfiniteTransition(label = "hand")
    val press by bob.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "press")
    val density = LocalDensity.current
    val at = target?.let { session.cardAt[it] } ?: Offset(session.bulbAt.x, session.bulbAt.y + session.bulbWidth * 0.95f)

    Column(
        Modifier
            .offset {
                with(density) { IntOffset((at.x - 80.dp.toPx()).toInt(), (at.y - 6.dp.toPx()).toInt()) }
            }
            .width(160.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (target != null) {
            Canvas(
                Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        translationY = 8.dp.toPx() * press
                        val s = 1f - 0.08f * press
                        scaleX = s
                        scaleY = s
                    },
            ) { drawHand() }
        }
        Text(
            words,
            style = Display,
            fontSize = 18.sp,
            color = Dusk.paperInk,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Dusk.amber)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** A cartoon pointing hand, finger up, drawn in a 44-unit box. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHand() {
    val s = size.minDimension / 44f
    val hand = Path().apply {
        moveTo(17f * s, 4f * s)
        cubicTo(17f * s, 1f * s, 23f * s, 1f * s, 23f * s, 4f * s)
        lineTo(23f * s, 18f * s)
        cubicTo(26f * s, 16f * s, 30f * s, 17f * s, 30f * s, 20f * s)
        cubicTo(33f * s, 19f * s, 37f * s, 21f * s, 36f * s, 25f * s)
        lineTo(35f * s, 34f * s)
        cubicTo(34f * s, 40f * s, 30f * s, 43f * s, 24f * s, 43f * s)
        lineTo(19f * s, 43f * s)
        cubicTo(14f * s, 43f * s, 11f * s, 40f * s, 9f * s, 36f * s)
        lineTo(5f * s, 27f * s)
        cubicTo(4f * s, 24f * s, 8f * s, 22f * s, 10f * s, 24f * s)
        lineTo(17f * s, 30f * s)
        close()
    }
    drawPath(hand, Color.White)
    drawPath(hand, Dusk.paperInk, style = Stroke(2.2f * s, join = StrokeJoin.Round))
}

/** A night's one-line introduction: the moths that join, or the twist, then the button to begin. */
@Composable
internal fun NightIntro(night: Night, session: Session) {
    val rise = remember(night) { Animatable(0f) }
    LaunchedEffect(night) { rise.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    val moths = newOn(night)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f * rise.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .graphicsLayer {
                    translationY = (1f - rise.value) * 120.dp.toPx()
                    alpha = rise.value.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Dusk.panel)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("NIGHT ${night.number}", style = Label, color = Dusk.amber)
            Spacer(Modifier.height(4.dp))
            Text(
                if (night.twist != Twist.None) night.twist.title else "New moths",
                style = Display,
                fontSize = 26.sp,
                color = Dusk.ink,
            )
            Spacer(Modifier.height(14.dp))
            IntroArt(night, moths)
            Spacer(Modifier.height(14.dp))
            Text(
                if (night.twist != Twist.None) night.twist.tip
                else "${moths.joinToString(" and ") { it.label }} join the porch. ${night.pairs} pairs tonight.",
                color = Dusk.inkSoft,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Light the porch", Modifier.fillMaxWidth()) { session.beginAfterIntro() }
        }
    }
}

@Composable
private fun IntroArt(night: Night, moths: List<MothId>) {
    val loop = rememberInfiniteTransition(label = "intro")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")
    when (night.twist) {
        Twist.Wanderer -> Box(Modifier.size(150.dp, 84.dp)) {
            // Two card backs trade places.
            for (side in 0..1) {
                val x = if (side == 0) t else 1f - t
                Box(
                    Modifier
                        .offset(x = 90.dp * x, y = (-14).dp * kotlin.math.sin(t * Math.PI.toFloat()) * (if (side == 0) 1f else -1f) + 10.dp)
                        .size(60.dp, 76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Dusk.cardBack),
                    contentAlignment = Alignment.Center,
                ) {
                    MothAtRest(Dusk.cardEmblem, Dusk.cardBack, Modifier.fillMaxSize().padding(8.dp))
                }
            }
        }
        Twist.Leaves -> MothArt(MothId.Leaf, Modifier.size(84.dp).graphicsLayer { rotationZ = -12f + 24f * t })
        Twist.Shadow -> MothArt(MothId.Iris, Modifier.size(84.dp), flat = Dusk.locked)
        Twist.None -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            moths.forEachIndexed { i, moth ->
                MothArt(moth, Modifier.size(76.dp), open = 0.8f + 0.2f * if (i % 2 == 0) t else 1f - t)
            }
        }
    }
}
