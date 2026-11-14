package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

internal const val BOARD_COLUMNS = 4
private val GAP = 8.dp
private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val WRONG = Color(0xFFFF8A75)
private val PAPER_EDGE = Color(0xFFE2CFA0)
private val LEAF_PAPER = Color(0xFFD9C7A2)
private val SILHOUETTE = Color(0xFF3A3328)

/** The board fills whatever space it is given: cards keep a 3:4 shape and shrink to fit. */
@Composable
internal fun Board(session: Session, modifier: Modifier) {
    val round = session.round
    val count = round.deck.size
    val columns = if (count <= 4) 2 else BOARD_COLUMNS
    val rows = (count + columns - 1) / columns
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val byWidth = (maxWidth - GAP * (columns - 1)) / columns
        val byHeight = (maxHeight - GAP * (rows - 1)) / rows * 0.75f
        val cardW = min(byWidth, byHeight)
        val cardH = cardW / 0.75f
        Column(verticalArrangement = Arrangement.spacedBy(GAP), horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        if (index < count) Card(index, session, cardW, cardH)
                    }
                }
            }
        }
    }
}

/**
 * One card. Its three states differ in colour, brightness and motion:
 * face down is a dark slate back; face up is warm paper, lifted off the board, its moth flapping;
 * matched is an empty dim slot with a gold rim, because the moth has flown to the bulb.
 */
@Composable
private fun Card(index: Int, session: Session, width: Dp, height: Dp) {
    val round = session.round
    val id = round.deck[index]
    val up = round.isFaceUp(index)
    val matched = round.matched[index]
    val lost = round.phase == Phase.Lost && !matched
    val wrong = round.miss && index in round.open
    val lifted = index in round.open
    val playable = round.phase == Phase.Play && !matched && index !in round.spent
    var pressed by remember { mutableStateOf(false) }
    var centre by remember { mutableStateOf(Offset.Zero) }
    val flapPhase = remember { Random.nextFloat() * 6.283f }

    val turn by animateFloatAsState(if (up) 180f else 0f, tween(300), label = "turn")
    // The moth wakes up just after the card faces you; on a wrong pair it flinches shut.
    val wings by animateFloatAsState(
        when {
            wrong -> 0.14f
            up -> 1f
            else -> 0.1f
        },
        if (wrong) tween(90) else tween(380, delayMillis = if (up) 120 else 0),
        label = "wings",
    )
    val lift by animateFloatAsState(
        if (lifted) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, tween(60), label = "press")
    val emptied by animateFloatAsState(if (matched) 1f else 0f, tween(240), label = "emptied")
    val shake = remember { Animatable(0f) }
    val red = remember { Animatable(0f) }
    // A Wanderer swap: the two cards slide across to each other's places.
    val wander = remember { Animatable(0f) }
    val swapWith = round.swap?.let { (a, b) -> if (a == index) b else if (b == index) a else null }
    LaunchedEffect(round.swaps) {
        if (swapWith != null) {
            wander.snapTo(1f)
            wander.animateTo(0f, tween(if (session.calm) 0 else 520))
        }
    }
    val leaf = id == MothId.Leaf
    val spent = index in round.spent
    LaunchedEffect(spent) {
        if (spent) {
            red.snapTo(1f)
            delay(220)
            red.animateTo(0f, tween(300))
        }
    }
    LaunchedEffect(wrong) {
        if (wrong) {
            launch {
                red.snapTo(1f)
                delay(150)
                red.animateTo(0f, tween(140))
            }
            if (!session.calm) for (x in SHAKE) shake.animateTo(x, tween(45))
        }
    }

    Box(
        Modifier
            .size(width, height)
            .onGloballyPositioned {
                centre = it.boundsInRoot().center
                session.cardAt[index] = centre
                session.cardWidth = it.size.width.toFloat()
            }
            .graphicsLayer {
                rotationY = turn
                cameraDistance = 16f * density
                // Mid-turn the card lifts off the board, so the flip doesn't look flat.
                val mid = sin(turn / 180f * Math.PI.toFloat())
                translationY = -6.dp.toPx() * mid - 3.dp.toPx() * lift
                translationX = shake.value * density
                if (swapWith != null && wander.value > 0f) {
                    // Start from the other card's spot and slide home, lifted above its neighbours.
                    val from = session.cardAt[swapWith] - session.cardAt[index]
                    translationX += from.x * wander.value
                    translationY += from.y * wander.value - 10.dp.toPx() * sin(wander.value * Math.PI.toFloat())
                }
                val s = press * (1f + 0.06f * lift)
                scaleX = s
                scaleY = s
                shadowElevation = (1f - emptied) * (5f + 11f * lift) * density
                ambientShadowColor = Dusk.shadow
                spotShadowColor = Dusk.shadow
                shape = CARD_SHAPE
                clip = true
            }
            .drawWithContent {
                val faceUp = turn > 90f
                val corner = CornerRadius(16.dp.toPx())
                val fill = when {
                    !faceUp -> Dusk.cardBack
                    lost -> lerp(Dusk.paper, Dusk.cardBack, 0.62f)
                    leaf -> lerp(LEAF_PAPER, WRONG, red.value)
                    else -> lerp(lerp(Dusk.paper, WRONG, red.value), Dusk.slot, emptied)
                }
                drawRect(fill)
                drawContent()

                // Cards near the bulb catch more of its light; empty slots stay dark.
                val reach = session.bulbWidth * 4.2f
                val far = if (reach > 0f) ((centre - session.bulbAt).getDistance() / reach).coerceIn(0f, 1f) else 1f
                val glow = session.power().coerceIn(0f, 1.3f) * (0.4f + 0.6f * session.timeLeft)
                drawRect(Dusk.glowDeep, alpha = (0.24f * (1f - far) * glow * (1f - 0.8f * emptied)).coerceIn(0f, 1f))

                val edge = when {
                    !faceUp -> Dusk.cardEdge
                    lost -> Dusk.cardEdge
                    else -> lerp(PAPER_EDGE, Dusk.amber.copy(alpha = 0.55f), emptied)
                }
                val line = 1.5.dp.toPx()
                drawRoundRect(edge, Offset(line / 2, line / 2), size.copy(size.width - line, size.height - line), corner, style = Stroke(line))
                if (emptied < 1f) {
                    // A thin highlight along the top edge, like the lip of a real card.
                    val inset = 16.dp.toPx()
                    drawLine(
                        Color.White.copy(alpha = 0.18f * (1f - emptied)),
                        Offset(inset, 1.dp.toPx()),
                        Offset(size.width - inset, 1.dp.toPx()),
                        1.dp.toPx(),
                    )
                }
            }
            .pointerInput(playable) {
                if (!playable) return@pointerInput
                // The card answers on press, not on release.
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    session.flip(index)
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = "Card ${index + 1} of ${round.deck.size}, " + when {
                    matched -> "${id.label}, matched"
                    up -> id.label
                    else -> "face down"
                }
                if (playable) onClick { session.flip(index); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (turn <= 90f) {
            MothAtRest(Dusk.cardEmblem, Dusk.cardBack, Modifier.fillMaxSize().padding(10.dp))
        } else if (!matched) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .graphicsLayer {
                        rotationY = 180f
                        alpha = if (lost) 0.5f else 1f
                    },
            ) {
                // An idle moth flaps gently, each card out of step with the others.
                val flap = if (lost || leaf || session.calm) 1f else 0.95f + 0.05f * sin(session.clock.seconds * 5.236f + flapPhase)
                // On a Shadow night one moth is only a silhouette during the peek.
                val silhouette = if (round.phase == Phase.Peek && id == round.shadow) SILHOUETTE else null
                moth(
                    id,
                    Offset(size.width / 2, size.height / 2),
                    size.minDimension,
                    if (leaf) 1f else wings * flap,
                    flat = silhouette,
                    gold = id in session.store.progress.mastered,
                )
            }
        }
    }
}

private val SHAKE = floatArrayOf(-7f, 7f, -5f, 5f, 0f)
