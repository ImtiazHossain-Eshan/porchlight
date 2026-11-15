package app.porchlight.game

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

/** The card is laid out 360 units wide; at three pixels a unit it renders 1080 pixels wide. */
private const val CARD_UNITS = 360
private const val CARD_PX = 1080
private const val UNIT_PX = CARD_PX / CARD_UNITS

/** Square for a feed post (4:5, 1080 x 1350), or tall for a story (9:16, 1080 x 1920). */
internal enum class CardShape(val label: String, val heightUnits: Int) {
    Post("Post", 450),
    Story("Story", 640);

    val heightPx get() = heightUnits * UNIT_PX
}

/** 20261115 -> "Sun 15 Nov". */
internal fun cardDate(day: Long): String {
    val c = Calendar.getInstance()
    c.clear()
    c.set((day / 10_000).toInt(), (day / 100 % 100).toInt() - 1, (day % 100).toInt())
    return String.format(Locale.UK, "%ta %<te %<tb", c)
}

/**
 * The daily share card. Drawn with the game's own wall, lamp and moths, never a screenshot, and
 * without shaders, so it comes out the same on every phone. Lay it out at [CARD_UNITS] wide.
 * [stars] is how many star sockets have popped in so far (the preview counts them up).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareCard(card: DailyCard, gold: Boolean, shape: CardShape, modifier: Modifier = Modifier, stars: Int = 3) {
    val clock = remember { Clock() }
    val tilt = remember { Tilt() }
    val story = shape == CardShape.Story
    val lit = card.won
    val density = LocalDensity.current
    val bulbTop = if (story) 40.dp else 26.dp
    val bulbSize = if (story) 120.dp else 84.dp
    Box(modifier.background(Dusk.wallTop)) {
        with(density) {
            NightScene(
                clock = clock,
                tilt = tilt,
                calm = true,
                bulb = { Offset(CARD_UNITS.dp.toPx() / 2f, (bulbTop + bulbSize * 0.6f).toPx()) },
                light = { if (lit) 1.25f else 0.12f },
                reach = { (if (story) 330.dp else 260.dp).toPx() },
                warmth = { 1f },
                shaders = false,
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = if (story) 26.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PORCHLIGHT · DAILY PORCH", style = Label, fontSize = 10.sp, letterSpacing = 2.sp, color = Dusk.amber)
            Text(cardDate(card.day), style = Label, fontSize = 9.sp, color = Dusk.inkSoft)
            Spacer(Modifier.height(if (story) 10.dp else 2.dp))
            Bulb(
                clock = clock,
                timeLeft = { 1f },
                power = { if (lit) 1f else 0f },
                still = true,
                lamp = Lamp.Jar,
                ownHalo = true,
                modifier = Modifier.size(bulbSize * 0.7f, bulbSize),
            )
            MothArt(card.lead, Modifier.size(if (story) 128.dp else 76.dp), gold = gold)
            Spacer(Modifier.height(if (story) 14.dp else 2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                repeat(3) {
                    Star(
                        it < card.stars,
                        shown = it < stars,
                        modifier = Modifier.padding(bottom = if (it == 1) 8.dp else 0.dp),
                    )
                }
            }
            Text(
                card.score.grouped(),
                style = Gilt,
                fontSize = if (story) 58.sp else 44.sp,
                color = if (lit) Craft.gold else Dusk.inkSoft,
            )
            Text(card.line, fontSize = 12.sp, color = Dusk.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(if (story) 22.dp else 10.dp))

            // The flight log: the order of the round, and nothing about where the cards lay.
            val tile = if (card.log.size > 20) 20.dp else 24.dp
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = 11,
                modifier = Modifier.semantics { contentDescription = logDescription(card.log) },
            ) {
                card.log.forEach { LogTile(it, Modifier.size(tile)) }
            }

            val chips = buildList {
                if (card.overdrive) add("Overdrive ×${card.bestMultiplier}")
                if (card.streak >= 2) add("Day ${card.streak} streak")
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(if (story) 18.dp else 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { Chip(it, Craft.navyDeep, Craft.gold, Modifier.border(1.dp, Craft.goldDeep, RoundedCornerShape(6.dp))) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Can you beat my porch? · Porchlight", style = Label, fontSize = 10.sp, color = Dusk.inkSoft)
        }
    }
}

/** One turned pair on the card: a moth on a warm tile, ringed brighter the higher the streak; a miss is an empty dark tile. */
@Composable
private fun LogTile(entry: LogEntry, modifier: Modifier) {
    val shape = RoundedCornerShape(5.dp)
    when (entry) {
        LogEntry.Miss -> Box(modifier.clip(shape).background(Craft.navyDeep).border(1.dp, Craft.iron, shape))
        is LogEntry.Match -> {
            val ring = when (entry.multiplier) {
                1 -> null
                2 -> Dusk.glow.copy(alpha = 0.7f)
                else -> Craft.gold
            }
            val ringWidth = when (entry.multiplier) {
                2 -> 1.5.dp
                3 -> 2.dp
                else -> 2.5.dp
            }
            Box(
                modifier
                    .drawBehind {
                        // ×4 glows past its tile.
                        if (entry.multiplier >= MAX_MULTIPLIER) {
                            drawCircle(
                                Brush.radialGradient(
                                    0.6f to Craft.gold.copy(alpha = 0.55f),
                                    1f to Color.Transparent,
                                    radius = size.minDimension * 0.68f,
                                ),
                                radius = size.minDimension * 0.68f,
                            )
                        }
                    }
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Craft.parchment, Craft.parchmentShade)))
                    .then(if (ring != null) Modifier.border(ringWidth, ring, shape) else Modifier)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                MothArt(entry.moth, Modifier.fillMaxSize())
            }
        }
    }
}

private fun logDescription(log: List<LogEntry>): String =
    "The round in order: " + log.joinToString(", ") {
        when (it) {
            LogEntry.Miss -> "miss"
            is LogEntry.Match -> "${it.moth.label} times ${it.multiplier}"
        }
    }

/**
 * The share window: the card scaled to fit, a post/story switch, and two buttons. The card is
 * composed at its full pixel size and recorded into a graphics layer, so the shared image is
 * rendered from the composable at 1080 pixels wide, not taken from the screen.
 */
@Composable
internal fun ShareSheet(card: DailyCard, gold: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shape by remember { mutableStateOf(CardShape.Post) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    val text = dailyShare(card.day, card.stars, card.score)
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Dusk.shadow.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(12.dp)
                .plank(Wood.Walnut, seed = 21)
                // Taps inside the sheet stay in the sheet.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Share today's porch", style = Gilt, fontSize = 20.sp, color = Dusk.amber)
                BrassButton(Glyph.Close, "Close", enabled = true, size = 36.dp, modifier = Modifier.align(Alignment.CenterEnd), onClick = onClose)
            }
            Spacer(Modifier.height(10.dp))

            // The preview: it springs in, and again whenever the shape changes.
            val pop = remember { Animatable(0.92f) }
            var starsShown by remember { mutableIntStateOf(0) }
            LaunchedEffect(shape) {
                starsShown = 0
                pop.snapTo(0.92f)
                launch { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
                delay(220)
                repeat(3) {
                    starsShown = it + 1
                    delay(170)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                val ratio = shape.heightUnits.toFloat() / CARD_UNITS
                val roomW = maxWidth
                val roomH = if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) roomW * ratio else maxHeight
                val w = minOf(roomW, roomH / ratio)
                Box(
                    Modifier
                        .size(w, w * ratio)
                        .graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, Craft.goldDeep, RoundedCornerShape(10.dp)),
                ) {
                    FullSizeCard(card, gold, shape, layer, w, starsShown)
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Craft.navyDeep).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CardShape.entries.forEach { option ->
                    val on = option == shape
                    Text(
                        option.label.uppercase(),
                        style = Label,
                        color = if (on) Dusk.paperInk else Dusk.inkSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) Craft.gold else Color.Transparent)
                            .clickable(role = Role.Tab) { shape = option }
                            .semantics { selected = on }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(if (copied) "Copied" else "Copy text", Modifier.weight(1f)) {
                    copyText(context, text)
                    copied = true
                }
                PrimaryButton("Share image", Modifier.weight(1.3f), busy = busy) {
                    if (busy) return@PrimaryButton
                    busy = true
                    scope.launch {
                        shareImage(context, layer, card.day, text)
                        busy = false
                    }
                }
            }
        }
    }
}

/**
 * Lays the card out at its real 1080-pixel width, records it into [layer], and shows it scaled
 * down to [shownWidth]. Card units are pinned to three pixels and fonts to their own size, so the
 * image is identical whatever the phone's density or font scale.
 */
@Composable
private fun FullSizeCard(card: DailyCard, gold: Boolean, shape: CardShape, layer: GraphicsLayer, shownWidth: androidx.compose.ui.unit.Dp, stars: Int) {
    val screen = LocalDensity.current
    val scale = with(screen) { shownWidth.toPx() } / CARD_PX
    CompositionLocalProvider(LocalDensity provides Density(UNIT_PX.toFloat(), 1f)) {
        ShareCard(
            card,
            gold,
            shape,
            stars = stars,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(CARD_PX, shape.heightPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeWithLayer(0, 0) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        )
    }
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Porchlight daily", text))
    }
}

/** The text-only share, as before: also the fallback if the image can't be made. */
internal fun shareText(context: Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share today's porch"))
    }
}

/**
 * Turns the recorded card into a PNG in cache/share/, then offers it with the text line. Any
 * failure along the way falls back to sharing the text alone; nothing here may crash the game.
 */
private suspend fun shareImage(context: Context, layer: GraphicsLayer, day: Long, text: String) {
    val uri = runCatching {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "porchlight-daily-$day.png")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }
    }.getOrNull()
    if (uri == null) {
        shareText(context, text)
        return
    }
    runCatching {
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Some share targets read the image from the clip data rather than the extra.
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, "Share today's porch"))
    }.onFailure { shareText(context, text) }
}
