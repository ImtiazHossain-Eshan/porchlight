package app.porchlight.game

import android.content.Intent
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
                add(Reward(badge.title, badge.detail) { BadgeMedal(badge, true, Modifier.size(54.dp)) })
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
            Text(
                if (won) "EVERY PAIR FOUND" else "THE BULB BURNED OUT",
                style = Display,
                fontSize = 17.sp,
                letterSpacing = 2.sp,
                color = Color(0xFFFFF0C8),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            val score by animateIntAsState(
                if (step >= STEP_SCORE) round.score else 0,
                tween(if (skipped) 0 else 700, easing = FastOutSlowInEasing),
                label = "score",
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    score.grouped(),
                    style = Display.copy(shadow = Shadow(Craft.goldDeep, Offset(0f, 3f), 0f)),
                    fontSize = 44.sp,
                    color = Craft.gold,
                    modifier = Modifier.semantics { contentDescription = "Score ${round.score}" },
                )
                if (report.newBest && step >= STEP_SCORE) Chip("NEW BEST", Craft.danger, Color.White)
            }
            // The record sits under the score it is measured against, not in the bonus table.
            if (!report.practice && !report.newBest) {
                Reveal(step >= STEP_SCORE, skipped) {
                    Text(
                        "Best ${progress.best.grouped()}",
                        style = Label,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFD9CDB4),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // The bonus table, inked on a strip of the panel.
            Reveal(step >= STEP_SCORE, skipped) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .border(1.dp, Craft.goldDeep.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Line("Pairs found", "${round.pairs} of ${round.pairCount}", (round.score - timeBonus).grouped())
                    Line(
                        "Time left",
                        if (won) String.format(Locale.US, "%.1f s", round.left / 1000f) else "none",
                        "+${timeBonus.grouped()}",
                    )
                    Line("Wrong pairs", "${round.misses}", if (report.practice) "practice" else "")
                }
            }
            Spacer(Modifier.height(12.dp))

            Reveal(step >= STEP_XP, skipped) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (report.levelAfter > report.levelBefore) {
                        // Levelling up is an event: the old number gives way to the new one.
                        val flip by animateFloatAsState(
                            if (step >= STEP_XP) 1f else 0f,
                            if (skipped) tween(0) else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
                            label = "flip",
                        )
                        Row(
                            Modifier.semantics(mergeDescendants = true) { contentDescription = "Level up: level ${report.levelAfter}" },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${report.levelBefore}", style = Display, fontSize = 24.sp, color = Color(0xFF8C97A6))
                            GlyphArt(Glyph.Right, Craft.gold, Modifier.size(20.dp))
                            Text(
                                "${report.levelAfter}",
                                style = Display.copy(shadow = Shadow(Craft.gold.copy(alpha = 0.8f), Offset.Zero, 24f)),
                                fontSize = 40.sp,
                                color = Craft.gold,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = 0.4f + 0.6f * flip
                                    scaleY = 0.4f + 0.6f * flip
                                },
                            )
                        }
                    }
                    Text(
                        when {
                            report.practice -> "PRACTICE ROUND · NO XP"
                            report.levelAfter > report.levelBefore -> "LEVEL UP · +${report.xp} XP"
                            else -> "+${report.xp} XP"
                        },
                        style = Display,
                        fontSize = 14.sp,
                        letterSpacing = 2.sp,
                        color = Color(0xFFFFF0C8),
                    )
                    LevelBar(if (step >= STEP_XP) progress.xp else progress.xp - report.xp, Modifier.fillMaxWidth(), animate = !skipped)
                }
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
                                    .width(118.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.28f))
                                    .border(1.dp, Craft.goldDeep.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                                    .semantics(mergeDescendants = true) {},
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                reward.art()
                                Spacer(Modifier.height(6.dp))
                                Text(reward.title, style = Label, fontSize = 10.sp, color = Craft.gold, textAlign = TextAlign.Center, maxLines = 2)
                                Text(reward.detail, color = Color(0xFFD9CDB4), fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 3)
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
                    color = Color(0xFFD9CDB4),
                    fontSize = 13.sp,
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
                    val text = dailyShare(todaySeed(), report.stars, round.score)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }
}

/** One row of the bonus table: what, how much of it, and what it was worth. */
@Composable
private fun Line(label: String, amount: String, worth: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFD9CDB4), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(amount, color = Color(0xFFFFF0C8), fontSize = 14.sp, modifier = Modifier.padding(end = 14.dp))
        Text(worth, style = Display, fontSize = 14.sp, color = Craft.gold, textAlign = TextAlign.End, modifier = Modifier.width(92.dp))
    }
}

/** A night's number on a brass seal. */
@Composable
private fun NightSeal(number: Int) {
    Box(Modifier.size(54.dp).metal(corner = 27.dp), contentAlignment = Alignment.Center) {
        Text("$number", style = Carved, fontSize = 24.sp, color = Dusk.paperInk)
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
