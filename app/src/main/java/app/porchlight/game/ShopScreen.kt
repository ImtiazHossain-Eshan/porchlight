package app.porchlight.game

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

internal enum class ShopTab(val title: String) {
    Boosts("Boosts"),
    Looks("Looks"),
    Moths("Moths"),
    Fireflies("Fireflies"),
}

/** This month, 1 to 12: seasonal looks are shown in their season. */
internal fun thisMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

/**
 * The Porch shop: boosts and skips, looks, moths, and Fireflies themselves. Everything shows its
 * price; nothing is bought without the confirm sheet, and nothing here is needed to finish the game.
 */
@Composable
internal fun ShopScreen(session: Session) {
    val progress = session.store.progress
    val month = remember { thisMonth() }
    MenuFrame("Porch shop", session) {
        FireflyCounter(
            progress.wallet.balance,
            Modifier.semantics { contentDescription = "You have ${fireflies(progress.wallet.balance)}" },
            swarm = session.swarm,
            calm = session.calm,
            pending = session.store.gift,
        )
        Spacer(Modifier.height(12.dp))
        Tabs(session)
        Spacer(Modifier.height(14.dp))
        when (session.shopTab) {
            ShopTab.Boosts -> BoostsTab(session)
            ShopTab.Looks -> LooksTab(session, month)
            ShopTab.Moths -> MothsTab(session, month)
            ShopTab.Fireflies -> FirefliesTab(session)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Four tabs on one walnut rail: the open one is a raised oak board. */
@Composable
private fun Tabs(session: Session) {
    Row(
        Modifier
            .fillMaxWidth()
            .plank(Wood.Walnut, corner = 12.dp, elevation = 4.dp, seed = 21)
            .border(1.5.dp, BrassRule, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ShopTab.entries.forEach { tab ->
            val open = tab == session.shopTab
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val sink by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "tab")
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .graphicsLayer { scaleX = sink; scaleY = sink }
                    .then(if (open) Modifier.plank(Wood.Oak, corner = 9.dp, elevation = 3.dp, seed = tab.ordinal + 4) else Modifier)
                    .clickable(interactionSource = press, indication = null, role = Role.Tab) {
                        if (!open) {
                            session.shopTab = tab
                            session.sfx.play(Sound.Flip, vary = 0.06f)
                        }
                    }
                    .semantics { selected = open },
                contentAlignment = Alignment.Center,
            ) {
                FitText(tab.title.uppercase(), if (open) signOnOak else Label.copy(fontWeight = FontWeight.Bold), TypeScale.Label, if (open) Dusk.paperInk else Dusk.amber)
            }
        }
    }
}

/** A section's heading, engraved small on the wall. */
@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        style = Label,
        color = Dusk.inkSoft,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** A short note on parchment: how something works, said plainly. */
@Composable
private fun Note(text: String) {
    Text(
        text,
        color = Craft.parchmentInk,
        fontSize = TypeScale.Body,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .parchment(8.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** One row on a walnut board: an icon, words, and something on the right. */
@Composable
private fun Shelf(title: String, detail: String, icon: @Composable () -> Unit, end: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .plank(Wood.Walnut, corner = 12.dp, elevation = 5.dp, seed = title.length)
            .border(1.dp, BrassRule, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, color = Dusk.ink, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 18.sp)
        }
        end()
    }
}

internal val Boost.glyph: Glyph get() = when (this) {
    Boost.Time -> Glyph.Clock
    Boost.Peek -> Glyph.Eye
    Boost.Look -> Glyph.Lamp
    Boost.Pair -> Glyph.Twins
    Boost.Relight -> Glyph.Bulb
}

// -------------------------------------------------------------------------------------------------
// Boosts and getting ahead

@Composable
private fun BoostsTab(session: Session) {
    val progress = session.store.progress
    Note(
        "Boosts help with one round, and you choose them at the moment they're for. A round with any " +
            "boost earns up to ${Economy.BOOSTED_MAX_STARS} stars: the third star needs a clear without one. " +
            "The daily porch never takes boosts, so it's the same deal for everyone.",
    )
    Spacer(Modifier.height(12.dp))
    Heading("For a round")
    Boost.entries.forEach { boost ->
        Shelf(boost.title, "${boost.detail} ${boost.where}.", icon = { GlyphArt(boost.glyph, Craft.brassLight, Modifier.size(26.dp)) }) {
            Price(boost.price, Dusk.amber)
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(6.dp))
    Heading("Getting ahead")
    val waiting = progress.waitingChapter
    val chapterCost = waiting?.let { chapterPrice(progress, it) }
    var any = false
    if (waiting != null && chapterCost != null) {
        any = true
        val chapter = CHAPTERS[waiting - 1]
        val short = progress.starsTo(waiting)
        Shelf(
            "Open chapter $waiting early",
            "${chapter.name} needs ${chapter.gate} stars and you have ${progress.totalStars}. Open it now, or find the last $short on earlier levels.",
            icon = { GlyphArt(Glyph.Lock, Craft.brassLight, Modifier.size(26.dp)) },
        ) {
            BuyChip(chapterCost) {
                session.offer = Offer(
                    title = "Open ${chapter.name}",
                    detail = "Chapter $waiting opens now. Your stars stay as they are, and every level before it stays open to replay.",
                    price = chapterCost,
                    verb = "Open",
                    buy = { p, now -> openChapterEarly(p, waiting, now) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (skipOffered(progress)) {
        any = true
        val n = progress.current
        Shelf(
            "Skip level $n",
            "Three tries in a row on this one. Skip it to open level ${n + 1}; it stays here to play later, with no stars until you win it.",
            icon = { GlyphArt(Glyph.Next, Craft.brassLight, Modifier.size(26.dp)) },
        ) {
            BuyChip(Economy.SKIP_LEVEL_PRICE) { session.offer = skipOffer(n) }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (!any) {
        Text(
            "Nothing to skip right now. After ${Economy.SKIP_AFTER_LOSSES} losses in a row on your newest level you can skip it, " +
                "and a chapter you can't open yet can be opened early, for ${Economy.CHAPTER_PRICE_PER_STAR} Fireflies a missing star.",
            color = Dusk.inkSoft, fontSize = TypeScale.Body, lineHeight = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

internal fun skipOffer(n: Int) = Offer(
    title = "Skip level $n",
    detail = "Level ${n + 1} opens. Level $n stays to play whenever you like, with no stars until you win it.",
    price = Economy.SKIP_LEVEL_PRICE,
    verb = "Skip",
    buy = { p, now -> skipLevel(p, now) },
)

/** A small oak tag with a price: tap to see it on the confirm sheet. */
@Composable
private fun BuyChip(price: Int, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "buy")
    Box(
        Modifier
            .graphicsLayer { scaleX = sink; scaleY = sink }
            .plank(Wood.Oak, corner = 10.dp, elevation = 4.dp, seed = price)
            .border(1.dp, BrassRule, RoundedCornerShape(10.dp))
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Price(price, Dusk.paperInk)
    }
}

// -------------------------------------------------------------------------------------------------
// Looks

@Composable
private fun LooksTab(session: Session, month: Int) {
    for (slot in listOf(Slot.Back, Slot.Jar, Slot.Porch)) {
        Heading(slot.title)
        val looks = LOOK_CATALOG.filter { it.slot == slot && session.store.progress.shows(it, month) }
        val perRow = if (slot == Slot.Porch) 2 else 3
        looks.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { look -> LookTile(session, look, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    Text(
        "Looks change how the porch looks, never how it plays. Seasonal ones show up in their season, and once one is yours it's yours for good.",
        color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** What a look shows of itself: a card back, the jar, a corner of porch, or the moth in its colour. */
@Composable
internal fun LookPicture(session: Session, look: Look, modifier: Modifier = Modifier) {
    when {
        look.back != null -> CardBackPreview(look.back, modifier.aspectRatio(0.75f))
        look.jar != null -> Bulb(
            // Burning low and without its halo, so the glass shows its own colour.
            clock = session.clock, timeLeft = { 1f }, power = { 0.35f }, lamp = Lamp.Jar,
            still = session.calm, style = look.jar, ownHalo = false, modifier = modifier.aspectRatio(0.7f),
        )
        look.porch != null -> PorchPreview(look.porch, modifier.aspectRatio(1.4f))
        look.colours != null -> MothArt(look.moth!!, modifier.aspectRatio(1f), colours = look.colours)
    }
}

@Composable
private fun LookTile(session: Session, look: Look, modifier: Modifier) {
    val progress = session.store.progress
    val owned = progress.owns(look)
    val wearing = progress.wearing(look.slotKey)?.id == look.id || (look.gift == Gift.Default && progress.wearing(look.slotKey) == null)
    val price = look.price
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "look")
    Column(
        modifier
            .graphicsLayer { scaleX = sink; scaleY = sink }
            .shadow(5.dp, RoundedCornerShape(12.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .velvet(12.dp)
            .border(if (wearing) 2.dp else 1.dp, if (wearing) SolidColor(Craft.gold) else BrassRule, RoundedCornerShape(12.dp))
            .clickable(interactionSource = press, indication = null, role = Role.Button) { tapLook(session, look) }
            .semantics {
                contentDescription = look.name + when {
                    wearing -> ", wearing"
                    owned -> ", yours. Tap to wear."
                    price != null -> ", ${fireflies(price)}. Tap to preview."
                    else -> ""
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            LookPicture(session, look, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(6.dp))
        Text(look.name, color = Dusk.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 16.sp, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        when {
            wearing -> Chip("WEARING", Craft.gold, Dusk.paperInk)
            owned -> Chip("YOURS", Craft.brassDark, Dusk.ink)
            price != null -> Price(price, Dusk.amber)
        }
        if (look.season != null && !owned) {
            Spacer(Modifier.height(4.dp))
            Text("IN SEASON", style = Label, color = Dusk.inkSoft, maxLines = 1)
        }
    }
}

/** Tapping a look: an owned one is worn (or, if worn, taken off); one for sale opens its preview to buy. */
private fun tapLook(session: Session, look: Look) {
    val progress = session.store.progress
    if (progress.owns(look)) {
        val worn = progress.wearing(look.slotKey)?.id == look.id
        session.store.change { if (worn) takeOff(it, look.slotKey) else wear(it, look) }
        session.sfx.play(Sound.Flip, vary = 0.06f)
        session.buzz(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
        return
    }
    val price = look.price ?: return
    session.offer = Offer(
        title = look.name,
        detail = lookDetail(look),
        price = price,
        preview = { LookPicture(session, look, Modifier.height(140.dp)) },
        buy = { p, now -> buyLook(p, look, thisMonth(), now) },
    )
}

private fun lookDetail(look: Look): String = when (look.slot) {
    Slot.Back -> "A card back. It changes the face-down side of every card; the moths on the faces stay the same."
    Slot.Jar -> "The porch jar and the round's bulb, in a new metal and glass."
    Slot.Porch -> "The wall, the railing and the colour of the porch light."
    Slot.Moth -> "${look.moth!!.label} in new colours, on the porch and in the Moths page. On the board every moth keeps its own colours, so pairs always match."
} + (look.season?.let { " A ${it.title} look: shown in its season, kept for good." } ?: "") + " You can take it off any time."

// -------------------------------------------------------------------------------------------------
// Moths

@Composable
private fun MothsTab(session: Session, month: Int) {
    val progress = session.store.progress
    Heading("Meet a lead early")
    val locked = LEADS.filter { earlyLeadOffered(progress, it) }
    if (locked.isEmpty()) {
        Text(
            "Every lead moth has joined you.",
            color = Dusk.inkSoft, fontSize = TypeScale.Body, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }
    locked.forEach { moth ->
        Shelf(
            moth.label,
            "${moth.perkName}: ${moth.perk} Joins at level ${moth.unlockLevel}, or now.",
            icon = { MothArt(moth, Modifier.size(40.dp)) },
        ) {
            BuyChip(Economy.EARLY_LEAD_PRICE) {
                session.offer = Offer(
                    title = "Meet ${moth.label} now",
                    detail = "${moth.label} can lead your rounds from today instead of from level ${moth.unlockLevel}. ${moth.perk}",
                    price = Economy.EARLY_LEAD_PRICE,
                    verb = "Meet",
                    preview = { MothArt(moth, Modifier.size(130.dp)) },
                    buy = { p, now -> buyLeadEarly(p, moth, now) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(6.dp))
    Heading("Moth colours")
    val colours = LOOK_CATALOG.filter { it.slot == Slot.Moth && progress.shows(it, month) }
    colours.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { look -> LookTile(session, look, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(10.dp))
    }
    Text(
        "A colour shows on the porch and in the Moths page. On the board every moth keeps its own colours, so a pair always looks like a pair.",
        color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

// -------------------------------------------------------------------------------------------------
// Fireflies

@Composable
private fun FirefliesTab(session: Session) {
    FreeFirefliesCard(session)
    PackShelf(session)
    Spacer(Modifier.height(18.dp))
    EarnedByPlaying()
}

// -------------------------------------------------------------------------------------------------
// Free Fireflies: the opt-in ad, as a lamplit card with a jar of live fireflies.

@Composable
private fun FreeFirefliesCard(session: Session) {
    val ads = session.ads ?: return
    val left = adsLeft(session.store.progress, Placement.Shop, todaySeed())
    // No ad to show and some left: the card simply isn't there (it comes back when one loads).
    if (!ads.ready && left > 0) return
    val activity = androidx.activity.compose.LocalActivity.current
    val clock = session.clock
    val calm = session.calm
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                // A night-blue box lit from the jar on the left, fireflies drifting across it.
                drawRect(Brush.verticalGradient(listOf(Color(0xFF1C2B3E), Color(0xFF0E1622))))
                val jar = Offset(size.width * 0.2f, size.height * 0.5f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC94A).copy(alpha = 0.42f), Color.Transparent), jar, size.width * 0.55f), size.width * 0.55f, jar)
                val t = if (calm) 0f else clock.seconds
                for (k in 0 until 9) {
                    val x = size.width * ((0.32f + k * 0.083f + 0.03f * kotlin.math.sin(t * 0.6f + k)) % 1f)
                    val y = size.height * (0.15f + 0.7f * ((k * 0.37f + 0.05f * t * (1 + k % 3)) % 1f))
                    val a = 0.25f + 0.35f * (0.5f + 0.5f * kotlin.math.sin(t * 2.3f + k * 1.7f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE27A).copy(alpha = a), Color.Transparent), Offset(x, y), 7.dp.toPx()), 7.dp.toPx(), Offset(x, y))
                }
            }
            .border(1.5.dp, BrassRule, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FireflyJar(fill = 0.8f, bugs = 9, clock = clock, calm = calm, modifier = Modifier.size(84.dp, 112.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("+${Economy.AD_SHOP_REWARD}", style = Display, fontSize = 44.sp, color = Craft.gold, lineHeight = 46.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fireflies,\nfree", color = Dusk.ink, fontSize = TypeScale.Body, fontWeight = FontWeight.Bold, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    if (left > 0) "Watch a short ad, only if you want to." else "That's all of today's. Come back tomorrow.",
                    color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                // Today's watches as five little lanterns: lit ones are still to use.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$left of ${Economy.AD_SHOP_PER_DAY} left today" },
                ) {
                    repeat(Economy.AD_SHOP_PER_DAY) { i -> FireflyIcon(Modifier.size(20.dp), lit = if (i < left) 1f else 0f) }
                }
            }
        }
    }
    if (left > 0 && ads.ready && !ads.showing) {
        PrimaryButton("Watch for ${Economy.AD_SHOP_REWARD}", Modifier.fillMaxWidth().padding(bottom = 18.dp).offset(y = (-6).dp), glyph = Glyph.Play) {
            session.watchShopAd(activity)
        }
    }
}

/**
 * A mason jar of fireflies: [fill] (0..1) is how full of light it is, [bugs] how many fireflies drift
 * in it. They wander slowly and their tails pulse; under Reduce motion they hold still.
 */
@Composable
internal fun FireflyJar(fill: Float, bugs: Int, clock: Clock, calm: Boolean, modifier: Modifier = Modifier, dim: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lid = androidx.compose.ui.geometry.Rect(w * 0.2f, 0f, w * 0.8f, h * 0.14f)
        val body = androidx.compose.ui.geometry.Rect(w * 0.06f, h * 0.12f, w * 0.94f, h)
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.2f)
        val glass = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(body, corner)) }
        // The light inside, filling from the bottom.
        val light = if (dim) 0.25f else 1f
        drawPath(glass, Color(0xFF26313B))
        clipPath(glass) {
            val top = body.bottom - body.height * fill.coerceIn(0f, 1f)
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFFFB347).copy(alpha = 0.35f * light), Color(0xFFFFE28A).copy(alpha = 0.85f * light)), top, body.bottom),
                Offset(body.left, top), androidx.compose.ui.geometry.Size(body.width, body.bottom - top),
            )
            val t = if (calm) 0f else clock.seconds
            val random = kotlin.random.Random(bugs * 31 + 7)
            for (k in 0 until bugs) {
                val bx = random.nextFloat()
                val by = random.nextFloat()
                val x = body.left + body.width * (0.18f + 0.64f * ((bx + 0.06f * kotlin.math.sin(t * (0.7f + bx) + k)) % 1f))
                val y = top + (body.bottom - top) * (0.12f + 0.76f * ((by + 0.05f * kotlin.math.cos(t * (0.5f + by) + k * 2f)) % 1f))
                val pulse = 0.6f + 0.4f * kotlin.math.sin(t * 3f + k * 1.3f)
                firefly(Offset(x, y), w * 0.2f, lit = pulse * light)
            }
        }
        // Thick glass: a pale skin and two reflections.
        drawPath(glass, Color(0xFFCFE6F2), alpha = 0.6f, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.035f))
        drawLine(Color.White, Offset(body.left + w * 0.14f, body.top + h * 0.12f), Offset(body.left + w * 0.14f, body.bottom - h * 0.16f), w * 0.05f, androidx.compose.ui.graphics.StrokeCap.Round, alpha = 0.22f)
        // The brass lid and its thread.
        val (lightMetal, metal, dark) = Trim.Brass.tones
        drawRoundRect(metal, lid.topLeft, lid.size, androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
        drawRect(lightMetal, Offset(lid.left + lid.width * 0.12f, lid.top), androidx.compose.ui.geometry.Size(lid.width * 0.12f, lid.height), alpha = 0.6f)
        drawLine(dark, Offset(lid.left, lid.center.y), Offset(lid.right, lid.center.y), w * 0.02f)
    }
}

// -------------------------------------------------------------------------------------------------
// Fireflies packs: a shelf of jars, filling up from a handful to a porchful. Behind the grown-up check.

private val PACK_FILL = mapOf(Product.Handful to 0.5f, Product.Jar to 0.68f, Product.Lantern to 0.85f, Product.Porchful to 1f)
private val PACK_BUGS = mapOf(Product.Handful to 2, Product.Jar to 5, Product.Lantern to 8, Product.Porchful to 13)

@Composable
private fun PackShelf(session: Session) {
    val till = session.till ?: return
    val progress = session.store.progress
    val open = session.grownUp
    val activity = androidx.activity.compose.LocalActivity.current
    fun buy(product: Product) {
        if (activity != null) {
            session.sfx.play(Sound.Tap)
            till.buy(activity, product)
        }
    }
    val ask = { session.askGrownUp("Fireflies packs, the starter pack and Supporter cost real money.") {} }
    Text(
        "Fireflies packs",
        style = Display, fontSize = 24.sp, color = Dusk.ink,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp).semantics { heading() },
    )
    Text(
        if (open) "Exactly what it says on the jar. Payment is through Google Play."
        else "These cost real money, so a grown-up opens this shelf.",
        color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 17.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .plank(Wood.Walnut, corner = 14.dp, elevation = 8.dp, seed = 33)
                .border(1.5.dp, BrassRule, RoundedCornerShape(14.dp))
                .then(if (open) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = "Ask a grown-up") { ask() })
                .padding(horizontal = 10.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                for (product in Product.packs) {
                    val price = till.prices[product]
                    val bonus = bonusPercent(product, till.micros)
                    Column(
                        Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription = "${product.title}: ${fireflies(product.fireflies)}" + (price?.let { ", $it" } ?: "")
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val tall = 64.dp + 14.dp * Product.packs.indexOf(product)
                        FireflyJar(
                            fill = PACK_FILL.getValue(product), bugs = PACK_BUGS.getValue(product),
                            clock = session.clock, calm = session.calm,
                            modifier = Modifier.size(tall * 0.72f, tall),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(product.fireflies.grouped(), style = Display, fontSize = 17.sp, color = Craft.gold, maxLines = 1)
                        Text(product.title.removePrefix("A ").uppercase(), style = Label, color = Dusk.inkSoft, maxLines = 1)
                        if (open && price != null) {
                            Spacer(Modifier.height(6.dp))
                            MoneyChip(price, Modifier.fillMaxWidth().padding(horizontal = 3.dp), compact = true) { buy(product) }
                            Text(bonus?.let { "+$it%" } ?: " ", style = Label, color = Dusk.amber, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            // The shelf the jars stand on.
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Wood.Oak.light, Wood.Oak.dark))))
        }
        if (!open) {
            // A brass padlock hung on the shelf.
            Canvas(Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 14.dp).size(34.dp, 40.dp)) {
                val (l, m, d) = Trim.Brass.tones
                drawArc(d, 180f, 180f, false, Offset(size.width * 0.2f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.12f))
                drawRoundRect(m, Offset(0f, size.height * 0.36f), androidx.compose.ui.geometry.Size(size.width, size.height * 0.64f), androidx.compose.ui.geometry.CornerRadius(size.width * 0.16f))
                drawRect(l, Offset(size.width * 0.12f, size.height * 0.4f), androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.56f), alpha = 0.6f)
                drawCircle(d, size.width * 0.1f, Offset(size.width / 2f, size.height * 0.62f))
                drawLine(d, Offset(size.width / 2f, size.height * 0.62f), Offset(size.width / 2f, size.height * 0.82f), size.width * 0.08f)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (!open) {
        PrimaryButton("Ask a grown-up to open", Modifier.fillMaxWidth(), glyph = Glyph.Lock) { ask() }
        return
    }
    if (till.prices.isEmpty()) {
        Note("Google Play isn't selling anything here right now. Please try again later.")
        return
    }
    if (!progress.ownsProduct(Product.Starter)) {
        till.prices[Product.Starter]?.let { price ->
            Shelf(
                "${Product.Starter.title} · once only",
                Product.Starter.gives,
                icon = { CardBackPreview(lookById(STARTER_LOOK)!!.back!!, Modifier.size(30.dp, 40.dp)) },
            ) { MoneyChip(price) { buy(Product.Starter) } }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (progress.supporter) {
        Text(
            "You're a Supporter. Thank you for keeping the porch light on.",
            color = Dusk.amber, fontSize = TypeScale.Body, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
    } else {
        till.prices[Product.Supporter]?.let { price ->
            Shelf(
                Product.Supporter.title,
                Product.Supporter.gives,
                icon = { PorchPreview(lookById(SUPPORTER_LOOK)!!.porch!!, Modifier.size(40.dp, 30.dp)) },
            ) { MoneyChip(price) { buy(Product.Supporter) } }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (till.pending.isNotEmpty()) {
        Note(
            "Waiting for your payment to go through: ${till.pending.joinToString { it.title.lowercase() }}. " +
                "It arrives the moment Google Play says it's paid.",
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Earned by playing: each way to earn, its amount stamped on a brass coin.

@Composable
private fun EarnedByPlaying() {
    Text(
        "Earned by playing",
        style = Display, fontSize = 24.sp, color = Dusk.ink,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp).semantics { heading() },
    )
    Text(
        "Every chapter, moth and look can be earned this way.",
        color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 17.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .plank(Wood.Walnut, corner = 14.dp, elevation = 6.dp, seed = 41)
            .border(1.5.dp, BrassRule, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        val rows = listOf(
            Triple(Glyph.Star, "Each new star on a level", "${Economy.NEW_STAR[0]}-${Economy.NEW_STAR[2]}"),
            Triple(Glyph.Crown, "A boss beaten, first time", "${Economy.BOSS_FIRST_CLEAR}"),
            Triple(Glyph.Map, "A whole chapter cleared", "${Economy.CHAPTER_COMPLETE}"),
            Triple(Glyph.Calendar, "The daily porch (more with a streak)", "${Economy.DAILY_BASE}-${Economy.DAILY_CAP}"),
            Triple(Glyph.Trophy, "A badge", "${Economy.BADGE}"),
            Triple(Glyph.Wing, "A level up", "${Economy.LEVEL_UP}"),
            Triple(Glyph.Lamp, "Your first visit each day", "${Economy.FIRST_OPEN}"),
            Triple(Glyph.Replay, "A win with no new star", "${Economy.REPLAY_WIN}"),
        )
        rows.forEachIndexed { i, (glyph, what, amount) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "$what: $amount Fireflies" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlyphArt(glyph, Craft.brassLight, Modifier.size(22.dp))
                Text(what, color = Dusk.ink, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), lineHeight = 19.sp)
                // The amount, stamped on a little brass plaque that grows to fit it.
                Box(
                    Modifier
                        .widthIn(min = 52.dp)
                        .height(34.dp)
                        .shadow(4.dp, RoundedCornerShape(17.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFE9A6), Craft.gold, Craft.goldDeep)))
                        .border(1.5.dp, Craft.goldDeep, RoundedCornerShape(17.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+$amount", style = Display, fontSize = 16.sp, color = Color(0xFF3E2810), maxLines = 1)
                }
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.25f)))
        }
    }
}

/** A price in money, on a small oak tag: tap to go to Google Play's own checkout. */
@Composable
private fun MoneyChip(price: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sink by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "money")
    Box(
        modifier
            .graphicsLayer { scaleX = sink; scaleY = sink }
            .plank(Wood.Oak, corner = 10.dp, elevation = 4.dp, seed = price.length)
            .border(1.dp, BrassRule, RoundedCornerShape(10.dp))
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Buy for $price" }
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A long local price shrinks to fit its tag rather than being cut off.
        FitText(price, BodyText.copy(fontWeight = FontWeight.Bold), if (compact) 14.sp else TypeScale.Body, Dusk.paperInk)
    }
}
