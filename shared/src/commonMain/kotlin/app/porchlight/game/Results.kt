package app.porchlight.game

import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.exp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
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
    val platform = LocalPlatform.current
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
        Column(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true }
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .graphicsLayer {
                    // Everything rises in from below, tipped back in 3D, and leans with the phone.
                    val p = slide.value
                    cameraDistance = 16f * density
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    translationY = p * size.height * 0.25f
                    rotationX = p * 30f + if (calm) 0f else -session.tilt.y * 3f
                    rotationY = if (calm) 0f else session.tilt.x * 4f
                    alpha = appear
                    translationX = shake.value * density
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The top bar: back to the porch, and where this was.
            Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BrassButton(Glyph.Close, "Back to the porch", enabled = true, size = 48.dp) { session.toPorch() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (round.daily) "DAILY PORCH" else "LEVEL ${round.level.number}",
                        style = Label,
                        color = accent,
                    )
                    Text(
                        if (round.daily) "Today's deal" else chapterOf(round.level).name,
                        style = Modern,
                        fontSize = 18.sp,
                        color = Dusk.ink,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.size(48.dp))
            }

            // The result, its details and the actions, laid out by the room there is: one column on a
            // phone held upright (smaller on a short screen), two side by side when it is wide.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val landscape = maxWidth > maxHeight * 1.15f
                val compact = maxHeight < 660.dp || landscape
                // Below this the tally drops its small captions.
                val dense = maxHeight < 760.dp || landscape
                val heroPart: @Composable () -> Unit = {
                        // The hero: the pairs found as a ring round a lit (or burnt-out) bulb.
                        HeroLamp(
                            scale = if (maxHeight < 560.dp) 0.6f else if (compact) 0.72f else if (dense) 0.84f else 1f,
                            session = session,
                            moths = remember(report) { round.deck.filter { it != MothId.Leaf }.distinct().take(4) },
                            won = won,
                            found = round.pairs,
                            of = round.pairCount,
                            play = headline,
                            instant = skipped,
                            calm = calm,
                            t = { t },
                            modifier = Modifier.onGloballyPositioned { if (round.score == 0) spots.score = it.boundsInRoot().center },
                        )

                        // Stars on an arc, the middle one raised and bigger, slamming in with a burst.
                        Row(
                            Modifier.offset(y = (-22).dp),
                            horizontalArrangement = Arrangement.spacedBy((-4).dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            repeat(3) { i ->
                                MedalStar(
                                    filled = i < report.stars,
                                    turn = starsIn > i,
                                    instant = skipped,
                                    calm = calm,
                                    size = (if (i == 1) 70.dp else 54.dp) * (if (compact) 0.8f else 1f),
                                    tilt = (i - 1) * 16f,
                                    drop = if (i == 1) 0.dp else 14.dp,
                                    t = { t },
                                    hint = if (i >= report.stars && won) starHint(i, round) else null,
                                )
                            }
                        }

                        // The headline, then a word on a loss: how close, or a tip.
                        HeroTitle(
                            if (won) "Every pair found!" else "The bulb burned out",
                            won = won,
                            shown = headline,
                            instant = skipped,
                            calm = calm,
                            t = { t },
                            fontSize = if (compact) 28.sp else 36.sp,
                        )
                        if (note != null) {
                            TypeText(note, shown = headline, instant = skipped || calm, modifier = Modifier.padding(top = 6.dp))
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
                }
                val detailsPart: @Composable () -> Unit = {
                        // Three numbers: pairs, score, and time left (a win) or your best (a loss).
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("PAIRS", Glyph.Moth, accent, headline, skipped, 0, Modifier.weight(1f)) {
                                Text("${round.pairs}/${round.pairCount}", style = numberStyle(accent), fontSize = 24.sp, maxLines = 1)
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
                                CountUp(scoreShown, numberStyle(accent), 24.sp, durationMs = 300, instant = skipped, calm = calm)
                            }
                            if (won) {
                                StatTile("TIME LEFT", Glyph.Clock, accent, headline, skipped, 2, Modifier.weight(1f)) {
                                    Text("${(round.left + 999) / 1000} s", style = numberStyle(accent), fontSize = 24.sp, maxLines = 1)
                                }
                            } else {
                                StatTile("BEST", Glyph.Trophy, accent, headline, skipped, 2, Modifier.weight(1f)) {
                                    Text(progress.best.grouped(), style = numberStyle(accent), fontSize = 24.sp, maxLines = 1)
                                }
                            }
                        }

                        // The tally: short lines that build the score.
                        if (lines.isNotEmpty() || (won && round.misses == 0) || report.practice) {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .plank(Wood.Walnut, corner = 14.dp, elevation = 4.dp, seed = 9)
                                    .border(1.dp, BrassRule, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                lines.forEachIndexed { i, line ->
                                    SlideIn(linesIn > i, skipped || calm) {
                                        TallyRow(line, accent, last = i == lines.lastIndex, dense = dense, modifier = Modifier.onGloballyPositioned { spots.lines[i] = it.boundsInRoot().center })
                                    }
                                }
                                if (won && round.misses == 0) {
                                    Reveal(linesIn >= lines.size, skipped, stamp = !calm) { FlawlessBadge(Modifier.padding(top = 8.dp, bottom = 4.dp)) }
                                }
                                if (report.practice) Text("Practice round", style = BodyText, color = MUTED, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (!report.practice && !report.newBest && round.score > 0 && progress.best > round.score) {
                            Reveal(linesIn >= lines.size && scoreShown == round.score, skipped) { ShortOfBest(round.score, progress.best, accent) }
                        }

                        Spacer(Modifier.height(12.dp))
                        Reveal(xpIn, skipped) {
                            XpPanel(session, report, accent, play = xpIn, instant = skipped, t = { t }, onPlaced = { spots.emblem = it })
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
                                                .plate(RoundedCornerShape(12.dp))
                                                .border(1.dp, BrassRule, RoundedCornerShape(12.dp))
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
                val actionsPart: @Composable (Modifier) -> Unit = { modifier ->
                Box(
                    modifier
                        .padding(top = 10.dp)
                        .graphicsLayer {
                            alpha = buttons.value.coerceIn(0f, 1f)
                            translationY = (1f - buttons.value) * 40.dp.toPx()
                        },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // After a win, the next level comes first whenever it is open, whether this win
                            // opened it or it was open already; Again replays the level just played.
                            val thisLevel = round.level.number
                            val nextLevel = thisLevel + 1
                            val goOn = won && !round.daily && nextLevel <= LEVELS.size && progress.isOpen(nextLevel)
                            val play: (Int) -> Unit = { n ->
                                if (session.store.progress.current != n) session.store.change { it.copy(current = n) }
                                session.start(daily = false)
                            }
                            val replay: () -> Unit = { if (round.daily) session.start() else play(thisLevel) }
                            // Three losses running on this level: a gentler try, offered and never pushed.
                            val easier = !round.daily && !won && progress.easierOffered && progress.current == round.level.number
                            when {
                                // Side by side, so the screen keeps its room for the result: replay small on
                                // the left, the next level big on the right where the thumb lands.
                                goOn -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SecondaryButton("Again", Modifier.weight(1f), glyph = Glyph.Replay) { replay() }
                                    PlayButton("Next level", calm, Modifier.weight(1.9f), sub = "Level $nextLevel", glyph = Glyph.Next) { play(nextLevel) }
                                }
                                easier -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SecondaryButton("Easier", Modifier.weight(1f), glyph = Glyph.Bulb) { session.start(daily = false, easy = true) }
                                        PlayButton("Try again", calm, Modifier.weight(1.6f), glyph = Glyph.Replay) { replay() }
                                    }
                                    if (!compact) Text("Easier bulb: a fifth more time, one star at most.", style = BodyText, color = MUTED, textAlign = TextAlign.Center)
                                }
                                else -> PlayButton(if (won) "Again" else "Try again", calm, Modifier.fillMaxWidth(), glyph = Glyph.Replay) { replay() }
                            }
                            if (round.daily && !report.practice) {
                            SecondaryButton("Share", Modifier.fillMaxWidth(), glyph = Glyph.Share) {
                                val card = session.store.progress.dailyCard
                                if (card != null) session.sharing = card
                                else platform.shareText(dailyShare(todaySeed(), report.stars, round.score))
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
                if (landscape) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            FitHeight { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { heroPart() } }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                FitHeight { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { detailsPart() } }
                            }
                            actionsPart(Modifier.widthIn(max = 480.dp).fillMaxWidth())
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        // The whole result on one page: if it is taller than the room, it shrinks to fit.
                        Box(Modifier.weight(1f).widthIn(max = 480.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FitHeight {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    heroPart()
                                    Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
                                    detailsPart()
                                }
                            }
                        }
                        actionsPart(Modifier.widthIn(max = 480.dp).fillMaxWidth())
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
                            val y = f.from.y + (f.to.y - f.from.y) * t - 30.dp.toPx() * sin(t * PI.toFloat())
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
                            val a = i * 30.0 * PI / 180.0 + 0.2
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
                            val bend = (if (i % 2 == 0) -1f else 1f) * 40f * density * sin(p * PI.toFloat())
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
private val MUTED = Dusk.inkSoft
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
        // At the lowest quality step, half the confetti and embers.
        for (k in 0 until (if (Gpu.quality >= 2) 23 else 46)) {
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
        for (k in 0 until (if (Gpu.quality >= 2) 17 else 34)) {
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
            .plate(RoundedCornerShape(12.dp))
            .border(1.dp, BrassRule, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        value()
        Text(label, style = Label, color = MUTED, maxLines = 1)
    }
}

/** "312 short of your best", over a brass gauge set in an ebony slot: the score against the best. */
@Composable
private fun ShortOfBest(score: Int, best: Int, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 10.dp)) {
        Text("${(best - score).grouped()} short of your best", style = BodyText, color = MUTED)
        Canvas(
            Modifier
                .padding(top = 5.dp)
                .size(180.dp, 12.dp)
                .plate(RoundedCornerShape(6.dp))
                .border(1.dp, BrassRule, RoundedCornerShape(6.dp)),
        ) {
            val inset = 2.5.dp.toPx()
            val w = (size.width - inset * 2) * (score.toFloat() / best).coerceIn(0f, 1f)
            if (w > 0f) {
                drawRoundRect(
                    Brush.verticalGradient(listOf(Craft.brassLight, Craft.gold, Craft.brassDark), inset, size.height - inset),
                    Offset(inset, inset),
                    Size(w, size.height - inset * 2),
                    CornerRadius(size.height),
                )
            }
            // Ticks cut every tenth, so it reads as a gauge.
            for (k in 1 until 10) {
                val x = inset + (size.width - inset * 2) * k / 10f
                drawLine(Color.Black.copy(alpha = 0.4f), Offset(x, inset), Offset(x, size.height - inset), 1.dp.toPx())
            }
        }
    }
}

/**
 * The hero: the porch's own mason-jar lamp, lit gold with moths circling it in 3D (a win), or dark
 * and smoking (a loss), inside a ring of one segment per pair that light up one by one.
 */
@Composable
private fun HeroLamp(scale: Float, session: Session, moths: List<MothId>, won: Boolean, found: Int, of: Int, play: Boolean, instant: Boolean, calm: Boolean, t: () -> Float, modifier: Modifier = Modifier) {
    val lit = remember { Animatable(0f) }
    val pop = remember { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(play, instant) {
        if (instant || calm) {
            lit.snapTo(found.toFloat())
            pop.snapTo(1f)
            return@LaunchedEffect
        }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        if (play) lit.animateTo(found.toFloat(), tween(120 * found.coerceAtLeast(1), easing = LinearEasing))
    }
    val accent = if (won) WIN else LOSS
    val deep = if (won) WIN_DEEP else LOSS_DEEP
    Box(
        modifier
            .size(170.dp * scale)
            .graphicsLayer {
                val p = pop.value
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
                alpha = p.coerceIn(0f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val time = t()
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            // The light behind the lamp, and slow rays on a win.
            val breathe = 0.85f + 0.15f * sin(time * 2f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = (if (won) 0.5f else 0.22f) * breathe), Color.Transparent), c, r), r, c)
            if (won) {
                rotate(time * 12f, c) {
                    for (k in 0 until 16) {
                        val a = k * (2 * PI.toFloat() / 16)
                        val ray = androidx.compose.ui.graphics.Path().apply {
                            moveTo(c.x + cos(a - 0.06f) * r * 0.45f, c.y + sin(a - 0.06f) * r * 0.45f)
                            lineTo(c.x + cos(a) * r * 0.98f, c.y + sin(a) * r * 0.98f)
                            lineTo(c.x + cos(a + 0.06f) * r * 0.45f, c.y + sin(a + 0.06f) * r * 0.45f)
                            close()
                        }
                        drawPath(ray, Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent), c, r))
                    }
                }
            }
            // The ring: one segment per pair; found ones light up with a glow.
            val ringR = r * 0.86f
            val stroke = 9.dp.toPx()
            val gap = if (of > 1) 6f else 0f
            val each = 360f / of.coerceAtLeast(1)
            val box = Offset(c.x - ringR, c.y - ringR)
            val ringSize = Size(ringR * 2, ringR * 2)
            for (k in 0 until of) {
                val start = -90f + k * each + gap / 2f
                val sweep = each - gap
                val on = (lit.value - k).coerceIn(0f, 1f)
                drawArc(Color.White.copy(alpha = 0.07f), start, sweep, false, box, ringSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (on > 0f) {
                    drawArc(accent.copy(alpha = 0.35f * on), start, sweep, false, box, ringSize, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                    drawArc(
                        Brush.sweepGradient(listOf(deep, accent, Color(0xFFFFF4D6), accent, deep), c),
                        start,
                        sweep * on,
                        false,
                        box,
                        ringSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        // The lamp itself.
        Bulb(
            clock = session.clock,
            timeLeft = { if (won) 1f else 0f },
            power = { if (won) 1.05f + 0.1f * sin(t() * 2f) else 0f },
            modifier = Modifier.size(70.dp * scale, 100.dp * scale).offset(y = (-6).dp * scale),
            lamp = Lamp.Jar,
            smoke = { !won },
            still = calm,
            ownHalo = true,
        )
        // Moths circling the light on a tilted ring: smaller and dimmer behind, bigger in front.
        if (won) {
            moths.forEachIndexed { k, moth ->
                // Drawn, not composed, each frame: the wings beat in the draw step only.
                Canvas(
                    Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            val a = t() * 0.9f + k * (2 * PI.toFloat() / moths.size)
                            val depth = sin(a)
                            translationX = cos(a) * 62.dp.toPx() * scale
                            translationY = depth * 20.dp.toPx() - 4.dp.toPx() + sin(t() * 2.5f + k) * 4.dp.toPx()
                            val sc = 0.7f + 0.3f * depth
                            scaleX = sc
                            scaleY = sc
                            alpha = 0.55f + 0.45f * (depth + 1f) / 2f
                            rotationZ = cos(a) * 25f
                        },
                ) { moth(moth, Offset(size.width / 2f, size.height / 2f), size.minDimension, 0.55f + 0.45f * abs(sin(t() * 12f + k))) }
            }
        }
    }
}

/** A five-point star path, pointing up. */
private fun starPath(c: Offset, r: Float, inner: Float = 0.48f) = androidx.compose.ui.graphics.Path().apply {
    for (k in 0 until 10) {
        val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
        val rr = if (k % 2 == 0) r else r * inner
        val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

/**
 * A star as a struck gold medal: a dark rim, a bevelled face lit from the top left, a highlight, and
 * sparkles glinting round it. It slams in from big with a ring of light bursting out. An unearned
 * star is an engraved gunmetal socket.
 */
@Composable
private fun MedalStar(filled: Boolean, turn: Boolean, instant: Boolean, calm: Boolean, size: Dp, tilt: Float, drop: Dp, t: () -> Float, hint: String?) {
    val land = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(turn, instant) {
        if (!turn) return@LaunchedEffect
        if (instant || calm) {
            land.snapTo(1f)
            return@LaunchedEffect
        }
        if (filled) launch {
            delay(140)
            burst.snapTo(0f)
            burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
        land.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(Modifier.width(size + 18.dp).padding(top = drop), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .graphicsLayer {
                    val l = land.value
                    rotationZ = tilt + (1f - l) * -40f
                    val sc = if (filled) 2.2f - 1.2f * l else 0.6f + 0.4f * l
                    scaleX = sc
                    scaleY = sc
                    alpha = l.coerceIn(0f, 1f)
                },
        ) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            if (filled) {
                val b = burst.value
                if (b < 1f) drawCircle(WIN.copy(alpha = 0.7f * (1f - b)), r * (0.8f + 1.1f * b), c, style = Stroke(3.dp.toPx() * (1f - b)))
                drawCircle(Brush.radialGradient(listOf(WIN.copy(alpha = 0.45f), Color.Transparent), c, r * 1.25f), r * 1.25f, c)
                drawPath(starPath(c, r), Color(0xFF8A4B00))
                drawPath(
                    starPath(Offset(c.x, c.y - r * 0.02f), r * 0.9f),
                    Brush.linearGradient(listOf(Color(0xFFFFF6C2), Color(0xFFFFD04A), Color(0xFFE38A00)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)),
                )
                drawPath(
                    starPath(c, r * 0.55f, inner = 0.5f),
                    Brush.linearGradient(listOf(Color(0xFFFFE07A), Color(0xFFFFB420)), Offset(c.x + r * 0.5f, c.y + r * 0.5f), Offset(c.x - r * 0.5f, c.y - r * 0.5f)),
                )
                drawOval(Color.White.copy(alpha = 0.55f), Offset(c.x - r * 0.42f, c.y - r * 0.55f), Size(r * 0.38f, r * 0.2f))
                if (!calm) {
                    val time = t()
                    for (k in 0 until 3) {
                        val g = sin(time * 2.6f + k * 2.1f + tilt).coerceAtLeast(0f)
                        if (g <= 0f) continue
                        val a = k * 2.1f + tilt * 0.1f
                        val at = Offset(c.x + cos(a) * r, c.y + sin(a) * r * 0.95f)
                        val s = r * 0.22f * g
                        drawLine(Color.White.copy(alpha = g), Offset(at.x - s, at.y), Offset(at.x + s, at.y), 1.5.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = g), Offset(at.x, at.y - s), Offset(at.x, at.y + s), 1.5.dp.toPx(), StrokeCap.Round)
                    }
                }
            } else {
                drawPath(starPath(c, r), Color(0xFF0B0D14))
                drawPath(
                    starPath(Offset(c.x, c.y + r * 0.03f), r * 0.88f),
                    Brush.linearGradient(listOf(Color(0xFF3A4054), Color(0xFF1B1F2B)), Offset(c.x, c.y - r), Offset(c.x, c.y + r)),
                )
                drawPath(starPath(c, r * 0.88f), Color.White.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
            }
        }
        if (hint != null) {
            Reveal(turn, instant) {
                Text(hint.uppercase(), style = Label, color = MUTED, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** One line of the tally: an icon on a tinted tile, what it was and a word about it, and its points. */
@Composable
private fun TallyRow(line: TallyLine, accent: Color, last: Boolean, dense: Boolean = false, modifier: Modifier = Modifier) {
    val (icon, caption) = when (line.kind) {
        TallyKind.Pairs -> Glyph.Moth to "Matched"
        TallyKind.Streak -> Glyph.Flame to "Best chain"
        TallyKind.Time -> Glyph.Clock to "Bulb to spare"
        TallyKind.Misses -> Glyph.Close to "Wrong pairs"
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = if (dense) 4.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (dense) 28.dp else 34.dp)
                    .shadow(3.dp, RoundedCornerShape(50), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                    .metal(corner = 17.dp, copper = line.kind == TallyKind.Streak || line.kind == TallyKind.Misses),
                contentAlignment = Alignment.Center,
            ) { GlyphArt(icon, Dusk.paperInk, Modifier.size(17.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(line.text, style = BodyText, fontWeight = FontWeight.Bold, color = Dusk.ink, maxLines = 1)
                if (!dense) Text(caption, style = Label, color = MUTED, maxLines = 1)
            }
            if (line.points > 0) {
                Text("+${line.points.grouped()}", style = numberStyle(accent, strong = true), fontSize = 19.sp, textAlign = TextAlign.End, modifier = modifier)
            } else {
                Spacer(modifier)
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Craft.brassDark.copy(alpha = 0.55f)))
    }
}

/** No wrong pairs: a small brass plaque, stamped under the tally, its words cut into the metal. */
@Composable
private fun FlawlessBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(4.dp, RoundedCornerShape(6.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .metal(corner = 6.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphArt(Glyph.Check, Dusk.paperInk, Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("CLEAN SWEEP · NO WRONG PAIRS", style = TextEngraved, fontSize = 12.sp, letterSpacing = 1.2.sp, color = Dusk.paperInk)
    }
}

/**
 * XP: the level emblem beside a thick, glossy bar in segments that fills with what this round
 * earned, a light running along it. A level-up fills it, flashes UP!, spins the emblem and refills
 * from empty. Nothing glows outside the bar.
 */
@Composable
private fun XpPanel(session: Session, report: Report, accent: Color, play: Boolean, instant: Boolean, t: () -> Float, onPlaced: (Offset) -> Unit) {
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
            bar.animateTo(levelFill(xpAfter), tween(800, easing = FastOutSlowInEasing))
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
    val into = xpAfter - xpFor(level)
    val span = xpFor(level + 1) - xpFor(level)
    Row(
        Modifier
            .fillMaxWidth()
            .plank(Wood.Walnut, corner = 14.dp, elevation = 4.dp, seed = 11)
            .border(1.dp, BrassRule, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Emblem(
            report.levelAfter,
            Modifier
                .flies("emblem")
                .size(56.dp)
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
                Text("LEVEL ${report.levelAfter}", style = numberStyle(WIN), fontSize = 18.sp)
                if (up) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "UP!",
                        style = Label,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1206),
                        modifier = Modifier
                            .graphicsLayer {
                                val b = banner.value
                                alpha = b.coerceIn(0f, 1f)
                                scaleX = 0.5f + 0.5f * b
                                scaleY = 0.5f + 0.5f * b
                            }
                            .metal(corner = 5.dp)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (report.practice) "NO XP" else "+${report.xp} XP",
                    style = TextEngraved,
                    fontSize = 12.sp,
                    color = Dusk.paperInk,
                    modifier = Modifier
                        .metal(corner = 5.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                val radius = CornerRadius(size.height / 2f)
                // A recessed track.
                drawRoundRect(Color(0xFF07080D), cornerRadius = radius)
                drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), 0f, size.height * 0.5f), cornerRadius = radius)
                val w = size.width * bar.value.coerceIn(0f, 1f)
                if (w > 0f) {
                    drawRoundRect(Brush.verticalGradient(listOf(Craft.brassLight, Craft.gold, Craft.brassDark)), size = Size(w, size.height), cornerRadius = radius)
                    // Gloss on the top.
                    drawRoundRect(
                        Color.White.copy(alpha = 0.28f),
                        Offset(2.dp.toPx(), 1.5.dp.toPx()),
                        Size((w - 4.dp.toPx()).coerceAtLeast(0f), size.height * 0.32f),
                        CornerRadius(size.height / 4f),
                    )
                    if (!calm) {
                        val run = ((t() * 0.45f) % 1.4f) * size.width
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent), run - 36.dp.toPx(), run),
                            size = Size(w, size.height),
                        )
                    }
                }
                // Segments, so it reads as a gauge.
                for (k in 1 until 10) {
                    val x = size.width * k / 10f
                    drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("${xpToGo(xpAfter).grouped()} to level ${level + 1}", style = Label, color = MUTED, modifier = Modifier.weight(1f))
                Text("${into.grouped()} / ${span.grouped()}", style = Label, color = MUTED.copy(alpha = 0.7f))
            }
        }
    }
}


// ---------------------------------------------------------------------------------------------
// Type that moves

/** Big numbers: white melting into the result's colour toward the bottom, with a soft glow. */
private fun numberStyle(accent: Color, strong: Boolean = false) = Modern.copy(
    brush = Brush.verticalGradient(
        if (strong) listOf(lerp(Color.White, accent, 0.35f), accent)
        else listOf(Color.White, Color.White, lerp(Color.White, accent, 0.6f)),
    ),
    shadow = androidx.compose.ui.graphics.Shadow(accent.copy(alpha = 0.45f), Offset(0f, 0f), 14f),
)

/** A band of light that passes over whatever this is drawn on, every [every] seconds, only where it has paint. */
private fun Modifier.glint(t: () -> Float, calm: Boolean, every: Float = 3.5f, strength: Float = 0.55f): Modifier =
    if (calm) this
    else this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val run = (t() % every) / 1.1f
            if (run < 1f) {
                val x = -size.width * 0.3f + run * size.width * 1.6f
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
                        Offset(x - size.height * 1.2f, 0f),
                        Offset(x, size.height),
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            }
        }

/**
 * The headline as a moment: each letter drops in on its own, flipping over in 3D, one after
 * another; then the words ride a slow wave. The letters are cast metal, light at the top and the
 * result's colour at the bottom, glowing, with a glint passing through now and then. A loss's
 * letters stutter like a sign failing before they settle, dimmer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroTitle(text: String, won: Boolean, shown: Boolean, instant: Boolean, calm: Boolean, t: () -> Float, fontSize: TextUnit = 36.sp) {
    val letters = text.length
    val drops = remember(text) { List(letters) { Animatable(if (calm) 1f else 0f) } }
    val landedAt = remember(text) { mutableFloatStateOf(-1f) }
    LaunchedEffect(shown, instant) {
        if (!shown) return@LaunchedEffect
        if (instant || calm) {
            drops.forEach { it.snapTo(1f) }
            landedAt.floatValue = -10f
            return@LaunchedEffect
        }
        landedAt.floatValue = t()
        drops.forEachIndexed { i, d ->
            launch {
                delay(i * 32L)
                d.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val accent = if (won) WIN else LOSS
    val top = if (won) Color(0xFFFFF8DA) else Color(0xFFFFE6DC)
    val bottom = if (won) Color(0xFFFFB83A) else Color(0xFFFF6A4D)
    val style = Modern.copy(
        fontSize = fontSize,
        lineHeight = fontSize * 1.12f,
        letterSpacing = (-0.6).sp,
        brush = Brush.verticalGradient(listOf(top, top, bottom)),
        shadow = androidx.compose.ui.graphics.Shadow(accent.copy(alpha = 0.75f), Offset(0f, 0f), 26f),
    )
    FlowRow(
        Modifier
            .fillMaxWidth()
            .glint(t, calm, every = 3.2f, strength = 0.7f)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.Center,
    ) {
        var index = 0
        val words = text.split(" ")
        words.forEachIndexed { w, word ->
            Row {
                for (ch in word) {
                    val i = index++
                    val drop = drops[i]
                    Text(
                        "$ch",
                        style = style,
                        modifier = Modifier.graphicsLayer {
                            val d = drop.value
                            cameraDistance = 8f * density
                            rotationX = (1f - d) * -110f
                            translationY = -(1f - d) * 34.dp.toPx() + if (calm) 0f else sin(t() * 2.2f - i * 0.42f) * 2.5.dp.toPx()
                            val sc = 1f + (1f - d) * 0.6f
                            scaleX = sc
                            scaleY = sc
                            var a = d.coerceIn(0f, 1f)
                            if (!won && !calm && landedAt.floatValue >= 0f) {
                                // A failing sign: a few letters stutter for a moment after they land.
                                val since = t() - landedAt.floatValue - i * 0.032f
                                if (since in 0.3f..1.3f && (i * 7) % 3 == 0) {
                                    a *= if (sin(since * 47f + i) > 0.2f) 1f else 0.25f
                                }
                            }
                            alpha = a
                        },
                    )
                }
            }
            if (w < words.lastIndex) {
                index++
                Text(" ", style = style)
            }
        }
    }
}

/** A line that types itself out, letter by letter, holding its full size from the start. */
@Composable
private fun TypeText(text: String, shown: Boolean, instant: Boolean, modifier: Modifier = Modifier) {
    val typed = remember(text) { Animatable(if (instant) text.length.toFloat() else 0f) }
    LaunchedEffect(shown, instant) {
        if (!shown) return@LaunchedEffect
        if (instant) typed.snapTo(text.length.toFloat())
        else {
            delay(450)
            typed.animateTo(text.length.toFloat(), tween(text.length * 28, easing = LinearEasing))
        }
    }
    val n = typed.value.toInt().coerceIn(0, text.length)
    Text(
        buildAnnotatedString {
            append(text.substring(0, n))
            withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(n)) }
        },
        style = BodyText.copy(letterSpacing = 0.3.sp),
        color = Color(0xFFC9CFDD),
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { contentDescription = text },
    )
}

// ---------------------------------------------------------------------------------------------
// Modern type and actions

/** The scorecard's own type: Fredoka at its heaviest, tight, with tabular figures. */
private val Modern get() = TextStyle(
    fontFamily = BodyFont,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.2).sp,
)

/**
 * Fades the last few dp of a scrolling area out while there is more below, so content slides away
 * under what follows instead of being cut. When everything fits, nothing fades.
 */
private fun Modifier.fadeEnd(scroll: androidx.compose.foundation.ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (scroll.value >= scroll.maxValue) return@drawWithContent
        val edge = 28.dp.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - edge, size.height),
            topLeft = Offset(0f, size.height - edge),
            size = Size(size.width, edge),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Lays out [content] at its natural height and, if that is taller than the room there is, scales it
 * down evenly (from its top centre) so it all fits on one page instead of scrolling.
 */
@Composable
private fun FitHeight(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
        val room = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val scale = (room.toFloat() / placeable.height.coerceAtLeast(1)).coerceAtMost(1f)
        val shown = (placeable.height * scale).toInt()
        layout(placeable.width, shown) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}
