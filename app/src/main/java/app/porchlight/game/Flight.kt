package app.porchlight.game

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Things that fly from one screen to the next instead of fading: a moth from the porch into its
 * jar, a menu tile into the screen's title plaque, the porch jar up into the round's bulb, the
 * level emblem from the scorecard home to the porch.
 *
 * Every screen is shown inside one shared-transition layout; each screen's content, and the
 * scorecard, provide their own animation scope here. With reduce motion on there is none, and
 * [flies] does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal class Flights(val layout: SharedTransitionScope, val scope: AnimatedVisibilityScope)

internal val LocalFlights = staticCompositionLocalOf<Flights?> { null }

/** How a flight moves: a spring that lands with a little settle. */
private val SETTLE = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
}

/** A moth's flight: up over the top in an arc, as a moth would go, rather than a straight slide. */
private val ARC = BoundsTransform { from, to ->
    keyframes {
        durationMillis = 620
        val lift = maxOf(from.height, to.height) * 0.5f + 24f
        val mid = Rect(
            Offset((from.left + to.left) / 2f, minOf(from.top, to.top) - lift),
            androidx.compose.ui.geometry.Size((from.width + to.width) / 2f, (from.height + to.height) / 2f),
        )
        from at 0 using FastOutSlowInEasing
        mid at 300 using FastOutSlowInEasing
        to at 620
    }
}

/** How a shared thing changes over: [Element] keeps one face, [Bounds] crossfades two, [Arc] is a moth. */
internal enum class Fly { Element, Bounds, Arc }

/**
 * Marks this as the [key] that flies between screens. Two screens using the same key trade it:
 * it lifts off the one leaving and lands where the other has it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.flies(key: String?, how: Fly = Fly.Element): Modifier {
    val flights = LocalFlights.current ?: return this
    if (key == null) return this
    return with(flights.layout) {
        val state = rememberSharedContentState(key)
        when (how) {
            Fly.Element -> this@flies.sharedElement(state, flights.scope, boundsTransform = SETTLE)
            Fly.Arc -> this@flies.sharedElement(state, flights.scope, boundsTransform = ARC)
            Fly.Bounds -> this@flies.sharedBounds(state, flights.scope, boundsTransform = SETTLE)
        }
    }
}
