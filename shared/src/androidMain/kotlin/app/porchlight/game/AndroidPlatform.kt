package app.porchlight.game

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

/**
 * Android's side of [Platform]: the SharedPreferences file "porchlight" with the same keys as ever,
 * one streaming AudioTrack, the game rotation vector, and share intents through a FileProvider.
 * [pretendApi] lets a debug build act like an older phone, so the fallbacks can be checked on a new one.
 */
class AndroidPlatform(private val activity: Activity, pretendApi: Int = 0) : Platform {
    private val api = if (pretendApi > 0) minOf(pretendApi, Build.VERSION.SDK_INT) else Build.VERSION.SDK_INT

    override val storage: Storage = PreferencesStorage(activity.getSharedPreferences("porchlight", Context.MODE_PRIVATE))

    /** AGSL shaders need Android 13 (API 33). */
    override val shaders = api >= 33

    /** Blur needs Android 12 (API 31). */
    override val blur = api >= 31

    override fun startAudio(engine: AudioEngine): AudioOutput {
        thread(name = "porchlight-sfx", isDaemon = true) {
            engine.prepare()
            runCatching { mix(engine) }
        }
        // The thread stops itself once the engine stops running.
        return object : AudioOutput {
            override fun release() = Unit
        }
    }

    private fun mix(engine: AudioEngine) {
        val frames = 512
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), frames * 4))
            .build()
        track.play()
        val buffer = FloatArray(frames)
        val pcm = ShortArray(frames)
        try {
            while (engine.running) {
                if (!engine.awake) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    engine.silence()
                    Thread.sleep(60)
                    continue
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                engine.render(buffer)
                for (i in 0 until frames) {
                    pcm[i] = (buffer[i] * 30_000f).toInt().coerceIn(-32_768, 32_767).toShort()
                }
                track.write(pcm, 0, frames)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    override fun tiltSensor(): TiltSensor? {
        val sensors = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: return null
        return RotationTilt(sensors, sensor)
    }

    /** Reads the game rotation vector (gyro and accelerometer, no compass) as pitch and roll. */
    private class RotationTilt(private val sensors: SensorManager, private val sensor: Sensor) : TiltSensor {
        private val matrix = FloatArray(9)
        private val angles = FloatArray(3)
        private var listener: SensorEventListener? = null

        override fun start(onReading: (pitch: Float, roll: Float) -> Unit) {
            stop()
            val next = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    SensorManager.getOrientation(matrix, angles)
                    onReading(angles[1], angles[2])
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            listener = next
            sensors.registerListener(next, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        override fun stop() {
            listener?.let { sensors.unregisterListener(it) }
            listener = null
        }
    }

    override fun copyText(text: String) {
        runCatching {
            val clipboard = activity.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Porchlight daily", text))
        }
    }

    /** The text-only share, as before: also the fallback if the image can't be made. */
    override fun shareText(text: String) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            activity.startActivity(Intent.createChooser(send, "Share today's porch"))
        }
    }

    /**
     * Turns the recorded card into a PNG in cache/share/, then offers it with the text line. Any
     * failure along the way falls back to sharing the text alone; nothing here may crash the game.
     */
    override suspend fun shareImage(image: ImageBitmap, fileName: String, text: String) {
        val context = activity
        val uri = runCatching {
            val bitmap = image.asAndroidBitmap()
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, fileName)
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            }
        }.getOrNull()
        if (uri == null) {
            shareText(text)
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
        }.onFailure { shareText(text) }
    }
}

/** [Storage] over SharedPreferences, so an existing save reads exactly as it was written. */
private class PreferencesStorage(private val prefs: SharedPreferences) : Storage {
    override fun contains(key: String) = prefs.contains(key)
    override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)

    override fun edit(changes: Storage.Editor.() -> Unit) {
        val edit = prefs.edit()
        object : Storage.Editor {
            override fun putInt(key: String, value: Int) { edit.putInt(key, value) }
            override fun putLong(key: String, value: Long) { edit.putLong(key, value) }
            override fun putBoolean(key: String, value: Boolean) { edit.putBoolean(key, value) }
            override fun putString(key: String, value: String?) { edit.putString(key, value) }
            override fun putStringSet(key: String, value: Set<String>) { edit.putStringSet(key, value) }
            override fun remove(key: String) { edit.remove(key) }
        }.changes()
        edit.apply()
    }
}
