package app.porchlight.game

import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
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
                    contentDescription = if (round.phase == Phase.Peek) {
                        "Remember the cards, ${peekNumber(round.rules.peekMs)} seconds"
                    } else round.say
                },
            )
        }

        FlockLayer(session, front = true)
        FxLayer(session)
        Pops(session)
        if (round.tutorial) TutorialHints(session)
        // Tapping anywhere during the deal and warm-up skips straight to the peek.
        if (round.phase == Phase.Peek && (session.peekStage == PeekStage.Deal || session.peekStage == PeekStage.Warm) && !session.peekPaused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { session.skipIntro = true }
                    .semantics { contentDescription = "Dealing the cards. Tap to skip." },
            )
        }
        if (round.paused || session.peekPaused) Paused(session)
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
        // Beside the board (landscape, tablets) the header is a tall column: the lamp grows to fill
        // it and the plates and the band gather under it, instead of sitting at the far bottom.
        val tall = maxHeight > maxWidth
        val bulbH = if (tall) min(maxHeight * 0.46f, maxWidth * 0.62f / 0.7f) else min(maxHeight * 0.66f, maxWidth * 0.42f / 0.7f)
        val plateRow = if (tall) 0.62f else 1f
        // Each plate keeps to its own side of the bulb and its brass works: with a big font or a
        // narrow phone it shrinks to fit rather than running into the gauge or the gears.
        val plateRoom = if (tall) maxWidth * 0.48f else ((maxWidth - bulbH * 0.7f * 1.6f) / 2).coerceAtLeast(72.dp)
        ScoreReadout(
            round.score,
            Modifier.align(BiasAlignment(-1f, plateRow)).padding(bottom = if (tall) 0.dp else 44.dp).shrinkToFit(plateRoom, TransformOrigin(0f, 0.5f)),
        )
        StreakReadout(
            multiplierAt(if (round.running) round.streak else 0, round.rules),
            session.calm,
            Modifier.align(BiasAlignment(1f, plateRow)).padding(bottom = if (tall) 0.dp else 44.dp).shrinkToFit(plateRoom, TransformOrigin(1f, 0.5f)),
        )

        Column(
            Modifier.align(if (tall) BiasAlignment(0f, -0.55f) else Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Under five seconds the air over the lamp shimmers with heat. The shader's layer reaches
            // well past the bulb and its gears on every side, so nothing near it is cut off at a hard edge.
            Box(
                Modifier
                    .overhang(SHIMMER_SIDE, SHIMMER_PAD)
                    .graphicsLayer {
                        renderEffect = if (low && !session.calm) {
                            session.effects.shimmer(size.width, size.height, session.clock.seconds, 1.dp.toPx(), SHIMMER_PAD.toPx())
                        } else null
                    }
                    .padding(horizontal = SHIMMER_SIDE, vertical = SHIMMER_PAD),
            ) {
                Bulb(
                    clock = session.clock,
                    timeLeft = { session.timeLeft },
                    power = session.power,
                    last = { low },
                    arc = round.phase != Phase.Idle,
                    smoke = { low },
                    still = session.calm,
                    modifier = Modifier
                        // The porch jar (or the picked night's coin) grows into this bulb.
                        .flies("lamp")
                        .size(bulbH * 0.7f, bulbH)
                        .tracksBulb(session)
                        .semantics { contentDescription = "${(round.left + 999) / 1000} seconds left" },
                )
            }
            // The time, in the bulb's own colour: whole seconds, then tenths under ten.
            val seconds = round.left / 1000f
            Text(
                if (seconds < 10f) String.format(Locale.US, "%.1f", seconds) else "${seconds.toInt()}",
                style = Display.copy(shadow = glowOf(glassColour(session.timeLeft, low || round.phase == Phase.Lost), 14f)),
                fontSize = TypeScale.Title,
                color = glassColour(session.timeLeft, low || round.phase == Phase.Lost),
                modifier = Modifier.offset(y = (-6).dp),
            )
        }

        // One thing at a time in the band between the bulb and the board: never over the cards.
        Band(session, Modifier.align(if (tall) BiasAlignment(0f, 0.86f) else Alignment.BottomCenter))

        if (round.rules.pairBonusMs > 0) {
            TopUp(session.topUps, round.rules.pairBonusMs, Modifier.align(Alignment.TopCenter).padding(top = bulbH * 0.2f))
        }

        // Out of the round at any time: the cross asks first, then goes back to the porch.
        if (round.running || round.phase == Phase.Idle) {
            BrassButton(
                Glyph.Close,
                if (round.tutorial) "Skip the tutorial" else "Leave round",
                enabled = !round.paused && !session.peekPaused,
                size = 50.dp,
                modifier = Modifier.align(Alignment.TopStart),
            ) { if (round.tutorial) session.endTutorial() else session.leaving = true }
        }
        Column(Modifier.align(Alignment.TopEnd), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if ((round.phase == Phase.Play || round.phase == Phase.Peek) && !round.tutorial) {
                BrassButton(Glyph.Pause, "Pause", enabled = !round.paused && !session.peekPaused, size = 50.dp, onClick = session::pause)
            }
            if (round.phase == Phase.Play && round.rules.flashes > 0) {
                BrassButton(
                    Glyph.Eye,
                    if (round.flashes > 0) "Second look" else "Second look used",
                    enabled = round.flashes > 0 && !round.flashing,
                    size = 50.dp,
                    onClick = session::flash,
                )
            }
        }
    }
}

/**
 * The band under the bulb. During the peek it says REMEMBER with one big number counting the
 * seconds left to look; then GO!; when a Wanderer moves cards it warns SWAP; when the streak hits
 * its cap it says OVERDRIVE.
 */
@Composable
private fun Band(session: Session, modifier: Modifier) {
    val round = session.round
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
            round.phase == Phase.Peek && session.peekStage == PeekStage.Look -> PeekCount(session)
            session.peekStage == PeekStage.Go -> GoWord(session.calm)
            swap.value > 0.01f -> Text(
                "SWAP!",
                style = Display.copy(shadow = glowOf(Craft.danger, 20f)),
                fontSize = TypeScale.Title,
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

/**
 * REMEMBER and one big number: the whole seconds left in the peek, rounded up, so it matches the
 * real time to look. Each number punches in and the last one burns red-amber. Screen readers hear
 * the peek once from the round's status line, so this stays silent to them.
 */
@Composable
private fun PeekCount(session: Session) {
    val number by remember { derivedStateOf { peekNumber(session.peekLeft) } }
    val calm = session.calm
    Row(
        Modifier.wrapContentHeight(unbounded = true).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("REMEMBER", style = TextGlow, fontSize = TypeScale.Title)
        AnimatedContent(
            targetState = number,
            transitionSpec = {
                if (calm) fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                else (fadeIn(tween(110)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 900f), initialScale = 1.4f)) togetherWith
                    fadeOut(tween(140))
            },
            contentAlignment = Alignment.Center,
            label = "peekNumber",
        ) { n ->
            // The last number burns red-amber; the others are the bulb's warm glow.
            if (n == 1) {
                Text("$n", style = Display.copy(shadow = glowOf(Craft.danger, 28f), lineHeight = TypeScale.Hero), fontSize = TypeScale.Hero, color = lerp(Dusk.amber, Craft.danger, 0.5f))
            } else {
                Text("$n", style = TextGlow.copy(lineHeight = TypeScale.Hero), fontSize = TypeScale.Hero)
            }
        }
    }
}

/** GO!: punches in as the cards fold shut, holds for a moment, then fades. */
@Composable
private fun GoWord(calm: Boolean) {
    val scale = remember { Animatable(if (calm) 1f else 1.4f) }
    val alpha = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f)) }
        alpha.animateTo(1f, tween(110))
        delay(GO_MS)
        alpha.animateTo(0f, tween(220))
    }
    Text(
        "GO!",
        style = TextGlow,
        fontSize = TypeScale.Hero,
        modifier = Modifier.wrapContentHeight(unbounded = true).clearAndSetSemantics { }.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
        },
    )
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
        style = TextGlow,
        fontSize = TypeScale.Heading,
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
                Text("PAUSED", style = TextCarved, fontSize = TypeScale.Title, color = Dusk.paperInk)
                Text("Tap to continue", style = BodyText, color = Color(0xFF3A2612))
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Keep playing", Modifier.fillMaxWidth()) { session.resume() }
            // A daily porch can't be started over: it has one scored try.
            if (!session.round.daily) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Start over", Modifier.fillMaxWidth()) { session.start(daily = false) }
            }
            Spacer(Modifier.height(8.dp))
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
            Text("Leave this round?", style = Display, fontSize = TypeScale.Title, color = Craft.parchmentInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "The bulb goes out and this round won't count.",
                color = Craft.parchmentInk,
                fontSize = TypeScale.Body,
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

/**
 * How far the heat-shimmer layer reaches past the bulb's box, so its ripple never meets a hard edge.
 * Sideways it also has to clear the gears and the gauge, which stand out past the box.
 */
private val SHIMMER_PAD = 24.dp
private val SHIMMER_SIDE = 64.dp

/**
 * Lays the content out bigger than it says it is ([sideways] left and right, [upDown] above and below): the parent places it as if
 * it had no padding, while its layer still covers the margin. Used for the shimmer's bounds.
 */
private fun Modifier.overhang(sideways: Dp, upDown: Dp) = layout { measurable, constraints ->
    val x = sideways.roundToPx()
    val y = upDown.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * x, 2 * y))
    layout(placeable.width - 2 * x, placeable.height - 2 * y) { placeable.place(-x, -y) }
}

/**
 * Lets the content take its natural size, and if that is wider than [max], scales it down
 * (around [origin]) so it fits, reporting the smaller size to the layout. Text never wraps or
 * clips; the whole plate simply gets a little smaller.
 */
internal fun Modifier.shrinkToFit(max: Dp, origin: TransformOrigin = TransformOrigin.Center) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
    val limit = max.roundToPx()
    val k = if (placeable.width > limit) limit.toFloat() / placeable.width else 1f
    val w = (placeable.width * k).toInt()
    val h = (placeable.height * k).toInt()
    layout(w, h) {
        placeable.placeWithLayer(((w - placeable.width) * origin.pivotFractionX).toInt(), ((h - placeable.height) / 2f).toInt()) {
            scaleX = k
            scaleY = k
            transformOrigin = origin
        }
    }
}
