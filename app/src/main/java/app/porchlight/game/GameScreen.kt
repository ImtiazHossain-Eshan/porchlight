package app.porchlight.game

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The porch unlit and lit. Every color on screen comes from one of these. */
private data class Palette(
    val wall: Color,
    val wallLow: Color,
    val rule: Color,
    val ink: Color,
    val inkSoft: Color,
    val plate: Color,
    val plateShade: Color,
    val plateInkSoft: Color,
    val tape: Color,
    val tileDown: Color,
    val tileDownMoth: Color,
    val tileUp: Color,
    val tileMatch: Color,
    val glass: Color,
    val bulbLine: Color,
    val filament: Color,
)

private val UNLIT = Palette(
    wall = Color(0xFF17323A), wallLow = Color(0xFF122930), rule = Color(0xFF2B525B),
    ink = Color(0xFFEDF3EA), inkSoft = Color(0xFFA9C6BE),
    plate = Color(0xFFCBC5B3), plateShade = Color(0xFFA39D8B), plateInkSoft = Color(0xFF4A4538),
    tape = Color(0xFFD7AB0B),
    tileDown = Color(0xFF214851), tileDownMoth = Color(0xFF346670), tileUp = Color(0xFFEFE6CC), tileMatch = Color(0xFFE9CF6A),
    glass = Color(0xFF1F424A), bulbLine = Color(0xFF5F8D8E), filament = Color(0xFF5F8D8E),
)

private val LIT = Palette(
    wall = Color(0xFFBFE3DA), wallLow = Color(0xFFADD7CC), rule = Color(0xFF86BDB1),
    ink = Color(0xFF15181A), inkSoft = Color(0xFF2E4B49),
    plate = Color(0xFFF3ECDB), plateShade = Color(0xFFD2C8B0), plateInkSoft = Color(0xFF5F594A),
    tape = Color(0xFFF4C20D),
    tileDown = Color(0xFF2C6F6C), tileDownMoth = Color(0xFF48928C), tileUp = Color(0xFFFFF7DC), tileMatch = Color(0xFFFFE27A),
    glass = Color(0xFFFFE566), bulbLine = Color(0xFF15181A), filament = Color(0xFFC92F26),
)

private val HOT = Color(0xFFC92F26)
private val HOT_INK = Color(0xFFFFF4EE)
private val PLATE_INK = Color(0xFF15181A)
private val TAPE_INK = Color(0xFF15181A)
private val GOLD = Color(0xFFF4C20D)
private val LOW_WALL = Color(0xFF9FCBC0)

private val Display = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp)
private val Label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp)

private fun now() = SystemClock.elapsedRealtime()

private fun Int.grouped() = String.format(Locale.US, "%,d", this)

/** A score burst that floats up off the board when a pair lands. */
private data class Burst(val id: Int, val points: Int, val multiplier: Int)

@Composable
fun GameScreen(store: Store, sfx: Sfx) {
    val progress = store.progress
    var lead by remember { mutableStateOf(MothId.Wick) }
    var round by remember { mutableStateOf(Round.ready(MothId.Wick)) }
    var report by remember { mutableStateOf<Report?>(null) }
    var burst by remember { mutableStateOf<Burst?>(null) }
    var burstCount by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    sfx.muted = progress.muted

    val lit = round.phase == Phase.Peek || round.phase == Phase.Play || round.phase == Phase.Won
    val low = round.phase == Phase.Play && round.left <= LOW_MS
    val p = if (lit) LIT else UNLIT
    val wall by animateColorAsState(if (low) LOW_WALL else p.wall, tween(if (low) 0 else 450), label = "wall")
    val ink by animateColorAsState(p.ink, tween(450), label = "ink")

    // The peek, the clock, and the bookkeeping when a round ends.
    LaunchedEffect(round.phase) {
        when (round.phase) {
            Phase.Peek -> {
                delay(round.rules.peekMs)
                round = round.go(now())
                sfx.play(Sound.Go)
            }
            Phase.Play -> while (round.phase == Phase.Play) {
                withFrameMillis { }
                round = round.tick(now())
            }
            Phase.Won, Phase.Lost -> {
                val result = store.finish(round, lead)
                report = result
                sfx.play(if (round.phase == Phase.Won) Sound.Win else Sound.Lose)
                if (result.levelAfter > result.levelBefore || result.newBadges.isNotEmpty()) {
                    delay(750)
                    sfx.play(Sound.LevelUp)
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            }
            Phase.Idle -> Unit
        }
    }
    LaunchedEffect(round.miss) {
        if (round.miss) {
            delay(round.rules.flipBackMs)
            round = round.settle()
        }
    }
    LaunchedEffect(round.flashing) {
        if (round.flashing) {
            delay(FLASH_MS)
            round = round.unflash()
        }
    }
    // The last five seconds tick out loud.
    val secondsLeft = ((round.left + 999) / 1000).toInt()
    LaunchedEffect(secondsLeft, round.phase) {
        if (round.phase == Phase.Play && secondsLeft in 1..5) sfx.play(Sound.Tick)
    }

    val pickLead = { id: MothId ->
        if (!progress.unlocked(id)) {
            sfx.play(Sound.Locked)
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            round = round.copy(say = "Reach level ${id.unlockLevel} to bring ${id.label} to the porch.")
        } else if (!round.running) {
            lead = id
            sfx.play(Sound.Flip)
            round = Round.ready(id).copy(say = "${id.label} leads.")
            report = null
        }
    }
    val start = {
        if (!round.running) {
            report = null
            round = Round.start(lead)
            sfx.play(Sound.Start)
        }
    }
    val flip = { index: Int ->
        val before = round
        round = round.flip(index, now())
        when {
            round.pairs > before.pairs -> {
                val multiplier = multiplierAt(before.streak, before.rules)
                sfx.play(sfx.matchFor(multiplier))
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                burstCount += 1
                burst = Burst(burstCount, PAIR_POINTS * multiplier, multiplier)
            }
            round.miss && !before.miss -> {
                sfx.play(Sound.Miss)
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
            round.open.size > before.open.size -> sfx.play(Sound.Flip)
        }
    }

    Box(Modifier.fillMaxSize().background(wall)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TopBar(progress, p, ink) { store.setMuted(!progress.muted) }
            Header(p, ink, lit)
            Readout(round, p, low)

            Box {
                Board(round, p, flip)
                burst?.let { BurstPop(it) { if (burst?.id == it.id) burst = null } }
            }

            report?.let { result ->
                if (round.over) {
                    val reveal = remember(result) { BringIntoViewRequester() }
                    LaunchedEffect(result) {
                        delay(500)
                        reveal.bringIntoView()
                    }
                    Box(Modifier.bringIntoViewRequester(reveal)) { ResultCard(round, result, progress, p) }
                }
            }

            LeadPicker(lead, round.running, progress, p, pickLead)
            Text(
                "${lead.label} leads · ${lead.perkName}. ${lead.perk}",
                color = ink,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )

            if (round.phase == Phase.Play && round.rules.flashes > 0) {
                BigButton(
                    text = if (round.flashes > 0) "Second look" else "Second look used",
                    enabled = round.flashes > 0 && !round.flashing,
                    container = p.plate,
                    content = PLATE_INK,
                    p = p,
                    onClick = {
                        sfx.play(Sound.Go)
                        round = round.flash()
                    },
                )
            } else {
                BigButton(
                    text = when (round.phase) {
                        Phase.Idle -> "Start the round"
                        Phase.Peek -> "Look closely"
                        Phase.Play -> "Round in play"
                        Phase.Won, Phase.Lost -> "Change the bulb"
                    },
                    enabled = !round.running,
                    container = HOT,
                    content = HOT_INK,
                    p = p,
                    onClick = start,
                )
            }

            Text(
                round.say,
                color = ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            Badges(progress, p, ink)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TopBar(progress: Progress, p: Palette, ink: Color, onSound: () -> Unit) {
    val level = progress.level
    val floor = xpFor(level)
    val next = xpFor(level + 1)
    val fill by animateFloatAsState(
        ((progress.xp - floor).toFloat() / (next - floor)).coerceIn(0f, 1f),
        tween(900, easing = FastOutSlowInEasing),
        label = "xp",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(p.tape)
                .semantics { contentDescription = "Level $level" },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LV", style = Label, fontSize = 9.sp, color = TAPE_INK, lineHeight = 9.sp)
                Text("$level", style = Display, fontSize = 18.sp, color = TAPE_INK, lineHeight = 18.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text("PORCHLIGHT", style = Display, fontSize = 14.sp, letterSpacing = 1.sp, color = ink, modifier = Modifier.weight(1f))
                Text("${progress.xp - floor} / ${next - floor} XP", style = Label, color = p.inkSoft)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(p.wallLow),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(RoundedCornerShape(4.dp))
                        .background(p.tape),
                )
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (progress.muted) p.wallLow else p.tape)
                .clickable(role = Role.Switch, onClick = onSound)
                .semantics { stateDescription = if (progress.muted) "Sound off" else "Sound on" }
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Text(
                if (progress.muted) "SOUND OFF" else "SOUND ON",
                style = Label,
                color = if (progress.muted) p.inkSoft else TAPE_INK,
            )
        }
    }
}

@Composable
private fun Header(p: Palette, ink: Color, lit: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Six moths. One bulb.\nBeat the clock.",
            style = Display,
            fontSize = 28.sp,
            lineHeight = 29.sp,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        BulbArt(
            glass = p.glass,
            line = p.bulbLine,
            filament = p.filament,
            socket = p.plateShade,
            modifier = Modifier
                .size(width = 52.dp, height = 74.dp)
                .semantics { contentDescription = if (lit) "Porch light on" else "Porch light off" },
        )
    }
}

@Composable
private fun Readout(round: Round, p: Palette, low: Boolean) {
    val total = round.rules.roundMs.toFloat()
    val seconds = String.format(Locale.US, "%.1f", round.left / 1000f)
    val multiplier = multiplierAt(if (round.running) round.streak else 0, round.rules)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(multiplier) {
        if (multiplier > 1) {
            pulse.snapTo(1.5f)
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.plate)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(
                Modifier
                    .padding(end = 14.dp)
                    .semantics { contentDescription = "${(round.left + 999) / 1000} seconds left" },
            ) {
                Text("BULB", style = Label, color = p.plateInkSoft)
                Text("${seconds}s", style = Display, fontSize = 32.sp, color = if (low) HOT else PLATE_INK)
            }
            Stat("SCORE", round.score.grouped(), p, Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                Text("STREAK", style = Label, color = p.plateInkSoft)
                Text(
                    "×$multiplier",
                    style = Display,
                    fontSize = 20.sp,
                    color = if (multiplier >= MAX_MULTIPLIER) HOT else PLATE_INK,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
                )
            }
            Stat("PAIRS", "${round.pairs}/6", p, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).background(p.plateShade)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((round.left / total).coerceIn(0f, 1f))
                    .background(HOT),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, p: Palette, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = Label, color = p.plateInkSoft)
        Text(value, style = Display, fontSize = 20.sp, color = PLATE_INK)
    }
}

@Composable
private fun Board(round: Round, p: Palette, onFlip: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until 4) {
                    val index = row * 4 + col
                    Card(index, round, p, Modifier.weight(1f), onFlip)
                }
            }
        }
    }
}

@Composable
private fun Card(index: Int, round: Round, p: Palette, modifier: Modifier, onFlip: (Int) -> Unit) {
    val id = round.deck[index]
    val up = round.isFaceUp(index)
    val matched = round.matched[index]
    val missed = round.phase == Phase.Lost && !matched
    val wrong = round.miss && index in round.open
    val turn by animateFloatAsState(if (up) 180f else 0f, tween(320), label = "turn")
    val wings by animateFloatAsState(if (up) 1f else 0.1f, tween(420), label = "wings")
    val pop by animateFloatAsState(
        if (matched) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pop",
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(wrong) {
        if (wrong) {
            for (x in listOf(-7f, 7f, -5f, 5f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    val playable = round.phase == Phase.Play && !matched

    val face = when {
        missed -> p.tileDown
        matched -> p.tileMatch
        wrong -> Color(0xFFF6C7BF)
        else -> p.tileUp
    }

    Box(
        modifier
            .aspectRatio(0.8f)
            .graphicsLayer {
                rotationY = turn
                cameraDistance = 14f * density
                translationX = shake.value * density
                val bump = 1f + 0.06f * sin(pop * Math.PI.toFloat())
                scaleX = bump
                scaleY = bump
            }
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (turn > 90f) face else p.tileDown)
            .then(if (matched) Modifier.border(3.dp, GOLD, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(enabled = playable, role = Role.Button) { onFlip(index) }
            .semantics {
                contentDescription = "Card ${index + 1} of 12, " +
                    if (up) id.label + if (matched) ", matched" else "" else "face down"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (turn > 90f) {
            MothArt(
                id,
                open = wings,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .graphicsLayer {
                        rotationY = 180f
                        alpha = if (missed) 0.55f else 1f
                    },
            )
        } else {
            MothAtRest(p.tileDownMoth, p.tileDown, Modifier.fillMaxSize().padding(12.dp))
        }
    }
}

@Composable
private fun BurstPop(burst: Burst, onDone: () -> Unit) {
    val rise = remember(burst.id) { Animatable(0f) }
    LaunchedEffect(burst.id) {
        rise.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationY = (20f - 50f * rise.value) * density
                alpha = 1f - rise.value * rise.value
                val grow = 0.6f + 0.6f * (1f - (1f - rise.value) * (1f - rise.value)).coerceAtMost(0.7f)
                scaleX = grow
                scaleY = grow
            },
        ) {
            Text(
                "+${burst.points}",
                style = Display,
                fontSize = 44.sp,
                color = HOT,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xF2FFF7DC))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
            )
            if (burst.multiplier > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (burst.multiplier >= MAX_MULTIPLIER) "×${burst.multiplier} OVERDRIVE!" else "×${burst.multiplier} STREAK!",
                    style = Label,
                    fontSize = 14.sp,
                    color = TAPE_INK,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(GOLD)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Star(filled: Boolean, delayMs: Int, p: Palette) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        shown.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Canvas(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = shown.value
                scaleY = shown.value
                rotationZ = (1f - shown.value) * -40f
            },
    ) {
        val c = Offset(size.width / 2, size.height / 2 + size.height * 0.04f)
        val outer = size.minDimension / 2
        val inner = outer * 0.45f
        val star = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outer else inner
                val a = Math.toRadians(-90.0 + i * 36.0)
                val pt = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
            }
            close()
        }
        drawPath(star, if (filled) GOLD else p.plateShade)
        drawPath(star, PLATE_INK, style = Stroke(2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun ResultCard(round: Round, report: Report, progress: Progress, p: Palette) {
    val won = round.phase == Phase.Won
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.plate)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (won) "ALL SIX PAIRS!" else "THE BULB BURNED OUT.",
                    style = Display,
                    fontSize = 22.sp,
                    color = PLATE_INK,
                )
                val seconds = String.format(Locale.US, "%.1f", round.left / 1000f)
                Text(
                    if (won) "$seconds s to spare · ${round.misses} wrong ${if (round.misses == 1) "pair" else "pairs"}" else "${round.pairs} of 6 pairs found",
                    color = p.plateInkSoft,
                    fontSize = 14.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { Star(it < report.stars, 250 + it * 220, p) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                round.score.grouped(),
                style = Display,
                fontSize = 30.sp,
                color = PLATE_INK,
            )
            if (report.newBest) Chip("NEW BEST", HOT, HOT_INK)
            Spacer(Modifier.weight(1f))
            Chip("+${report.xp} XP", GOLD, TAPE_INK)
        }

        if (report.levelAfter > report.levelBefore) {
            Text("Level up! You're now level ${report.levelAfter}.", fontWeight = FontWeight.ExtraBold, color = PLATE_INK)
        }
        report.newMoths.forEach { moth ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MothArt(moth, Modifier.size(40.dp))
                Text(
                    "${moth.label} joined the porch: ${moth.perkName}.",
                    fontWeight = FontWeight.Bold,
                    color = PLATE_INK,
                    fontSize = 15.sp,
                )
            }
        }
        report.newBadges.forEach { badge ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BadgeMedal(true, p, Modifier.size(32.dp))
                Text("Badge: ${badge.title}", fontWeight = FontWeight.Bold, color = PLATE_INK, fontSize = 15.sp)
            }
        }
        if (report.levelAfter == report.levelBefore) {
            val needed = xpFor(progress.level + 1) - progress.xp
            Text("$needed XP to level ${progress.level + 1}.", color = p.plateInkSoft, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Chip(text: String, background: Color, color: Color) {
    Text(
        text,
        style = Label,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun LeadPicker(lead: MothId, running: Boolean, progress: Progress, p: Palette, onPick: (MothId) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MothId.entries.forEach { id ->
            val chosen = id == lead
            val open = progress.unlocked(id)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        translationY = if (chosen) -3.dp.toPx() else 0f
                        alpha = if (chosen || !open) 1f else 0.62f
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (chosen) p.tape else if (open) p.tileUp else p.wallLow)
                    .clickable(enabled = !running, role = Role.RadioButton) { onPick(id) }
                    .semantics {
                        contentDescription = if (open) "${id.label}: ${id.perkName}" else "${id.label}, locked until level ${id.unlockLevel}"
                        stateDescription = if (chosen) "Leading" else "Not leading"
                    }
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (open) {
                    MothArt(id, Modifier.fillMaxSize())
                } else {
                    MothAtRest(p.rule, p.wallLow, Modifier.fillMaxSize().padding(4.dp))
                    Text(
                        "LV ${id.unlockLevel}",
                        style = Label,
                        fontSize = 10.sp,
                        color = TAPE_INK,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(2.dp))
                            .background(p.tape)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgeMedal(earned: Boolean, p: Palette, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(if (earned) GOLD else p.wallLow, r, c)
        drawCircle(if (earned) PLATE_INK else p.rule, r, c, style = Stroke(2.dp.toPx()))
        if (earned) {
            val tick = Path().apply {
                moveTo(c.x - r * 0.38f, c.y + r * 0.02f)
                lineTo(c.x - r * 0.08f, c.y + r * 0.32f)
                lineTo(c.x + r * 0.42f, c.y - r * 0.3f)
            }
            drawPath(tick, PLATE_INK, style = Stroke(r * 0.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}

@Composable
private fun Badges(progress: Progress, p: Palette, ink: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Badges", style = Display, fontSize = 20.sp, color = ink, modifier = Modifier.weight(1f))
            Text(
                "${progress.badges.size}/${Badge.entries.size} · best ${progress.best.grouped()} · ${progress.rounds} ${if (progress.rounds == 1) "round" else "rounds"}",
                color = p.inkSoft,
                fontSize = 13.sp,
            )
        }
        Badge.entries.forEach { badge ->
            val earned = badge in progress.badges
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BadgeMedal(earned, p, Modifier.size(28.dp))
                Column {
                    Text(badge.title, fontWeight = FontWeight.ExtraBold, color = if (earned) ink else p.inkSoft, fontSize = 15.sp)
                    Text(badge.detail, color = p.inkSoft, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun BigButton(
    text: String,
    enabled: Boolean,
    container: Color,
    content: Color,
    p: Palette,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = p.rule,
            disabledContentColor = p.ink,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

