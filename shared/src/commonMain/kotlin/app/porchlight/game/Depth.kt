package app.porchlight.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/**
 * How far the phone is tilted from where the player is holding it, each axis -1..1. Only draw code
 * reads it, so a tilt redraws without recomposing.
 */
internal class Tilt {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
}

/** One radian of tilt is far more than a player leans a phone: this much reads as "fully tilted". */
private const val FULL_TILT = 0.35f

/**
 * Reads the platform's tilt sensor (on Android the game rotation vector: gyro and accelerometer, no
 * compass), smooths it, and measures it against a resting position that slowly follows the player,
 * so the scene sits level however the phone is held. Listens only while the app is resumed and
 * [enabled] is true.
 */
@Composable
internal fun rememberTilt(enabled: Boolean): Tilt {
    val tilt = remember { Tilt() }
    val platform = LocalPlatform.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle) {
        val sensor = platform.tiltSensor()
        val fake = Gpu.fakeTilt
        if (!enabled || sensor == null || fake != null) {
            tilt.x = if (enabled) fake?.first ?: 0f else 0f
            tilt.y = if (enabled) fake?.second ?: 0f else 0f
            return@DisposableEffect onDispose { }
        }
        var restPitch = Float.NaN
        var restRoll = Float.NaN
        fun reading(pitch: Float, roll: Float) {
            if (restPitch.isNaN()) {
                restPitch = pitch
                restRoll = roll
            }
            // The resting position drifts towards how the phone is held now, over a few seconds.
            restPitch += (pitch - restPitch) * 0.01f
            restRoll += (roll - restRoll) * 0.01f
            val wantX = ((roll - restRoll) / FULL_TILT).coerceIn(-1f, 1f)
            val wantY = ((pitch - restPitch) / FULL_TILT).coerceIn(-1f, 1f)
            // Low-pass: hand tremor never reaches the screen.
            val nextX = tilt.x + (wantX - tilt.x) * 0.12f
            val nextY = tilt.y + (wantY - tilt.y) * 0.12f
            if (abs(nextX - tilt.x) > 0.001f) tilt.x = nextX
            if (abs(nextY - tilt.y) > 0.001f) tilt.y = nextY
        }
        fun listen() = sensor.start(::reading)
        fun stop() {
            sensor.stop()
            restPitch = Float.NaN
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> listen()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) listen()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
            tilt.x = 0f
            tilt.y = 0f
        }
    }
    return tilt
}

/**
 * Watches how long frames take. If more than a quarter of the frames in a two-second window run
 * over 1.5 times the screen's frame time, it steps [Gpu.quality] down one level: first the bloom
 * drops to half resolution, then the particles thin out. It never steps back up in a session.
 */
internal class FrameWatch(private val frameSeconds: Float) {
    private var frames = 0
    private var slow = 0
    private var elapsed = 0f
    private var warmup = 2f

    fun frame(dt: Float) {
        if (dt <= 0f || dt >= 0.05f) return // paused, or the first frame back
        if (warmup > 0f) {
            warmup -= dt
            return
        }
        frames++
        if (dt > frameSeconds * 1.5f) slow++
        elapsed += dt
        if (elapsed < 2f) return
        if (slow * 4 > frames && Gpu.quality < 2) {
            Gpu.quality++
            log("Frames running long ($slow of $frames slow): quality down to ${Gpu.quality}")
            warmup = 2f
        }
        frames = 0
        slow = 0
        elapsed = 0f
    }
}
