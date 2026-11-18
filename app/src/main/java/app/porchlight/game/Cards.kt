package app.porchlight.game

import kotlin.math.cos
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import android.os.Build
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/** Three columns for a small board, four otherwise. */
internal fun boardColumns(count: Int) = if (count <= 6) 3 else BOARD_COLUMNS

/** The gap between cards: tighter on the tall boards, so the cards keep their size. */
private fun gapFor(rows: Int) = when {
    rows >= 6 -> 4.dp
    rows >= 5 -> 6.dp
    else -> 8.dp
}

/** Cards narrower than this are hard to read and hit: a crowded board squares them up first. */
private val MIN_CARD = 64.dp
private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val WRONG = Color(0xFFFF8A75)
private val PAPER_EDGE = Color(0xFFE2CFA0)
private val LEAF_PAPER = Color(0xFFD9C7A2)
private val SILHOUETTE = Color(0xFF3A3328)

/**
 * The board fills whatever space it is given: cards keep a 3:4 shape and shrink to fit. On a tall
 * board in a short space they square up (down to 1:1) before they get narrower than [MIN_CARD].
 * In a Blackout the board goes dark: the cards still take taps, but nothing face up shows.
 */
@Composable
internal fun Board(session: Session, modifier: Modifier) {
    val round = session.round
    val count = round.deck.size
    val columns = boardColumns(count)
    val rows = (count + columns - 1) / columns
    BoxWithConstraints(
        modifier.onGloballyPositioned { session.boardTop = it.boundsInRoot().top }.graphicsLayer {
            val tilt = session.tilt
            cameraDistance = 16f * density
            rotationY = tilt.x * 4f
            rotationX = -tilt.y * 4f
            translationX = tilt.x * parallaxPx(density) * DEPTH_BOARD
            translationY = tilt.y * parallaxPx(density) * DEPTH_BOARD
        },
        contentAlignment = Alignment.Center,
    ) {
        val gap = gapFor(rows)
        val byWidth = (maxWidth - gap * (columns - 1)) / columns
        val rowHeight = (maxHeight - gap * (rows - 1)) / rows
        var cardW = min(byWidth, rowHeight * 0.75f)
        var cardH = cardW / 0.75f
        if (cardW < MIN_CARD) {
            cardW = min(byWidth, min(MIN_CARD, rowHeight))
            cardH = min(rowHeight, cardW / 0.75f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until columns) {
                        val index = row * columns + col
                        if (index < count) Card(index, session, cardW, cardH, row, rows)
                    }
                }
            }
        }
        // The Blackout: the board goes dark over the cards. It takes no taps of its own, so the
        // cards under it can still be turned.
        val out by animateFloatAsState(
            if (session.dark == Dark.Out) 1f else 0f,
            tween(if (session.calm) 0 else 70),
            label = "blackout",
        )
        if (out > 0.001f) {
            Canvas(Modifier.matchParentSize().semantics { contentDescription = "The bulb is out" }) {
                drawRect(Color(0xFF05070A), alpha = 0.96f * out)
            }
        }
    }
}

/**
 * One card, drawn as a thick physical tile. Its three states differ in colour, brightness and
 * motion: face down is a slate back with a foil sheen; face up is warm paper, lifted off the board,
 * its moth flapping; matched is an empty dim slot, because the moth has flown to the bulb.
 */
@Composable
private fun Card(index: Int, session: Session, width: Dp, height: Dp, row: Int, rows: Int) {
    val round = session.round
    val id = round.deck[index]
    // During the deal and warm-up the cards lie face down, though the round is already in its peek.
    val faceUp = round.isFaceUp(index) && !(round.phase == Phase.Peek && session.peekStage != PeekStage.Look)
    // Turning up for the peek runs in a wave from the bulb down (top row first); folding shut at
    // its end runs back up (bottom row first). A player's own flips turn at once.
    var up by remember { mutableStateOf(faceUp) }
    LaunchedEffect(faceUp) {
        val wait = when {
            session.calm -> 0L
            faceUp && round.phase == Phase.Peek -> row * WAVE_STAGGER_MS
            !faceUp && session.peekStage == PeekStage.Go -> (rows - 1 - row) * WAVE_STAGGER_MS
            else -> 0L
        }
        if (wait > 0) delay(wait)
        up = faceUp
    }
    // The deal: each card drops onto the board in turn and bounces a little as it lands.
    val drop = remember { Animatable(1f) }
    LaunchedEffect(session.dealt) {
        if (session.dealt > 0 && round.phase == Phase.Peek && session.peekStage == PeekStage.Deal && !session.calm && !session.skipIntro) {
            drop.snapTo(0f)
            delay(index * DEAL_STAGGER_MS)
            drop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f))
        }
    }
    LaunchedEffect(session.skipIntro) { if (session.skipIntro) drop.snapTo(1f) }
    val matched = round.matched[index]
    val lost = round.phase == Phase.Lost && !matched
    val wrong = round.miss && index in round.open
    val lifted = index in round.open
    val playable = round.phase == Phase.Play && !matched && index !in round.spent
    val calm = session.calm
    val gold = id in session.store.progress.mastered
    var centre by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    // Each card sways on its own slow cycle, so the board never moves in lockstep.
    val sway = remember { floatArrayOf(Random.nextFloat() * 6.283f, 4f + Random.nextFloat() * 2f) }
    val flapPhase = remember { Random.nextFloat() * 6.283f }
    val foil = remember { FoilShader.create() }
    val goldFoil = remember(gold) { if (gold) FoilShader.create() else null }
    val goldBrush = goldFoil?.brush

    // The flip: a spring that overshoots a little, so the card swings past and settles.
    val turn by animateFloatAsState(
        if (up) 180f else 0f,
        if (calm) spring(dampingRatio = 1f, stiffness = 500f) else spring(dampingRatio = 0.62f, stiffness = 380f),
        label = "turn",
    )
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
    // Pressing tilts the card towards the finger and squeezes it.
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    // A tiny squash as the card lands after a flip.
    val squash = remember { Animatable(1f) }
    var flips by remember { mutableIntStateOf(0) }
    LaunchedEffect(up) {
        if (flips++ == 0) return@LaunchedEffect
        delay(230)
        squash.snapTo(0.97f)
        delay(60)
        squash.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    val emptied by animateFloatAsState(if (matched) 1f else 0f, tween(240), label = "emptied")
    val shake = remember { Animatable(0f) }
    val red = remember { Animatable(0f) }
    // A Wanderer swap: the two cards slide across to each other's places.
    val wander = remember { Animatable(0f) }
    val swapWith = round.swap?.let { (a, b) -> if (a == index) b else if (b == index) a else null }
    LaunchedEffect(round.swaps) {
        if (swapWith != null) {
            wander.snapTo(1f)
            wander.animateTo(0f, tween(if (calm) 0 else 520))
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
            if (!calm) for (x in SHAKE) shake.animateTo(x, tween(45))
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
                cameraDistance = 12f * density
                val idle = if (calm || matched) 0f else session.clock.seconds * 6.283f / sway[1] + sway[0]
                rotationY = turn + tiltY.value + 2f * cos(idle)
                rotationX = tiltX.value + 2f * sin(idle)
                // Mid-flip the card rises off the board: its shadow grows from 2dp to 18dp and back.
                val mid = sin(turn / 180f * Math.PI.toFloat()).coerceAtLeast(0f)
                translationY = -6.dp.toPx() * mid - 3.dp.toPx() * lift - 36.dp.toPx() * (1f - drop.value)
                alpha = drop.value.coerceIn(0f, 1f)
                translationX = shake.value * density
                if (swapWith != null && wander.value > 0f) {
                    // Start from the other card's spot and slide home, lifted above its neighbours.
                    val from = session.cardAt[swapWith] - session.cardAt[index]
                    translationX += from.x * wander.value
                    translationY += from.y * wander.value - 10.dp.toPx() * sin(wander.value * Math.PI.toFloat())
                }
                // With only one or two pairs left, the last cards breathe a little so the eye lands on them.
                val few = round.phase == Phase.Play && !matched && !calm && round.pairCount - round.pairs in 1..2
                val breathe = if (few) 1f + 0.02f * sin(session.clock.seconds * 2.6f) else 1f
                val s = press.value * (1f + 0.06f * lift) * breathe
                scaleX = s
                scaleY = s * squash.value
                shadowElevation = (1f - emptied) * (2f + 16f * mid + 6f * lift) * density
                ambientShadowColor = Dusk.shadow
                spotShadowColor = Dusk.shadow
                shape = CARD_SHAPE
                clip = true
            }
            .drawWithCache {
                val corner = CornerRadius(16.dp.toPx())
                val frame = Stroke(2.5.dp.toPx())
                val inset = 5.dp.toPx()
                val innerCorner = CornerRadius(11.dp.toPx())
                val innerSize = Size(size.width - inset * 2, size.height - inset * 2)
                // The tile is ebony: a soft shade down it and a few lines of grain, the same each time.
                val timber = Brush.verticalGradient(listOf(Wood.Ebony.light, Wood.Ebony.base, Wood.Ebony.dark))
                val grainRandom = Random(index * 31 + 5)
                val grain = Path()
                var gy = size.height * 0.06f
                while (gy < size.height) {
                    val bend = size.height * (0.01f + grainRandom.nextFloat() * 0.03f)
                    grain.moveTo(0f, gy)
                    grain.quadraticTo(size.width * 0.5f, gy + if (grainRandom.nextBoolean()) bend else -bend, size.width, gy)
                    gy += size.height * (0.07f + grainRandom.nextFloat() * 0.1f)
                }
                val grainLine = Stroke(1.dp.toPx())
                // The face is a sheet of parchment set into the frame, brighter in the middle.
                val paper = Brush.radialGradient(
                    0f to Color(0xFFF6E9C8),
                    1f to Craft.parchment,
                    center = Offset(size.width / 2f, size.height * 0.45f),
                    radius = size.height * 0.7f,
                )
                // An empty socket is dark, and darker still under its top lip.
                val hollow = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.35f to Color.Transparent)
                // A soft white spot of reflected light; it slides opposite to the tilt.
                val spot = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    center = Offset.Zero,
                    radius = 100f,
                )
                // Warm light catching the top edge from the bulb above.
                val rim = Brush.verticalGradient(0f to Dusk.glow, 1f to Color.Transparent, startY = 0f, endY = size.height * 0.2f)
                // The fallback foil: a pale band that slides across with the tilt.
                val band = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    startX = 0f,
                    endX = size.width * 0.7f,
                )
                val foilBrush = foil?.brush
                val half = frame.width / 2f
                val frameSize = Size(size.width - frame.width, size.height - frame.width)
                val hair = Stroke(1.dp.toPx())
                val stud = 2.2.dp.toPx()

                onDrawWithContent {
                    val faceUp = turn > 90f
                    val full = 1f - emptied
                    drawRect(timber)
                    drawPath(grain, Wood.Ebony.dark, alpha = 0.7f, style = grainLine)
                    if (faceUp && full > 0f) {
                        val wash = if (leaf) LEAF_PAPER else if (lost) lerp(Craft.parchment, Dusk.cardBack, 0.62f) else null
                        if (wash != null) {
                            drawRoundRect(wash, Offset(inset, inset), innerSize, innerCorner, alpha = full)
                        } else {
                            drawRoundRect(paper, Offset(inset, inset), innerSize, innerCorner, alpha = full)
                        }
                        if (red.value > 0f) drawRoundRect(WRONG, Offset(inset, inset), innerSize, innerCorner, alpha = red.value * full)
                    }
                    if (emptied > 0f) {
                        drawRect(Dusk.slot, alpha = emptied)
                        drawRect(hollow, alpha = emptied)
                    }
                    drawContent()

                    // How the card faces the light: its own tilt plus the whole board's.
                    val nx = ((tiltY.value / 12f) + session.tilt.x * 0.5f).coerceIn(-1f, 1f)
                    val ny = ((tiltX.value / 12f) + session.tilt.y * 0.5f).coerceIn(-1f, 1f)
                    val time = if (calm) 0f else session.clock.seconds

                    if (!faceUp) {
                        if (foilBrush != null && Build.VERSION.SDK_INT >= 33) {
                            foil.update(size.width, size.height, nx, ny, time, gold = true, strength = 0.16f)
                            drawRect(foilBrush)
                        } else {
                            translate(size.width * (0.15f - nx * 0.45f), 0f) { drawRect(band, size = size.copy(width = size.width * 0.7f)) }
                        }
                    }

                    // Cards near the bulb catch more of its light; empty sockets stay dark.
                    val reach = session.bulbWidth * 4.2f
                    val far = if (reach > 0f) ((centre - session.bulbAt).getDistance() / reach).coerceIn(0f, 1f) else 1f
                    val glow = session.power().coerceIn(0f, 1.3f) * (0.4f + 0.6f * session.timeLeft)
                    val near = (1f - far) * glow * full
                    val lightHue = session.hue ?: Dusk.glowDeep
                    drawRect(lightHue, alpha = (0.16f * near).coerceIn(0f, 1f))
                    drawRect(rim, size = size.copy(height = size.height * 0.2f), alpha = (0.5f * near).coerceIn(0f, 1f))
                    // The last seconds wash the whole board red.
                    if (session.hue == Craft.danger && full > 0f) drawRect(Craft.danger, alpha = 0.14f * full)

                    if (full > 0f) {
                        val r = size.width * 0.95f
                        translate(size.width * (0.5f - nx * 0.45f), size.height * (0.35f + ny * 0.45f)) {
                            scale(r / 100f, r / 100f, pivot = Offset.Zero) {
                                drawCircle(spot, 100f, Offset.Zero, alpha = (if (faceUp) 0.2f else 0.26f) * full)
                            }
                        }
                    }

                    // The embossed brass frame. An empty socket keeps only a dull trace of it.
                    drawRoundRect(BrassRule, Offset(half, half), frameSize, corner, alpha = 0.25f + 0.75f * full, style = frame)
                    if (!faceUp) {
                        drawRoundRect(Craft.brassDark, Offset(inset, inset), innerSize, innerCorner, style = hair)
                        drawRoundRect(Craft.brassLight, Offset(inset + hair.width, inset + hair.width), Size(innerSize.width - hair.width * 2, innerSize.height - hair.width * 2), innerCorner, alpha = 0.35f, style = hair)
                        val at = inset + stud * 2.2f
                        rivet(Offset(at, at), stud)
                        rivet(Offset(size.width - at, at), stud)
                        rivet(Offset(at, size.height - at), stud)
                        rivet(Offset(size.width - at, size.height - at), stud)
                    } else if (full > 0f && round.phase == Phase.Peek) {
                        // While the cards are shown, each face is rimmed in the bulb's warm light,
                        // brightening in the peek's last half second as a warning.
                        val warn = if (session.peekLeft in 1..PEEK_WARNING_MS) 1f else 0f
                        drawRoundRect(
                            lerp(Dusk.glowDeep, Dusk.glow, warn),
                            Offset(inset, inset),
                            innerSize,
                            innerCorner,
                            alpha = 0.7f + 0.3f * warn,
                            style = Stroke((2f + 0.8f * warn).dp.toPx()),
                        )
                    }
                }
            }
            .pointerInput(playable, calm) {
                if (!playable) return@pointerInput
                // The card answers on press, not on release, and tips towards the finger.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val fx = (down.position.x / size.width - 0.5f) * 2f
                    val fy = (down.position.y / size.height - 0.5f) * 2f
                    val give = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                    scope.launch { press.animateTo(0.97f, give) }
                    if (!calm) {
                        scope.launch { tiltX.animateTo(-fy * 12f, give) }
                        scope.launch { tiltY.animateTo(fx * 12f, give) }
                    }
                    session.flip(index)
                    waitForUpOrCancellation()
                    val back = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
                    scope.launch { press.animateTo(1f, back) }
                    scope.launch { tiltX.animateTo(0f, back) }
                    scope.launch { tiltY.animateTo(0f, back) }
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
            MothAtRest(Dusk.cardEmblem, Craft.brassDark, Modifier.fillMaxSize().padding(14.dp))
        } else if (!matched) {
            val sheen = remember(gold) {
                Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFE9A8), Color.Transparent))
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(9.dp)
                    .graphicsLayer {
                        rotationY = 180f
                        alpha = if (lost) 0.5f else 1f
                        // A gold moth gets its foil painted only where the moth is.
                        if (gold) compositingStrategy = CompositingStrategy.Offscreen
                    },
            ) {
                // An idle moth flaps gently, each card out of step with the others.
                val flap = if (lost || leaf || calm) 1f else 0.95f + 0.05f * sin(session.clock.seconds * 5.236f + flapPhase)
                // On a Shadow level one moth is only a silhouette during the peek.
                val silhouette = if (round.phase == Phase.Peek && id == round.shadow) SILHOUETTE else null
                moth(
                    id,
                    Offset(size.width / 2, size.height / 2),
                    size.minDimension,
                    if (leaf) 1f else wings * flap,
                    flat = silhouette,
                    gold = gold,
                )
                if (gold && silhouette == null) {
                    val nx = (tiltY.value / 12f + session.tilt.x * 0.5f).coerceIn(-1f, 1f)
                    val ny = (tiltX.value / 12f + session.tilt.y * 0.5f).coerceIn(-1f, 1f)
                    if (goldFoil != null && Build.VERSION.SDK_INT >= 33) {
                        goldFoil.update(size.width, size.height, -nx, ny, if (calm) 0f else session.clock.seconds, gold = true, strength = 0.42f)
                        drawRect(goldBrush!!, blendMode = BlendMode.SrcAtop)
                    } else {
                        translate(size.width * (-0.5f + nx * 0.5f), 0f) {
                            drawRect(sheen, size = size.copy(width = size.width * 2f), alpha = 0.5f, blendMode = BlendMode.SrcAtop)
                        }
                    }
                }
            }
        }
    }
}

private val SHAKE = floatArrayOf(-7f, 7f, -5f, 5f, 0f)
