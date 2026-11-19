package app.porchlight.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The currency's name, said the same way everywhere. */
internal fun fireflies(n: Int) = if (n == 1) "1 Firefly" else "${n.grouped()} Fireflies"

private val TAIL = Color(0xFFFFF2A8)
private val GLOW = Color(0xFFFFD45A)
private val BODY = Color(0xFF2A1C10)
private val WING = Color(0xFFE8EEF2)

/**
 * A firefly, [width] pixels wide, centred on [centre]: a dark body, two pale wings, and a tail that
 * glows. [lit] (0..1) is how brightly the tail burns.
 */
internal fun DrawScope.firefly(centre: Offset, width: Float, lit: Float = 1f, alpha: Float = 1f) {
    val u = width / 24f
    val tail = Offset(centre.x, centre.y + 5f * u)
    if (lit > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(GLOW.copy(alpha = 0.75f * lit * alpha), Color.Transparent), tail, 11f * u),
            11f * u, tail,
        )
    }
    // Wings, swept back.
    drawOval(WING, Offset(centre.x - 9.5f * u, centre.y - 5.5f * u), Size(9f * u, 5f * u), alpha = 0.75f * alpha)
    drawOval(WING, Offset(centre.x + 0.5f * u, centre.y - 5.5f * u), Size(9f * u, 5f * u), alpha = 0.75f * alpha)
    // A fine outline, so the wings still read on pale parchment.
    drawOval(BODY, Offset(centre.x - 9.5f * u, centre.y - 5.5f * u), Size(9f * u, 5f * u), alpha = 0.55f * alpha, style = Stroke(0.7f * u))
    drawOval(BODY, Offset(centre.x + 0.5f * u, centre.y - 5.5f * u), Size(9f * u, 5f * u), alpha = 0.55f * alpha, style = Stroke(0.7f * u))
    // Body and head.
    drawOval(BODY, Offset(centre.x - 2.6f * u, centre.y - 7f * u), Size(5.2f * u, 11f * u), alpha = alpha)
    drawCircle(BODY, 2.4f * u, Offset(centre.x, centre.y - 8.2f * u), alpha = alpha)
    // The lantern in its tail.
    drawOval(lerpColour(Color(0xFF8A7A3A), TAIL, lit), Offset(centre.x - 3f * u, centre.y + 2f * u), Size(6f * u, 6.5f * u), alpha = alpha)
    drawOval(BODY, Offset(centre.x - 3f * u, centre.y + 2f * u), Size(6f * u, 6.5f * u), alpha = 0.5f * alpha, style = Stroke(0.8f * u))
}

private fun lerpColour(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

@Composable
internal fun FireflyIcon(modifier: Modifier = Modifier, lit: Float = 1f) {
    Canvas(modifier) { firefly(Offset(size.width / 2f, size.height / 2f), size.minDimension, lit) }
}

/** A price: a firefly and the number, in [color]. */
@Composable
internal fun Price(n: Int, color: Color, modifier: Modifier = Modifier, size: TextUnit = TypeScale.Body, icon: Dp = 18.dp) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = fireflies(n) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FireflyIcon(Modifier.size(icon))
        Text(n.grouped(), color = color, fontSize = size, fontWeight = FontWeight.Bold, style = BodyText)
    }
}

/** Something to buy with Fireflies, waiting for the player's one confirming tap. */
internal class Offer(
    val title: String,
    val detail: String,
    val price: Int,
    /** Takes the price and hands over the thing: one of the pure functions in Shop.kt. */
    val buy: (Progress, Long) -> SpendResult,
    /** What the confirm button says, before the price. */
    val verb: String = "Buy",
    val preview: (@Composable () -> Unit)? = null,
    /** After a purchase goes through. */
    val then: () -> Unit = {},
)

/**
 * The one confirm before anything is bought: what it is, its price, and the balance after it.
 * Nothing counts down and nothing is marked down; "Not now" is always there and always as easy.
 * Short of Fireflies, it says how many more are needed, and where they come from.
 */
@Composable
internal fun ConfirmSheet(session: Session, offer: Offer) {
    val balance = session.store.progress.wallet.balance
    val after = balance - offer.price
    val short = after < 0
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .tapToDismiss { session.offer = null }
            .semantics { contentDescription = "${offer.title}. Tap outside to close." },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .padding(horizontal = 24.dp)
                .shadow(14.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
                .parchment(8.dp)
                .swallowTaps()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            offer.preview?.let {
                Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { it() }
                Spacer(Modifier.height(12.dp))
            }
            Text(offer.title, style = Display, fontSize = 24.sp, color = Craft.parchmentInk, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(offer.detail, color = Craft.parchmentInk, fontSize = TypeScale.Body, lineHeight = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            // The sums, written out like a shop slip.
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Slip("Price", offer.price)
                Slip("You have", balance)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Craft.parchmentInkSoft.copy(alpha = 0.5f)))
                if (short) {
                    Text(
                        "You need ${fireflies(-after)} more.",
                        color = Craft.parchmentInk, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                } else {
                    Slip("After", after, bold = true)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (short) {
                PrimaryButton("Get Fireflies", Modifier.fillMaxWidth()) {
                    session.offer = null
                    session.openShop(ShopTab.Fireflies)
                }
            } else {
                PrimaryButton("${offer.verb} for ${offer.price.grouped()}", Modifier.fillMaxWidth()) { session.accept(offer) }
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Not now", Modifier.fillMaxWidth()) { session.offer = null }
        }
    }
}

@Composable
private fun Slip(label: String, n: Int, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Craft.parchmentInkSoft, fontSize = TypeScale.Body, modifier = Modifier.weight(1f))
        Price(n, Craft.parchmentInk, size = if (bold) TypeScale.Heading else TypeScale.Body)
    }
}

/**
 * The balance on a small ebony plate with a brass rim: on the porch, in the shop and on the results.
 * With a [swarm] it is where earned fireflies fly to: it holds back those still in the air, rolls
 * its number up as each one lands, and bobs to catch it.
 */
@Composable
internal fun FireflyCounter(balance: Int, modifier: Modifier = Modifier, swarm: Swarm? = null, calm: Boolean = false, pending: Int = 0) {
    val bob = remember { androidx.compose.animation.core.Animatable(1f) }
    val landed = swarm?.landed ?: 0
    androidx.compose.runtime.LaunchedEffect(landed) {
        if (landed > 0 && !calm) {
            bob.snapTo(1.12f)
            bob.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
        }
    }
    // Fireflies given but not yet flown in (the daily gift, a purchase) are held back too.
    val shown = (balance - (swarm?.inFlight ?: 0) - (if (calm) 0 else pending)).coerceAtLeast(0)
    Row(
        modifier
            .height(44.dp)
            .then(
                if (swarm != null) Modifier.onGloballyPositioned { swarm.counterAt = it.boundsInRoot().let { b -> Offset(b.left + 22.dp.value * it.size.height / 44f, b.center.y) } }
                else Modifier,
            )
            .graphicsLayer { scaleX = bob.value; scaleY = bob.value }
            .plate(RoundedCornerShape(22.dp))
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FireflyIcon(Modifier.size(28.dp), lit = 0.7f + 0.3f * (bob.value - 1f) / 0.12f)
        CountUp(shown, Display, 18.sp, color = Dusk.amber, durationMs = 380, calm = calm)
    }
}

/** A scrim that closes on a tap anywhere outside what it holds. */
internal fun Modifier.tapToDismiss(onTap: () -> Unit) = pointerInput(Unit) { detectTapGestures { onTap() } }

/** Keeps taps on a sheet from reaching the scrim behind it. */
internal fun Modifier.swallowTaps() = pointerInput(Unit) { detectTapGestures { } }

/** The counter on the porch's top bar: tap it for the shop. */
@Composable
internal fun FireflyButton(session: Session, modifier: Modifier = Modifier) {
    val balance = session.store.progress.wallet.balance
    FireflyCounter(
        balance,
        swarm = session.swarm,
        calm = session.calm,
        pending = session.store.gift,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button) { session.openShop() }
            .semantics { contentDescription = "${fireflies(balance)}. Open the Porch shop." },
    )
}

/**
 * An opt-in ad button, with what it gives written on it. It shows only when an ad is loaded; once
 * today's cap for [placement] is used it says "Come back tomorrow" instead. It never plays anything
 * by itself.
 */
@Composable
internal fun AdButton(session: Session, placement: Placement, label: String, modifier: Modifier = Modifier, onTap: (android.app.Activity?) -> Unit) {
    val ads = session.ads ?: return
    val left = adsLeft(session.store.progress, placement, todaySeed())
    val activity = androidx.activity.compose.LocalActivity.current
    when {
        left <= 0 -> Text(
            "Come back tomorrow",
            style = Label, color = Dusk.inkSoft, textAlign = TextAlign.Center,
            modifier = modifier.padding(vertical = 10.dp).semantics { contentDescription = "$label. None left today: come back tomorrow." },
        )
        ads.ready && !ads.showing -> SecondaryButton(label, modifier, glyph = Glyph.Play) { onTap(activity) }
        else -> Unit
    }
}
