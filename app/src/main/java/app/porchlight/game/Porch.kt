package app.porchlight.game

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** Where each moth rests on the wall, as fractions of the stage. */
private val SLOTS = listOf(
    Offset(0.15f, 0.22f), Offset(0.85f, 0.22f),
    Offset(0.13f, 0.56f), Offset(0.87f, 0.56f),
    Offset(0.29f, 0.86f), Offset(0.71f, 0.86f),
)
private val LEAD_SPOT = Offset(0.5f, 0.7f)

/** What each lead does, in a few words. The full sentence shows when the line is tapped. */
private fun brief(lead: MothId): String = when (lead) {
    MothId.Luna -> "+5 s bulb"
    MothId.Wick -> "streak starts ×2, −3 s bulb"
    MothId.Atlas -> "one second look"
    MothId.Dot -> "taps past a wrong pair"
    MothId.Iris -> "longer peek"
    MothId.Sorbet -> "+1 s a pair"
    else -> ""
}

/** "8 pairs · 40 s · Wanderer" */
internal fun nightLine(night: Night): String =
    "${night.pairs} pairs · ${night.roundMs / 1000} s" + if (night.twist != Twist.None) " · ${night.twist.title}" else ""

/**
 * The lobby. The logo, a lit mason jar hanging in the middle with the moths resting round it, an
 * oak sign that starts a round, and four brass-rimmed tiles that lead to everything else.
 */
@Composable
internal fun Porch(session: Session) {
    // Arriving on the porch, the light catches: two quick flickers, then it holds.
    LaunchedEffect(Unit) {
        if (!session.calm) {
            for ((level, ms) in listOf(0.15f to 90L, 1f to 120L, 0.3f to 70L, 1f to 0L)) {
                session.flicker = level
                delay(ms)
            }
        }
        session.flicker = 1f
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        if (maxWidth > maxHeight) {
            // Landscape and wide tablets: the jar on the left, the signs on the right.
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stage(session, Modifier.fillMaxHeight().weight(1f))
                Column(
                    Modifier.fillMaxHeight().weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TopRow(session)
                    Spacer(Modifier.height(10.dp))
                    Logo()
                    Controls(session)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TopRow(session)
                Spacer(Modifier.height(6.dp))
                Logo()
                Stage(session, Modifier.fillMaxWidth().weight(1f))
                Controls(session)
            }
        }
    }
}

@Composable
private fun TopRow(session: Session) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LevelBar(session.store.progress.xp, Modifier.weight(1f))
        BrassButton(Glyph.Gear, "Settings", enabled = true) { session.open(Screen.Settings) }
    }
}

/** The name, in gold leaf with the lamp's glow behind it. */
@Composable
private fun Logo() {
    Text(
        "PORCHLIGHT",
        style = Display.copy(shadow = Shadow(Dusk.glowDeep.copy(alpha = 0.75f), Offset(0f, 2f), 26f)),
        fontSize = 33.sp,
        letterSpacing = 2.sp,
        color = Craft.gold,
        maxLines = 1,
    )
}

@Composable
private fun Controls(session: Session) {
    val progress = session.store.progress
    val lead = session.lead
    val night = NIGHTS[progress.night - 1]
    // On a tablet the sign and tiles keep to a comfortable width instead of spanning the screen.
    Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // One line: who leads and what they do. Tapping a locked moth explains how to meet it here.
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) {
                    session.noticeMoth = null
                    session.notice = if (session.notice == null) "${lead.perkName}: ${lead.perk}" else null
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            session.noticeMoth?.let { MothArt(it, Modifier.size(30.dp), flat = Dusk.locked) }
            Text(
                session.notice ?: "${lead.label} · ${brief(lead)}",
                color = if (session.notice != null) Dusk.amber else Dusk.ink,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Light the porch", Modifier.fillMaxWidth()) { session.start(daily = false) }
        Spacer(Modifier.height(8.dp))
        Text(
            "NIGHT ${night.number} · ${nightLine(night).uppercase()}" + if (progress.best > 0) "   BEST ${progress.best.grouped()}" else "",
            style = Label,
            color = Dusk.inkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuTile(Glyph.Map, "Nights", Modifier.weight(1f)) { session.open(Screen.Nights) }
            MenuTile(Glyph.Calendar, "Daily", Modifier.weight(1f)) { session.open(Screen.Daily) }
            MenuTile(Glyph.Moth, "Moths", Modifier.weight(1f)) { session.open(Screen.Moths) }
            MenuTile(Glyph.Trophy, "Badges", Modifier.weight(1f)) { session.open(Screen.Badges) }
        }
    }
}

/** A walnut tile with a brass rule: an icon over its name. */
@Composable
private fun MenuTile(glyph: Glyph, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(70.dp)
            .plank(Wood.Walnut, corner = 12.dp, elevation = 5.dp, seed = label.length + 3)
            .border(1.5.dp, BrassRule, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphArt(glyph, Craft.brassLight, Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = Label, fontSize = 10.sp, color = Dusk.ink, maxLines = 1)
    }
}

/** The jar and the moths around it. Tap an unlocked moth to make it the lead. */
@Composable
private fun Stage(session: Session, modifier: Modifier) {
    val progress = session.store.progress
    BoxWithConstraints(
        modifier.drawBehind {
            // The porch itself: an iron bracket the jar hangs from, and a railing along the bottom.
            val mid = size.width / 2f
            val arm = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Craft.ironDark, Offset(mid - 46.dp.toPx(), 4.dp.toPx()), Offset(mid + 46.dp.toPx(), 4.dp.toPx()), arm.width, StrokeCap.Round)
            drawLine(Craft.iron, Offset(mid - 44.dp.toPx(), 3.dp.toPx()), Offset(mid + 44.dp.toPx(), 3.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(Craft.ironDark, Offset(mid, 4.dp.toPx()), Offset(mid, 16.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            rivet(Offset(mid - 40.dp.toPx(), 4.dp.toPx()), 2.5.dp.toPx())
            rivet(Offset(mid + 40.dp.toPx(), 4.dp.toPx()), 2.5.dp.toPx())

            val rail = size.height - 10.dp.toPx()
            val post = 9.dp.toPx()
            val wood = Wood.Night
            var x = 6.dp.toPx()
            while (x < size.width) {
                drawRect(wood.dark, Offset(x, rail - 30.dp.toPx()), androidx.compose.ui.geometry.Size(post * 0.5f, 34.dp.toPx()), alpha = 0.9f)
                x += 26.dp.toPx()
            }
            drawRect(wood.dark, Offset(0f, rail - 36.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 8.dp.toPx()))
            drawRect(wood.light, Offset(0f, rail - 36.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx()), alpha = 0.6f)
            drawRect(wood.dark, Offset(0f, rail + 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 6.dp.toPx()))
        },
    ) {
        val w = maxWidth
        val h = maxHeight
        val jarH = min(h * 0.5f, w * 0.62f)
        Bulb(
            clock = session.clock,
            timeLeft = { 1f },
            power = session.power,
            lamp = Lamp.Jar,
            still = session.calm,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .size(jarH * 0.7f, jarH)
                .tracksBulb(session)
                .semantics { contentDescription = "The porch light, in its jar" },
        )

        val fly = spring<Dp>(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
        LEADS.forEachIndexed { i, id ->
            val chosen = id == session.lead
            val open = progress.unlocked(id)
            val spot = if (chosen) LEAD_SPOT else SLOTS[i]
            val x by animateDpAsState(w * spot.x, fly, label = "x")
            val y by animateDpAsState(h * spot.y, fly, label = "y")
            // Moths shrink on short screens so they never crowd each other or the jar.
            // ...and grow on tablets, where the wall has room for them.
            val size by animateDpAsState(
                if (chosen) min(min(150.dp, h * 0.28f), w * 0.3f) else min(min(96.dp, h * 0.18f), w * 0.2f),
                fly,
                label = "size",
            )
            Box(
                Modifier
                    .offset(x - size / 2, y - size / 2)
                    .size(size)
                    .clip(CircleShape)
                    .drawBehind {
                        // The lead hovers in a soft halo of the lamp's light.
                        if (chosen) drawCircle(Brush.radialGradient(listOf(Dusk.glow.copy(alpha = 0.34f), Color.Transparent)))
                    }
                    .clickable(role = Role.RadioButton) { session.pickLead(id) }
                    .semantics {
                        contentDescription =
                            if (open) "${id.label}: ${id.perkName}" else "${id.label}, locked until level ${id.unlockLevel}"
                        stateDescription = if (chosen) "Leading" else "Not leading"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (open) {
                    val gold = id in progress.mastered
                    Canvas(Modifier.fillMaxSize()) {
                        // Every few seconds one resting moth flutters: each takes its turn.
                        val t = session.clock.seconds
                        val turn = (t / 2.6f).toInt() % LEADS.size == i
                        val beat = t % 2.6f
                        val flutter = if (turn && beat < 0.55f && !session.calm) kotlin.math.abs(kotlin.math.sin(beat * 17f)) else 0f
                        // The lead keeps a slow wingbeat of its own as it hovers.
                        val hover = if (chosen && !session.calm) 0.08f * (0.5f + 0.5f * kotlin.math.sin(t * 3.2f)) else 0f
                        // `size` here is the canvas, not the moth's dp size above.
                        val canvas = this.size
                        moth(id, Offset(canvas.width / 2f, canvas.height / 2f), canvas.minDimension * (if (chosen) 0.86f else 1f), 1f - 0.6f * flutter - hover, gold = gold)
                    }
                } else {
                    // Not met yet: a dim shape and a small padlock.
                    MothArt(id, Modifier.fillMaxSize().padding(4.dp), flat = Color(0xFF2B3742))
                    GlyphArt(Glyph.Lock, Dusk.inkSoft, Modifier.size(size * 0.34f))
                }
            }
        }
    }
}
