package app.porchlight.game

import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One thing a round unlocked, shown as a card after the level. */
private class Reward(val title: String, val detail: String, val art: @Composable () -> Unit)

/** Points flying from a tally line up into the score. */
private class Flight(val text: String, val from: Offset, val to: Offset) {
    val t = Animatable(0f)
}

/** Where things sit on screen, in root pixels, so things can fly between them. */
private class Spots {
    var origin = Offset.Zero
    var score = Offset.Zero
    var emblem = Offset.Zero
    val lines = HashMap<Int, Offset>()
}

private val STAR_PITCH = floatArrayOf(1f, 1.26f, 1.5f) // C, E, G

/**
 * The scorecard, as a short moment rather than a receipt: the headline lights (or burns out), the
 * stars fly in from the bulb, the tally builds the score line by line, the score lands, XP orbs fly
 * into the level, any unlocks arrive one at a time, and only then do the buttons rise in. About
 * three seconds; tapping anywhere jumps to the end. Reduce motion: plain fades and final values.
 */
@Composable
internal fun Results(session: Session, report: Report) {
    val round = session.round
    val won = round.phase == Phase.Won
    val progress = session.store.progress
    val context = LocalContext.current
    val calm = session.calm
    val lines = remember(report) { tally(round) }
    val note = remember(report) { lossNote(round) }
    val spots = remember(report) { Spots() }

    var skipped by remember(report) { mutableStateOf(false) }
    val slide = remember(report) { Animatable(1f) }
    var headline by remember(report) { mutableStateOf(false) }
    var starsIn by remember(report) { mutableIntStateOf(0) }
    var linesIn by remember(report) { mutableIntStateOf(0) }
    var scoreShown by remember(report) { mutableIntStateOf(0) }
    var bestIn by remember(report) { mutableStateOf(false) }
    var xpIn by remember(report) { mutableStateOf(false) }
    var rewardsIn by remember(report) { mutableIntStateOf(0) }
    val punch = remember(report) { Animatable(1f) }
    val sparks = remember(report) { Animatable(0f) }
    val shake = remember(report) { Animatable(0f) }
    val orbs = remember(report) { Animatable(0f) }
    val buttons = remember(report) { Animatable(0f) }
    val flights = remember(report) { mutableStateListOf<Flight>() }

    val rewards = remember(report) {
        buildList {
            report.newLevel?.let { n ->
                add(Reward("Level $n is open", levelLine(levelAt(n))) { LevelSeal(n) })
            }
            report.newChapter?.let { c ->
                add(Reward("Chapter $c is open", CHAPTERS[c - 1].name) { LevelSeal(c, roman = true) })
            }
            report.newMastery?.let { moth ->
                add(Reward("${moth.label} turned gold", "Three stars with ${moth.label} leading.") { MothArt(moth, Modifier.size(54.dp), gold = true) })
            }
            report.newMoths.forEach { moth ->
                add(Reward("${moth.label} joined", moth.perkName) { MothArt(moth, Modifier.size(54.dp)) })
            }
            // A chapter's own badge has its moment under the headline, not among the cards.
            report.newBadges.filter { it != Badge.forChapter(report.chapterDone ?: 0) }.forEach { badge ->
                add(
                    Reward(badge.title, badge.detail) {
                        // A new badge: the card stamps down, a shine crosses it, its ribbon unfurls.
                        val shine = remember { Animatable(0f) }
                        val ribbon = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            delay(250)
                            shine.animateTo(1f, tween(520))
                            ribbon.animateTo(1f, tween(360))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BadgeMedal(badge, true, Modifier.size(54.dp), shine = if (shine.value < 1f) shine.value else -1f)
                            TierRibbon(badge.metal, Modifier.size(50.dp, 6.dp), open = ribbon.value)
                        }
                    },
                )
            }
        }
    }
    val levelUp = report.levelAfter > report.levelBefore

    // Everything at its final value: a tap to skip, or reduce motion (with a fade).
    suspend fun finish(fade: Boolean) {
        flights.clear()
        headline = true
        starsIn = 3
        linesIn = lines.size
        scoreShown = round.score
        bestIn = report.newBest
        xpIn = true
        orbs.snapTo(1f)
        rewardsIn = rewards.size
        if (fade) buttons.animateTo(1f, tween(300)) else buttons.snapTo(1f)
    }

    LaunchedEffect(report) { slide.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(report, skipped) {
        if (skipped) {
            finish(fade = false)
            return@LaunchedEffect
        }
        if (calm) {
            delay(250)
            finish(fade = true)
            return@LaunchedEffect
        }
        val sfx = session.sfx
        delay(250)
        headline = true
        if (won) sfx.play(Sound.Buzz, volume = 0.3f, pitch = 1.2f)
        delay(320)

        // Stars: each flies in from the bulb; a missed one's socket only shakes.
        repeat(3) { i ->
            starsIn = i + 1
            if (i < report.stars) sfx.play(Sound.Match1, volume = 0.6f, pitch = STAR_PITCH[i]) else sfx.play(Sound.Thunk, volume = 0.35f)
            delay(210)
        }
        delay(120)

        // The tally builds the score: each line slides in, and its points fly up into the score.
        var running = 0
        lines.forEachIndexed { i, line ->
            linesIn = i + 1
            delay(120)
            if (line.points > 0) {
                val from = spots.lines[i] ?: spots.score
                val flight = Flight("+${line.points.grouped()}", from - spots.origin, spots.score - spots.origin)
                flights += flight
                flight.t.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
                flights -= flight
                running += line.points
                scoreShown = running
                // The odometer ticks as it rolls, faster and faster.
                launch {
                    for (k in 0 until 6) {
                        sfx.play(Sound.Tick, volume = 0.3f, pitch = 1f + k * 0.06f)
                        delay(70L - k * 9L)
                    }
                }
                delay(330)
            }
        }
        scoreShown = round.score

        // The score lands: a punch and a burst of gold.
        if (round.score > 0) {
            launch {
                punch.snapTo(1.15f)
                punch.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
            }
            launch {
                sparks.snapTo(0f)
                sparks.animateTo(1f, tween(650, easing = LinearEasing))
            }
            sfx.play(Sound.Bass, volume = 0.6f)
            delay(220)
        }
        if (report.newBest) {
            bestIn = true
            delay(520)
        }

        // XP: gold orbs fly from the score into the level emblem.
        xpIn = true
        launch {
            orbs.snapTo(0f)
            orbs.animateTo(1f, tween(650, easing = LinearEasing))
        }
        delay(if (levelUp) 1_600 else 700)

        // Unlocks, one at a time, each with its own note.
        rewards.forEachIndexed { i, _ ->
            rewardsIn = i + 1
            sfx.play(sfx.matchFor(i + 3), volume = 0.55f)
            session.buzz(HapticFeedbackType.SegmentTick)
            delay(320)
        }
        buttons.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }

    // A clock for everything that keeps moving: the aurora, the hero's rays, the shine on Again.
    var t by remember(report) { mutableFloatStateOf(0f) }
    LaunchedEffect(report, calm) {
        if (calm) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    val accent = if (won) WIN else LOSS
    val accentDeep = if (won) WIN_DEEP else LOSS_DEEP
    val appear = (1f - slide.value).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        // The backdrop: a veil, a slow aurora in the result's colour, and confetti (a win) or embers (a loss).
        Canvas(Modifier.fillMaxSize()) { resultBackdrop(won, if (calm) 0f else t, appear, calm) }
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true }
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .widthIn(max = 440.dp)
                    .graphicsLayer {
                        // The card swings up out of the floor in 3D, then leans with the phone.
                        val p = slide.value
                        cameraDistance = 16f * density
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        translationY = p * size.height * 0.5f
                        rotationX = p * 55f + if (calm) 0f else -session.tilt.y * 5f
                        rotationY = if (calm) 0f else session.tilt.x * 6f
                        val sc = 0.9f + 0.1f * appear
                        scaleX = sc
                        scaleY = sc
                        alpha = appear
                        translationX = shake.value * density
                    },
            ) {
                Column(
                    Modifier
                        .shadow(30.dp, CARD_SHAPE, ambientColor = Color.Black, spotColor = accentDeep)
                        .clip(CARD_SHAPE)
                        .drawBehind { glassCard(accent, if (calm) 0f else t) }
                        .border(1.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f), Color.White.copy(alpha = 0.08f), accent.copy(alpha = 0.25f))), CARD_SHAPE)
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Where this was: a small pill at the top.
                        Text(
                            if (round.daily) "DAILY PORCH" else "LEVEL ${round.level.number} · ${chapterOf(round.level).name.uppercase()}",
                            style = Label,
                            color = accent,
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        Spacer(Modifier.height(14.dp))

                        // The hero: the pairs found as a ring round a lit (or burnt-out) bulb.
                        HeroDisc(
                            won = won,
                            found = round.pairs,
                            of = round.pairCount,
                            play = headline,
                            instant = skipped,
                            calm = calm,
                            t = { t },
                            modifier = Modifier.onGloballyPositioned { if (round.score == 0) spots.score = it.boundsInRoot().center },
                        )

                        // Stars flip in over the bottom of the hero.
                        Row(
                            Modifier.offset(y = (-18).dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            repeat(3) { i ->
                                FlipStar(
                                    filled = i < report.stars,
                                    turn = starsIn > i,
                                    instant = skipped,
                                    calm = calm,
                                    size = if (i == 1) 50.dp else 40.dp,
                                    glow = accent,
                                    hint = if (i >= report.stars && won) starHint(i, round) else null,
                                )
                            }
                        }

                        // The headline, then a word on a loss: how close, or a tip.
                        Reveal(headline, skipped) {
                            Text(
                                if (won) "Every pair found!" else "The bulb burned out",
                                style = Display,
                                fontSize = 30.sp,
                                lineHeight = 34.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (note != null) {
                            Reveal(headline, skipped) {
                                Text(note, style = BodyText, color = MUTED, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        report.chapterDone?.let { chapter ->
                            Reveal(headline, skipped, stamp = !calm) {
                                ChapterStamp(chapter, onLand = {
                                    if (!skipped) {
                                        session.sfx.play(Sound.Thunk)
                                        session.buzz(HapticFeedbackType.LongPress)
                                    }
                                })
                            }
                        }
                        if (bestIn) {
                            Reveal(true, skipped, stamp = !calm) {
                                LaunchedEffect(Unit) {
                                    if (!skipped && !calm) {
                                        session.sfx.play(Sound.Thunk)
                                        session.buzz(HapticFeedbackType.LongPress)
                                        for (x in listOf(3f, -3f, 2f, -2f, 0f)) shake.animateTo(x, tween(40))
                                    }
                                }
                                Text(
                                    "NEW BEST",
                                    style = Label,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1206),
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .background(Brush.horizontalGradient(listOf(WIN, Color(0xFFFFE9A8), WIN)), RoundedCornerShape(50))
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        // Three numbers: pairs, score, and time left (a win) or your best (a loss).
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("PAIRS", Glyph.Moth, accent, headline, skipped, 0, Modifier.weight(1f)) {
                                Text("${round.pairs}/${round.pairCount}", style = Display, fontSize = 22.sp, color = Color.White, maxLines = 1)
                            }
                            StatTile(
                                "SCORE",
                                Glyph.Star,
                                accent,
                                headline,
                                skipped,
                                1,
                                Modifier
                                    .weight(1.25f)
                                    .onGloballyPositioned { if (round.score > 0) spots.score = it.boundsInRoot().center }
                                    .graphicsLayer {
                                        scaleX = punch.value
                                        scaleY = punch.value
                                    },
                            ) {
                                CountUp(scoreShown, Display.copy(color = Color.White), 22.sp, durationMs = 300, instant = skipped, calm = calm)
                            }
                            if (won) {
                                StatTile("TIME LEFT", Glyph.Clock, accent, headline, skipped, 2, Modifier.weight(1f)) {
                                    Text("${(round.left + 999) / 1000} s", style = Display, fontSize = 22.sp, color = Color.White, maxLines = 1)
                                }
                            } else {
                                StatTile("BEST", Glyph.Trophy, accent, headline, skipped, 2, Modifier.weight(1f)) {
                                    Text(progress.best.grouped(), style = Display, fontSize = 22.sp, color = Color.White, maxLines = 1)
                                }
                            }
                        }

                        // The tally: short lines that build the score.
                        if (lines.isNotEmpty() || (won && round.misses == 0) || report.practice) {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                lines.forEachIndexed { i, line ->
                                    SlideIn(linesIn > i, skipped || calm) {
                                        Line(line, accent, Modifier.onGloballyPositioned { spots.lines[i] = it.boundsInRoot().center })
                                    }
                                }
                                if (won && round.misses == 0) {
                                    Reveal(linesIn >= lines.size, skipped) {
                                        Row(Modifier.padding(top = 4.dp)) { Chip("CLEAN!", Craft.goldDeep, Color(0xFFFFF6DC)) }
                                    }
                                }
                                if (report.practice) Text("Practice round", style = BodyText, color = MUTED, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (!report.practice && !report.newBest && round.score > 0 && progress.best > round.score) {
                            Reveal(linesIn >= lines.size && scoreShown == round.score, skipped) { ShortOfBest(round.score, progress.best, accent) }
                        }

                        Spacer(Modifier.height(12.dp))
                        Reveal(xpIn, skipped) {
                            XpBar(session, report, accent, play = xpIn, instant = skipped, t = { t }, onPlaced = { spots.emblem = it })
                        }

                        if (rewards.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                                rewards.forEachIndexed { i, reward ->
                                    Reveal(rewardsIn > i, skipped, stamp = !calm) {
                                        Column(
                                            Modifier
                                                .width(132.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f))))
                                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                                .semantics(mergeDescendants = true) {},
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            reward.art()
                                            Spacer(Modifier.height(6.dp))
                                            Text(reward.title.uppercase(), style = Label, color = accent, textAlign = TextAlign.Center, maxLines = 2)
                                            Text(reward.detail, style = BodyText, color = MUTED, lineHeight = 19.sp, textAlign = TextAlign.Center, maxLines = 3)
                                        }
                                    }
                                }
                            }
                        }

                        if (round.daily) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (report.practice) "Today's scored try is already in. A new porch comes tomorrow."
                                else "Daily porch · ${report.dailyStreak} ${if (report.dailyStreak == 1) "day" else "days"} running",
                                style = BodyText,
                                color = MUTED,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // The buttons rise in last, so they can't be hit by accident mid-sequence.
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.graphicsLayer {
                            alpha = buttons.value.coerceIn(0f, 1f)
                            translationY = (1f - buttons.value) * 24.dp.toPx()
                        },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton("Porch", Glyph.Porch, primary = false, accent, accentDeep, { t }, Modifier.weight(1f)) { session.toPorch() }
                                PillButton(if (!round.daily && progress.current != round.level.number) "Next level" else "Again", Glyph.Play, primary = true, accent, accentDeep, { t }, Modifier.weight(1.4f)) { session.start() }
                            }
                            // Three losses running on this level: a gentler try, offered and never pushed.
                            if (!round.daily && !won && progress.easierOffered && progress.current == round.level.number) {
                                PillButton("Easier bulb", Glyph.Bulb, primary = false, accent, accentDeep, { t }, Modifier.fillMaxWidth()) {
                                    session.start(daily = false, easy = true)
                                }
                                Text(
                                    "A fifth more time. One star at most.",
                                    style = BodyText,
                                    color = MUTED,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (round.daily && !report.practice) {
                                PillButton("Share", Glyph.Share, primary = false, accent, accentDeep, { t }, Modifier.fillMaxWidth()) {
                                    val card = session.store.progress.dailyCard
                                    if (card != null) session.sharing = card
                                    else shareText(context, dailyShare(todaySeed(), report.stars, round.score))
                                }
                            }
                        }
                        // Until they have risen, a tap there skips ahead like anywhere else.
                        if (buttons.value < 0.95f) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true },
                            )
                        }
                    }
                }
            }
        }

        // What flies: points from the tally into the score, sparks off the landed score, XP orbs.
        Box(Modifier.fillMaxSize().onGloballyPositioned { spots.origin = it.positionInRoot() }) {
            flights.forEach { f ->
                Text(
                    f.text,
                    style = TextGlow,
                    fontSize = TypeScale.Heading,
                    modifier = Modifier
                        .offset {
                            val t = f.t.value
                            val x = f.from.x + (f.to.x - f.from.x) * t
                            val y = f.from.y + (f.to.y - f.from.y) * t - 30.dp.toPx() * sin(t * Math.PI.toFloat())
                            IntOffset(x.roundToInt() - 30.dp.roundToPx(), y.roundToInt() - 12.dp.roundToPx())
                        }
                        .graphicsLayer { alpha = 1f - f.t.value * f.t.value },
                )
            }
            val s = sparks.value
            val o = orbs.value
            if ((s > 0f && s < 1f) || (o > 0f && o < 1f && !calm)) {
                Canvas(Modifier.fillMaxSize()) {
                    val score = spots.score - spots.origin
                    if (s > 0f && s < 1f) {
                        for (i in 0 until 12) {
                            val a = i * 30.0 * Math.PI / 180.0 + 0.2
                            val d = (28f + 90f * s) * density
                            drawCircle(Craft.gold, 3.5f * density * (1f - s), Offset(score.x + (d * cos(a)).toFloat(), score.y + (d * sin(a)).toFloat()), alpha = 1f - s)
                        }
                    }
                    if (o > 0f && o < 1f && spots.emblem != Offset.Zero) {
                        val to = spots.emblem - spots.origin
                        for (i in 0 until 8) {
                            // Each orb leaves a little after the last and arcs to the emblem.
                            val p = ((o - i * 0.06f) / 0.58f).coerceIn(0f, 1f)
                            if (p <= 0f || p >= 1f) continue
                            val bend = (if (i % 2 == 0) -1f else 1f) * 40f * density * sin(p * Math.PI.toFloat())
                            val at = Offset(score.x + (to.x - score.x) * p + bend, score.y + (to.y - score.y) * p)
                            drawCircle(Brush.radialGradient(listOf(Dusk.glow, Color.Transparent), at, 9f * density), 9f * density, at)
                            drawCircle(Color.White, 2.2f * density, at)
                        }
                    }
                }
            }
        }
    }
}

/** A tally line slides in from the left as its turn comes. */
@Composable
private fun SlideIn(shown: Boolean, instant: Boolean, content: @Composable () -> Unit) {
    val t by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0) else tween(220, easing = FastOutSlowInEasing),
        label = "slideIn",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = t
            translationX = -(1f - t) * 40.dp.toPx()
        },
    ) { content() }
}

/** A level's number, or a chapter's numeral, on a brass seal. */
@Composable
private fun LevelSeal(number: Int, roman: Boolean = false) {
    Box(Modifier.size(54.dp).metal(corner = 27.dp), contentAlignment = Alignment.Center) {
        Text(if (roman) roman(number) else "$number", style = TextEngraved, fontSize = TypeScale.Title, color = Dusk.paperInk)
    }
}

/** A finished chapter: its badge, big, and CHAPTER II COMPLETE under it. */
@Composable
private fun ChapterStamp(chapter: Int, onLand: () -> Unit) {
    val badge = Badge.forChapter(chapter) ?: return
    LaunchedEffect(chapter) { onLand() }
    Column(
        Modifier.padding(top = 6.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgeMedal(badge, true, Modifier.size(76.dp).graphicsLayer { rotationZ = -8f })
        Text("CHAPTER ${roman(chapter)} COMPLETE", style = Label, color = Craft.gold, textAlign = TextAlign.Center)
        Text(CHAPTERS[chapter - 1].name, style = BodyText, color = Color(0xFFD9CDB4), textAlign = TextAlign.Center)
    }
}

/** 1..5 as I..V, for the chapters. */
internal fun roman(n: Int) = listOf("I", "II", "III", "IV", "V").getOrElse(n - 1) { "$n" }

/** Fades a part of the scorecard in. [stamp] slams it down from large instead. */
@Composable
private fun Reveal(shown: Boolean, instant: Boolean, stamp: Boolean = false, content: @Composable () -> Unit) {
    val t by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0) else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "reveal",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = t.coerceIn(0f, 1f)
            if (stamp) {
                val s = 1f + (1f - t) * 0.8f
                scaleX = s
                scaleY = s
            } else {
                translationY = (1f - t) * 12.dp.toPx()
            }
        },
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// The modern scorecard's pieces

private val WIN = Color(0xFFFFC857)
private val WIN_DEEP = Color(0xFFFF8A3D)
private val LOSS = Color(0xFFFF7A59)
private val LOSS_DEEP = Color(0xFFB0385A)
private val MUTED = Color(0xFFA9B0C2)
private val CARD_SHAPE = RoundedCornerShape(28.dp)
private val CONFETTI = listOf(Color(0xFFFFC857), Color(0xFFFF7A9C), Color(0xFF7CC6FF), Color(0xFF8BE3B0), Color(0xFFB9A2FF), Color(0xFFFFFFFF))

/** Behind the card: a dark veil, an aurora drifting in the result's colours, and confetti or embers. */
private fun DrawScope.resultBackdrop(won: Boolean, t: Float, appear: Float, calm: Boolean) {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF06070C).copy(alpha = 0.82f * appear))
    val a = if (won) WIN else LOSS
    val b = if (won) WIN_DEEP else LOSS_DEEP
    for ((k, colour) in listOf(a, b, Color(0xFF4A6CFF)).withIndex()) {
        val c = Offset(
            w * (0.5f + 0.35f * sin(t * (0.21f + k * 0.07f) + k * 2.1f)),
            h * (0.35f + 0.25f * cos(t * (0.17f + k * 0.05f) + k * 1.3f)),
        )
        val r = w * (0.75f + 0.1f * k)
        drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = (0.22f - k * 0.05f) * appear), Color.Transparent), c, r), r, c)
    }
    if (calm) return
    if (won) {
        // Confetti, each piece tumbling over in 3D as it falls.
        for (k in 0 until 46) {
            val seed = (k * 0.6180339f) % 1f
            val speed = 0.09f + ((k * 7) % 11) * 0.012f
            val y = (((t * speed + seed) % 1.15f) - 0.08f) * h
            val x = w * ((k * 0.3819f) % 1f) + sin(t * 1.3f + k) * w * 0.04f
            val turn = cos(t * (2f + (k % 5) * 0.6f) + k)
            val pw = (5f + (k % 3) * 2f) * density
            val at = Offset(x, y)
            rotate(t * (60f + (k % 7) * 25f) + k * 40f, at) {
                drawRect(CONFETTI[k % CONFETTI.size].copy(alpha = 0.85f * appear), Offset(at.x - pw * abs(turn) / 2f, at.y - pw), Size(pw * abs(turn).coerceAtLeast(0.12f), pw * 2f))
            }
        }
    } else {
        // Embers drifting up off the burnt bulb.
        for (k in 0 until 34) {
            val seed = (k * 0.6180339f) % 1f
            val p = (t * (0.06f + (k % 6) * 0.01f) + seed) % 1f
            val y = h * (1.05f - p * 1.1f)
            val x = w * ((k * 0.3819f) % 1f) + sin(t * 0.9f + k * 1.7f) * w * 0.05f
            val glow = sin(p * PI.toFloat()) * (0.6f + 0.4f * sin(t * 6f + k))
            val r = (1.5f + (k % 3)) * density
            drawCircle(Brush.radialGradient(listOf(LOSS.copy(alpha = 0.5f * glow * appear), Color.Transparent), Offset(x, y), r * 5f), r * 5f, Offset(x, y))
            drawCircle(Color(0xFFFFD2A8).copy(alpha = glow * appear), r, Offset(x, y))
        }
    }
}

/** The card itself: dark glass, a glow of the result's colour from the top, and a slow sheen. */
private fun DrawScope.glassCard(accent: Color, t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1C2030), Color(0xFF12141D), Color(0xFF0D0F16))))
    val top = Offset(size.width / 2f, 0f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent), top, size.width * 0.9f), size.width * 0.9f, top)
    // A faint grid of light, like a screen's glass.
    val sweep = ((t * 0.12f) % 1.6f) - 0.3f
    val x = size.width * sweep * 1.4f
    drawRect(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent), Offset(x - size.width * 0.3f, 0f), Offset(x, size.height)),
    )
}

/**
 * The hero: a ring that fills with the pairs found, round a disc. A win is a lit gold bulb with rays
 * turning behind it; a loss is a burnt-out bulb, smoke curling up off it and an ember pulse round it.
 */
@Composable
private fun HeroDisc(won: Boolean, found: Int, of: Int, play: Boolean, instant: Boolean, calm: Boolean, t: () -> Float, modifier: Modifier = Modifier) {
    val fill = remember { Animatable(0f) }
    val pop = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(play, instant) {
        val target = if (of > 0) found.toFloat() / of else 0f
        if (instant || calm) {
            fill.snapTo(target)
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        if (play) fill.animateTo(target, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(
        modifier
            .size(132.dp)
            .graphicsLayer {
                val p = pop.value
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
                alpha = p.coerceIn(0f, 1f)
                cameraDistance = 12f * density
                rotationY = if (calm) 0f else sin(t() * 0.8f) * 10f
            },
    ) {
        val time = t()
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val accent = if (won) WIN else LOSS
        val deep = if (won) WIN_DEEP else LOSS_DEEP
        val ring = 7.dp.toPx()
        if (won) {
            // Rays turning slowly behind the disc.
            rotate(time * 18f, c) {
                for (k in 0 until 14) {
                    val a = k * (2 * PI.toFloat() / 14)
                    drawLine(
                        accent.copy(alpha = 0.22f),
                        Offset(c.x + cos(a) * r * 0.7f, c.y + sin(a) * r * 0.7f),
                        Offset(c.x + cos(a) * r * 1.0f, c.y + sin(a) * r * 1.0f),
                        4.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        } else {
            // An ember pulse.
            val pulse = 0.5f + 0.5f * sin(time * 2.4f)
            drawCircle(accent.copy(alpha = 0.18f * pulse), r * (0.92f + 0.06f * pulse), c, style = Stroke(2.dp.toPx()))
        }
        // The ring: a track, and the pairs found as a gradient arc.
        val inset = ring / 2f + 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(ring))
        if (fill.value > 0f) {
            drawArc(
                Brush.sweepGradient(listOf(deep, accent, deep), c),
                -90f,
                360f * fill.value,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(ring, cap = StrokeCap.Round),
            )
        }
        // The disc.
        val disc = r * 0.68f
        if (won) {
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), c, disc * 1.5f), disc * 1.5f, c)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4D6), accent, deep), c, disc), disc, c)
            glyph(Glyph.Bulb, c, disc * 1.05f, Color(0xFF3A2208))
        } else {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2E3242), Color(0xFF181A24)), c, disc), disc, c)
            drawCircle(Color.White.copy(alpha = 0.08f), disc, c, style = Stroke(1.dp.toPx()))
            glyph(Glyph.BulbOff, c, disc * 1.05f, Color(0xFF8D93A6))
            // Smoke curling up off it.
            for (k in 0 until 4) {
                val p = (time * 0.35f + k * 0.25f) % 1f
                val at = Offset(c.x + sin(time * 1.4f + k * 2f) * disc * 0.25f * p, c.y - disc * 0.45f - p * disc * 1.1f)
                drawCircle(Color(0xFFB8BED0).copy(alpha = 0.22f * (1f - p)), disc * (0.12f + 0.22f * p), at)
            }
        }
    }
}

/** One star, flipping over in 3D as its turn comes, with a glow behind it if it was earned. */
@Composable
private fun FlipStar(filled: Boolean, turn: Boolean, instant: Boolean, calm: Boolean, size: Dp, glow: Color, hint: String?) {
    val flip = remember { Animatable(0f) }
    LaunchedEffect(turn, instant) {
        if (!turn) return@LaunchedEffect
        if (instant || calm) flip.snapTo(1f) else flip.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(Modifier.width(size + 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val f = flip.value
                    cameraDistance = 10f * density
                    rotationY = (1f - f) * 180f
                    val s = 0.4f + 0.6f * f
                    scaleX = s
                    scaleY = s
                    alpha = f.coerceIn(0f, 1f)
                }
                .drawBehind {
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    if (filled) {
                        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), Color.Transparent), c, this.size.width), this.size.width, c)
                    } else {
                        // An unearned star sits in a pale socket, so it reads on the dark glass.
                        drawCircle(Color.White.copy(alpha = 0.1f), this.size.width * 0.5f, c)
                        drawCircle(Color.White.copy(alpha = 0.18f), this.size.width * 0.5f, c, style = Stroke(1.dp.toPx()))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Star(filled, shown = true, instant = true, modifier = Modifier.size(size))
        }
        if (hint != null) {
            Reveal(turn, instant) {
                Text(hint.uppercase(), style = Label, color = MUTED, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** A number on a small glass tile with its label and icon; the tiles flip up one after another. */
@Composable
private fun StatTile(label: String, glyph: Glyph, accent: Color, shown: Boolean, instant: Boolean, order: Int, modifier: Modifier, value: @Composable () -> Unit) {
    val up = remember { Animatable(0f) }
    LaunchedEffect(shown, instant) {
        if (!shown) return@LaunchedEffect
        if (instant) {
            up.snapTo(1f)
            return@LaunchedEffect
        }
        delay(order * 90L)
        up.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(
        modifier
            .graphicsLayer {
                val u = up.value
                cameraDistance = 10f * density
                transformOrigin = TransformOrigin(0.5f, 1f)
                rotationX = (1f - u) * 80f
                alpha = u.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlyphArt(glyph, accent, Modifier.size(12.dp))
            Text(label, style = Label, color = MUTED, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        value()
    }
}

/** "312 short of your best", with a thin bar of the score against the best. */
@Composable
private fun ShortOfBest(score: Int, best: Int, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        Text("${(best - score).grouped()} short of your best", style = BodyText, color = MUTED)
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(160.dp, 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = (score.toFloat() / best).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(accent),
            )
        }
    }
}

/** One tally line: an icon, what happened, and what it was worth (if anything). */
@Composable
private fun Line(line: TallyLine, accent: Color, modifier: Modifier = Modifier) {
    val icon = when (line.kind) {
        TallyKind.Pairs -> Glyph.Moth
        TallyKind.Streak -> Glyph.Flame
        TallyKind.Time -> Glyph.Clock
        TallyKind.Misses -> Glyph.Close
    }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphArt(icon, accent, Modifier.size(16.dp))
        Text(line.text, style = BodyText, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.weight(1f))
        if (line.points > 0) {
            Text("+${line.points.grouped()}", style = BodyText, fontWeight = FontWeight.Bold, color = accent, textAlign = TextAlign.End, modifier = modifier)
        } else {
            Spacer(modifier)
        }
    }
}

/**
 * XP: the level emblem, small, and a bar that fills with what this round earned, a light running
 * along it. A level-up fills the bar, flashes LEVEL UP, spins the emblem and refills from empty.
 */
@Composable
private fun XpBar(session: Session, report: Report, accent: Color, play: Boolean, instant: Boolean, t: () -> Float, onPlaced: (Offset) -> Unit) {
    val progress = session.store.progress
    val xpAfter = progress.xp
    val xpBefore = xpAfter - report.xp
    val up = report.levelAfter > report.levelBefore
    val calm = session.calm
    val bar = remember(report) { Animatable(levelFill(xpBefore)) }
    val spin = remember(report) { Animatable(0f) }
    val flip = remember(report) { Animatable(if (up) 0f else 1f) }
    val burst = remember(report) { Animatable(0f) }
    val banner = remember(report) { Animatable(if (up) 0f else 1f) }
    LaunchedEffect(report, play, instant) {
        if (!play) return@LaunchedEffect
        if (instant || calm) {
            bar.snapTo(levelFill(xpAfter))
            flip.snapTo(1f)
            banner.snapTo(1f)
            spin.snapTo(0f)
            return@LaunchedEffect
        }
        delay(150)
        if (!up) {
            bar.animateTo(levelFill(xpAfter), tween(700, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        bar.animateTo(1f, tween(450))
        launch { spin.animateTo(360f, tween(900, easing = FastOutSlowInEasing)) }
        flip.animateTo(1f, tween(380))
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(650))
        }
        session.sfx.play(Sound.LevelUp)
        session.buzz(HapticFeedbackType.Confirm)
        launch { banner.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        bar.snapTo(0f)
        bar.animateTo(levelFill(xpAfter), tween(600, easing = FastOutSlowInEasing))
    }
    val level = levelFor(xpAfter)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Emblem(
            report.levelAfter,
            Modifier
                .flies("emblem")
                .size(52.dp)
                .onGloballyPositioned { onPlaced(it.boundsInRoot().center) }
                .graphicsLayer {
                    cameraDistance = 12f * density
                    rotationY = spin.value
                },
            xp = bar.value,
            from = report.levelBefore,
            flip = flip.value,
            burst = burst.value,
            still = calm,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Level ${report.levelAfter}", style = BodyText, fontWeight = FontWeight.Bold, color = Color.White)
                if (up) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "LEVEL UP",
                        style = Label,
                        color = Color(0xFF1A1206),
                        modifier = Modifier
                            .graphicsLayer {
                                val b = banner.value
                                alpha = b.coerceIn(0f, 1f)
                                scaleX = 0.5f + 0.5f * b
                                scaleY = 0.5f + 0.5f * b
                            }
                            .background(Brush.horizontalGradient(listOf(WIN, Color(0xFFFFE9A8))), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(if (report.practice) "NO XP" else "+${report.xp} XP", style = Label, color = accent)
            }
            Spacer(Modifier.height(6.dp))
            // The bar, with a soft light at its leading edge and a shine running along it.
            Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = radius)
                val w = size.width * bar.value.coerceIn(0f, 1f)
                if (w > 0f) {
                    drawRoundRect(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent), 0f, w), size = Size(w, size.height), cornerRadius = radius)
                    if (!calm) {
                        val run = ((t() * 0.5f) % 1.4f) * size.width
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent), run - 40.dp.toPx(), run),
                            size = Size(w, size.height),
                            cornerRadius = radius,
                        )
                    }
                    val head = Offset(w, size.height / 2f)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), head, size.height * 1.4f), size.height * 1.4f, head)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("${xpToGo(xpAfter).grouped()} XP to level ${level + 1}", style = Label, color = MUTED)
        }
    }
}

/** A modern pill button: solid in the result's colour with a moving shine, or quiet glass. */
@Composable
private fun PillButton(text: String, glyph: Glyph, primary: Boolean, accent: Color, deep: Color, t: () -> Float, modifier: Modifier, onClick: () -> Unit) {
    val touch = remember { MutableInteractionSource() }
    val pressed by touch.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press")
    val shape = RoundedCornerShape(18.dp)
    val ink = if (primary) Color(0xFF1A1206) else Color.White
    Row(
        modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .shadow(if (primary) 14.dp else 0.dp, shape, ambientColor = deep, spotColor = deep)
            .clip(shape)
            .then(
                if (primary) Modifier.background(Brush.verticalGradient(listOf(Color(0xFFFFF0C4).copy(alpha = 0.9f).compositeOver(accent), accent, deep)))
                else Modifier.background(Color.White.copy(alpha = 0.07f)).border(1.dp, Color.White.copy(alpha = 0.16f), shape),
            )
            .drawWithContent {
                drawContent()
                if (primary) {
                    val run = ((t() * 0.35f) % 1.5f) * size.width * 1.6f - size.width * 0.3f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            Offset(run - size.height, 0f),
                            Offset(run, size.height),
                        ),
                    )
                }
            }
            .clickable(interactionSource = touch, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphArt(glyph, ink, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = BodyText, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ink, maxLines = 1)
    }
}
