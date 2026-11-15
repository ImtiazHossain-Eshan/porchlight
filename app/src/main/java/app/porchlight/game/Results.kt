package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// The order things are revealed in, one step every 350 ms.
private const val STEP_STARS = 1 // 1, 2, 3: one star each
private const val STEP_SCORE = 4
private const val STEP_XP = 5
private const val STEP_REWARDS = 6
private const val STEP_END = 7

/** One thing a round unlocked, shown as a card in the rewards strip. */
private class Reward(val title: String, val detail: String, val art: @Composable () -> Unit)

/**
 * The scorecard: a gilded panel that springs up over the blurred board. Stars, the score, the bonus
 * that made it, the level gauge, then whatever the round unlocked. Tapping anywhere skips ahead.
 */
@Composable
internal fun Results(session: Session, report: Report) {
    val round = session.round
    val won = round.phase == Phase.Won
    val progress = session.store.progress
    var step by remember(report) { mutableIntStateOf(0) }
    var skipped by remember(report) { mutableStateOf(false) }
    val slide = remember(report) { Animatable(1f) }
    val context = LocalContext.current

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
                        // A new badge: it stamps down (the card does), a shine crosses it, its ribbon unfurls.
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
    LaunchedEffect(report) { slide.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(report) {
        delay(300)
        while (step < STEP_END) {
            step += 1
            val celebrate = step == STEP_REWARDS &&
                (report.levelAfter > report.levelBefore || report.newBadges.isNotEmpty() || report.newNight != null)
            if (step == STEP_REWARDS && !skipped) {
                // Each reward lands with its own note, climbing.
                launch {
                    repeat(rewards.size) { i ->
                        delay(140L * i)
                        session.sfx.play(session.sfx.matchFor(i + 3), volume = 0.55f)
                    }
                }
            }
            if (celebrate && !skipped) {
                session.sfx.play(Sound.LevelUp)
                session.buzz(HapticFeedbackType.Confirm)
            } else if (step in STEP_STARS until STEP_STARS + report.stars && !skipped) {
                session.sfx.play(session.sfx.matchFor(step), volume = 0.6f)
            }
            delay(350)
        }
    }

    // What made the score: the pairs, and the seconds left on the bulb.
    val timeBonus = if (won) (round.left / 1000).toInt() * SECOND_POINTS else 0

    Box(
        Modifier
            .fillMaxSize()
            // Below Android 12 the round behind isn't blurred, so a darker veil keeps its plates and cards quiet.
            .background(Color.Black.copy(alpha = (if (Gpu.blur) 0.5f else 0.8f) * (1f - slide.value).coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                skipped = true
                step = STEP_END
            }
            .safeDrawingPadding()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .graphicsLayer { translationY = slide.value * size.height * 1.2f }
                .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .gilded(18.dp)
                .padding(horizontal = 26.dp, vertical = 30.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                repeat(3) {
                    // The middle star stands a little taller, as on a medal.
                    Star(
                        it < report.stars,
                        shown = step >= STEP_STARS + it,
                        instant = skipped,
                        modifier = Modifier.padding(bottom = if (it == 1) 12.dp else 0.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // The headline: a win lights up letter by letter; a loss flickers and cools to an ember.
            if (won) {
                NeonOn("EVERY PAIR FOUND", TextGlow, TypeScale.Title, instant = skipped, calm = session.calm)
            } else {
                NeonOff(
                    "THE BULB BURNED OUT",
                    TextGlow,
                    TypeScale.Title,
                    ember = Craft.copperLight,
                    instant = skipped,
                    calm = session.calm,
                    onFizzle = { session.sfx.play(Sound.Buzz, volume = 0.6f, pitch = 0.8f) },
                )
            }
            Spacer(Modifier.height(8.dp))

            // The hero: the score rolls up like an odometer. Nobody wants a giant zero, so a round
            // with no score shows the pairs found instead, dimmer.
            if (round.score > 0) {
                Box(contentAlignment = Alignment.Center) {
                    CountUp(
                        if (step >= STEP_SCORE) round.score else 0,
                        TextGlow,
                        TypeScale.Hero,
                        durationMs = 800,
                        instant = skipped,
                        calm = session.calm,
                    )
                }
                if (report.newBest && step >= STEP_SCORE) {
                    Stamp(
                        instant = skipped,
                        calm = session.calm,
                        delayMs = 750,
                        onLand = {
                            session.sfx.play(Sound.Thunk)
                            session.buzz(HapticFeedbackType.LongPress)
                        },
                    ) { Chip("NEW BEST", Craft.danger, Color.White) }
                }
            } else {
                Text("${round.pairs} / ${round.pairCount} pairs", style = Display, fontSize = TypeScale.Title, color = Dusk.inkSoft)
            }
            // What's next, not a raw number: how far short of the record.
            val short = progress.best - round.score
            if (!report.practice && !report.newBest && round.score > 0 && short > 0) {
                Reveal(step >= STEP_SCORE, skipped) {
                    Text("${short.grouped()} short of your best", style = BodyText, color = Dusk.inkSoft)
                }
            }
            Spacer(Modifier.height(10.dp))

            // The tally: only the lines that say something. With no score the hero already says the
            // pairs, so a round with nothing else to tell has no tally at all.
            val tally = round.score > 0 || round.bestMultiplier >= 2 || round.misses > 0 || report.practice
            if (tally) Reveal(step >= STEP_SCORE, skipped) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .border(1.dp, Craft.goldDeep.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (round.score > 0) Line(Glyph.Moth, "${round.pairs} of ${round.pairCount} pairs", (round.score - timeBonus).takeIf { it > 0 }?.let { "+${it.grouped()}" } ?: "")
                    if (round.bestMultiplier >= 2) Line(Glyph.Flame, "Best streak ×${round.bestMultiplier}", "")
                    if (won && timeBonus > 0) {
                        Line(Glyph.Clock, String.format(Locale.US, "%.1f s left", round.left / 1000f), "+${timeBonus.grouped()}")
                    }
                    if (round.misses > 0) Line(Glyph.Close, if (round.misses == 1) "1 miss" else "${round.misses} misses", "")
                    if (won && round.misses == 0) {
                        Row(Modifier.padding(top = 4.dp)) { Chip("CLEAN!", Craft.goldDeep, Color(0xFFFFF6DC)) }
                    }
                    if (report.practice) Text("Practice round", style = BodyText, color = Dusk.inkSoft, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            Reveal(step >= STEP_XP, skipped) {
                LevelSection(session, report, play = step >= STEP_XP, instant = skipped)
            }

            if (rewards.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    rewards.forEachIndexed { i, reward ->
                        Reveal(step >= STEP_REWARDS, skipped, stamp = true, delayMs = i * 140) {
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

            Spacer(Modifier.height(18.dp))
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
    }
}

/** One tally line: an icon, what happened, and what it was worth (if anything). */
@Composable
private fun Line(icon: Glyph, what: String, worth: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphArt(icon, Craft.brassLight, Modifier.size(18.dp))
        Text(what, style = BodyText, color = Color(0xFFFFF0C8), modifier = Modifier.weight(1f))
        if (worth.isNotEmpty()) Text(worth, style = BodyText, fontWeight = FontWeight.Bold, color = Craft.gold, textAlign = TextAlign.End)
    }
}

/**
 * XP and the level: the emblem with its ring filling, "+40 XP", and what's next. A level-up lifts
 * the emblem, spins it once in 3D, flips the number like a split-flap, empties and refills the
 * ring, throws sparks, and pops on any new tier's wings, rivets and bulb one by one.
 */
@Composable
private fun LevelSection(session: Session, report: Report, play: Boolean, instant: Boolean) {
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
    LaunchedEffect(report, play, instant) {
        if (!play) return@LaunchedEffect
        if (instant || calm) {
            ring.snapTo(levelFill(xpAfter))
            flip.snapTo(1f)
            parts.snapTo(1f)
            lift.snapTo(0f)
            spin.snapTo(0f)
            return@LaunchedEffect
        }
        if (!up) {
            ring.animateTo(levelFill(xpAfter), tween(800, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        ring.animateTo(1f, tween(450))
        launch { lift.animateTo(1f, tween(220)) }
        launch { spin.animateTo(360f, tween(900, easing = FastOutSlowInEasing)) }
        delay(250)
        flip.animateTo(1f, tween(420))
        launch { burst.snapTo(0f); burst.animateTo(1f, tween(650)) }
        session.sfx.play(Sound.LevelUp)
        ring.snapTo(0f)
        launch { ring.animateTo(levelFill(xpAfter), tween(600, easing = FastOutSlowInEasing)) }
        if (newTier) parts.animateTo(1f, tween(900))
        lift.animateTo(0f, spring(dampingRatio = 0.5f))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Emblem(
            report.levelAfter,
            Modifier
                .size(if (up) 96.dp else 72.dp)
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
        Text(
            when {
                report.practice -> "PRACTICE ROUND · NO XP"
                up -> "LEVEL ${report.levelAfter} · +${report.xp} XP"
                else -> "+${report.xp} XP"
            },
            style = Label,
            color = Color(0xFFFFF0C8),
        )
        Text("${xpToGo(xpAfter).grouped()} XP to level ${levelFor(xpAfter) + 1}", style = BodyText, color = Dusk.inkSoft)
    }
}

/** A night's number on a brass seal. */
@Composable
private fun NightSeal(number: Int) {
    Box(Modifier.size(54.dp).metal(corner = 27.dp), contentAlignment = Alignment.Center) {
        Text("$number", style = TextEngraved, fontSize = TypeScale.Title, color = Dusk.paperInk)
    }
}

/** Fades a part of the scorecard in. [stamp] slams it down from large, after [delayMs]. */
@Composable
private fun Reveal(shown: Boolean, instant: Boolean, stamp: Boolean = false, delayMs: Int = 0, content: @Composable () -> Unit) {
    val t by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0)
        else if (delayMs > 0) tween(320, delayMillis = delayMs, easing = FastOutSlowInEasing)
        else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
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
