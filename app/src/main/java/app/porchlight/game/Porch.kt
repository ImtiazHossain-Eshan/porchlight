package app.porchlight.game

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntOffset
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
    MothId.Dot -> "instant flip-back, 1 free miss"
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
                    Logo(session.calm)
                    Tonight(session)
                    Spacer(Modifier.height(12.dp))
                    Controls(session)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TopRow(session)
                Spacer(Modifier.height(6.dp))
                Logo(session.calm)
                Tonight(session)
                Stage(session, Modifier.fillMaxWidth().weight(1f))
                Controls(session)
            }
        }
    }
}

@Composable
private fun TopRow(session: Session) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LevelPlate(session.store.progress.xp, Modifier.weight(1f), still = session.calm)
        BrassButton(Glyph.Gear, "Settings", enabled = true) { session.open(Screen.Settings) }
    }
}

/** The name lights letter by letter like a neon sign, once each time the app starts. */
private var logoLit = false

@Composable
private fun Logo(calm: Boolean) {
    val first = remember { !logoLit }
    LaunchedEffect(Unit) { logoLit = true }
    NeonOn(
        "PORCHLIGHT",
        TextGlow.copy(letterSpacing = 2.sp),
        TypeScale.Title,
        instant = !first,
        calm = calm,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(session: Session) {
    val progress = session.store.progress
    val lead = session.lead
    val night = NIGHTS[progress.night - 1]
    // On a tablet the sign and tiles keep to a comfortable width instead of spanning the screen.
    Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Who leads, on a small brass-edged tag: the moth, its name, what it does. Tap for the full
        // sentence; tapping a locked moth explains here how to meet it.
        val notice = session.notice
        Row(
            Modifier
                .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1C12), Color(0xFF17100A))))
                .border(1.dp, BrassRule, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button) {
                    session.noticeMoth = null
                    session.notice = if (session.notice == null) "${lead.perkName}: ${lead.perk}" else null
                }
                .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MothArt(session.noticeMoth ?: lead, Modifier.size(28.dp), flat = if (session.noticeMoth != null) Dusk.locked else null)
            if (notice != null) {
                Text(notice, style = BodyText, color = Dusk.amber, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(lead.label.uppercase(), style = Label, color = Dusk.amber, maxLines = 1)
                Text(brief(lead), style = BodyText, color = Dusk.ink, maxLines = 1)
            }
        }
        // Room to breathe between the tag, the sign and the tiles.
        Spacer(Modifier.height(16.dp))
        PlayButton("Play", session.calm, Modifier.fillMaxWidth()) { session.start(daily = false) }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuTile(Glyph.Map, "Nights", 0, session, Modifier.weight(1f)) { session.open(Screen.Nights) }
            MenuTile(Glyph.Calendar, "Daily", 1, session, Modifier.weight(1f)) { session.open(Screen.Daily) }
            MenuTile(Glyph.Moth, "Moths", 2, session, Modifier.weight(1f)) { session.open(Screen.Moths) }
            MenuTile(Glyph.Trophy, "Badges", 3, session, Modifier.weight(1f)) { session.open(Screen.Badges) }
        }
    }
}

/** A walnut tile with a brass rule: an icon over its name. */
@Composable
private fun MenuTile(glyph: Glyph, label: String, index: Int, session: Session, modifier: Modifier, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "sink")
    val clock = session.clock
    val calm = session.calm
    // A raised walnut block: its dark side shows below the face, and the face sinks onto it when
    // pressed. The mark sits on a small brass coin that bobs gently, each tile in its own time,
    // and a glint crosses each tile now and then.
    Box(
        modifier
            .height(80.dp)
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .shadow(6.dp, RoundedCornerShape(12.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3A2414), Color(0xFF140B05)))),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                // The tile grows into the title plaque of the screen it opens.
                .flies("plaque-$label", Fly.Bounds)
                .graphicsLayer { translationY = sink * 6.dp.toPx() }
                .plank(Wood.Walnut, corner = 12.dp, elevation = 0.dp, seed = label.length + 3)
                .border(1.5.dp, BrassRule, RoundedCornerShape(12.dp))
                .drawWithContent {
                    drawContent()
                    if (!calm) {
                        val cycle = (clock.seconds + index * 1.3f) % 5f
                        if (cycle < 0.8f) {
                            val x = -size.width * 0.4f + size.width * 1.8f * (cycle / 0.8f)
                            clipRect {
                                rotate(25f, Offset(x, size.height / 2f)) {
                                    drawRect(
                                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), x - 14.dp.toPx(), x + 14.dp.toPx()),
                                        Offset(x - 14.dp.toPx(), -size.height),
                                        androidx.compose.ui.geometry.Size(28.dp.toPx(), size.height * 3f),
                                    )
                                }
                            }
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        if (!calm) translationY = 2.dp.toPx() * kotlin.math.sin(clock.seconds * 2.2f + index * 1.4f)
                    }
                    .drawBehind {
                        val (c, rr) = coinFace(size)
                        coin(c, rr, press = sink)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.offset(y = (-2).dp)) {
                    GlyphArt(glyph, Color.White.copy(alpha = 0.45f), Modifier.size(18.dp).offset(y = 1.dp))
                    GlyphArt(glyph, Color(0xFF3E2810), Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(label.uppercase(), style = Label, color = Dusk.ink, maxLines = 1)
        }
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
                // The jar rises into the round's bulb when a round starts, and comes back after.
                .flies("lamp")
                .size(jarH * 0.7f, jarH)
                .tracksBulb(session)
                // The jar is the porch light's switch: tap it off and on.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Switch,
                ) { session.toggleLamp() }
                .semantics {
                    contentDescription = "The porch light"
                    stateDescription = if (session.porchDark) "Off" else "On"
                },
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
                    .offset { IntOffset((x - size / 2).roundToPx(), (y - size / 2).roundToPx()) }
                    // Each moth flies off to its own jar on the Moths screen, and back.
                    .flies("moth-${id.name}", Fly.Arc)
                    .size(size)
                    .drawBehind {
                        // The lead hovers in a soft halo of the lamp's light.
                        if (chosen) drawCircle(Brush.radialGradient(listOf(Dusk.glow.copy(alpha = 0.34f), Color.Transparent)))
                    }
                    // No clip here: a circle clip cut the wing tips off. The pick shows as the moth flying to the lead spot.
                    .clickable(interactionSource = null, indication = null, role = Role.RadioButton) { session.pickLead(id) }
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
                        val own = if (turn && beat < 0.55f && !session.calm) kotlin.math.abs(kotlin.math.sin(beat * 17f)) else 0f
                        // When the light is switched, every moth starts up at once, then settles.
                        val since = t - session.stirAt
                        val stir = if (since in 0f..1.1f && !session.calm) kotlin.math.abs(kotlin.math.sin(since * 21f + i)) * (1f - since / 1.1f) else 0f
                        val flutter = maxOf(own, stir)
                        // The lead keeps a slow wingbeat of its own as it hovers.
                        val hover = if (chosen && !session.calm) 0.08f * (0.5f + 0.5f * kotlin.math.sin(t * 3.2f)) else 0f
                        // `size` here is the canvas, not the moth's dp size above.
                        val canvas = this.size
                        // In the dark the moths are only shapes; with the light back they glow again.
                        val lift = if (stir > 0f) -canvas.height * 0.06f * stir else 0f
                        val shown = if (session.porchDark) 0.35f else 1f
                        drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, canvas), androidx.compose.ui.graphics.Paint().apply { alpha = shown })
                        moth(id, Offset(canvas.width / 2f, canvas.height / 2f + lift), canvas.minDimension * (if (chosen) 0.86f else 1f), 1f - 0.6f * flutter - hover, gold = gold)
                        drawContext.canvas.restore()
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

/**
 * Tonight, quietly, under the title: the night, its pairs and time, and the best score, each with
 * a small mark. Plain faint lettering: it informs without competing with the play sign.
 */
@Composable
private fun Tonight(session: Session) {
    val progress = session.store.progress
    val night = NIGHTS[progress.night - 1]
    Row(
        Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Fact(Glyph.Map, "NIGHT ${night.number}")
        Fact(Glyph.Moth, "${night.pairs} PAIRS")
        Fact(Glyph.Clock, "${night.roundMs / 1000} S")
        if (progress.best > 0) Fact(Glyph.Trophy, progress.best.grouped())
    }
}

@Composable
private fun Fact(glyph: Glyph, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GlyphArt(glyph, Dusk.inkSoft.copy(alpha = 0.75f), Modifier.size(12.dp))
        Text(text, style = Label, color = Dusk.inkSoft.copy(alpha = 0.85f), maxLines = 1)
    }
}
