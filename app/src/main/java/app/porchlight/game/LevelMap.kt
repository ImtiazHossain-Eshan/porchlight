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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Fifty levels: one long parchment path down five chapter scenes, ten coins each.

/** Where each coin sits across its scene, snaking left and right down the sheet. */
private val SNAKE = listOf(0.22f, 0.5f, 0.78f, 0.5f)

/** The mark each twist wears on the map and the level card. */
internal val Twist.glyph: Glyph get() = when (this) {
    Twist.Wanderer -> Glyph.Swap
    Twist.Leaves -> Glyph.DeadLeaf
    Twist.Shadow -> Glyph.Shade
    Twist.Lookalikes -> Glyph.Twins
    Twist.Blackout -> Glyph.BulbOff
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
                        for (chapter in CHAPTERS) {
                            ChapterScene(
                                chapter, layout, session,
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
                val details: @Composable () -> Unit = { LevelCard(picked, progress, lockedHint) }
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
            .parchment(8.dp, seed = chapter.number + 2),
    ) {
        val w = maxWidth
        val stops = List(levels.size) { layout.stop(it, w) }
        val density = LocalDensity.current
        val stopsPx = with(density) { stops.map { Offset(it.x.toPx(), it.y.toPx()) } }
        Canvas(Modifier.fillMaxSize()) {
            sceneArt(chapter.number, top = layout.header.toPx())
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
                        Craft.parchmentInkSoft,
                        alpha = 0.55f,
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
                Text("CHAPTER ${roman(chapter.number)} · ${chapter.scene.uppercase()}", style = Label, color = Craft.parchmentInkSoft, maxLines = 1)
                Text(chapter.name, style = Display, fontSize = TypeScale.Heading, color = Craft.parchmentInk, maxLines = 1)
            }
            Star(earned > 0, shown = true, instant = true, modifier = Modifier.size(18.dp))
            Text(" $earned/${levels.size * 3}", style = Label, color = Craft.parchmentInk)
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
            .graphicsLayer { alpha = if (chapterOpen) 1f else 0.45f }
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
            Row(Modifier.padding(top = 1.dp), horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
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

/** The picked level, under the map: its number and chapter, cards, seconds, twists and best stars. */
@Composable
private fun LevelCard(level: Level, progress: Progress, hint: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .plank(Wood.Walnut, seed = 5)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Level ${level.number}", style = BodyText, fontWeight = FontWeight.Bold, fontSize = TypeScale.Heading, color = Dusk.amber)
            Text(
                "  ${chapterOf(level).name}" + when (level.kind) {
                    Kind.Boss -> " · boss"
                    Kind.Breather -> " · breather"
                    Kind.Normal -> ""
                },
                style = BodyText,
                color = Dusk.inkSoft,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val stars = progress.levelStars[level.number - 1]
            Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
                repeat(3) { Star(it < stars, shown = true, instant = true, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardFact(Glyph.Moth, "${level.cards} cards")
            CardFact(Glyph.Clock, "${level.roundMs / 1000} s")
            for (twist in level.twists) CardFact(twist.glyph, if (level.twists.size > 1) "" else twist.title)
        }
        Spacer(Modifier.height(2.dp))
        when {
            hint != null -> Text(hint, color = Dusk.amber, fontSize = TypeScale.Body)
            level.twists.isNotEmpty() -> Text(level.twists.joinToString(" · ") { it.title }, color = Dusk.inkSoft, fontSize = TypeScale.Body, maxLines = 1)
            else -> Text("No twists. Just the moths.", color = Dusk.inkSoft, fontSize = TypeScale.Body)
        }
    }
}

@Composable
private fun CardFact(glyph: Glyph, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GlyphArt(glyph, Dusk.ink, Modifier.size(16.dp))
        if (text.isNotEmpty()) Text(text, color = Dusk.ink, fontSize = TypeScale.Body, maxLines = 1)
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

// ---------------------------------------------------------------------------------------------
// The five scenes, inked lightly on the parchment behind the coins.

private fun DrawScope.sceneArt(chapter: Int, top: Float) {
    val w = size.width
    val h = size.height
    val ink = Craft.parchmentInkSoft
    val line = Stroke(1.4f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun at(x: Float, y: Float) = Offset(w * x, top + (h - top) * y)

    // Soft stains, so the sheet looks handled, and a rule under the chapter's name.
    for ((x, y, r) in listOf(Triple(0.7f, 0.15f, 0.2f), Triple(0.2f, 0.6f, 0.22f), Triple(0.8f, 0.85f, 0.18f))) {
        val c = at(x, y)
        drawCircle(Brush.radialGradient(listOf(Craft.parchmentShade.copy(alpha = 0.35f), Color.Transparent), c, w * r), w * r, c)
    }
    drawLine(ink, Offset(w * 0.05f, top), Offset(w * 0.95f, top), line.width, alpha = 0.35f)

    fun house(x: Float, y: Float, s: Float, lit: Boolean) {
        val body = Path().apply {
            moveTo(w * (x - 0.07f * s), at(0f, y).y)
            lineTo(w * (x - 0.07f * s), at(0f, y - 0.05f * s).y)
            lineTo(w * x, at(0f, y - 0.09f * s).y)
            lineTo(w * (x + 0.07f * s), at(0f, y - 0.05f * s).y)
            lineTo(w * (x + 0.07f * s), at(0f, y).y)
            close()
        }
        drawPath(body, Craft.parchmentShade.copy(alpha = 0.5f))
        drawPath(body, ink, alpha = 0.6f, style = line)
        if (lit) {
            val lamp = at(x, y - 0.03f * s)
            drawCircle(Brush.radialGradient(listOf(Dusk.glow.copy(alpha = 0.8f), Color.Transparent), lamp, w * 0.06f * s), w * 0.06f * s, lamp)
        }
    }
    fun pine(x: Float, y: Float, s: Float) {
        val tree = Path().apply {
            moveTo(w * x, at(0f, y - 0.06f * s).y)
            lineTo(w * (x - 0.035f * s), at(0f, y).y)
            lineTo(w * (x + 0.035f * s), at(0f, y).y)
            close()
        }
        drawPath(tree, Craft.bioDeep.copy(alpha = 0.12f))
        drawPath(tree, ink, alpha = 0.5f, style = line)
        drawLine(ink, at(x, y), at(x, y + 0.015f * s), line.width, StrokeCap.Round, alpha = 0.5f)
    }
    fun flower(x: Float, y: Float, s: Float) {
        val c = at(x, y)
        val r = w * 0.012f * s
        drawLine(ink, c, Offset(c.x, c.y + r * 4), line.width, StrokeCap.Round, alpha = 0.45f)
        for (k in 0 until 5) {
            val a = Math.toRadians(k * 72.0)
            drawCircle(Color(0xFFC4607A).copy(alpha = 0.35f), r, Offset(c.x + (r * 1.3f * kotlin.math.cos(a)).toFloat(), c.y + (r * 1.3f * kotlin.math.sin(a)).toFloat()))
        }
        drawCircle(Dusk.glowDeep.copy(alpha = 0.6f), r * 0.7f, c)
    }
    fun leaf(x: Float, y: Float, deg: Float) {
        glyph(Glyph.DeadLeaf, at(x, y), w * 0.05f, Color(0xFF8A5A2B), alpha = 0.45f)
        if (deg > 0f) Unit
    }
    fun moon(x: Float, y: Float) {
        val c = at(x, y)
        drawCircle(Dusk.glow.copy(alpha = 0.35f), w * 0.05f, c)
        drawCircle(Craft.parchment, w * 0.045f, Offset(c.x + w * 0.024f, c.y - w * 0.014f))
        drawCircle(ink, w * 0.05f, c, alpha = 0.4f, style = line)
    }
    fun twinkle(x: Float, y: Float) {
        val c = at(x, y)
        val r = w * 0.012f
        drawLine(ink, Offset(c.x - r, c.y), Offset(c.x + r, c.y), line.width * 0.8f, StrokeCap.Round, alpha = 0.45f)
        drawLine(ink, Offset(c.x, c.y - r), Offset(c.x, c.y + r), line.width * 0.8f, StrokeCap.Round, alpha = 0.45f)
    }

    when (chapter) {
        // The porch: the house the path starts from, its steps and lamp, and a rail.
        1 -> {
            house(0.12f, 0.12f, 1.2f, lit = true)
            for (k in 0 until 3) drawLine(ink, at(0.04f + k * 0.01f, 0.14f + k * 0.02f), at(0.2f - k * 0.01f, 0.14f + k * 0.02f), line.width, alpha = 0.4f)
            for (k in 0 until 7) drawLine(ink, at(0.86f + k * 0.015f, 0.55f), at(0.86f + k * 0.015f, 0.62f), line.width, alpha = 0.35f)
            drawLine(ink, at(0.85f, 0.55f), at(0.97f, 0.55f), line.width, alpha = 0.4f)
            moon(0.88f, 0.08f)
            for ((x, y) in listOf(0.06f to 0.5f, 0.92f to 0.9f, 0.1f to 0.86f)) glyph(Glyph.Moth, at(x, y), w * 0.05f, ink, alpha = 0.35f)
        }
        // The garden: a picket fence, flower beds and a sundial.
        2 -> {
            for (k in 0 until 9) {
                val x = 0.03f + k * 0.022f
                drawLine(ink, at(x, 0.3f), at(x, 0.37f), line.width * 1.4f, StrokeCap.Round, alpha = 0.35f)
            }
            drawLine(ink, at(0.02f, 0.33f), at(0.22f, 0.33f), line.width, alpha = 0.35f)
            for ((x, y) in listOf(0.08f to 0.62f, 0.12f to 0.66f, 0.9f to 0.2f, 0.94f to 0.25f, 0.88f to 0.7f, 0.93f to 0.75f, 0.05f to 0.92f)) flower(x, y, 1f)
            val dial = at(0.92f, 0.48f)
            drawCircle(ink, w * 0.035f, dial, alpha = 0.4f, style = line)
            drawLine(ink, dial, Offset(dial.x + w * 0.025f, dial.y - w * 0.02f), line.width, alpha = 0.5f)
        }
        // The woods: stands of pines, and leaves on the ground.
        3 -> {
            for ((x, y, s) in listOf(Triple(0.07f, 0.15f, 1.2f), Triple(0.13f, 0.2f, 0.9f), Triple(0.9f, 0.3f, 1.1f), Triple(0.95f, 0.36f, 0.8f),
                Triple(0.06f, 0.6f, 1f), Triple(0.12f, 0.66f, 1.2f), Triple(0.9f, 0.78f, 1f), Triple(0.94f, 0.84f, 0.8f))) pine(x, y, s)
            for ((x, y) in listOf(0.2f to 0.45f, 0.82f to 0.58f, 0.15f to 0.92f, 0.86f to 0.08f)) leaf(x, y, 0f)
        }
        // The rooftops: a row of roofs and chimneys under the moon.
        4 -> {
            val roofs = Path().apply {
                moveTo(0f, at(0f, 0.2f).y)
                lineTo(w * 0.06f, at(0f, 0.14f).y); lineTo(w * 0.12f, at(0f, 0.2f).y)
                lineTo(w * 0.12f, at(0f, 0.16f).y); lineTo(w * 0.2f, at(0f, 0.1f).y); lineTo(w * 0.26f, at(0f, 0.16f).y)
            }
            drawPath(roofs, ink, alpha = 0.45f, style = line)
            drawRect(ink.copy(alpha = 0.35f), at(0.16f, 0.08f), Size(w * 0.018f, (h - top) * 0.03f))
            val far = Path().apply {
                moveTo(w * 0.78f, at(0f, 0.72f).y)
                lineTo(w * 0.84f, at(0f, 0.66f).y); lineTo(w * 0.9f, at(0f, 0.72f).y); lineTo(w * 0.94f, at(0f, 0.68f).y); lineTo(w, at(0f, 0.72f).y)
            }
            drawPath(far, ink, alpha = 0.45f, style = line)
            moon(0.88f, 0.15f)
            for ((x, y) in listOf(0.7f to 0.06f, 0.96f to 0.4f, 0.06f to 0.5f, 0.1f to 0.85f)) twinkle(x, y)
        }
        // The last porch: the house again, its lamp burning bright, rays reaching across the sheet.
        else -> {
            house(0.86f, 0.95f, 1.4f, lit = true)
            val lamp = at(0.86f, 0.91f)
            for (k in 0 until 7) {
                val a = Math.toRadians(180.0 + k * 15.0)
                drawLine(Dusk.glowDeep, lamp, Offset(lamp.x + (w * 0.5f * kotlin.math.cos(a)).toFloat(), lamp.y + (w * 0.5f * kotlin.math.sin(a)).toFloat()), line.width, alpha = 0.18f)
            }
            for ((x, y) in listOf(0.08f to 0.2f, 0.92f to 0.3f, 0.1f to 0.6f, 0.7f to 0.8f, 0.05f to 0.9f)) glyph(Glyph.Moth, at(x, y), w * 0.05f, ink, alpha = 0.4f)
            moon(0.1f, 0.05f)
        }
    }
}
