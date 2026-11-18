package app.porchlight.game

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * The web's side of [Platform]: localStorage, Web Audio, DeviceOrientationEvent, and the Web Share
 * API with a download fallback. Built for Safari on iPhone first; works in any current browser.
 */
class WebPlatform : Platform {
    override val storage: Storage = LocalStorage("porchlight:")

    /**
     * Skia on WebGL runs SkSL runtime shaders and blur. `?noshaders` or `?noblur` in the address
     * switches them off, to check the fallbacks (like Android's `pretendApi` debug extra).
     */
    override val shaders = !pageQueryHas("noshaders")
    override val blur = !pageQueryHas("noblur")

    override fun startAudio(engine: AudioEngine): AudioOutput? {
        val handle = webAudioStart(
            SAMPLE_RATE,
            AUDIO_FRAMES,
            fill = {
                val ok = engine.running && engine.awake && engine.render(audioBuffer)
                if (!engine.awake) engine.silence()
                ok
            },
            sample = { i -> audioBuffer[i].toDouble() },
        ) ?: return null
        // Building the sounds takes a moment, so it waits until the first frames are on screen.
        afterDelay(300) { engine.prepare() }
        return object : AudioOutput {
            override fun resume() = webAudioResume(handle)
            override fun release() = webAudioClose(handle)
        }
    }

    private val audioBuffer = FloatArray(AUDIO_FRAMES)

    override fun tiltSensor(): TiltSensor = OrientationTilt()

    /** Safari on iPhone has no vibration API, so the web plays no haptics. */
    override val haptics: HapticFeedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }

    override fun copyText(text: String) = webCopyText(text)

    override fun shareText(text: String) = webShareText(text)

    override suspend fun shareImage(image: ImageBitmap, fileName: String, text: String) {
        val png = image.encodePng()
        if (png == null) {
            shareText(text)
            return
        }
        val bytes = newBytes(png.size)
        for (i in png.indices) setByte(bytes, i, png[i].toInt())
        webSharePng(bytes, fileName, text)
    }
}

private const val AUDIO_FRAMES = 2048

/** [Storage] over localStorage, every key under [prefix]. Works (in memory only) where storage is blocked. */
private class LocalStorage(private val prefix: String) : Storage {
    private val memory = HashMap<String, String>()

    private fun read(key: String): String? = storageGet(prefix + key) ?: memory[key]

    private fun write(key: String, value: String?) {
        if (value == null) {
            memory.remove(key)
            storageRemove(prefix + key)
        } else {
            memory[key] = value
            storageSet(prefix + key, value)
        }
    }

    override fun contains(key: String) = read(key) != null
    override fun getInt(key: String, default: Int) = read(key)?.toIntOrNull() ?: default
    override fun getLong(key: String, default: Long) = read(key)?.toLongOrNull() ?: default
    override fun getBoolean(key: String, default: Boolean) = read(key)?.toBooleanStrictOrNull() ?: default
    override fun getString(key: String): String? = read(key)?.takeIf { it.startsWith(TEXT) }?.removePrefix(TEXT)
    override fun getStringSet(key: String): Set<String>? {
        val raw = read(key)?.takeIf { it.startsWith(SET) }?.removePrefix(SET) ?: return null
        return if (raw.isEmpty()) emptySet() else raw.split(SEPARATOR).toSet()
    }

    override fun edit(changes: Storage.Editor.() -> Unit) {
        object : Storage.Editor {
            override fun putInt(key: String, value: Int) = write(key, value.toString())
            override fun putLong(key: String, value: Long) = write(key, value.toString())
            override fun putBoolean(key: String, value: Boolean) = write(key, value.toString())
            override fun putString(key: String, value: String?) = write(key, value?.let { TEXT + it })
            override fun putStringSet(key: String, value: Set<String>) = write(key, SET + value.joinToString(SEPARATOR))
            override fun remove(key: String) = write(key, null)
        }.changes()
    }

    private companion object {
        // Strings and sets carry a mark, so a number is never read back as text or the other way round.
        const val TEXT = "s:"
        const val SET = "S:"
        const val SEPARATOR = "\u001E"
    }
}

/**
 * The browser's device orientation. Safari asks the player first, and only from a tap, so the
 * request waits for the first tap after the round starts listening. beta and gamma turn the other
 * way from Android's pitch and roll, so both are flipped to match the Android build.
 */
private class OrientationTilt : TiltSensor {
    private var handle: JsAny? = null

    override fun start(onReading: (pitch: Float, roll: Float) -> Unit) {
        stop()
        handle = orientationStart { beta, gamma ->
            onReading(-(beta * DEG).toFloat(), -(gamma * DEG).toFloat())
        }
    }

    override fun stop() {
        handle?.let { orientationStop(it) }
        handle = null
    }

    private companion object {
        const val DEG = kotlin.math.PI / 180.0
    }
}

// JavaScript glue. Each function is one small piece of browser API.

private fun pageQueryHas(flag: String): Boolean = js("new URLSearchParams(window.location.search).has(flag)")

private fun storageGet(key: String): String? =
    js("(function(){ try { return window.localStorage.getItem(key); } catch (e) { return null; } })()")

private fun storageSet(key: String, value: String): Unit =
    js("(function(){ try { window.localStorage.setItem(key, value); } catch (e) {} })()")

private fun storageRemove(key: String): Unit =
    js("(function(){ try { window.localStorage.removeItem(key); } catch (e) {} })()")

private fun afterDelay(ms: Int, run: () -> Unit): Unit = js("setTimeout(run, ms)")

/**
 * A Web Audio context playing a script processor that pulls [frames] samples at a time from
 * [fill] and [sample]. Safari starts it suspended: the first tap anywhere resumes it, and it
 * suspends while the page is hidden. Returns null where there is no Web Audio.
 */
private fun webAudioStart(rate: Int, frames: Int, fill: () -> Boolean, sample: (Int) -> Double): JsAny? = js(
    """(function(){
        var Ctx = window.AudioContext || window.webkitAudioContext;
        if (!Ctx) return null;
        var ctx;
        try { ctx = new Ctx({ sampleRate: rate }); } catch (e) { ctx = new Ctx(); }
        var node = ctx.createScriptProcessor(frames, 1, 1);
        node.onaudioprocess = function(e) {
            var out = e.outputBuffer.getChannelData(0);
            var ok = fill();
            for (var i = 0; i < out.length; i++) out[i] = ok ? sample(i) : 0;
        };
        node.connect(ctx.destination);
        var unlock = function() {
            if (ctx.state !== 'running' && document.visibilityState === 'visible') ctx.resume();
        };
        ['pointerdown', 'touchend', 'keydown'].forEach(function(t) { document.addEventListener(t, unlock, true); });
        var visible = function() {
            if (document.visibilityState === 'visible') ctx.resume(); else ctx.suspend();
        };
        document.addEventListener('visibilitychange', visible);
        window.addEventListener('pageshow', visible);
        var handle = { ctx: ctx, node: node, unlock: unlock, visible: visible };
        window.porchlightAudio = handle; // for checking its state from the console
        return handle;
    })()""",
)

private fun webAudioResume(handle: JsAny): Unit =
    js("(function(){ if (document.visibilityState === 'visible' && handle.ctx.state !== 'running') handle.ctx.resume(); })()")

private fun webAudioClose(handle: JsAny): Unit = js(
    """(function(){
        ['pointerdown', 'touchend', 'keydown'].forEach(function(t) { document.removeEventListener(t, handle.unlock, true); });
        document.removeEventListener('visibilitychange', handle.visible);
        window.removeEventListener('pageshow', handle.visible);
        try { handle.node.disconnect(); handle.ctx.close(); } catch (e) {}
    })()""",
)

/** Listens to deviceorientation, asking Safari's permission on the next tap if it needs one. */
private fun orientationStart(onReading: (Double, Double) -> Unit): JsAny = js(
    """(function(){
        var h = { listening: false };
        h.listener = function(e) { if (e.beta != null && e.gamma != null) onReading(e.beta, e.gamma); };
        h.listen = function() { if (!h.listening) { window.addEventListener('deviceorientation', h.listener); h.listening = true; } };
        var D = window.DeviceOrientationEvent;
        if (D && typeof D.requestPermission === 'function') {
            h.ask = function() {
                document.removeEventListener('touchend', h.ask, true);
                D.requestPermission().then(function(state) { if (state === 'granted') h.listen(); }).catch(function() {});
            };
            document.addEventListener('touchend', h.ask, true);
        } else if (D) {
            h.listen();
        }
        return h;
    })()""",
)

private fun orientationStop(handle: JsAny): Unit = js(
    """(function(){
        if (handle.ask) document.removeEventListener('touchend', handle.ask, true);
        window.removeEventListener('deviceorientation', handle.listener);
        handle.listening = false;
    })()""",
)

private fun newBytes(size: Int): JsAny = js("new Uint8Array(size)")

private fun setByte(bytes: JsAny, index: Int, value: Int): Unit = js("bytes[index] = value")

/** Copies [text], with the old textarea trick where the Clipboard API isn't allowed. */
private fun webCopyText(text: String): Unit = js(
    """(function(){
        var fallback = function() {
            var t = document.createElement('textarea');
            t.value = text; t.setAttribute('readonly', ''); t.style.position = 'fixed'; t.style.opacity = '0';
            document.body.appendChild(t); t.select();
            try { document.execCommand('copy'); } catch (e) {}
            document.body.removeChild(t);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).catch(fallback);
        else fallback();
    })()""",
)

private fun webShareText(text: String): Unit = js(
    """(function(){
        if (navigator.share) navigator.share({ text: text }).catch(function() {});
        else if (navigator.clipboard) navigator.clipboard.writeText(text).catch(function() {});
    })()""",
)

/**
 * Shares the PNG with the text through the share sheet. Where files can't be shared (or the share
 * is refused), the PNG downloads and the text goes to the clipboard instead.
 */
private fun webSharePng(bytes: JsAny, name: String, text: String): Unit = js(
    """(function(){
        var blob = new Blob([bytes], { type: 'image/png' });
        var fallback = function() {
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url; a.download = name;
            document.body.appendChild(a); a.click(); document.body.removeChild(a);
            setTimeout(function() { URL.revokeObjectURL(url); }, 10000);
            if (navigator.clipboard) navigator.clipboard.writeText(text).catch(function() {});
        };
        var file;
        try { file = new File([blob], name, { type: 'image/png' }); } catch (e) { file = null; }
        if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
            navigator.share({ files: [file], text: text }).catch(function(e) { if (!e || e.name !== 'AbortError') fallback(); });
        } else {
            fallback();
        }
    })()""",
)
