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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

// The order things are revealed in, one step every 350 ms.
private const val STEP_STARS = 1 // 1, 2, 3: one star each
private const val STEP_SCORE = 4
private const val STEP_XP = 5
private const val STEP_LEVEL = 6
private const val STEP_MOTH = 7
private const val STEP_BADGE = 8
private const val STEP_END = 9

/** The results slide up over the dimmed board. Tapping anywhere skips to the end. */
@Composable
internal fun Results(session: Session, report: Report) {
    val round = session.round
    val won = round.phase == Phase.Won
    val progress = session.store.progress
    var step by remember(report) { mutableIntStateOf(0) }
    var skipped by remember(report) { mutableStateOf(false) }
    val slide = remember(report) { Animatable(1f) }

    LaunchedEffect(report) { slide.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
    LaunchedEffect(report) {
        delay(300)
        while (step < STEP_END) {
            step += 1
            val celebrate = (step == STEP_LEVEL && report.levelAfter > report.levelBefore) ||
                (step == STEP_BADGE && report.newBadges.isNotEmpty())
            if (celebrate && !skipped) {
                session.sfx.play(Sound.LevelUp)
                session.buzz(HapticFeedbackType.Confirm)
            } else if (step in STEP_STARS until STEP_STARS + report.stars && !skipped) {
                session.sfx.play(session.sfx.matchFor(step), volume = 0.6f)
            }
            delay(350)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f * (1f - slide.value)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                skipped = true
                step = STEP_END
            },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = slide.value * size.height }
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Dusk.panel)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (won) "Every pair found" else "The bulb burned out",
                style = Display,
                fontSize = 26.sp,
                color = Dusk.ink,
                textAlign = TextAlign.Center,
            )
            val seconds = String.format(Locale.US, "%.1f", round.left / 1000f)
            Text(
                if (won) "$seconds s to spare · ${round.misses} wrong ${if (round.misses == 1) "pair" else "pairs"}"
                else "${round.pairs} of ${round.pairCount} pairs found",
                color = Dusk.inkSoft,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { Star(it < report.stars, shown = step >= STEP_STARS + it, instant = skipped) }
            }
            Spacer(Modifier.height(10.dp))

            val score by animateIntAsState(
                if (step >= STEP_SCORE) round.score else 0,
                tween(if (skipped) 0 else 700, easing = FastOutSlowInEasing),
                label = "score",
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(score.grouped(), style = Display, fontSize = 46.sp, color = Dusk.amber)
                if (report.newBest && step >= STEP_SCORE) Chip("NEW BEST", Dusk.ember, Dusk.ink)
            }
            Spacer(Modifier.height(10.dp))

            Reveal(step >= STEP_XP, skipped) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("+${report.xp} XP", Dusk.amber, Dusk.paperInk)
                    LevelBar(if (step >= STEP_XP) progress.xp else progress.xp - report.xp, Modifier.fillMaxWidth(), animate = !skipped)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (report.levelAfter > report.levelBefore) {
                    Reveal(step >= STEP_LEVEL, skipped) {
                        Text(
                            "LEVEL UP · LEVEL ${report.levelAfter}",
                            style = Display,
                            fontSize = 18.sp,
                            color = Dusk.paperInk,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Dusk.amber)
                                .padding(vertical = 8.dp),
                        )
                    }
                }
                report.newNight?.let { night ->
                    Reveal(step >= STEP_LEVEL, skipped) {
                        Callout("Night $night is open", nightLine(NIGHTS[night - 1]))
                    }
                }
                report.newMastery?.let { moth ->
                    Reveal(step >= STEP_MOTH, skipped, fromSide = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MothArt(moth, Modifier.size(52.dp), gold = true)
                            Column {
                                Text("${moth.label} turned gold", fontWeight = FontWeight.ExtraBold, color = Dusk.ink, fontSize = 16.sp)
                                Text("Three stars with ${moth.label} leading. Its cards are gold now.", color = Dusk.inkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (round.daily) {
                    Reveal(step >= STEP_XP, skipped) {
                        Callout(
                            if (report.practice) "Practice round" else "Daily porch · ${report.dailyStreak} ${if (report.dailyStreak == 1) "day" else "days"} running",
                            if (report.practice) "Today's scored attempt is already in. Come back tomorrow for a new porch."
                            else "Same porch for everyone today. Share how you did.",
                        )
                    }
                    if (!report.practice) {
                        val context = LocalContext.current
                        SecondaryButton("Share", Modifier.fillMaxWidth()) {
                            val text = dailyShare(todaySeed(), report.stars, round.score)
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, null))
                        }
                    }
                }
                report.newMoths.forEach { moth ->
                    Reveal(step >= STEP_MOTH, skipped, fromSide = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MothArt(moth, Modifier.size(52.dp))
                            Column {
                                Text("${moth.label} joined the porch", fontWeight = FontWeight.ExtraBold, color = Dusk.ink, fontSize = 16.sp)
                                Text("${moth.perkName}: ${moth.perk}", color = Dusk.inkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                }
                report.newBadges.forEach { badge ->
                    Reveal(step >= STEP_BADGE, skipped, stamp = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BadgeMedal(badge, true, Modifier.size(38.dp))
                            Column {
                                Text("Badge: ${badge.title}", fontWeight = FontWeight.ExtraBold, color = Dusk.ink, fontSize = 16.sp)
                                Text(badge.detail, color = Dusk.inkSoft, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Porch", Modifier.weight(1f)) { session.toPorch() }
                PrimaryButton("Again", Modifier.weight(1.4f)) { session.start() }
            }
        }
    }
}

/** Fades a line of the results in. [fromSide] flies it in from the right; [stamp] slams it down. */
@Composable
private fun Reveal(
    shown: Boolean,
    instant: Boolean,
    fromSide: Boolean = false,
    stamp: Boolean = false,
    content: @Composable () -> Unit,
) {
    val t by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0) else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "reveal",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = t.coerceIn(0f, 1f)
            if (fromSide) translationX = (1f - t) * size.width
            if (stamp) {
                val s = 1f + (1f - t) * 1.2f
                scaleX = s
                scaleY = s
            } else if (!fromSide) {
                translationY = (1f - t) * 12.dp.toPx()
            }
        },
    ) { content() }
}

@Composable
private fun Callout(title: String, detail: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Dusk.slot)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(title, fontWeight = FontWeight.ExtraBold, color = Dusk.amber, fontSize = 16.sp)
        Text(detail, color = Dusk.inkSoft, fontSize = 13.sp)
    }
}
