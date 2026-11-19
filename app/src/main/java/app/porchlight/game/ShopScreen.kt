package app.porchlight.game

import androidx.compose.animation.core.Spring
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
        FireflyCounter(progress.wallet.balance, Modifier.semantics { contentDescription = "You have ${fireflies(progress.wallet.balance)}" })
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
    Heading("Earned by playing")
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .parchment(8.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        EarnLine("A level's first, second and third star", "${Economy.NEW_STAR[0]} · ${Economy.NEW_STAR[1]} · ${Economy.NEW_STAR[2]}")
        EarnLine("A win with no new star", "${Economy.REPLAY_WIN}")
        EarnLine("A boss beaten for the first time", "${Economy.BOSS_FIRST_CLEAR}")
        EarnLine("Every level of a chapter cleared", "${Economy.CHAPTER_COMPLETE}")
        EarnLine("The daily porch", "${Economy.DAILY_BASE} to ${Economy.DAILY_CAP}")
        EarnLine("A badge", "${Economy.BADGE}")
        EarnLine("A level up", "${Economy.LEVEL_UP}")
        EarnLine("Your first visit each day", "${Economy.FIRST_OPEN}")
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Every chapter, moth and look can be earned by playing. A daily streak adds ${Economy.DAILY_PER_STREAK_DAY} a day, up to ${Economy.DAILY_CAP}.",
        color = Dusk.inkSoft, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun EarnLine(what: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(what, color = Craft.parchmentInk, fontSize = TypeScale.Body, modifier = Modifier.weight(1f), lineHeight = 19.sp)
        Spacer(Modifier.width(8.dp))
        FireflyIcon(Modifier.size(16.dp))
        Spacer(Modifier.width(3.dp))
        Text(amount, color = Craft.parchmentInk, fontSize = TypeScale.Body, fontWeight = FontWeight.Bold)
    }
}
