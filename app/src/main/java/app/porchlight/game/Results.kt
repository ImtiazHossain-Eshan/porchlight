package app.porchlight.game

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
            report.newNight?.let { n ->
                add(Reward("Night $n is open", nightLine(NIGHTS[n - 1])) { NightSeal(n) })
            }
            report.newMastery?.let { moth ->
                add(Reward("${moth.label} turned gold", "Three stars with ${moth.label} leading.") { MothArt(moth, Modifier.size(54.dp), gold = true) })
            }
            report.newMoths.forEach { moth ->
                add(Reward("${moth.label} joined", moth.perkName) { MothArt(moth, Modifier.size(54.dp)) })
            }
            report.newBadges.forEach { badge ->
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

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                // Below Android 12 the round behind isn't blurred, so a darker veil keeps its plates and cards quiet.
                .background(Color.Black.copy(alpha = (if (Gpu.blur) 0.5f else 0.8f) * (1f - slide.value).coerceIn(0f, 1f)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true }
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .widthIn(max = 460.dp)
                    .graphicsLayer {
                        translationY = slide.value * size.height * 1.2f
                        translationX = shake.value * density
                    },
            ) {
                Column(
                    Modifier
                        .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                        .gilded(18.dp)
                        .padding(horizontal = 24.dp, vertical = 26.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Stars, flying in from the bulb.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        repeat(3) { i ->
                            StarSlot(
                                filled = i < report.stars,
                                turn = starsIn > i,
                                instant = skipped,
                                calm = calm,
                                from = session.bulbAt,
                                hint = if (i >= report.stars && won) starHint(i, round) else null,
                                // The middle star stands a little taller, as on a medal.
                                modifier = Modifier.padding(top = if (i == 1) 0.dp else 12.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // The headline: a win lights up letter by letter; a loss flickers and cools to an ember.
                    if (headline) {
                        if (won) {
                            NeonOn("EVERY PAIR FOUND", TextGlow, TypeScale.Title, instant = skipped, calm = calm)
                        } else {
                            NeonOff(
                                "THE BULB BURNED OUT",
                                TextGlow,
                                TypeScale.Title,
                                ember = Craft.copperLight,
                                instant = skipped,
                                calm = calm,
                                onFizzle = { session.sfx.play(Sound.Buzz, volume = 0.6f, pitch = 0.8f) },
                            )
                        }
                    } else {
                        // Hold the headline's place so nothing jumps when it lights.
                        Text(if (won) "EVERY PAIR FOUND" else "THE BULB BURNED OUT", style = Display, fontSize = TypeScale.Title, color = Color.Transparent, textAlign = TextAlign.Center)
                    }
                    // A loss gets a word: how close it was, or a tip.
                    if (note != null) {
                        Reveal(headline, skipped) {
                            Text(note, style = BodyText, color = Color(0xFFD9CDB4), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // The hero. Nobody wants a giant zero: a round with no score shows the pairs,
                    // dimmer, with the dark bulb and one moth flying off.
                    if (round.score > 0) {
                        Box(
                            Modifier
                                .onGloballyPositioned { spots.score = it.boundsInRoot().center }
                                .graphicsLayer {
                                    scaleX = punch.value
                                    scaleY = punch.value
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            CountUp(scoreShown, TextGlow, TypeScale.Hero, durationMs = 300, instant = skipped, calm = calm)
                        }
                        if (!report.practice && !report.newBest && progress.best > round.score) {
                            Reveal(linesIn >= lines.size && scoreShown == round.score, skipped) { ShortOfBest(round.score, progress.best) }
                        }
                    } else {
                        ZeroHero(round, session.lead, calm, Modifier.onGloballyPositioned { spots.score = it.boundsInRoot().center })
                    }
                    Spacer(Modifier.height(10.dp))

                    // The tally: icons and short lines that build the score.
                    if (lines.isNotEmpty() || (won && round.misses == 0) || report.practice) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .border(1.dp, Craft.goldDeep.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            lines.forEachIndexed { i, line ->
                                SlideIn(linesIn > i, skipped || calm) {
                                    Line(line, Modifier.onGloballyPositioned { spots.lines[i] = it.boundsInRoot().center })
                                }
                            }
                            if (won && round.misses == 0) {
                                Reveal(linesIn >= lines.size, skipped) {
                                    Row(Modifier.padding(top = 4.dp)) { Chip("CLEAN!", Craft.goldDeep, Color(0xFFFFF6DC)) }
                                }
                            }
                            if (report.practice) Text("Practice round", style = BodyText, color = Dusk.inkSoft, modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Reveal(xpIn, skipped) {
                        LevelSection(session, report, play = xpIn, instant = skipped, onPlaced = { spots.emblem = it })
                    }

                    if (rewards.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            rewards.forEachIndexed { i, reward ->
                                Reveal(rewardsIn > i, skipped, stamp = !calm) {
                                    Column(
                                        Modifier
                                            .width(136.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.28f))
                                            .border(1.dp, Craft.goldDeep.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 10.dp)
                                            .semantics(mergeDescendants = true) {},
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        reward.art()
                                        Spacer(Modifier.height(6.dp))
                                        Text(reward.title.uppercase(), style = Label, color = Craft.gold, textAlign = TextAlign.Center, maxLines = 2)
                                        Text(reward.detail, style = BodyText, color = Color(0xFFD9CDB4), lineHeight = 19.sp, textAlign = TextAlign.Center, maxLines = 3)
                                    }
                                }
                            }
                        }
                    }

                    if (round.daily) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (report.practice) "Today's scored try is already in. A new porch comes tomorrow."
                            else "Daily porch · ${report.dailyStreak} ${if (report.dailyStreak == 1) "day" else "days"} running",
                            style = BodyText,
                            color = Color(0xFFD9CDB4),
                            textAlign = TextAlign.Center,
                        )
                    }

                    // The buttons rise in last, so they can't be hit by accident mid-sequence.
                    Spacer(Modifier.height(18.dp))
                    Box(
                        Modifier.graphicsLayer {
                            alpha = buttons.value.coerceIn(0f, 1f)
                            translationY = (1f - buttons.value) * 24.dp.toPx()
                        },
                    ) {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SecondaryButton("Porch", Modifier.weight(1f)) { session.toPorch() }
                                PrimaryButton("Again", Modifier.weight(1.3f)) { session.start() }
                            }
                            if (round.daily && !report.practice) {
                                Spacer(Modifier.height(8.dp))
                                SecondaryButton("Share", Modifier.fillMaxWidth()) {
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

                // NEW BEST: a red-gold ribbon stamped diagonally across the card's corner.
                if (bestIn) {
                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(18.dp))) {
                        Stamp(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 40.dp, y = 22.dp)
                                .graphicsLayer { rotationZ = 38f },
                            instant = skipped,
                            calm = calm,
                            onLand = {
                                session.sfx.play(Sound.Thunk)
                                session.buzz(HapticFeedbackType.LongPress)
                                session.scope?.launch {
                                    for (x in listOf(3f, -3f, 2f, -2f, 0f)) shake.animateTo(x, tween(40))
                                }
                            },
                        ) {
                            Box(
                                Modifier
                                    .width(170.dp)
                                    .background(Brush.horizontalGradient(listOf(Craft.danger, Craft.gold, Craft.danger)))
                                    .border(1.dp, Craft.goldDeep)
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("NEW BEST", style = Label, fontWeight = FontWeight.Bold, color = Color.White)
                            }
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

/**
 * One star socket. On its [turn] a filled star flies in from the bulb ([from], root pixels), spins
 * once and lands; an empty socket gives a small shake and stays dark, with a [hint] under it.
 */
@Composable
private fun StarSlot(filled: Boolean, turn: Boolean, instant: Boolean, calm: Boolean, from: Offset, hint: String?, modifier: Modifier = Modifier) {
    var centre by remember { mutableStateOf(Offset.Zero) }
    val fly = remember { Animatable(1f) }
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(turn, instant) {
        if (!turn) return@LaunchedEffect
        if (instant) {
            fly.snapTo(0f)
            return@LaunchedEffect
        }
        if (filled) {
            fly.animateTo(0f, tween(if (calm) 250 else 420, easing = FastOutSlowInEasing))
        } else if (!calm) {
            for (x in listOf(5f, -4f, 3f, -2f, 0f)) wobble.animateTo(x, tween(40))
        }
    }
    Column(modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.onGloballyPositioned { centre = it.boundsInRoot().center }, contentAlignment = Alignment.Center) {
            Star(false, shown = true, instant = true, modifier = Modifier.graphicsLayer { rotationZ = wobble.value })
            if (filled && turn) {
                Star(
                    true,
                    shown = true,
                    instant = true,
                    modifier = Modifier.graphicsLayer {
                        val f = fly.value
                        if (calm) {
                            // Reduce motion: the star simply fades in.
                            alpha = 1f - f
                        } else {
                            if (from != Offset.Zero && centre != Offset.Zero) {
                                translationX = (from.x - centre.x) * f
                                translationY = (from.y - centre.y) * f
                            }
                            rotationZ = -360f * f
                            val s = 1f - 0.5f * f
                            scaleX = s
                            scaleY = s
                        }
                    },
                )
            }
        }
        if (hint != null) {
            Reveal(turn, instant) {
                Text(hint.uppercase(), style = Label, color = Dusk.inkSoft, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** A round with no score: the pairs found as the hero, dimmed, the dark bulb, and one moth leaving. */
@Composable
private fun ZeroHero(round: Round, lead: MothId, calm: Boolean, modifier: Modifier = Modifier) {
    val away = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!calm) {
            delay(500)
            away.animateTo(1f, tween(1_600, easing = LinearEasing))
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Bulb(
                clock = remember { Clock() },
                timeLeft = { 0f },
                power = { 0f },
                lamp = Lamp.Jar,
                still = true,
                modifier = Modifier.size(30.dp, 44.dp),
            )
            Text("${round.pairs} / ${round.pairCount} pairs", style = Display, fontSize = TypeScale.Title, color = Dusk.inkSoft)
        }
        if (!calm && away.value < 1f) {
            MothArt(
                lead,
                Modifier
                    .size(30.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        val t = away.value
                        translationX = 120.dp.toPx() * t + 8.dp.toPx() * sin(t * 18f)
                        translationY = -70.dp.toPx() * t
                        alpha = 1f - t
                    },
                open = 0.8f,
            )
        }
    }
}

/** "312 short of your best", with a thin bar of the score against the best. */
@Composable
private fun ShortOfBest(score: Int, best: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 2.dp)) {
        Text("${(best - score).grouped()} short of your best", style = BodyText, color = Dusk.inkSoft)
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(140.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Dusk.slot),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = (score.toFloat() / best).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(Craft.gold),
            )
        }
    }
}

/** One tally line: an icon, what happened, and what it was worth (if anything). */
@Composable
private fun Line(line: TallyLine, modifier: Modifier = Modifier) {
    val icon = when (line.kind) {
        TallyKind.Pairs -> Glyph.Moth
        TallyKind.Streak -> Glyph.Flame
        TallyKind.Time -> Glyph.Bulb
        TallyKind.Misses -> Glyph.Close
    }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphArt(icon, Craft.brassLight, Modifier.size(18.dp))
        Text(line.text, style = BodyText, color = Color(0xFFFFF0C8), modifier = Modifier.weight(1f))
        if (line.points > 0) {
            Text("+${line.points.grouped()}", style = BodyText, fontWeight = FontWeight.Bold, color = Craft.gold, textAlign = TextAlign.End, modifier = modifier)
        } else {
            Spacer(modifier)
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

/**
 * XP and the level: the emblem with its ring filling as the orbs arrive, "+40 XP", what's next, and
 * the raw numbers small. A level-up lifts the emblem, spins it once in 3D, flips the number like a
 * split-flap, empties and refills the ring, throws sparks, pops on any new tier's parts, and slides
 * a LEVEL banner in.
 */
@Composable
private fun LevelSection(session: Session, report: Report, play: Boolean, instant: Boolean, onPlaced: (Offset) -> Unit) {
    val progress = session.store.progress
    val xpAfter = progress.xp
    val xpBefore = xpAfter - report.xp
    val up = report.levelAfter > report.levelBefore
    val newTier = tierFor(report.levelAfter) != tierFor(report.levelBefore)
    val calm = session.calm
    val ring = remember(report) { Animatable(levelFill(xpBefore)) }
    val lift = remember(report) { Animatable(0f) }
    val spin = remember(report) { Animatable(0f) }
    val flip = remember(report) { Animatable(if (up) 0f else 1f) }
    val burst = remember(report) { Animatable(0f) }
    val parts = remember(report) { Animatable(if (up && newTier) 0f else 1f) }
    val banner = remember(report) { Animatable(if (up) 0f else 1f) }
    LaunchedEffect(report, play, instant) {
        if (!play) return@LaunchedEffect
        if (instant || calm) {
            ring.snapTo(levelFill(xpAfter))
            flip.snapTo(1f)
            parts.snapTo(1f)
            lift.snapTo(0f)
            spin.snapTo(0f)
            banner.snapTo(1f)
            return@LaunchedEffect
        }
        if (!up) {
            delay(200)
            ring.animateTo(levelFill(xpAfter), tween(650, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        delay(200)
        ring.animateTo(1f, tween(400))
        launch { lift.animateTo(1f, tween(220)) }
        launch { spin.animateTo(360f, tween(900, easing = FastOutSlowInEasing)) }
        delay(250)
        flip.animateTo(1f, tween(420))
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(650))
        }
        session.sfx.play(Sound.LevelUp)
        session.buzz(HapticFeedbackType.Confirm)
        ring.snapTo(0f)
        launch { ring.animateTo(levelFill(xpAfter), tween(600, easing = FastOutSlowInEasing)) }
        launch { banner.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
        if (newTier) parts.animateTo(1f, tween(900))
        lift.animateTo(0f, spring(dampingRatio = 0.5f))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Emblem(
            report.levelAfter,
            Modifier
                .size(if (up) 96.dp else 72.dp)
                .onGloballyPositioned { onPlaced(it.boundsInRoot().center) }
                .graphicsLayer {
                    cameraDistance = 12f * density
                    rotationY = spin.value
                    translationY = -10.dp.toPx() * lift.value
                },
            xp = ring.value,
            from = report.levelBefore,
            flip = flip.value,
            parts = parts.value,
            burst = burst.value,
            still = calm,
        )
        if (up) {
            Text(
                "LEVEL ${report.levelAfter}",
                style = TextGlow,
                fontSize = TypeScale.Title,
                modifier = Modifier.graphicsLayer {
                    alpha = banner.value.coerceIn(0f, 1f)
                    translationX = -(1f - banner.value) * 80.dp.toPx()
                },
            )
        }
        Text(if (report.practice) "PRACTICE ROUND · NO XP" else "+${report.xp} XP", style = Label, color = Color(0xFFFFF0C8))
        val level = levelFor(xpAfter)
        Text("${xpToGo(xpAfter).grouped()} XP to level ${level + 1}", style = BodyText, color = Dusk.inkSoft)
        Text(
            "${(xpAfter - xpFor(level)).grouped()} / ${(xpFor(level + 1) - xpFor(level)).grouped()}",
            style = Label,
            color = Dusk.inkSoft.copy(alpha = 0.7f),
        )
    }
}

/** A night's number on a brass seal. */
@Composable
private fun NightSeal(number: Int) {
    Box(Modifier.size(54.dp).metal(corner = 27.dp), contentAlignment = Alignment.Center) {
        Text("$number", style = TextEngraved, fontSize = TypeScale.Title, color = Dusk.paperInk)
    }
}

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
