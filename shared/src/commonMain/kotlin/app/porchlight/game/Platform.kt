package app.porchlight.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback

/**
 * Everything the game needs from the phone or the browser it runs on. The host app makes one and
 * hands it to [PorchlightApp]; the game never reaches past it to the platform.
 */
interface Platform {
    /** Where the save lives. Android keeps its SharedPreferences file and keys. */
    val storage: Storage

    /** Runtime shaders (card foil, the bulb's glow, heat shimmer). Without them every effect has a plainer fallback. */
    val shaders: Boolean

    /** Gaussian blur, for the bloom and the glow on the last seconds. */
    val blur: Boolean

    /** Starts playing what [engine] mixes, or returns null where there is no sound. */
    fun startAudio(engine: AudioEngine): AudioOutput?

    /** The phone's tilt, or null where there is no sensor (or it isn't allowed). */
    fun tiltSensor(): TiltSensor?

    /** Haptics of its own, or null to use Compose's [androidx.compose.ui.platform.LocalHapticFeedback]. */
    val haptics: HapticFeedback? get() = null

    fun copyText(text: String)

    /** Offers [text] to other apps. */
    fun shareText(text: String)

    /** Offers [image] (as a PNG named [fileName]) with [text]; falls back to the text alone if it can't. */
    suspend fun shareImage(image: ImageBitmap, fileName: String, text: String)

    /**
     * A round is live ([on]) or over. iOS hides the status bar and home indicator and defers edge
     * swipes while it is; Android and the web leave the screen as it is.
     */
    fun setImmersive(on: Boolean) {}
}

/** A small key-value save, read once at launch and written after every change. */
interface Storage {
    fun contains(key: String): Boolean
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String): String?
    fun getStringSet(key: String): Set<String>?

    /** Applies a batch of changes together. */
    fun edit(changes: Editor.() -> Unit)

    interface Editor {
        fun putInt(key: String, value: Int)
        fun putLong(key: String, value: Long)
        fun putBoolean(key: String, value: Boolean)

        /** A null value removes the key. */
        fun putString(key: String, value: String?)
        fun putStringSet(key: String, value: Set<String>)
        fun remove(key: String)
    }
}

/** The game's mixer, as the platform's audio output sees it. Mono, [SAMPLE_RATE] Hz. */
interface AudioEngine {
    /** Builds every sound. Slow (a few hundred milliseconds), so call it off the main thread where there is one. */
    fun prepare()

    /** Fills [out] with the next samples, each -1..1. Returns false until [prepare] has finished. */
    fun render(out: FloatArray): Boolean

    /** False while the app is in the background: the output should pause. */
    val awake: Boolean

    /** False once the game is gone: the output should stop for good. */
    val running: Boolean

    /** Drops every sound still playing. */
    fun silence()
}

/** A platform's playing stream. */
interface AudioOutput {
    /** The app came back to the foreground (or a tap unlocked audio on the web). */
    fun resume() {}
    fun release()
}

const val SAMPLE_RATE = 44_100

/** Pitch and roll in radians, as the phone reports them. */
interface TiltSensor {
    fun start(onReading: (pitch: Float, roll: Float) -> Unit)
    fun stop()
}

/** Options a debug launch can set, to check a level, an XP total or the fallbacks quickly. */
data class DebugLaunch(
    /** Start at a lower quality: 1 half-size bloom, 2 also fewer particles. */
    val quality: Int = 0,
    /** A fixed tilt, each axis -1..1, instead of the sensor. */
    val fakeTilt: Pair<Float, Float>? = null,
    /** Set the XP, to see the level emblem at a given level. */
    val setXp: Int? = null,
    /** Open every level up to this one and pick it. */
    val level: Int? = null,
    /** Open this screen at launch (Levels, Daily, Moths, Badges, Settings, or Round to start one), for screenshots. */
    val screen: String? = null,
)

internal val LocalPlatform = staticCompositionLocalOf<Platform> { error("No Platform: call PorchlightApp(platform)") }

// Small platform hooks the screens call directly.

/** A monotonic clock in milliseconds; only differences between readings mean anything. */
internal expect fun now(): Long

internal expect fun log(message: String)

/** Back on Android. iOS and the web have no back key: every screen shows its own way out instead. */
@Composable
internal expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** The phone asks for less motion: animations off on Android, Reduce Motion on iOS, prefers-reduced-motion on the web. */
@Composable
internal expect fun systemReduceMotion(): Boolean

/** The screen's refresh rate in Hz. */
@Composable
internal expect fun displayRefreshRate(): Float

/** True while Android is recreating the screen (a rotation), when a pause shouldn't stop the round. */
@Composable
internal expect fun rememberChangingConfiguration(): () -> Boolean
