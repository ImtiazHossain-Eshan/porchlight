@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.porchlight.game

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSourceNode
import platform.AVFAudio.setActive
import platform.CoreGraphics.CGRectGetMidX
import platform.CoreGraphics.CGRectGetMidY
import platform.CoreGraphics.CGRectMake
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIPasteboard
import platform.UIKit.UISelectionFeedbackGenerator
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * iOS's side of [Platform]: NSUserDefaults with the Android keys, AVAudioEngine in the ambient
 * category (it mixes with the player's music and obeys the silent switch), CoreMotion at 60 Hz,
 * UIKit feedback generators and the system share sheet.
 */
class IosPlatform(private val onImmersive: (Boolean) -> Unit = {}) : Platform {
    override val storage: Storage = DefaultsStorage(NSUserDefaults.standardUserDefaults)

    /** Skia on Metal runs SkSL runtime shaders and blur on every iPhone that runs iOS 16. */
    override val shaders = true
    override val blur = true

    override fun startAudio(engine: AudioEngine): AudioOutput? = runCatching { EngineOutput(engine) }
        .onFailure { log("No audio: ${it.message}") }
        .getOrNull()

    override fun tiltSensor(): TiltSensor? {
        val motion = CMMotionManager()
        return if (motion.deviceMotionAvailable) MotionTilt(motion) else null
    }

    override val haptics: HapticFeedback = UIKitHaptics()

    override fun setImmersive(on: Boolean) = onImmersive(on)

    override fun copyText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    override fun shareText(text: String) = present(listOf(text))

    override suspend fun shareImage(image: ImageBitmap, fileName: String, text: String) {
        val png = image.encodePng()
        val url = png?.let { writeTemp(it, fileName) }
        if (url == null) shareText(text) else present(listOf(url, text))
    }

    private fun writeTemp(bytes: ByteArray, fileName: String): NSURL? {
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        return if (data.writeToURL(url, atomically = true)) url else null
    }

    /** The system share sheet, from the top screen. On iPad it pops over from the middle of the screen. */
    private fun present(items: List<Any>) {
        val top = topController() ?: return
        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        sheet.popoverPresentationController?.let { pop ->
            pop.sourceView = top.view
            top.view.bounds.let { b -> pop.sourceRect = CGRectMake(CGRectGetMidX(b), CGRectGetMidY(b), 0.0, 0.0) }
            pop.permittedArrowDirections = 0u
        }
        top.presentViewController(sheet, animated = true, completion = null)
    }

    private fun topController(): UIViewController? {
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow
            ?: UIApplication.sharedApplication.windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        var top = window?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}

/** [Storage] over NSUserDefaults. String sets are kept as arrays of strings. */
private class DefaultsStorage(private val defaults: NSUserDefaults) : Storage {
    override fun contains(key: String) = defaults.objectForKey(key) != null
    override fun getInt(key: String, default: Int) = (defaults.objectForKey(key) as? NSNumber)?.intValue ?: default
    override fun getLong(key: String, default: Long) = (defaults.objectForKey(key) as? NSNumber)?.longLongValue ?: default
    override fun getBoolean(key: String, default: Boolean) = (defaults.objectForKey(key) as? NSNumber)?.boolValue ?: default
    override fun getString(key: String): String? = defaults.objectForKey(key) as? String
    override fun getStringSet(key: String): Set<String>? =
        (defaults.objectForKey(key) as? List<*>)?.filterIsInstance<String>()?.toSet()

    override fun edit(changes: Storage.Editor.() -> Unit) {
        object : Storage.Editor {
            override fun putInt(key: String, value: Int) = defaults.setInteger(value.toLong(), key)
            override fun putLong(key: String, value: Long) = defaults.setObject(NSNumber(longLong = value), key)
            override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, key)
            override fun putString(key: String, value: String?) {
                if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
            }
            override fun putStringSet(key: String, value: Set<String>) = defaults.setObject(value.toList(), key)
            override fun remove(key: String) = defaults.removeObjectForKey(key)
        }.changes()
    }
}

/**
 * One AVAudioEngine with a source node that pulls the game's mix. The sounds are built on a
 * background queue first; until then the node plays silence.
 */
private class EngineOutput(private val engine: AudioEngine) : AudioOutput {
    private val av = AVAudioEngine()
    /** Resized only when iOS changes its buffer size, so the audio thread doesn't allocate. */
    private var buffer = FloatArray(512)
    private val interruption: Any

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        session.setActive(true, error = null)

        val format = AVAudioFormat(standardFormatWithSampleRate = SAMPLE_RATE.toDouble(), channels = 1u)
        val source = AVAudioSourceNode(format = format) { isSilence, _, frameCount, data ->
            val frames = frameCount.toInt()
            if (buffer.size != frames) buffer = FloatArray(frames)
            val chunk = buffer
            val out = data?.pointed?.mBuffers?.get(0)?.mData?.reinterpret<FloatVar>()
            val playing = engine.running && engine.awake && engine.render(chunk)
            if (!engine.awake) engine.silence()
            if (out != null) for (i in 0 until frames) out[i] = if (playing) chunk[i] else 0f
            if (!playing) isSilence?.pointed?.value = true
            0
        }
        av.attachNode(source)
        av.connect(source, to = av.mainMixerNode, format = format)
        av.prepare()

        // A phone call or another app's audio stops the engine; start it again when that ends.
        interruption = NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> resume() }

        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            engine.prepare()
            dispatch_async(dispatch_get_main_queue()) { resume() }
        }
    }

    override fun resume() {
        if (!engine.running || av.running) return
        AVAudioSession.sharedInstance().setActive(true, error = null)
        av.startAndReturnError(null)
    }

    override fun release() {
        NSNotificationCenter.defaultCenter.removeObserver(interruption)
        av.stop()
    }
}

/**
 * CoreMotion's attitude at 60 Hz. Its pitch and roll turn the other way from Android's
 * orientation angles, so both are flipped to match the Android build.
 */
private class MotionTilt(private val motion: CMMotionManager) : TiltSensor {
    override fun start(onReading: (pitch: Float, roll: Float) -> Unit) {
        motion.deviceMotionUpdateInterval = 1.0 / 60.0
        motion.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            val attitude = data?.attitude ?: return@startDeviceMotionUpdatesToQueue
            onReading(-attitude.pitch.toFloat(), -attitude.roll.toFloat())
        }
    }

    override fun stop() = motion.stopDeviceMotionUpdates()
}

/** The game's haptic moments on UIKit's feedback generators. */
private class UIKitHaptics : HapticFeedback {
    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val notify = UINotificationFeedbackGenerator()
    private val select = UISelectionFeedbackGenerator()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
            HapticFeedbackType.Confirm -> notify.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticFeedbackType.Reject -> notify.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            HapticFeedbackType.LongPress -> heavy.impactOccurred()
            HapticFeedbackType.SegmentTick -> light.impactOccurred()
            HapticFeedbackType.TextHandleMove -> select.selectionChanged()
            else -> medium.impactOccurred()
        }
    }
}
