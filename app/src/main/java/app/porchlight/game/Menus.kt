package app.porchlight.game

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
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
internal fun MenuFrame(title: String, session: Session, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            BrassButton(Glyph.Left, "Back to the porch", enabled = true, modifier = Modifier.align(Alignment.CenterStart)) { session.toPorch() }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .plank(Wood.Oak, corner = 10.dp, elevation = 6.dp, seed = title.length)
                    .border(1.5.dp, BrassRule, RoundedCornerShape(10.dp))
                    .padding(horizontal = 22.dp, vertical = 9.dp)
                    .semantics { heading() },
            ) {
                Text(title.uppercase(), style = Carved, fontSize = 17.sp, color = Dusk.paperInk, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

// ---------------------------------------------------------------------------------------------
// Six nights: a parchment map laid over brass works, with a copper pipe running night to night.

private val STOPS = listOf(
    Offset(0.27f, 0.10f), Offset(0.74f, 0.24f), Offset(0.30f, 0.41f),
    Offset(0.73f, 0.57f), Offset(0.28f, 0.74f), Offset(0.70f, 0.90f),
)

@Composable
internal fun NightsScreen(session: Session) {
    val progress = session.store.progress
    val picked = NIGHTS[progress.night - 1]
    var lockedHint by remember { mutableStateOf<String?>(null) }
    MenuFrame("Six nights", session, scroll = false) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .drawBehind {
                    // The works the map is pinned over: gears peeking out at the corners.
                    val t = if (session.calm) 0f else session.clock.seconds * 8f
                    gear(Offset(size.width - 6.dp.toPx(), 18.dp.toPx()), 34.dp.toPx(), 12, t)
                    gear(Offset(2.dp.toPx(), size.height - 30.dp.toPx()), 40.dp.toPx(), 14, -t * 0.8f, copper = true)
                    gear(Offset(size.width - 20.dp.toPx(), size.height - 6.dp.toPx()), 24.dp.toPx(), 9, t * 1.5f)
                }
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(8.dp),
        ) {
            val w = maxWidth
            val h = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                // The pipe: copper as far as the player has got, a faint inked route beyond.
                for (i in 0 until STOPS.size - 1) {
                    val a = Offset(STOPS[i].x * size.width, STOPS[i].y * size.height)
                    val b = Offset(STOPS[i + 1].x * size.width, STOPS[i + 1].y * size.height)
                    val run = Path().apply {
                        moveTo(a.x, a.y)
                        cubicTo(a.x, a.y + (b.y - a.y) * 0.7f, b.x, b.y - (b.y - a.y) * 0.7f, b.x, b.y)
                    }
                    if (i + 2 <= progress.nights) {
                        pipe(run, 9.dp.toPx())
                    } else {
                        drawPath(
                            run,
                            Craft.parchmentInkSoft,
                            alpha = 0.55f,
                            style = Stroke(
                                2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                            ),
                        )
                    }
                }
                // Brass pins holding the sheet down.
                val pin = 3.dp.toPx()
                for (corner in listOf(Offset(12f, 12f), Offset(size.width - 12f, 12f), Offset(12f, size.height - 12f), Offset(size.width - 12f, size.height - 12f))) {
                    rivet(Offset(corner.x.coerceIn(pin * 3, size.width - pin * 3), corner.y.coerceIn(pin * 3, size.height - pin * 3)), pin)
                }
            }
            NIGHTS.forEachIndexed { i, night ->
                val open = night.number <= progress.nights
                val chosen = night.number == progress.night
                val grow by animateFloatAsState(if (chosen) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "grow")
                val size = 60.dp
                Column(
                    Modifier
                        .offset(w * STOPS[i].x - 45.dp, h * STOPS[i].y - size / 2)
                        .width(90.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                        ) {
                            if (open) {
                                lockedHint = null
                                session.sfx.play(Sound.Flip, vary = 0.06f)
                                session.store.change { it.copy(night = night.number) }
                            } else {
                                lockedHint = "Night ${night.number} is locked: win two stars on night ${night.number - 1}."
                                session.sfx.play(Sound.Locked)
                            }
                        }
                        .semantics {
                            contentDescription = if (open) "Night ${night.number}, ${nightLine(night)}, best ${progress.nightStars[i]} stars"
                            else "Night ${night.number}, locked. Win two stars on night ${night.number - 1}."
                            stateDescription = if (chosen) "Selected" else "Not selected"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(size)
                            .graphicsLayer {
                                scaleX = grow
                                scaleY = grow
                            }
                            .shadow(if (chosen) 10.dp else 4.dp, CircleShape, ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                            .metal(corner = size / 2, dull = !open)
                            .then(if (chosen) Modifier.border(3.dp, Craft.gold, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (open) {
                            Text("${night.number}", style = Carved, fontSize = 24.sp, color = Dusk.paperInk)
                        } else {
                            GlyphArt(Glyph.Lock, Dusk.inkSoft, Modifier.size(26.dp))
                        }
                    }
                    if (open) {
                        Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                            repeat(3) { star ->
                                GlyphArt(
                                    Glyph.Star,
                                    if (star < progress.nightStars[i]) Craft.goldDeep else Craft.parchmentShade,
                                    Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .plank(Wood.Walnut, seed = 5)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("NIGHT ${picked.number}", style = Gilt, fontSize = 20.sp, color = Dusk.amber, modifier = Modifier.weight(1f))
                Text("${progress.nights} OF ${NIGHTS.size} OPEN", style = Label, color = Dusk.inkSoft)
            }
            Text(nightLine(picked), color = Dusk.ink, fontSize = 15.sp)
            val hint = lockedHint
            if (hint != null) {
                Text(hint, color = Dusk.amber, fontSize = 13.sp)
            } else if (picked.twist != Twist.None) {
                Text(picked.twist.tip, color = Dusk.inkSoft, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Light the porch", Modifier.fillMaxWidth()) { session.start(daily = false) }
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
    MenuFrame("Daily porch", session) {
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
                Text("Today's porch", style = Gilt, fontSize = 20.sp, color = Dusk.amber)
                Spacer(Modifier.height(4.dp))
                Text("The same 16 cards for everyone today. You get one scored try.", color = Dusk.ink, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        // The week: one stud a day, gold when played.
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .metal(corner = 17.dp, dull = entry == null)
                            .then(if (ago == 0) Modifier.border(2.dp, Craft.gold, CircleShape) else Modifier)
                            .semantics {
                                contentDescription = dayLabel(seed) + if (entry != null) ", ${entry.stars} stars" else ", not played"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (entry != null) Text("${entry.stars}", style = Carved, fontSize = 15.sp, color = Dusk.paperInk)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${seed % 100}", style = Label, color = if (ago == 0) Dusk.amber else Dusk.inkSoft)
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
                Text("Nothing here yet. Play today's porch to start your record.", color = Craft.parchmentInk, fontSize = 14.sp)
            }
            best.forEachIndexed { i, entry ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = Display, fontSize = 16.sp, color = Craft.parchmentInkSoft, modifier = Modifier.width(26.dp))
                    Text(dayLabel(entry.day), color = Craft.parchmentInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Row {
                        repeat(3) { GlyphArt(Glyph.Star, if (it < entry.stars) Craft.goldDeep else Craft.parchmentShade, Modifier.size(14.dp)) }
                    }
                    Text(
                        entry.score.grouped(),
                        style = Display,
                        fontSize = 16.sp,
                        color = Craft.parchmentInk,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(76.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        PrimaryButton(if (played) "Practise today's porch" else "Play today's porch", Modifier.fillMaxWidth()) { session.start(daily = true) }
        if (played) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Share today's result", Modifier.fillMaxWidth()) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, dailyShare(today, progress.dailyStars, progress.dailyScore))
                context.startActivity(Intent.createChooser(send, null))
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
        Text(value, style = Gilt, fontSize = 20.sp, color = Dusk.amber, maxLines = 1)
        Text(unit, color = Dusk.inkSoft, fontSize = 11.sp, maxLines = 1)
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            Text(MONTHS[((seed / 100 % 100).toInt() - 1).coerceIn(0, 11)].uppercase(), style = Label, color = Color(0xFFFFF0DC))
            Spacer(Modifier.height(6.dp))
            Text("${seed % 100}", style = Display, fontSize = 38.sp, color = Craft.parchmentInk)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lead moths: each in a glass jar on velvet, with what it is good at.

@Composable
internal fun MothsScreen(session: Session) {
    val progress = session.store.progress
    MenuFrame("Lead moths", session) {
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
                ShowcaseJar(moth, open, gold, Modifier.size(84.dp, 118.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(moth.label, style = Gilt, fontSize = 21.sp, color = if (open) Dusk.amber else Dusk.inkSoft)
                        if (leading) Chip("LEADING", Craft.gold, Dusk.paperInk)
                        if (gold) Chip("GOLD", Craft.goldDeep, Color(0xFFFFF6DC))
                    }
                    if (open) {
                        Text("${moth.role} · ${moth.perkName}", color = Dusk.inkSoft, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Pips("TIME", stats.time)
                        Pips("LOOK", stats.look)
                        Pips("STREAK", stats.streak)
                        Spacer(Modifier.height(6.dp))
                        Text(moth.perk, color = Dusk.ink, fontSize = 13.sp, lineHeight = 18.sp)
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlyphArt(Glyph.Lock, Dusk.inkSoft, Modifier.size(16.dp))
                            Text("Reach level ${moth.unlockLevel} to meet ${moth.label}.", color = Dusk.ink, fontSize = 14.sp)
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
        Text(label, style = Label, fontSize = 10.sp, color = Dusk.inkSoft, modifier = Modifier.width(58.dp))
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
private fun ShowcaseJar(moth: MothId, open: Boolean, gold: Boolean, modifier: Modifier) {
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
        MothArt(
            moth,
            Modifier.fillMaxWidth(0.72f).height(72.dp).offset(y = 6.dp),
            flat = if (open) null else Color(0xFF1A0B18),
            gold = gold,
        )
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
    var picked by remember { mutableStateOf(Badge.entries.firstOrNull { it in progress.badges } ?: Badge.FirstLight) }
    MenuFrame("Badges", session) {
        Text("${progress.badges.size} OF ${Badge.entries.size} EARNED", style = Label, color = Dusk.inkSoft)
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
                        val grow by animateFloatAsState(if (chosen) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "grow")
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
                                    contentDescription = "${badge.title}. ${badge.detail} " + if (earned) "Earned." else "Not earned yet."
                                    stateDescription = if (chosen) "Selected" else "Not selected"
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BadgeMedal(
                                badge,
                                earned,
                                Modifier
                                    .size(68.dp)
                                    .graphicsLayer {
                                        scaleX = grow
                                        scaleY = grow
                                    },
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                badge.title.uppercase(),
                                style = Label,
                                fontSize = 10.sp,
                                color = if (chosen) Dusk.amber else if (earned) Dusk.ink else Dusk.inkSoft,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
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
                Text(picked.title, style = Display, fontSize = 18.sp, color = Craft.parchmentInk)
                Text(picked.detail, color = Craft.parchmentInk, fontSize = 14.sp, lineHeight = 19.sp)
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
            fontSize = 13.sp,
            lineHeight = 18.sp,
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
            Text(title, color = Dusk.ink, fontSize = 16.sp)
            Text(detail, color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 17.sp)
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
