package app.porchlight.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Sound {
    Flip, Match1, Match2, Match3, Match4, Match5, Match6, Bass, Miss, Thunk,
    Start, Go, Tick, Buzz, Pop, Win, Lose, LevelUp, Locked, TopUp,
}

private const val RATE = 44_100

private enum class Wave { Sine, Triangle, Noise, Soft }

/**
 * One voice: a tone that may slide from [from] to [to] Hz, with a quick attack and a decay.
 * [tone] is a one-pole low-pass (0..1, lower is darker) that keeps the sound warm.
 */
private class Part(
    val atMs: Int,
    val ms: Int,
    val from: Double,
    val to: Double = from,
    val wave: Wave = Wave.Sine,
    val volume: Double = 0.5,
    val decay: Double = 4.0,
    val tone: Double = 1.0,
)

/** Every sound is synthesized when the app starts, so the game ships no audio files. */
private fun render(vararg parts: Part): FloatArray {
    val length = parts.maxOf { (it.atMs + it.ms) * RATE / 1000 } + 64
    val mix = FloatArray(length)
    val noise = Random(7)
    for (part in parts) {
        val start = part.atMs * RATE / 1000
        val count = part.ms * RATE / 1000
        val attack = min(count / 2, RATE * 5 / 1000).coerceAtLeast(1)
        var phase = 0.0
        var low = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / count
            phase += 2 * PI * (part.from + (part.to - part.from) * t) / RATE
            val raw = when (part.wave) {
                Wave.Sine -> sin(phase)
                Wave.Triangle -> 2 / PI * kotlin.math.asin(sin(phase))
                Wave.Soft -> sin(phase) + 0.25 * sin(2 * phase) + 0.1 * sin(3 * phase)
                Wave.Noise -> noise.nextDouble(-1.0, 1.0)
            }
            low += (raw - low) * part.tone
            val envelope = min(1.0, i.toDouble() / attack) * exp(-part.decay * t) * (1 - t)
            mix[start + i] += (low * envelope * part.volume).toFloat()
        }
    }
    return mix
}

/** C major pentatonic, C5 to C6: a perfect round climbs it like a tune. */
private val SCALE = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0, 1046.5)

private fun chime(base: Double) = render(
    Part(0, 380, base, wave = Wave.Sine, volume = 0.42, decay = 3.2),
    Part(0, 260, base * 2, wave = Wave.Sine, volume = 0.10, decay = 5.0),
    Part(0, 120, base * 3, wave = Wave.Triangle, volume = 0.05, decay = 8.0, tone = 0.4),
)

private fun synth(sound: Sound): FloatArray = when (sound) {
    // A soft paper flip: a short brushed noise and a low wooden knock.
    Sound.Flip -> render(
        Part(0, 45, 0.0, wave = Wave.Noise, volume = 0.28, decay = 6.0, tone = 0.18),
        Part(0, 40, 420.0, 300.0, Wave.Sine, volume = 0.22, decay = 7.0),
    )
    Sound.Match1 -> chime(SCALE[0])
    Sound.Match2 -> chime(SCALE[1])
    Sound.Match3 -> chime(SCALE[2])
    Sound.Match4 -> chime(SCALE[3])
    Sound.Match5 -> chime(SCALE[4])
    Sound.Match6 -> chime(SCALE[5])
    Sound.Bass -> render(Part(0, 420, 130.8, 128.0, Wave.Soft, volume = 0.42, decay = 3.5, tone = 0.25))
    Sound.Miss -> render(
        Part(0, 240, 196.0, 160.0, Wave.Triangle, volume = 0.3, decay = 3.0, tone = 0.12),
        Part(0, 240, 98.0, 82.0, Wave.Sine, volume = 0.3, decay = 3.0),
    )
    // The streak counter cracking: a low, dull thunk.
    Sound.Thunk -> render(
        Part(0, 160, 110.0, 60.0, Wave.Sine, volume = 0.5, decay = 5.0),
        Part(0, 30, 0.0, wave = Wave.Noise, volume = 0.2, decay = 9.0, tone = 0.1),
    )
    Sound.Start -> render(
        Part(0, 18, 0.0, wave = Wave.Noise, volume = 0.45, decay = 10.0, tone = 0.5),
        Part(10, 520, 120.0, wave = Wave.Soft, volume = 0.22, decay = 2.0, tone = 0.2),
    )
    Sound.Go -> render(
        Part(0, 140, SCALE[0], wave = Wave.Sine, volume = 0.3, decay = 3.0),
        Part(110, 260, SCALE[3], wave = Wave.Sine, volume = 0.32, decay = 3.0),
    )
    // A soft wooden click.
    Sound.Tick -> render(
        Part(0, 30, 800.0, 700.0, Wave.Sine, volume = 0.35, decay = 9.0),
        Part(0, 8, 0.0, wave = Wave.Noise, volume = 0.15, decay = 10.0, tone = 0.3),
    )
    // The dying bulb's electrical buzz.
    Sound.Buzz -> render(
        Part(0, 260, 100.0, wave = Wave.Triangle, volume = 0.16, decay = 1.0, tone = 0.35),
        Part(0, 260, 0.0, wave = Wave.Noise, volume = 0.05, decay = 1.0, tone = 0.6),
    )
    // The bulb pops and the glass tinkles.
    Sound.Pop -> render(
        Part(0, 40, 0.0, wave = Wave.Noise, volume = 0.55, decay = 8.0, tone = 0.7),
        Part(30, 180, 3520.0, wave = Wave.Sine, volume = 0.10, decay = 6.0),
        Part(90, 200, 4186.0, wave = Wave.Sine, volume = 0.08, decay = 6.0),
        Part(150, 240, 3136.0, wave = Wave.Sine, volume = 0.07, decay = 6.0),
        Part(230, 260, 4699.0, wave = Wave.Sine, volume = 0.05, decay = 6.0),
    )
    // A short rising sting.
    Sound.Win -> render(
        Part(0, 260, SCALE[2], wave = Wave.Sine, volume = 0.32, decay = 3.0),
        Part(180, 260, SCALE[3], wave = Wave.Sine, volume = 0.32, decay = 3.0),
        Part(360, 900, SCALE[5], wave = Wave.Sine, volume = 0.38, decay = 2.2),
        Part(360, 900, SCALE[2], wave = Wave.Sine, volume = 0.16, decay = 2.2),
        Part(360, 900, 130.8, wave = Wave.Soft, volume = 0.25, decay = 2.5, tone = 0.3),
    )
    // The same notes, falling.
    Sound.Lose -> render(
        Part(0, 340, SCALE[3] / 2, wave = Wave.Triangle, volume = 0.3, decay = 2.5, tone = 0.2),
        Part(260, 340, SCALE[2] / 2, wave = Wave.Triangle, volume = 0.3, decay = 2.5, tone = 0.2),
        Part(520, 900, SCALE[0] / 2, wave = Wave.Triangle, volume = 0.32, decay = 2.0, tone = 0.2),
    )
    Sound.LevelUp -> render(
        Part(0, 160, SCALE[0], wave = Wave.Triangle, volume = 0.28, decay = 2.5, tone = 0.35),
        Part(110, 160, SCALE[2], wave = Wave.Triangle, volume = 0.28, decay = 2.5, tone = 0.35),
        Part(220, 160, SCALE[3], wave = Wave.Triangle, volume = 0.28, decay = 2.5, tone = 0.35),
        Part(330, 600, SCALE[5], wave = Wave.Sine, volume = 0.36, decay = 2.5),
    )
    Sound.Locked -> render(Part(0, 120, 330.0, 290.0, Wave.Triangle, volume = 0.25, decay = 5.0, tone = 0.2))
    Sound.TopUp -> render(Part(0, 180, SCALE[4], SCALE[5], Wave.Sine, volume = 0.22, decay = 4.0))
}

/** Night crickets over a soft mains hum. It loops seamlessly every four seconds. */
private fun ambience(): FloatArray {
    val length = RATE * 4
    val out = FloatArray(length)
    val noise = Random(3)
    // Hum: 50 Hz and its harmonic. 4 s holds a whole number of cycles of both, so the loop is seamless.
    for (i in 0 until length) {
        val t = i.toDouble() / RATE
        out[i] = (0.03 * sin(2 * PI * 50 * t) + 0.012 * sin(2 * PI * 100 * t)).toFloat()
    }
    // Crickets: short trills of filtered noise around 4.5 kHz, a few per loop.
    val chirps = listOf(0.15, 0.55, 1.35, 1.75, 2.6, 3.0, 3.45)
    for (at in chirps) {
        val pitch = 4_300.0 + noise.nextDouble() * 500
        val start = (at * RATE).toInt()
        for (pulse in 0 until 4) {
            val p0 = start + pulse * RATE * 32 / 1000
            val n = RATE * 18 / 1000
            for (i in 0 until n) {
                val idx = (p0 + i) % length
                val env = sin(PI * i / n)
                val tone = sin(2 * PI * pitch * i / RATE) * (0.7 + 0.3 * noise.nextDouble())
                out[idx] += (tone * env * 0.035).toFloat()
            }
        }
    }
    return out
}

/**
 * A tiny mixer: one streaming AudioTrack, every sound a voice, so overlapping sounds never cut each
 * other off. The ambience loop runs as its own voice under the effects.
 */
class Sfx {
    private val samples = arrayOfNulls<FloatArray>(Sound.entries.size)
    private var loop: FloatArray? = null

    private class Voice(val data: FloatArray, val rate: Float, val volume: Float) {
        var at = 0f
    }

    private val voices = ArrayList<Voice>()
    private val lock = Any()
    private val random = Random(13)

    @Volatile var muted = false

    /** The ambience (crickets and hum) can be turned off separately from the effects. */
    @Volatile var music = true

    /** Only the porch and a live round play ambience. */
    @Volatile var ambient = false

    /** False while the app is in the background: the mixer stops and sleeps. */
    @Volatile var awake = true

    @Volatile private var running = true
    private var loopAt = 0
    private var loopLevel = 0f

    init {
        thread(name = "porchlight-sfx", isDaemon = true) {
            Sound.entries.forEach { samples[it.ordinal] = synth(it) }
            loop = ambience()
            runCatching { mix() }
        }
    }

    private fun mix() {
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
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), frames * 4))
            .build()
        track.play()
        val buffer = FloatArray(frames)
        val pcm = ShortArray(frames)
        val bed = loop!!
        try {
            while (running) {
                if (!awake) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                    synchronized(lock) { voices.clear() }
                    Thread.sleep(60)
                    continue
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                buffer.fill(0f)
                val wantLoop = if (ambient && music && !muted) 1f else 0f
                for (i in 0 until frames) {
                    loopLevel += (wantLoop - loopLevel) * 0.0002f
                    buffer[i] = bed[loopAt] * loopLevel
                    loopAt = (loopAt + 1) % bed.size
                }
                synchronized(lock) {
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        val v = it.next()
                        var i = 0
                        while (i < frames) {
                            val j = v.at.toInt()
                            if (j >= v.data.size - 1) break
                            val f = v.at - j
                            buffer[i] += (v.data[j] * (1 - f) + v.data[j + 1] * f) * v.volume
                            v.at += v.rate
                            i++
                        }
                        if (v.at.toInt() >= v.data.size - 1) it.remove()
                    }
                }
                for (i in 0 until frames) {
                    // A soft limiter keeps a pile of sounds from clipping.
                    val x = buffer[i]
                    val y = x / (1f + kotlin.math.abs(x) * 0.6f)
                    pcm[i] = (y * 30_000f).toInt().coerceIn(-32_768, 32_767).toShort()
                }
                track.write(pcm, 0, frames)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Plays [sound]. [vary] nudges the pitch by up to that fraction so repeats don't sound stamped. */
    fun play(sound: Sound, vary: Float = 0f, volume: Float = 1f) {
        if (muted) return
        val data = samples[sound.ordinal] ?: return
        val rate = if (vary > 0f) 1f + (random.nextFloat() * 2f - 1f) * vary else 1f
        synchronized(lock) {
            if (voices.size >= 16) voices.removeAt(0)
            voices += Voice(data, rate, volume)
        }
    }

    /** The chime for the [run]th pair in a row (1-based): it climbs the scale. */
    fun matchFor(run: Int) = when (run.coerceIn(1, 6)) {
        1 -> Sound.Match1
        2 -> Sound.Match2
        3 -> Sound.Match3
        4 -> Sound.Match4
        5 -> Sound.Match5
        else -> Sound.Match6
    }

    fun release() {
        running = false
    }
}
