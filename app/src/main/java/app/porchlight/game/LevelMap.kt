package app.porchlight.game

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Fifty levels: one long path down five painted chapter scenes, ten coins each.

/** Where each coin sits across its scene, snaking left and right down the sheet. */
private val SNAKE = listOf(0.22f, 0.5f, 0.78f, 0.5f)

/** The mark each twist wears on the map and the level card. */
internal val Twist.glyph: Glyph get() = when (this) {
    Twist.Wanderer -> Glyph.Swap
    Twist.Leaves -> Glyph.DeadLeaf
    Twist.Shadow -> Glyph.Shade
    Twist.Lookalikes -> Glyph.Twins
}

/** The sizes the path is laid out with, from the sheet's width, so it fits any phone or font. */
private class PathLayout(width: Dp) {
    val coin = (width * 0.16f).coerceIn(44.dp, 60.dp)
    val box = coin + 8.dp
    val star = (coin * 0.3f).coerceAtLeast(13.dp)
    val step = box * 0.86f
    val header = 58.dp
    val top = header + box * 0.7f
    val sceneHeight = top + step * 9 + box * 1.3f / 2 + star + 18.dp
    val gap = 14.dp

    fun stop(i: Int, width: Dp) = androidx.compose.ui.unit.DpOffset(width * SNAKE[i % SNAKE.size], top + step * i)
}

@Composable
internal fun LevelsScreen(session: Session) {
    val progress = session.store.progress
    val picked = levelAt(progress.current)
    var lockedHint by remember { mutableStateOf<String?>(null) }
    // The level waiting for a yes in the confirmation card, if one is open.
    var asking by remember { mutableStateOf<Level?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // The scenes' clock, in seconds. It stands still under Reduce motion, and so do the scenes.
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(session.calm) {
        if (session.calm) return@LaunchedEffect
        val start = withFrameNanos { it } - (clock.floatValue * 1e9f).toLong()
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1e9f }
    }
    Box(Modifier.fillMaxSize()) {
        MenuFrame("Levels", session, flight = "plaque-Levels", scroll = false, maxContentWidth = 980.dp) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val side = maxWidth > maxHeight * 1.3f
                val mapWidth = if (side) maxWidth * 0.58f else maxWidth
                val layout = PathLayout(mapWidth.coerceAtMost(560.dp))
                val density = LocalDensity.current
                val viewport = maxHeight

                /** How far down the path a level's coin sits, from the top of the scroll. */
                fun yOf(level: Int): Dp {
                    val chapter = (level - 1) / 10
                    return (layout.sceneHeight + layout.gap) * chapter + layout.stop((level - 1) % 10, 0.dp).y
                }

                fun scrollTo(level: Int, animate: Boolean) {
                    scope.launch {
                        snapshotFlow { scroll.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
                        val target = with(density) { (yOf(level) - viewport / 2).toPx() }.toInt().coerceIn(0, scroll.maxValue)
                        if (animate && !session.calm) scroll.animateScrollTo(target, tween(600)) else scroll.scrollTo(target)
                    }
                }
                // Open on the level picked.
                LaunchedEffect(Unit) { scrollTo(progress.current, animate = false) }

                val replay: (Int) -> Unit = { n ->
                    session.sfx.play(Sound.Flip, vary = 0.06f)
                    session.store.change { it.copy(current = n) }
                    lockedHint = null
                    scrollTo(n, animate = true)
                }

                val map: @Composable (Modifier) -> Unit = { mapModifier ->
                    Column(
                        mapModifier.verticalScroll(scroll),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val sceneStep = with(density) { (layout.sceneHeight + layout.gap).toPx() }
                        val sceneHeight = with(density) { layout.sceneHeight.toPx() }
                        val viewportPx = with(density) { viewport.toPx() }
                        for ((index, chapter) in CHAPTERS.withIndex()) {
                            ChapterScene(
                                chapter, layout, session,
                                // How far this scene's middle sits below the middle of the view.
                                depth = { index * sceneStep + sceneHeight / 2 - scroll.value - viewportPx / 2 },
                                onScreen = { kotlin.math.abs(index * sceneStep + sceneHeight / 2 - scroll.value - viewportPx / 2) < (sceneHeight + viewportPx) / 2 },
                                time = { clock.floatValue },
                                onPick = { level ->
                                    lockedHint = null
                                    session.sfx.play(Sound.Flip, vary = 0.06f)
                                    session.store.change { it.copy(current = level.number) }
                                    asking = level
                                },
                                onLocked = { level ->
                                    lockedHint = lockedReason(progress, level)
                                    session.sfx.play(Sound.Locked)
                                },
                                onReplay = replay,
                            )
                            Spacer(Modifier.height(layout.gap))
                        }
                    }
                }
                val details: @Composable () -> Unit = { LevelCard(picked, progress, lockedHint, time = { clock.floatValue }, calm = session.calm) }
                if (side) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        map(Modifier.width(mapWidth).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            details()
                            Spacer(Modifier.height(12.dp))
                            PlayButton("Play", session.calm, Modifier.fillMaxWidth()) { session.start(daily = false) }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        map(Modifier.fillMaxWidth().widthIn(max = 560.dp).weight(1f))
                        Spacer(Modifier.height(10.dp))
                        details()
                        Spacer(Modifier.height(10.dp))
                        PlayButton("Play", session.calm, Modifier.fillMaxWidth()) { session.start(daily = false) }
                    }
                }
            }
        }
        asking?.let { level -> LevelConfirm(level, progress, session) { asking = null } }
    }
}

/** Why a level can't be played yet, in one line. */
private fun lockedReason(progress: Progress, level: Level): String =
    if (!progress.chapterOpen(level.chapter)) "${progress.starsTo(level.chapter)} more stars to open Chapter ${level.chapter}."
    else "Level ${level.number} is locked: win level ${level.number - 1} first."

/** "12 more stars to open Chapter 3" */
private fun gateLine(progress: Progress, chapter: Int): String {
    val more = progress.starsTo(chapter)
    return "$more more ${if (more == 1) "star" else "stars"} to open Chapter $chapter"
}

/** One chapter's scene: its name, the country it is set in, and its ten coins along the copper pipe. */
@Composable
private fun ChapterScene(
    chapter: Chapter,
    layout: PathLayout,
    session: Session,
    depth: () -> Float,
    onScreen: () -> Boolean,
    time: () -> Float,
    onPick: (Level) -> Unit,
    onLocked: (Level) -> Unit,
    onReplay: (Int) -> Unit,
) {
    val progress = session.store.progress
    val open = progress.chapterOpen(chapter.number)
    val levels = chapter.levels
    val earned = levels.sumOf { progress.levelStars[it.number - 1] }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(layout.sceneHeight)
            .padding(horizontal = 6.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val look = sceneLook(chapter.number)
        val w = maxWidth
        val stops = List(levels.size) { layout.stop(it, w) }
        val density = LocalDensity.current
        val stopsPx = with(density) { stops.map { Offset(it.x.toPx(), it.y.toPx()) } }
        val top = with(density) { layout.header.toPx() }
        val calm = session.calm
        val reach = with(density) { 60.dp.toPx() }
        val lean = with(density) { 14.dp.toPx() }
        // One layer of the scene: it lags the coins by [scroll] of the way the map moves, and leans
        // with the phone by [tilt], so the far scenery seems further back than the near.
        fun Modifier.depthLayer(scroll: Float, tilt: Float) = graphicsLayer {
            if (!calm) {
                translationY = (depth() * scroll).coerceIn(-reach * scroll * 15f, reach * scroll * 15f) - session.tilt.y * lean * tilt
                translationX = -session.tilt.x * lean * tilt
            }
        }
        Canvas(Modifier.fillMaxSize()) { sceneSky(chapter.number) }
        // The scene's own light: it lags with the far scenery and breathes with the clock.
        Canvas(Modifier.fillMaxSize().depthLayer(FAR_SCROLL, 0.3f)) {
            if (onScreen()) sceneLight(chapter.number, time(), top)
        }
        Canvas(Modifier.fillMaxSize().depthLayer(FAR_SCROLL, 0.4f)) { sceneFar(chapter.number, top, stopsPx) }
        Canvas(Modifier.fillMaxSize().depthLayer(NEAR_SCROLL, 1f)) { sceneNear(chapter.number, top, stopsPx) }
        Canvas(Modifier.fillMaxSize().depthLayer(LIFE_SCROLL, 0.7f)) {
            if (onScreen()) sceneLife(chapter.number, time(), top, stopsPx)
        }
        Canvas(Modifier.fillMaxSize()) {
            sceneFrame(chapter.number, top, 8.dp.toPx())
            for (i in 0 until stopsPx.size - 1) {
                val a = stopsPx[i]
                val b = stopsPx[i + 1]
                val run = Path().apply {
                    moveTo(a.x, a.y)
                    cubicTo(a.x, a.y + (b.y - a.y) * 0.7f, b.x, b.y - (b.y - a.y) * 0.7f, b.x, b.y)
                }
                if (levels[i + 1].number <= progress.reached && open) {
                    pipe(run, 8.dp.toPx())
                } else {
                    drawPath(
                        run,
                        look.ink,
                        alpha = 0.6f,
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))),
                    )
                }
            }
            val pin = 3.dp.toPx()
            for (corner in listOf(Offset(12f, 12f), Offset(size.width - 12f, 12f), Offset(12f, size.height - 12f), Offset(size.width - 12f, size.height - 12f))) {
                rivet(Offset(corner.x.coerceIn(pin * 3, size.width - pin * 3), corner.y.coerceIn(pin * 3, size.height - pin * 3)), pin)
            }
        }
        // The chapter's name across the top of its sheet, and its stars so far.
        Row(
            Modifier.fillMaxWidth().height(layout.header).padding(horizontal = 18.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("CHAPTER ${roman(chapter.number)} · ${chapter.scene.uppercase()}", style = Label, color = look.ink.copy(alpha = 0.8f), maxLines = 1)
                Text(chapter.name, style = Display, fontSize = TypeScale.Heading, color = Dusk.ink, maxLines = 1)
            }
            Star(earned > 0, shown = true, instant = true, modifier = Modifier.size(18.dp))
            Text(" $earned/${levels.size * 3}", style = Label, color = Dusk.ink)
        }
        levels.forEachIndexed { i, level ->
            LevelCoin(level, stops[i], layout, session, open, onPick, onLocked)
        }
        if (!open) {
            GatePlaque(chapter, progress, Modifier.align(Alignment.Center), onReplay)
        }
    }
}

/** One level's coin: brass once open, iron while locked, gold when picked. A boss's is bigger and carries a lamp. */
@Composable
private fun LevelCoin(
    level: Level,
    at: androidx.compose.ui.unit.DpOffset,
    layout: PathLayout,
    session: Session,
    chapterOpen: Boolean,
    onPick: (Level) -> Unit,
    onLocked: (Level) -> Unit,
) {
    val progress = session.store.progress
    val open = progress.isOpen(level.number)
    val chosen = level.number == progress.current
    val stars = progress.levelStars[level.number - 1]
    val touch = remember { MutableInteractionSource() }
    val held by touch.collectIsPressedAsState()
    val hovered by touch.collectIsHoveredAsState()
    val near = held || hovered
    val grow by animateFloatAsState(
        (if (chosen) 1.12f else 1f) * (if (near && open) 1.15f else 1f),
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "grow",
    )
    val scale = if (level.boss) 1.3f else 1f
    val box = layout.box * scale
    val coin = layout.coin * scale
    Column(
        Modifier
            .offset(at.x - box * 0.75f, at.y - box / 2)
            .width(box * 1.5f)
            // Faded without an offscreen layer, which would clip the coin's raised rim to its box.
            .graphicsLayer {
                alpha = if (chapterOpen) 1f else 0.45f
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            }
            .hoverable(touch)
            .clickable(interactionSource = touch, indication = null, role = Role.Button) {
                if (open) onPick(level) else onLocked(level)
            }
            .semantics {
                val kind = when (level.kind) {
                    Kind.Boss -> ", boss"
                    Kind.Breather -> ", breather"
                    Kind.Normal -> ""
                }
                contentDescription = if (open) "Play level ${level.number}$kind, ${levelLine(level)}, best $stars stars"
                else "Level ${level.number}$kind, locked. ${lockedReason(progress, level)}"
                stateDescription = if (chosen) "Selected" else "Not selected"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(box)
                // The picked level's coin grows into the round's bulb when it starts.
                .flies(if (chosen) "lamp" else null)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                    translationY = if (near && open) -4.dp.toPx() else 0f
                    cameraDistance = 12f * density
                    rotationY = session.tilt.x * 16f
                    rotationX = -session.tilt.y * 16f
                }
                .drawBehind {
                    val (c, rr) = coinFace(this.size)
                    if (chosen) {
                        drawCircle(Brush.radialGradient(listOf(Craft.gold.copy(alpha = 0.55f), Color.Transparent), c, rr * 1.7f), rr * 1.7f, c)
                    }
                    coin(c, rr, dull = !open, gold = chosen)
                    // A boss hangs a lamp over its coin; a breather wears a sprig.
                    val mark = when (level.kind) {
                        Kind.Boss -> Glyph.Lamp
                        Kind.Breather -> Glyph.Sprig
                        Kind.Normal -> null
                    }
                    if (mark != null) {
                        val at = Offset(c.x + rr * 0.85f, c.y - rr * 0.85f)
                        drawCircle(Craft.parchment, rr * 0.38f, at)
                        drawCircle(Craft.parchmentInkSoft, rr * 0.38f, at, style = Stroke(1.dp.toPx()))
                        glyph(mark, at, rr * 0.55f, if (level.boss) Craft.goldDeep else Color(0xFF3F6B2E))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.offset(y = (-3).dp)) {
                if (open) {
                    FitText("${level.number}", TextEngraved, if (coin >= 54.dp) TypeScale.Title else TypeScale.Heading, Color(0xFF3E2810), Modifier.width(coin * 0.62f))
                } else {
                    GlyphArt(Glyph.Lock, Color(0xFF111316), Modifier.size(coin * 0.36f))
                }
            }
        }
        if (open) {
            // The stars sit on a small parchment tag, so they read on every scene.
            Row(
                Modifier
                    .padding(top = 1.dp)
                    .background(Craft.parchment.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy((-2).dp),
            ) {
                repeat(3) { star -> Star(star < stars, shown = true, instant = true, modifier = Modifier.size(layout.star)) }
            }
        }
    }
}

/** The plaque across a chapter that isn't open: how many stars it takes, and where to find them. */
@Composable
private fun GatePlaque(chapter: Chapter, progress: Progress, modifier: Modifier, onReplay: (Int) -> Unit) {
    val waiting = progress.waitingChapter == chapter.number
    Column(
        modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 320.dp)
            .shadow(10.dp, RoundedCornerShape(10.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .plank(Wood.Oak, corner = 10.dp, elevation = 0.dp, seed = chapter.number)
            .border(1.5.dp, BrassRule, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphArt(Glyph.Lock, Dusk.paperInk, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("${chapter.gate}", style = TextEngraved, fontSize = TypeScale.Heading, color = Dusk.paperInk)
            Spacer(Modifier.width(4.dp))
            Star(true, shown = true, instant = true, modifier = Modifier.size(22.dp))
        }
        Text(
            gateLine(progress, chapter.number),
            style = BodyText,
            fontWeight = FontWeight.Bold,
            color = Dusk.paperInk,
            textAlign = TextAlign.Center,
        )
        val best = progress.replays.firstOrNull()
        if (waiting && best != null) {
            Spacer(Modifier.height(10.dp))
            Text("Your best levels to replay:", style = Label, color = Dusk.paperInk.copy(alpha = 0.8f))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in progress.replays.take(3)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(role = Role.Button) { onReplay(n) }
                            .semantics { contentDescription = "Replay level $n, best ${progress.levelStars[n - 1]} stars" }
                            .drawBehind {
                                val (c, rr) = coinFace(size)
                                coin(c, rr)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$n", style = TextEngraved, fontSize = TypeScale.Body, color = Color(0xFF3E2810), modifier = Modifier.offset(y = (-2).dp))
                    }
                }
            }
        }
    }
}

/** Each twist's own colour on the level card. */
private val Twist.accent: Color get() = when (this) {
    Twist.Wanderer -> Color(0xFF7CC6FF)
    Twist.Leaves -> Color(0xFFF0955A)
    Twist.Shadow -> Color(0xFFB9A2FF)
    Twist.Lookalikes -> Color(0xFFF58FB0)
}

/**
 * The picked level, under the map: a brass medallion with its number (gold with a turning halo
 * for a boss), its chapter, a boss or breather badge, its best stars, and chips for its cards,
 * seconds and each twist in that twist's colour. It springs in when a new level is picked, and a
 * band of light passes across it now and then.
 */
@Composable
private fun LevelCard(level: Level, progress: Progress, hint: String?, time: () -> Float, calm: Boolean) {
    val look = sceneLook(level.chapter)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(level.number) {
        if (calm) return@LaunchedEffect
        pop.snapTo(0f)
        pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = pop.value
                alpha = 0.4f + 0.6f * p.coerceIn(0f, 1f)
                translationY = (1f - p) * 18.dp.toPx()
                scaleX = 0.96f + 0.04f * p
                scaleY = 0.96f + 0.04f * p
            }
            .shadow(10.dp, shape, ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .plank(Wood.Walnut, corner = 14.dp, elevation = 0.dp, seed = 5)
            .drawBehind {
                // The chapter's light pooling on the left, behind the medallion.
                val c = Offset(size.height * 0.55f, size.height / 2f)
                drawCircle(Brush.radialGradient(listOf(look.light.copy(alpha = 0.35f), Color.Transparent), c, size.height * 1.1f), size.height * 1.1f, c)
                // A band of light sweeping across every few seconds.
                if (!calm) {
                    val run = (time() % 4.5f) / 1.4f
                    if (run < 1f) {
                        val x = -size.width * 0.3f + run * size.width * 1.6f
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                                Offset(x - size.height, 0f),
                                Offset(x, size.height),
                            ),
                        )
                    }
                }
            }
            .border(1.5.dp, BrassRule, shape)
            .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The medallion.
        Box(
            Modifier
                .size(66.dp)
                .graphicsLayer {
                    if (!calm) {
                        rotationY = sin(time() * 0.9f) * 12f
                        cameraDistance = 10f * density
                    }
                }
                .drawBehind {
                    val (c, rr) = coinFace(size)
                    if (level.boss) {
                        val t = if (calm) 0f else time()
                        val pulse = 0.5f + 0.5f * sin(t * 2.4f)
                        drawCircle(Brush.radialGradient(listOf(Dusk.ember.copy(alpha = 0.35f + 0.25f * pulse), Color.Transparent), c, rr * 1.6f), rr * 1.6f, c)
                        rotate(t * 25f, c) {
                            for (k in 0 until 12) {
                                val a = k * PI.toFloat() / 6f
                                drawLine(
                                    Craft.gold.copy(alpha = 0.55f),
                                    Offset(c.x + cos(a) * rr * 1.08f, c.y + sin(a) * rr * 1.08f),
                                    Offset(c.x + cos(a) * rr * 1.4f, c.y + sin(a) * rr * 1.4f),
                                    2.dp.toPx(),
                                    StrokeCap.Round,
                                )
                            }
                        }
                    }
                    coin(c, rr, gold = level.boss)
                },
            contentAlignment = Alignment.Center,
        ) {
            FitText("${level.number}", TextEngraved, TypeScale.Title, Color(0xFF3E2810), Modifier.width(40.dp).offset(y = (-3).dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LEVEL ${level.number} · ${roman(level.chapter)}", style = Label, color = look.light, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                when (level.kind) {
                    Kind.Boss -> KindBadge("BOSS", Glyph.Lamp, Dusk.ember)
                    Kind.Breather -> KindBadge("BREATHER", Glyph.Sprig, Color(0xFF6FBF4A))
                    Kind.Normal -> Unit
                }
                Spacer(Modifier.weight(1f))
                val stars = progress.levelStars[level.number - 1]
                Row(
                    Modifier
                        .background(Craft.parchment.copy(alpha = 0.92f), RoundedCornerShape(50))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy((-3).dp),
                ) {
                    repeat(3) { Star(it < stars, shown = true, instant = true, modifier = Modifier.size(18.dp)) }
                }
            }
            Text(chapterOf(level).name, style = Display, fontSize = TypeScale.Heading, color = Dusk.ink, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                FactChip(Glyph.Moth, "${level.cards} cards", Dusk.ink)
                FactChip(Glyph.Clock, "${level.roundMs / 1000} s", Dusk.ink)
                for (twist in level.twists) FactChip(twist.glyph, twist.title, twist.accent)
                if (level.twists.isEmpty()) FactChip(Glyph.Moth, "No twists", Dusk.inkSoft)
            }
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, color = Dusk.amber, fontSize = TypeScale.Body, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A small badge beside the level's number: BOSS or BREATHER. */
@Composable
private fun KindBadge(text: String, glyph: Glyph, tint: Color) {
    Row(
        Modifier
            .background(tint.copy(alpha = 0.22f), RoundedCornerShape(50))
            .border(1.dp, tint.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        GlyphArt(glyph, tint, Modifier.size(11.dp))
        Text(text, style = Label, color = tint, maxLines = 1)
    }
}

/** One fact about the level on a dark chip: an icon and a few words, in [tint]. */
@Composable
private fun FactChip(glyph: Glyph, text: String, tint: Color) {
    Row(
        Modifier
            .background(Color.Black.copy(alpha = 0.32f), RoundedCornerShape(50))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphArt(glyph, tint, Modifier.size(14.dp))
        Text(text, color = tint, fontSize = TypeScale.Body, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * Asks before a level starts: a parchment card that springs up from the coin, with the level's
 * number and chapter, its cards, time and twists, the stars won so far, PLAY and Not now. After
 * three losses in a row it also offers the Easier bulb. Tapping outside or Back closes it.
 */
@Composable
private fun LevelConfirm(level: Level, progress: Progress, session: Session, onClose: () -> Unit) {
    val stars = progress.levelStars[level.number - 1]
    val rise = remember(level) { Animatable(0f) }
    LaunchedEffect(level) { rise.animateTo(1f, if (session.calm) tween(180) else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(Dusk.shadow.copy(alpha = 0.65f * rise.value.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .widthIn(max = 360.dp)
                .graphicsLayer {
                    val t = rise.value
                    alpha = t.coerceIn(0f, 1f)
                    if (!session.calm) {
                        scaleX = 0.8f + 0.2f * t
                        scaleY = 0.8f + 0.2f * t
                        translationY = (1f - t) * 40.dp.toPx()
                    }
                }
                .shadow(14.dp, RoundedCornerShape(10.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(10.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("LEVEL ${level.number}", style = Display, fontSize = TypeScale.Title, color = Craft.parchmentInk)
            Text(
                "Chapter ${level.chapter} · ${chapterOf(level).name}" + if (level.boss) " · boss" else "",
                style = TextInk,
                color = Craft.parchmentInkSoft,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                repeat(3) { i ->
                    Star(i < stars, shown = true, instant = true, modifier = Modifier.size(40.dp).padding(bottom = if (i == 1) 6.dp else 0.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(levelLine(level), style = TextInk, fontWeight = FontWeight.Bold, color = Craft.parchmentInk, textAlign = TextAlign.Center)
            for (twist in level.twists) {
                Text(twist.tip, style = TextInk, color = Craft.parchmentInkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            PlayButton("Play", session.calm, Modifier.fillMaxWidth()) {
                onClose()
                session.start(daily = false)
            }
            if (progress.easierOffered && progress.current == level.number) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Easier bulb", Modifier.fillMaxWidth()) {
                    onClose()
                    session.start(daily = false, easy = true)
                }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Not now", Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}
