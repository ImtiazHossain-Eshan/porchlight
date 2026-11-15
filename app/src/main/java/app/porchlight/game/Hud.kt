package app.porchlight.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** A brass rule round a shape, lit from the top-left. */
internal val BrassRule = Brush.linearGradient(listOf(Craft.brassLight, Craft.brass, Craft.brassDark, Craft.brass))

/** A small ebony plate with a brass rim: where the round keeps its numbers. */
internal fun Modifier.plate(shape: Shape = RoundedCornerShape(10.dp), lit: Color? = null): Modifier =
    this
        .shadow(4.dp, shape, ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
        .wood(Wood.Ebony, 10.dp, seed = 7, bevel = false)
        .clip(shape)
        .border(1.5.dp, if (lit != null) Brush.linearGradient(listOf(lit, Craft.brassLight, lit)) else BrassRule, shape)

/** Lettering cut into wood: dark, with a light edge below and to the right. */
internal val Carved = Display.copy(shadow = Shadow(Color.White.copy(alpha = 0.35f), Offset(1f, 1.5f), 0f))

/** Lettering in gold leaf on dark timber: a dark edge below gives it thickness. */
internal val Gilt = Display.copy(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 3f), 4f))

/** Score, top left of the round. It counts up instead of jumping. */
@Composable
internal fun ScoreReadout(score: Int, modifier: Modifier = Modifier) {
    val shown by animateIntAsState(score, tween(450, easing = FastOutSlowInEasing), label = "score")
    Column(
        modifier
            .plate()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Score $score" },
    ) {
        Text("SCORE", style = Label, color = Dusk.inkSoft)
        Text(shown.grouped(), style = Gilt, fontSize = 22.sp, color = Dusk.amber)
    }
}

/**
 * Streak multiplier, top right of the round. It pops as it climbs, turns gold for as long as
 * Overdrive holds, and cracks and drops when the streak breaks.
 */
@Composable
internal fun StreakReadout(multiplier: Int, calm: Boolean, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    val crack = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(multiplier) }
    LaunchedEffect(multiplier) {
        val was = last
        last = multiplier
        if (multiplier > was) {
            pulse.snapTo(1.3f + 0.12f * multiplier)
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        } else if (multiplier < was && !calm) {
            crack.snapTo(1f)
            crack.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
    }
    val over = multiplier >= MAX_MULTIPLIER
    Column(
        modifier
            .plate(lit = if (over) Craft.gold else null)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Streak times $multiplier" },
        horizontalAlignment = Alignment.End,
    ) {
        Text(if (over) "OVERDRIVE" else "STREAK", style = Label, color = if (over) Craft.gold else Dusk.inkSoft)
        Text(
            "×$multiplier",
            style = Gilt,
            fontSize = 22.sp,
            color = if (over) Craft.gold else Dusk.amber,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
                rotationZ = crack.value * 22f
                translationY = crack.value * 14.dp.toPx()
                transformOrigin = TransformOrigin(1f, 0.5f)
            },
        )
    }
}

/** A round brass button with a drawn icon. Unlit, it is dull iron. */
@Composable
internal fun BrassButton(
    glyph: Glyph,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "squeeze")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .shadow(5.dp, CircleShape, ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .metal(corner = size / 2, dull = !enabled)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        GlyphArt(glyph, if (enabled) Dusk.paperInk else Dusk.locked, Modifier.size(size * 0.5f))
    }
}

/** The level in a brass medallion, with the XP gauge beside it: an amber fill in a brass tube. */
@Composable
internal fun LevelBar(xp: Int, modifier: Modifier = Modifier, animate: Boolean = true) {
    val level = levelFor(xp)
    val floor = xpFor(level)
    val next = xpFor(level + 1)
    val fill by animateFloatAsState(
        ((xp - floor).toFloat() / (next - floor)).coerceIn(0f, 1f),
        tween(if (animate) 800 else 0, easing = FastOutSlowInEasing),
        label = "xp",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .shadow(4.dp, CircleShape, ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .metal(corner = 22.dp)
                .semantics { contentDescription = "Level $level" },
            contentAlignment = Alignment.Center,
        ) {
            Text("$level", style = Carved, fontSize = 19.sp, color = Dusk.paperInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text("LEVEL $level", style = Label, color = Dusk.ink, modifier = Modifier.weight(1f))
                Text("${xp - floor} / ${next - floor} XP", style = Label, color = Dusk.inkSoft)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Dusk.slot)
                    .border(1.5.dp, BrassRule, RoundedCornerShape(6.dp))
                    .padding(2.5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.verticalGradient(listOf(Dusk.glow, Dusk.amber, Dusk.glowDeep))),
                )
            }
        }
    }
}

/** A gold star, bevelled; an unearned one is an empty iron socket. */
@Composable
internal fun Star(filled: Boolean, shown: Boolean, modifier: Modifier = Modifier, instant: Boolean = false) {
    val scale by animateFloatAsState(
        if (shown) 1f else 0f,
        if (instant) tween(0) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "star",
    )
    Canvas(
        modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = (1f - scale) * -40f
            },
    ) {
        val c = Offset(size.width / 2, size.height / 2 + size.height * 0.04f)
        val outer = size.minDimension / 2 - 2.dp.toPx()
        val inner = outer * 0.47f
        val star = Path()
        // Every other facet is lit, so the star reads as cut metal.
        val facets = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = c.x + (r * cos(a)).toFloat()
            val y = c.y + (r * sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            if (i % 2 == 0) {
                val b = Math.toRadians(-90.0 + (i + 1) * 36.0)
                facets.moveTo(c.x, c.y)
                facets.lineTo(x, y)
                facets.lineTo(c.x + (inner * cos(b)).toFloat(), c.y + (inner * sin(b)).toFloat())
                facets.close()
            }
        }
        star.close()
        if (filled) {
            drawPath(star, Brush.verticalGradient(listOf(Color(0xFFFFE9A0), Craft.gold, Craft.goldDeep)))
            drawPath(facets, Color.White, alpha = 0.28f)
            drawPath(star, Craft.goldDeep, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        } else {
            drawPath(star, Craft.navyDeep)
            drawPath(star, Craft.iron, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

internal fun Mold.glyph(): Glyph = when (this) {
    Mold.Moth -> Glyph.Moth
    Mold.Beetle -> Glyph.Beetle
    Mold.Trophy -> Glyph.Trophy
    Mold.Star -> Glyph.Star
    Mold.Flame -> Glyph.Flame
    Mold.Moon -> Glyph.Moon
    Mold.Calendar -> Glyph.Calendar
    Mold.Clock -> Glyph.Clock
    Mold.Check -> Glyph.Check
}

private val BRONZE = listOf(Color(0xFFE2A56E), Color(0xFFA9683A), Color(0xFF5A3218))
private val SILVER = listOf(Color(0xFFF2F4F6), Color(0xFFA9B2BC), Color(0xFF58626E))
private val GOLD = listOf(Color(0xFFFFF0B0), Color(0xFFE9B23E), Color(0xFF8C5E12))
private val IRON = listOf(Color(0xFF4A4F56), Color(0xFF2C3036), Color(0xFF16181C))

/**
 * A cast badge: a scalloped rim, a sunken face, and the badge's mold pressed into it. Bronze,
 * silver or gold when earned (gold badges carry a gem); dull iron until then.
 */
@Composable
internal fun BadgeMedal(badge: Badge, earned: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val metal = when {
            !earned -> IRON
            badge.metal == Metal.Bronze -> BRONZE
            badge.metal == Metal.Silver -> SILVER
            else -> GOLD
        }
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - 1.dp.toPx()
        val rim = Path()
        val scallops = 16
        for (i in 0..scallops * 2) {
            val a = i * Math.PI / scallops
            val rr = if (i % 2 == 0) r else r * 0.92f
            val x = c.x + (rr * cos(a)).toFloat()
            val y = c.y + (rr * sin(a)).toFloat()
            if (i == 0) rim.moveTo(x, y) else rim.lineTo(x, y)
        }
        rim.close()
        val across = Offset(size.width, size.height)
        drawPath(rim, Brush.linearGradient(listOf(metal[0], metal[1], metal[2]), Offset.Zero, across))
        drawPath(rim, metal[2], style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
        drawCircle(Brush.linearGradient(listOf(metal[2], metal[1]), Offset.Zero, across), r * 0.74f, c)
        drawCircle(metal[0], r * 0.74f, c, alpha = 0.6f, style = Stroke(1.dp.toPx()))
        // The mold: a dark cut with a light edge beside it.
        val w = r * 0.9f
        glyph(badge.mold.glyph(), Offset(c.x + 0.7.dp.toPx(), c.y + 0.9.dp.toPx()), w, metal[0], alpha = if (earned) 0.7f else 0.25f)
        glyph(badge.mold.glyph(), c, w, metal[2])
        if (earned && badge.metal == Metal.Gold) {
            val gem = Offset(c.x, c.y - r * 0.82f)
            drawCircle(Craft.goldDeep, r * 0.15f, gem)
            drawCircle(Craft.bio, r * 0.11f, gem)
            drawCircle(Color.White, r * 0.04f, Offset(gem.x - r * 0.03f, gem.y - r * 0.03f), alpha = 0.8f)
        }
    }
}

/** The main action: an oak sign with carved lettering, held on by two brass rivets. */
@Composable
internal fun PrimaryButton(text: String, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    SignButton(text, Wood.Oak, Dusk.paperInk, Carved, modifier, onClick, busy)
}

/** A quieter action: a walnut sign with gilt lettering. */
@Composable
internal fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SignButton(text, Wood.Walnut, Dusk.amber, Gilt, modifier, onClick)
}

@Composable
private fun SignButton(text: String, wood: Wood, ink: Color, style: TextStyle, modifier: Modifier, onClick: () -> Unit, busy: Boolean = false) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // A sign gives under the finger with a spring, and sinks a little toward the wall.
    val squeeze by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "squeeze")
    Box(
        modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
                translationY = (1f - squeeze) * 60f
            }
            .plank(wood, corner = 12.dp, elevation = 8.dp, seed = text.length)
            .border(1.5.dp, BrassRule, RoundedCornerShape(12.dp))
            .drawBehind {
                rivet(Offset(14.dp.toPx(), size.height / 2), 3.5.dp.toPx())
                rivet(Offset(size.width - 14.dp.toPx(), size.height / 2), 3.5.dp.toPx())
            }
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Work in hand (the share image being made): a small spinner before the words.
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = ink,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 10.dp).size(16.dp),
                )
            }
            Text(text.uppercase(), style = style, fontSize = 17.sp, color = ink, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** A small stamped tag. */
@Composable
internal fun Chip(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Label,
        fontSize = 12.sp,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/**
 * A brass lever switch: the knob slides along an ebony slot, and the slot glows amber when on.
 * It is a switch to screen readers.
 */
@Composable
internal fun LeverSwitch(on: Boolean, description: String, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val at by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "lever")
    Box(
        modifier
            .size(64.dp, 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            )
            .semantics {
                contentDescription = description
                stateDescription = if (on) "On" else "Off"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(56.dp, 30.dp)) {
            val r = size.height / 2
            val track = CornerRadius(r)
            drawRoundRect(Dusk.slot, cornerRadius = track)
            drawRoundRect(Dusk.amber, cornerRadius = track, alpha = 0.55f * at)
            drawRoundRect(BrassRule, cornerRadius = track, style = Stroke(1.5.dp.toPx()))
            val x = r + (size.width - 2 * r) * at
            drawCircle(Dusk.shadow, r * 0.86f, Offset(x, r + 2.dp.toPx()), alpha = 0.5f)
            drawCircle(
                Brush.linearGradient(listOf(Craft.brassLight, Craft.brass, Craft.brassDark), Offset(x - r, 0f), Offset(x + r, size.height)),
                r * 0.82f,
                Offset(x, r),
            )
            drawCircle(Craft.brassDark, r * 0.82f, Offset(x, r), style = Stroke(1.dp.toPx()))
            drawCircle(Color.White, r * 0.22f, Offset(x - r * 0.25f, r - r * 0.28f), alpha = 0.55f)
        }
    }
}
