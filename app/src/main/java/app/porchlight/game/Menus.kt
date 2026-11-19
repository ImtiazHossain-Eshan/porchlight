package app.porchlight.game

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import kotlin.math.sin
import kotlin.math.abs
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 20261114 -> "14 Nov" */
internal fun dayLabel(seed: Long): String = "${seed % 100} ${MONTHS[((seed / 100 % 100).toInt() - 1).coerceIn(0, 11)]}"

/**
 * The frame every menu screen shares: a brass back button, the screen's name carved on a hanging
 * oak plaque, and the content below, kept to a readable width on tablets.
 */
@Composable
internal fun MenuFrame(title: String, session: Session, scroll: Boolean = true, maxContentWidth: Dp = 560.dp, flight: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            BrassButton(Glyph.Left, "Back to the porch", enabled = true, modifier = Modifier.align(Alignment.CenterStart)) { session.toPorch() }
            // The plaque keeps clear of the back button on both sides, so it stays centred.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
                    .flies(flight, Fly.Bounds)
                    .plank(Wood.Oak, corner = 10.dp, elevation = 6.dp, seed = title.length)
                    .border(1.5.dp, BrassRule, RoundedCornerShape(10.dp))
                    .padding(horizontal = 22.dp, vertical = 9.dp)
                    .semantics { heading() },
            ) {
                FitText(title.uppercase(), TextCarved, TypeScale.Title, Dusk.paperInk)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .widthIn(max = maxContentWidth)
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

// ---------------------------------------------------------------------------------------------
// The daily porch: today's calendar leaf, the week so far, and the player's own record.

@Composable
internal fun DailyScreen(session: Session) {
    val progress = session.store.progress
    val today = remember { todaySeed() }
    val played = progress.playedDaily(today)
    val context = LocalContext.current
    val alive = played || progress.dailyDay == todaySeed(-1)
    MenuFrame("Daily porch", session, flight = "plaque-Daily") {
        Row(
            Modifier
                .fillMaxWidth()
                .plank(Wood.Walnut, seed = 9)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CalendarLeaf(today, Modifier.size(92.dp, 108.dp))
            Column(Modifier.weight(1f)) {
                Text("Today's porch", style = BodyText, fontWeight = FontWeight.Bold, fontSize = TypeScale.Heading, color = Dusk.amber)
                Spacer(Modifier.height(4.dp))
                Text("The same 16 cards for everyone today. You get one scored try.", color = Dusk.ink, fontSize = TypeScale.Body, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        // The week: a brass stud with its stars for a played day, an empty socket for a missed one,
        // and a glowing ring around today.
        Row(
            Modifier
                .fillMaxWidth()
                .plank(Wood.Ebony, seed = 11)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (ago in 6 downTo 0) {
                val seed = todaySeed(-ago)
                val entry = progress.dailyLog.lastOrNull { it.day == seed }
                val isToday = ago == 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // A played day is a brass coin with its stars cut in; a missed day is an iron
                    // coin pressed into the plank; today is ringed in light (gold once played).
                    Box(
                        Modifier
                            .size(42.dp)
                            .drawBehind {
                                if (isToday) todayGlow()
                                val (c, rr) = coinFace(size)
                                coin(c, rr, press = if (entry == null && !isToday) 0.8f else 0f, dull = entry == null, gold = isToday && entry != null)
                                if (isToday && entry == null) {
                                    drawCircle(Craft.gold, rr * 1.02f, Offset(c.x, c.y), style = Stroke(2.5.dp.toPx()))
                                }
                            }
                            .semantics {
                                contentDescription = dayLabel(seed) + when {
                                    entry != null -> ", ${entry.stars} stars"
                                    isToday -> ", today, not played yet"
                                    else -> ", missed"
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (entry != null) {
                            Text("${entry.stars}", style = TextEngraved, fontSize = TypeScale.Heading, color = Color(0xFF3E2810), modifier = Modifier.offset(y = (-2).dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${seed % 100}", style = Label, color = if (isToday) Dusk.amber else Dusk.inkSoft)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Figure("STREAK", if (alive) "${progress.dailyStreak}" else "0", if (alive && progress.dailyStreak == 1) "day" else "days", Modifier.weight(1f))
            Figure("TODAY", if (played) progress.dailyScore.grouped() else "—", if (played) "${progress.dailyStars} of 3 stars" else "not played", Modifier.weight(1f))
            Figure("BEST", (progress.dailyLog.maxOfOrNull { it.score } ?: 0).grouped(), "your record", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        // The player's own best days. The game has no server, so there is nobody else to rank.
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(5.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(8.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("YOUR BEST DAYS", style = Label, color = Craft.parchmentInkSoft)
            Spacer(Modifier.height(6.dp))
            val best = progress.dailyLog.sortedByDescending { it.score }.take(3)
            if (best.isEmpty()) {
                Text("Nothing here yet. Play today's porch to start your record.", style = TextInk, color = Craft.parchmentInk)
            }
            best.forEachIndexed { i, entry ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = TextInk, fontWeight = FontWeight.Bold, color = Craft.parchmentInkSoft, modifier = Modifier.width(26.dp))
                    Text(dayLabel(entry.day), style = TextInk, color = Craft.parchmentInk, modifier = Modifier.weight(1f))
                    Row {
                        repeat(3) { GlyphArt(Glyph.Star, if (it < entry.stars) Craft.goldDeep else Craft.parchmentShade, Modifier.size(14.dp)) }
                    }
                    Text(
                        entry.score.grouped(),
                        style = TextInk,
                        fontWeight = FontWeight.Bold,
                        color = Craft.parchmentInk,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(76.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        PlayButton(if (played) "Practise" else "Play today", session.calm, Modifier.fillMaxWidth()) { session.start(daily = true) }
        if (played) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Share today's porch", Modifier.fillMaxWidth()) {
                // The card needs today's round; a day played before cards existed shares the text line.
                val card = progress.dailyCard?.takeIf { it.day == today }
                if (card != null) session.sharing = card
                else shareText(context, dailyShare(today, progress.dailyStars, progress.dailyScore))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A number on a small ebony plate, with what it counts above and its unit below. */
@Composable
private fun Figure(label: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier
            .plate()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = Label, color = Dusk.inkSoft)
        Text(value, style = BodyText, fontWeight = FontWeight.Bold, fontSize = TypeScale.Heading, color = Dusk.amber, maxLines = 1)
        Text(unit, style = BodyText, color = Dusk.inkSoft, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** A tear-off calendar leaf showing a date: two brass rings, a copper band with the month, the day below. */
@Composable
private fun CalendarLeaf(seed: Long, modifier: Modifier) {
    Box(modifier.semantics { contentDescription = dayLabel(seed) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val top = size.height * 0.1f
            val corner = CornerRadius(6.dp.toPx())
            drawRoundRect(Dusk.shadow, Offset(2.dp.toPx(), top + 4.dp.toPx()), Size(size.width, size.height - top), corner, alpha = 0.5f)
            drawRoundRect(Craft.parchmentShade, Offset(0f, top + 3.dp.toPx()), Size(size.width, size.height - top - 3.dp.toPx()), corner)
            drawRoundRect(Craft.parchment, Offset(0f, top), Size(size.width, size.height - top - 5.dp.toPx()), corner)
            drawRoundRect(Craft.copper, Offset(0f, top), Size(size.width, size.height * 0.26f), corner)
            drawRect(Craft.copper, Offset(0f, top + size.height * 0.16f), Size(size.width, size.height * 0.1f))
            for (x in listOf(0.28f, 0.72f)) {
                drawLine(Craft.brassDark, Offset(size.width * x, 0f), Offset(size.width * x, top + 8.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
                drawLine(Craft.brassLight, Offset(size.width * x, 2f), Offset(size.width * x, top + 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        // Each line sits in its own part of the leaf, measured from the drawing: the month centred
        // in the copper band below the binder rings, the day centred on the paper beneath.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val top = maxHeight * 0.1f
            val band = maxHeight * 0.26f
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = top + 6.dp)
                    .height(band - 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    MONTHS[((seed / 100 % 100).toInt() - 1).coerceIn(0, 11)].uppercase(),
                    style = BodyText.copy(lineHeight = TypeScale.Body),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = top + band)
                    .height(maxHeight - top - band - 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${seed % 100}", style = Display, fontSize = TypeScale.Title, color = Craft.parchmentInk, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lead moths: each in a glass jar on velvet, with what it is good at.

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MothsScreen(session: Session) {
    val progress = session.store.progress
    MenuFrame("Lead moths", session, flight = "plaque-Moths") {
        LEADS.forEach { moth ->
            val open = progress.unlocked(moth)
            val leading = moth == session.lead
            val gold = moth in progress.mastered
            val stats = leadStats(moth)
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                    .velvet(14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(if (leading) 2.5.dp else 1.5.dp, if (leading) androidx.compose.ui.graphics.SolidColor(Craft.gold) else BrassRule, RoundedCornerShape(14.dp))
                    .clickable(role = Role.RadioButton) { session.pickLead(moth) }
                    .semantics {
                        contentDescription = if (open) "${moth.label}, ${moth.role}. ${moth.perkName}: ${moth.perk}"
                        else "${moth.label}, locked. Reach level ${moth.unlockLevel}."
                        stateDescription = if (leading) "Leading" else "Not leading"
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ShowcaseJar(moth, open, gold, session.clock, session.calm, Modifier.size(84.dp, 118.dp))
                Column(Modifier.weight(1f)) {
                    // The name and its chips wrap onto two lines when a big font leaves no room.
                    FlowRow(
                        itemVerticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(moth.label, style = BodyText, fontWeight = FontWeight.Bold, fontSize = TypeScale.Heading, color = if (open) Dusk.amber else Dusk.inkSoft)
                        if (leading) Chip("LEADING", Craft.gold, Dusk.paperInk)
                        if (gold) Chip("GOLD", Craft.goldDeep, Color(0xFFFFF6DC))
                    }
                    if (open) {
                        Text("${moth.role} · ${moth.perkName}", color = Dusk.inkSoft, fontSize = TypeScale.Body)
                        Spacer(Modifier.height(6.dp))
                        Pips("TIME", stats.time)
                        Pips("LOOK", stats.look)
                        Pips("STREAK", stats.streak)
                        Spacer(Modifier.height(6.dp))
                        Text(moth.perk, color = Dusk.ink, fontSize = TypeScale.Body, lineHeight = 20.sp)
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Emblem(moth.unlockLevel, Modifier.size(34.dp), still = true)
                            val toGo = (xpFor(moth.unlockLevel) - progress.xp).coerceAtLeast(0)
                            Text("${toGo.grouped()} XP to meet ${moth.label}", color = Dusk.ink, fontSize = TypeScale.Body)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** A strength as three brass pips. */
@Composable
private fun Pips(label: String, value: Int) {
    Row(
        Modifier.padding(vertical = 2.dp).semantics(mergeDescendants = true) { contentDescription = "$label $value of 3" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Label, color = Dusk.inkSoft, softWrap = false, modifier = Modifier.widthIn(min = 58.dp).padding(end = 6.dp))
        Canvas(Modifier.size(66.dp, 10.dp)) {
            for (i in 0 until 3) {
                val x = 5.dp.toPx() + i * 22.dp.toPx()
                drawRoundRect(
                    if (i < value) Craft.brassLight else Craft.velvetDeep,
                    Offset(x - 5.dp.toPx(), 1.dp.toPx()),
                    Size(18.dp.toPx(), 8.dp.toPx()),
                    CornerRadius(4.dp.toPx()),
                )
                drawRoundRect(
                    Craft.brassDark,
                    Offset(x - 5.dp.toPx(), 1.dp.toPx()),
                    Size(18.dp.toPx(), 8.dp.toPx()),
                    CornerRadius(4.dp.toPx()),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
    }
}

/** A specimen jar: a walnut base, a brass lid, and the moth behind thick glass. Locked, it holds a shadow. */
@Composable
private fun ShowcaseJar(moth: MothId, open: Boolean, gold: Boolean, clock: Clock, calm: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // The base.
            drawRoundRect(Wood.Walnut.dark, Offset(w * 0.04f, h * 0.88f), Size(w * 0.92f, h * 0.12f), CornerRadius(4.dp.toPx()))
            drawRoundRect(Wood.Walnut.light, Offset(w * 0.04f, h * 0.88f), Size(w * 0.92f, h * 0.05f), CornerRadius(4.dp.toPx()))
            // The glass, pale against the velvet.
            drawRoundRect(Color(0xFFBFE2F0), Offset(w * 0.1f, h * 0.14f), Size(w * 0.8f, h * 0.76f), CornerRadius(w * 0.22f), alpha = 0.16f)
        }
        // A met moth is alive in its jar: its wings beat in depth (they fold towards you and
        // open again), and it turns slowly as if hovering. Each jar keeps its own rhythm.
        // Everything is read while drawing, so the list never recomposes for it.
        val phase = moth.ordinal * 1.7f
        Canvas(
            Modifier
                .fillMaxWidth(0.72f)
                .height(72.dp)
                .offset(y = 6.dp)
                // The same moth that rests on the porch flies in here.
                .flies("moth-${moth.name}", Fly.Arc)
                .graphicsLayer {
                    if (open && !calm) {
                        val t = clock.seconds
                        cameraDistance = 10f * density
                        rotationY = 22f * sin(t * 0.9f + phase)
                        rotationX = 8f * sin(t * 1.3f + phase)
                        translationY = 3.dp.toPx() * sin(t * 1.9f + phase)
                    }
                },
        ) {
            val t = clock.seconds
            // Quick beats in bursts, then a glide with the wings nearly open.
            val burst = (sin(t * 0.8f + phase) + 1f) / 2f
            val beat = if (open && !calm) abs(sin(t * 9f + phase)) else 1f
            val wing = 1f - (0.75f * burst) * (1f - beat)
            inUnitSquare { mothUnit(moth, wing, if (open) null else Color(0xFF1A0B18), gold, if (open) Wear.moths[moth] else null) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val glass = CornerRadius(w * 0.22f)
            drawRoundRect(Color(0xFFD6EEF8), Offset(w * 0.1f, h * 0.14f), Size(w * 0.8f, h * 0.76f), glass, alpha = 0.7f, style = Stroke(2.dp.toPx()))
            drawLine(Color.White, Offset(w * 0.2f, h * 0.28f), Offset(w * 0.2f, h * 0.7f), 3.dp.toPx(), StrokeCap.Round, alpha = 0.35f)
            drawLine(Color.White, Offset(w * 0.8f, h * 0.26f), Offset(w * 0.8f, h * 0.38f), 2.dp.toPx(), StrokeCap.Round, alpha = 0.25f)
            // The lid.
            drawRoundRect(Craft.brass, Offset(w * 0.2f, h * 0.04f), Size(w * 0.6f, h * 0.13f), CornerRadius(3.dp.toPx()))
            drawRect(Craft.brassLight, Offset(w * 0.28f, h * 0.05f), Size(w * 0.08f, h * 0.11f), alpha = 0.6f)
            drawLine(Craft.brassDark, Offset(w * 0.2f, h * 0.105f), Offset(w * 0.8f, h * 0.105f), 1.5.dp.toPx())
            drawRoundRect(Craft.brassDark, Offset(w * 0.2f, h * 0.04f), Size(w * 0.6f, h * 0.13f), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Badges: twelve castings on a walnut board. Tap one to read how it is earned.

@Composable
internal fun BadgesScreen(session: Session) {
    val progress = session.store.progress
    // Badges earned since they were last looked at: they stamp in here and wear a glowing dot.
    val fresh = remember { progress.badges - session.seenBadges }
    DisposableEffect(Unit) { onDispose { session.seenBadges = session.store.progress.badges } }
    var picked by remember { mutableStateOf(fresh.firstOrNull() ?: Badge.entries.firstOrNull { it in progress.badges } ?: Badge.FirstLight) }
    val tilt = session.tilt
    MenuFrame("Badges", session, flight = "plaque-Badges") {
        val toGo = Badge.entries.size - progress.badges.size
        Text(if (toGo == 0) "ALL ${Badge.entries.size} EARNED" else "$toGo ${if (toGo == 1) "BADGE" else "BADGES"} TO GO", style = Label, color = Dusk.inkSoft)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .plank(Wood.Walnut, seed = 13)
                .padding(vertical = 14.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Badge.entries.chunked(3).forEach { row ->
                Row {
                    row.forEach { badge ->
                        val earned = badge in progress.badges
                        val chosen = badge == picked
                        val new = badge in fresh
                        val grow by animateFloatAsState(if (chosen) 1.1f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "grow")
                        // A newly earned badge stamps in, a shine crosses it, and its ribbon unfurls.
                        val shine = remember { Animatable(if (new && !session.calm) 0f else 1f) }
                        val ribbon = remember { Animatable(if (new && !session.calm) 0f else 1f) }
                        if (new && !session.calm) {
                            LaunchedEffect(Unit) {
                                val order = fresh.toList().indexOf(badge)
                                delay(260L + order * 380L)
                                shine.animateTo(1f, tween(520))
                                ribbon.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Tab,
                                ) {
                                    picked = badge
                                    session.sfx.play(Sound.Flip, vary = 0.06f)
                                }
                                .semantics {
                                    contentDescription = "${badge.title}. ${badge.detail} " + when {
                                        new -> "Newly earned, ${badge.metal.name.lowercase()}."
                                        earned -> "Earned, ${badge.metal.name.lowercase()}."
                                        else -> "Not earned yet."
                                    }
                                    stateDescription = if (chosen) "Selected" else "Not selected"
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Stamp(
                                    instant = !new,
                                    calm = session.calm,
                                    delayMs = fresh.toList().indexOf(badge).coerceAtLeast(0) * 380L,
                                    onLand = { session.sfx.play(Sound.Thunk, volume = 0.7f) },
                                ) {
                                    BadgeMedal(
                                        badge,
                                        earned,
                                        Modifier
                                            .size(64.dp)
                                            .graphicsLayer {
                                                scaleX = grow
                                                scaleY = grow
                                                // Each medal leans a little with the phone, like the cards.
                                                cameraDistance = 12f * density
                                                rotationY = tilt.x * 14f
                                                rotationX = -tilt.y * 14f
                                            },
                                        shine = if (shine.value < 1f) shine.value else -1f,
                                    )
                                }
                                if (new) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(12.dp)
                                            .drawBehind {
                                                drawCircle(Brush.radialGradient(listOf(Dusk.glow, Color.Transparent)), size.minDimension)
                                                drawCircle(Dusk.glow, size.minDimension * 0.3f)
                                            },
                                    )
                                }
                            }
                            if (earned) TierRibbon(badge.metal, Modifier.size(60.dp, 7.dp), open = ribbon.value) else Spacer(Modifier.height(7.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                badge.title.uppercase(),
                                style = Label,
                                color = if (chosen) Dusk.amber else if (earned) Dusk.ink else Dusk.inkSoft,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                // A gutter each side, so two neighbouring names never read as one.
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(5.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(8.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BadgeMedal(picked, picked in progress.badges, Modifier.size(52.dp))
            Column(Modifier.weight(1f)) {
                Text(picked.title, style = TextInk, fontWeight = FontWeight.Bold, fontSize = TypeScale.Heading, color = Craft.parchmentInk)
                Text(picked.detail, style = TextInk, color = Craft.parchmentInk, lineHeight = 20.sp)
                Text(
                    if (picked in progress.badges) "EARNED · ${picked.metal.name.uppercase()}" else "NOT EARNED YET",
                    style = Label,
                    color = Craft.parchmentInkSoft,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Settings: a walnut switchboard of brass levers.

@Composable
internal fun SettingsScreen(session: Session) {
    val progress = session.store.progress
    val store = session.store
    MenuFrame("Settings", session) {
        Column(
            Modifier
                .fillMaxWidth()
                .plank(Wood.Walnut, seed = 17)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Lever(Glyph.Sound, "Sound effects", "Flips, chimes and the bulb.", !progress.muted) { store.setMuted(!progress.muted) }
            Groove()
            Lever(Glyph.Cricket, "Night sounds", "Crickets and the hum of the bulb.", progress.music) { store.change { it.copy(music = !it.music) } }
            Groove()
            Lever(Glyph.Buzz, "Vibration", "A buzz on matches and misses.", progress.haptics) { store.change { it.copy(haptics = !it.haptics) } }
            Groove()
            Lever(Glyph.Motion, "Reduce motion", "No shaking, tilting or sparks. Colours still change.", progress.reduceMotion) {
                store.change { it.copy(reduceMotion = !it.reduceMotion) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Replay the tutorial", Modifier.fillMaxWidth()) { session.startTutorial() }
        Spacer(Modifier.height(14.dp))
        Text(
            "Porchlight keeps everything on this phone. No account, no adverts, no network.",
            color = Dusk.inkSoft,
            fontSize = TypeScale.Body,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Lever(glyph: Glyph, title: String, detail: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphArt(glyph, if (on) Craft.brassLight else Dusk.locked, Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Dusk.ink, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Dusk.inkSoft, fontSize = TypeScale.Body, lineHeight = 19.sp)
        }
        LeverSwitch(on, title, onToggle = onToggle)
    }
}

/** A groove cut between rows of the switchboard: a dark line with a light edge under it. */
@Composable
private fun Groove() {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(Dusk.shadow, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), alpha = 0.7f)
        drawLine(Wood.Walnut.light, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 1.dp.toPx(), alpha = 0.6f)
    }
}

/** A soft lamplight halo behind today's stud, drawn with a plain gradient so it shows on every API level. */
private fun DrawScope.todayGlow() {
    val r = size.minDimension / 2f
    drawCircle(
        Brush.radialGradient(
            0.55f to Craft.gold.copy(alpha = 0.55f),
            1f to Color.Transparent,
            center = center,
            radius = r * 1.75f,
        ),
        radius = r * 1.75f,
    )
}

