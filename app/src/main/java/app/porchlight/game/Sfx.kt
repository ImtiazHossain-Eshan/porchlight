package app.porchlight.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Sound { Flip, Match1, Match2, Match3, Match4, Miss, Start, Go, Tick, Win, Lose, LevelUp, Locked }

private const val RATE = 44_100

private enum class Wave { Sine, Square, Triangle, Noise }

/** One voice: a tone that may slide from [from] to [to] Hz, with a quick attack and a decay. */
private class Part(
    val atMs: Int,
    val ms: Int,
    val from: Double,
    val to: Double = from,
    val wave: Wave = Wave.Sine,
    val volume: Double = 0.5,
    val decay: Double = 4.0,
)

/** Every sound is synthesized when the app starts, so the game ships no audio files. */
private fun render(vararg parts: Part): ShortArray {
    val length = parts.maxOf { (it.atMs + it.ms) * RATE / 1000 } + 64
    val mix = DoubleArray(length)
    val noise = Random(7)
    for (part in parts) {
        val start = part.atMs * RATE / 1000
        val count = part.ms * RATE / 1000
        val attack = min(count / 2, RATE * 4 / 1000)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            phase += 2 * PI * (part.from + (part.to - part.from) * t) / RATE
            val raw = when (part.wave) {
                Wave.Sine -> sin(phase)
                Wave.Square -> if (sin(phase) >= 0) 0.6 else -0.6
                Wave.Triangle -> 2 / PI * kotlin.math.asin(sin(phase))
                Wave.Noise -> noise.nextDouble(-1.0, 1.0)
            }
            val envelope = min(1.0, i.toDouble() / attack.coerceAtLeast(1)) * exp(-part.decay * t) * (1 - t)
            mix[start + i] += raw * envelope * part.volume
        }
    }
    return ShortArray(length) { (mix[it].coerceIn(-1.0, 1.0) * 30_000).toInt().toShort() }
}

private fun chime(base: Double) = render(
    Part(0, 90, base, wave = Wave.Triangle, volume = 0.45),
    Part(70, 220, base * 1.5, wave = Wave.Sine, volume = 0.5, decay = 3.0),
    Part(70, 220, base * 3, wave = Wave.Sine, volume = 0.12, decay = 5.0),
)

private fun synth(sound: Sound): ShortArray = when (sound) {
    Sound.Flip -> render(
        Part(0, 12, 0.0, wave = Wave.Noise, volume = 0.25, decay = 8.0),
        Part(0, 60, 1500.0, 900.0, Wave.Sine, volume = 0.22, decay = 6.0),
    )
    Sound.Match1 -> chime(660.0)
    Sound.Match2 -> chime(740.0)
    Sound.Match3 -> chime(830.0)
    Sound.Match4 -> chime(990.0)
    Sound.Miss -> render(
        Part(0, 170, 220.0, 150.0, Wave.Square, volume = 0.22, decay = 2.5),
        Part(0, 170, 110.0, 80.0, Wave.Sine, volume = 0.3, decay = 2.5),
    )
    Sound.Start -> render(
        Part(0, 18, 0.0, wave = Wave.Noise, volume = 0.5, decay = 10.0),
        Part(10, 420, 120.0, wave = Wave.Triangle, volume = 0.25, decay = 2.0),
        Part(10, 420, 240.0, wave = Wave.Sine, volume = 0.12, decay = 2.0),
    )
    Sound.Go -> render(Part(0, 160, 880.0, 1320.0, Wave.Triangle, volume = 0.4, decay = 3.0))
    Sound.Tick -> render(Part(0, 45, 1900.0, wave = Wave.Square, volume = 0.18, decay = 9.0))
    Sound.Win -> render(
        Part(0, 120, 523.0, wave = Wave.Triangle, volume = 0.45),
        Part(110, 120, 659.0, wave = Wave.Triangle, volume = 0.45),
        Part(220, 120, 784.0, wave = Wave.Triangle, volume = 0.45),
        Part(330, 520, 1047.0, wave = Wave.Triangle, volume = 0.5, decay = 2.5),
        Part(330, 520, 1568.0, wave = Wave.Sine, volume = 0.15, decay = 3.0),
    )
    Sound.Lose -> render(
        Part(0, 700, 440.0, 160.0, Wave.Triangle, volume = 0.4, decay = 2.0),
        Part(0, 60, 0.0, wave = Wave.Noise, volume = 0.3, decay = 9.0),
    )
    Sound.LevelUp -> render(
        Part(0, 90, 784.0, wave = Wave.Square, volume = 0.25),
        Part(90, 90, 1047.0, wave = Wave.Square, volume = 0.25),
        Part(180, 360, 1319.0, wave = Wave.Square, volume = 0.25, decay = 3.0),
        Part(180, 360, 2638.0, wave = Wave.Sine, volume = 0.08, decay = 4.0),
    )
    Sound.Locked -> render(Part(0, 90, 300.0, 260.0, Wave.Square, volume = 0.18, decay = 5.0))
}

class Sfx {
    private val tracks = ConcurrentHashMap<Sound, AudioTrack>()

    @Volatile
    var muted = false

    init {
        thread(name = "porchlight-sfx", isDaemon = true) {
            Sound.entries.forEach { sound ->
                runCatching { tracks[sound] = track(synth(sound)) }
            }
        }
    }

    private fun track(samples: ShortArray): AudioTrack {
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
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    fun play(sound: Sound) {
        if (muted) return
        val track = tracks[sound] ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun matchFor(multiplier: Int) = when (multiplier) {
        1 -> Sound.Match1
        2 -> Sound.Match2
        3 -> Sound.Match3
        else -> Sound.Match4
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }
}
