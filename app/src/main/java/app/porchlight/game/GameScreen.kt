package app.porchlight.game

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

internal enum class Screen {
    Porch, Levels, Daily, Moths, Badges, Settings, Round, Results;

    /** The lobby and the screens that hang off it. */
    val isMenu get() = this != Round && this != Results
}

private val OVERDRIVE_SHAKE = floatArrayOf(4f, -4f, 3f, -3f, 2f, -1f, 0f)

/** Something that just happened on the board, for the round screen to animate. */
internal sealed interface Fx {
    val n: Int

    data class Flipped(override val n: Int, val index: Int) : Fx
    data class Matched(
        override val n: Int,
        val first: Int,
        val second: Int,
        val moth: MothId,
        val multiplier: Int,
        val points: Int,
    ) : Fx

    data class Missed(override val n: Int, val first: Int, val second: Int, val brokeStreak: Int) : Fx
}

/** Everything the three screens share: which screen is up, the round in play, and the player's taps. */
internal class Session(val store: Store, val sfx: Sfx, val clock: Clock) {
    var screen by mutableStateOf(Screen.Porch)
    var lead by mutableStateOf(MothId.Luna)
    var round by mutableStateOf(Round.ready(MothId.Luna))
    var report by mutableStateOf<Report?>(null)
    var fx by mutableStateOf<Fx?>(null)
    var notice by mutableStateOf<String?>(null)
    var leaving by mutableStateOf(false)
    /** The daily card being previewed in the share window, if it is open. */
    var sharing by mutableStateOf<DailyCard?>(null)

    /** Centre of the bulb's glass and its width on screen, in pixels: the light and the moths aim for it. */
    var bulbAt by mutableStateOf(Offset.Zero)
    var bulbWidth by mutableFloatStateOf(0f)

    /** The top edge of the board, in pixels: effects stay above it. */
    var boardTop by mutableFloatStateOf(0f)

    /** Where each card sits on screen, so effects can start from it. */
    val cardAt = Array(32) { Offset.Zero }
    var cardWidth = 0f

    /** Fraction of the round's time left. Draw code reads it every frame. */
    var timeLeft by mutableFloatStateOf(1f)

    /** On a Blackout level: how long the round has been played, and whether the bulb is out. */
    var playedMs = 0L
    var dark by mutableStateOf(Dark.Lit)

    /** 1 normally; drops to 0.3 for an instant while the dying bulb flickers. */
    var flicker by mutableFloatStateOf(1f)

    /** Extra brightness that fades straight back out: Sorbet's top-up, big streaks. */
    val flare = Animatable(0f)

    /** The white flash as the bulb pops. */
    val pop = Animatable(0f)

    /** Counts Sorbet top-ups, so the "+1.5s" can float off the bulb each time. */
    var topUps by mutableIntStateOf(0)

    /** The porch lamp switched off by a tap on the jar (the lobby only). */
    var porchDark by mutableStateOf(false)

    /** When the jar was last tapped, in clock seconds: the moths start up at it. */
    var stirAt by mutableFloatStateOf(-10f)

    /** Tapping the jar: off with a click, on again with a couple of flickers and the bulb's hum. */
    fun toggleLamp() {
        stirAt = clock.seconds
        if (!porchDark) {
            porchDark = true
            sfx.play(Sound.Pop, volume = 0.45f, pitch = 1.3f)
            buzz(HapticFeedbackType.SegmentTick)
            return
        }
        porchDark = false
        buzz(HapticFeedbackType.Confirm)
        scope?.launch {
            if (!calm) {
                for ((dip, ms) in listOf(0.15f to 60L, 1f to 80L, 0.35f to 50L, 1f to 0L)) {
                    flicker = dip
                    if (ms > 0) delay(ms)
                }
            }
            flicker = 1f
            sfx.play(Sound.Buzz, volume = 0.7f)
        }
    }

    /** Badges already looked at on the Badges screen; any others are new. Kept for this run of the app. */
    var seenBadges: Set<Badge> = store.progress.badges

    /** Where the opening of the round is: deal, warm-up, peek, GO. Screen timing only. */
    var peekStage by mutableStateOf(PeekStage.None)

    /** Milliseconds of the peek still to look, counted by the screen so a pause can hold it. */
    var peekLeft by mutableLongStateOf(0L)

    /** The peek is held: the app went to the background or the player paused during it. */
    var peekPaused by mutableStateOf(false)

    /** Counts deals, so each one drops the cards in afresh. */
    var dealt by mutableIntStateOf(0)

    /** The player tapped through the deal and warm-up. */
    var skipIntro by mutableStateOf(false)

    /** How brightly the bulb burns right now; set by the root screen. */
    var power: () -> Float = { 0f }

    /** Reduce motion: no shake, hit-stop or particles; colour changes stay. */
    var calm by mutableStateOf(false)

    /** The colour the light takes for the round's state, or null for its own warm colour. */
    var hue by mutableStateOf<Color?>(null)

    /** The phone's tilt, for the parallax and the card highlights. Zero when motion is reduced. */
    var tilt = Tilt()

    /** Shader effects applied to whole layers: heat shimmer, the Overdrive aberration pulse. */
    val effects = LayerEffects.create()

    /** A Wanderer swap's swirl, 0..1, between the two cards that traded places. */
    val swirl = Animatable(1f)
    var swirlFrom = Offset.Zero
    var swirlTo = Offset.Zero

    /** The chromatic-aberration pulse on Overdrive, 0..1, over 150 ms. */
    val aberration = Animatable(0f)

    val flock = Flock()
    val particles = Particles()
    val pops = mutableStateListOf<Pop>()

    /** Warm pulse at the edge of the screen on a x3 or x4 pair. */
    val edge = Animatable(0f)

    /** Sideways camera shake in dp, on Overdrive. */
    val shake = Animatable(0f)
    var overdrives by mutableIntStateOf(0)
    var haptics: HapticFeedback? = null
    var scope: CoroutineScope? = null
    private var count = 0

    fun buzz(type: HapticFeedbackType) {
        if (store.progress.haptics) haptics?.performHapticFeedback(type)
    }

    /** The bulb breathes brighter for a moment. */
    fun flareUp(amount: Float, ms: Int = 300) {
        scope?.launch {
            flare.snapTo(amount)
            flare.animateTo(0f, tween(ms))
        }
    }

    /** A pair landed. The feedback grows with the streak: x1 sparks, x2 more, x3 the bulb flares, x4 Overdrive. */
    private fun celebrate(a: Offset, b: Offset, multiplier: Int) {
        val upper = if (a.y <= b.y) a else b
        pops.clear() // never stack ghosts: a new pop replaces the last
        pops += Pop(count, "+${PAIR_POINTS * multiplier}", Offset(upper.x, upper.y - cardWidth * 0.85f), big = multiplier >= MAX_MULTIPLIER)
        if (!calm) {
            val sparks = (6 + multiplier * 4) / (if (Gpu.quality >= 2) 2 else 1)
            val speed = cardWidth * (2.2f + 0.7f * multiplier)
            val size = cardWidth * (0.028f + 0.008f * multiplier)
            val bio = multiplier < MAX_MULTIPLIER
            particles.burst(a, sparks, speed, size, bio = bio)
            particles.burst(b, sparks, speed, size, bio = bio)
        }
        if (multiplier >= 3) {
            flareUp(if (multiplier >= MAX_MULTIPLIER) 0.7f else 0.4f, 450)
            scope?.launch {
                edge.snapTo(if (multiplier >= MAX_MULTIPLIER) 1f else 0.6f)
                edge.animateTo(0f, tween(520))
            }
        }
        if (multiplier >= MAX_MULTIPLIER) {
            overdrives += 1
            buzz(HapticFeedbackType.LongPress)
            if (!calm) {
                clock.freeze(0.08f) // hit-stop
                particles.ring(bulbAt, bulbWidth * 2.6f)
                scope?.launch {
                    aberration.snapTo(0f)
                    aberration.animateTo(1f, tween(40))
                    aberration.animateTo(0f, tween(110))
                }
                scope?.launch {
                    for (x in OVERDRIVE_SHAKE) shake.animateTo(x, tween(38))
                }
            }
        } else {
            buzz(HapticFeedbackType.Confirm)
        }
    }

    fun pickLead(id: MothId) {
        if (!store.progress.unlocked(id)) {
            sfx.play(Sound.Locked)
            buzz(HapticFeedbackType.Reject)
            notice = "Reach level ${id.unlockLevel} to meet ${id.label}."
            noticeMoth = id
            return
        }
        notice = null
        noticeMoth = null
        if (id != lead) {
            lead = id
            sfx.play(Sound.Flip)
            buzz(HapticFeedbackType.SegmentTick)
        }
    }

    /** Starts a round on the level picked on the porch, or today's daily porch. [easy] is the "Easier bulb" retry. */
    fun start(daily: Boolean = round.daily && !screen.isMenu, easy: Boolean = false) {
        report = null
        notice = null
        noticeMoth = null
        reset()
        val night = levelAt(store.progress.current)
        val fresh = !daily && (introducedOn(night) != null || newOn(night).isNotEmpty()) &&
            "level${night.number}" !in store.progress.tips
        round = if (daily) Round.daily(lead, todaySeed()) else Round.start(lead, night, easy = easy)
        screen = Screen.Round
        if (fresh) {
            // Meet the night first; the peek starts when the player is ready.
            round = round.copy(phase = Phase.Idle)
            intro = night
            return
        }
        sfx.play(Sound.Start)
        buzz(HapticFeedbackType.Confirm)
    }

    fun flip(index: Int) {
        val before = round
        val after = before.flip(index, now())
        if (after === before) return
        round = after
        count += 1
        when {
            after.pairs > before.pairs -> {
                val multiplier = multiplierAt(before.streak, before.rules)
                val first = before.open.first()
                val moth = before.deck[index]
                fx = Fx.Matched(count, first, index, moth, multiplier, PAIR_POINTS * multiplier)
                flock.add(moth, cardAt[first], cardWidth, clock.seconds)
                flock.add(moth, cardAt[index], cardWidth, clock.seconds)
                if (before.rules.pairBonusMs > 0) {
                    topUps += 1
                    flareUp(0.45f)
                }
                celebrate(cardAt[first], cardAt[index], multiplier)
                sfx.play(sfx.matchFor(before.streak + 1))
                if (multiplier >= 3) sfx.play(Sound.Bass)
                if (before.rules.pairBonusMs > 0) sfx.play(Sound.TopUp, volume = 0.7f)
            }
            after.miss && !before.miss -> {
                fx = Fx.Missed(count, after.open[0], after.open[1], multiplierAt(before.streak, before.rules))
                sfx.play(Sound.Miss)
                if (after.streak < before.streak) sfx.play(Sound.Thunk)
                buzz(HapticFeedbackType.Reject)
            }
            after.spent.size > before.spent.size -> {
                // A dead leaf: two seconds burn off the bulb.
                fx = Fx.Flipped(count, index)
                sfx.play(Sound.Locked)
                sfx.play(Sound.Buzz, volume = 0.8f)
                buzz(HapticFeedbackType.Reject)
                flareUp(-0.5f, 400)
            }
            else -> {
                fx = Fx.Flipped(count, index)
                sfx.play(Sound.Flip, vary = 0.06f)
                buzz(HapticFeedbackType.SegmentTick)
            }
        }
    }

    fun flash() {
        val after = round.flash()
        if (after === round) return
        round = after
        sfx.play(Sound.Go)
        buzz(HapticFeedbackType.Confirm)
    }

    /** A level's one-line introduction, shown once before its first round. */
    var intro by mutableStateOf<Level?>(null)

    /** The locked moth the porch is talking about, drawn as a silhouette beside the notice. */
    var noticeMoth by mutableStateOf<MothId?>(null)

    /** Four cards, two pairs, a hand that points. Skippable. */
    fun startTutorial() {
        report = null
        notice = null
        reset()
        round = Round.start(MothId.Luna, TUTORIAL)
        screen = Screen.Round
        sfx.play(Sound.Start)
    }

    fun endTutorial(done: String? = null) {
        store.change { it.copy(tutorialDone = true) }
        toPorch()
        notice = done
    }

    /** False while the app is in the background. */
    var foreground = true

    /** Freezes the bulb: the app went to the background, or the player pressed pause. */
    fun pause() {
        // During the opening the round's clock hasn't started: hold the screen's own countdown.
        if (round.phase == Phase.Peek && !round.tutorial) {
            peekPaused = true
            return
        }
        if (round.phase != Phase.Play || round.paused) return
        round = round.pause(now())
        flicker = 1f
    }

    fun resume() {
        if (peekPaused) {
            peekPaused = false
            buzz(HapticFeedbackType.Confirm)
            return
        }
        if (!round.paused) return
        round = round.resume(now())
        sfx.play(Sound.Go)
        buzz(HapticFeedbackType.Confirm)
    }

    /** The round and screen as Bundle-friendly values, for surviving process death and rotation. */
    fun saved(): Map<String, Any?> =
        if (screen == Screen.Round && (round.running || round.paused) && !round.tutorial) round.saved(lead) + ("screen" to screen.name)
        else mapOf("lead" to lead.name)

    fun restore(values: Map<String, Any?>) {
        (values["lead"] as? String)?.let { name -> MothId.entries.find { it.name == name }?.let { lead = it } }
        if (values["screen"] != Screen.Round.name) return
        val (saved, savedLead) = restoreRound(values) ?: return
        lead = savedLead
        round = saved
        screen = Screen.Round
        timeLeft = (saved.left.toFloat() / saved.rules.roundMs).coerceIn(0f, 1f)
        // Moths already found go straight back into orbit.
        for (i in saved.deck.indices) {
            if (saved.matched[i]) flock.add(saved.deck[i], Offset.Zero, 0f, -10f)
        }
    }

    /** Lights the porch after a night's introduction. */
    fun beginAfterIntro() {
        val night = intro ?: return
        store.change { it.copy(tips = it.tips + "level${night.number}") }
        intro = null
        round = round.copy(phase = Phase.Peek, say = "Look closely. The cards are about to fold shut.")
        sfx.play(Sound.Start)
        buzz(HapticFeedbackType.Confirm)
    }

    private fun reset() {
        fx = null
        intro = null
        flock.clear()
        particles.clear()
        pops.clear()
        timeLeft = 1f
        flicker = 1f
        peekStage = PeekStage.None
        peekPaused = false
        skipIntro = false
        playedMs = 0L
        dark = Dark.Lit
    }

    /** Opens one of the lobby's screens. */
    fun open(target: Screen) {
        notice = null
        noticeMoth = null
        screen = target
        sfx.play(Sound.Flip, vary = 0.06f)
        buzz(HapticFeedbackType.SegmentTick)
    }

    fun toPorch() {
        leaving = false
        porchDark = false
        report = null
        reset()
        round = Round.ready(lead, levelAt(store.progress.current))
        screen = Screen.Porch
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GameScreen(store: Store, sfx: Sfx) {
    val clock = rememberClock()
    val session = rememberSaveable(
        saver = mapSaver(save = { it.saved() }, restore = { Session(store, sfx, clock).apply { restore(it) } }),
    ) { Session(store, sfx, clock) }

    // Going to the background pauses the round (a rotation doesn't); coming back asks to continue.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    DisposableEffect(lifecycle, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    session.foreground = false
                    sfx.awake = false
                    if (activity?.isChangingConfigurations != true) session.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    session.foreground = true
                    sfx.awake = true
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    session.haptics = LocalHapticFeedback.current
    session.scope = rememberCoroutineScope()
    sfx.muted = store.progress.muted
    sfx.music = store.progress.music
    // Reduce motion: the in-game toggle, or animations switched off for the whole phone.
    val context = LocalContext.current
    val systemCalm = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    session.calm = store.progress.reduceMotion || systemCalm
    val round = session.round
    val low = round.phase == Phase.Play && !round.paused && round.left <= LOW_MS
    sfx.ambient = session.screen.isMenu || round.running

    // The opening and peek, the clock, and the bookkeeping when a round ends. Keyed on the seed
    // too, so starting over during a peek deals again.
    LaunchedEffect(round.phase, round.seed) {
        when (round.phase) {
            Phase.Peek -> {
                session.openRound(round, sfx)
                // The round's own clock starts here, when the peek ends, exactly as before.
                session.peekStage = PeekStage.Go
                session.round = session.round.go(now())
                if (session.foreground) sfx.play(Sound.Go) else session.pause()
                if (!session.calm) session.flareUp(0.5f, 400)
                // The cards fold shut bottom row first: a soft paper sound for each row.
                val rows = (round.deck.size + boardColumns(round.deck.size) - 1) / boardColumns(round.deck.size)
                for (row in 0 until rows) {
                    sfx.play(Sound.Flip, volume = 0.45f, pitch = 0.9f + 0.05f * row)
                    delay(if (session.calm) 0 else WAVE_STAGGER_MS)
                }
                delay(GO_MS + 250)
                if (session.peekStage == PeekStage.Go) session.peekStage = PeekStage.None
            }
            Phase.Play -> {
              var lastFrame = -1L
              while (session.round.phase == Phase.Play) {
                val frame = withFrameMillis { it }
                val current = session.round
                val t = now()
                // A Blackout counts the time actually played, so a pause holds it too.
                if (Twist.Blackout in current.level) {
                    if (lastFrame >= 0 && !current.paused) session.playedMs += frame - lastFrame
                    session.dark = if (current.paused) Dark.Lit else blackoutAt(session.playedMs)
                }
                lastFrame = frame
                // The bulb reads smooth time every frame, in drawing only...
                val left = if (current.paused) current.left else (current.endsAt - t).coerceAtLeast(0)
                session.timeLeft = (left.toFloat() / current.rules.roundMs).coerceIn(0f, 1f)
                // ...while the round itself, which the whole screen reads, changes only when its
                // readout or phase does: whole seconds, or tenths under ten seconds.
                val next = current.tick(t)
                if (next.phase != current.phase || readout(next.left) != readout(current.left)) session.round = next
              }
              session.dark = Dark.Lit
            }
            Phase.Won, Phase.Lost -> if (session.screen == Screen.Round && session.round.tutorial) {
                val won = session.round.phase == Phase.Won
                sfx.play(if (won) Sound.Win else Sound.Lose)
                delay(1_400)
                session.endTutorial(if (won) "That's the game. Light the porch when you're ready." else "Close. The real porch gives you more time.")
            } else if (session.screen == Screen.Round) {
                val won = session.round.phase == Phase.Won
                val result = store.finish(session.round, session.lead, todaySeed(), todaySeed(-1))
                if (won) {
                    sfx.play(Sound.Win)
                    session.flareUp(0.5f, 700)
                    delay(1_100)
                } else {
                    // The lamp pops: a bright burst for 120 ms, then dark, with the glass still hanging
                    // there. A faint flash crosses the screen with it, and the moths scatter.
                    session.timeLeft = 0f
                    session.flock.scatterAt = clock.seconds
                    sfx.play(Sound.Pop)
                    sfx.play(Sound.Lose)
                    session.buzz(HapticFeedbackType.LongPress)
                    session.flareUp(1.8f, 120)
                    session.pop.snapTo(0.3f)
                    session.pop.animateTo(0f, tween(120))
                    delay(1_250)
                }
                session.report = result
                session.screen = Screen.Results
            }
            Phase.Idle -> Unit
        }
    }
    LaunchedEffect(round.miss) {
        if (round.miss) {
            delay(round.rules.flipBackMs)
            session.round = session.round.settle()
        }
    }
    // The Blackout: half a second of flicker and a buzz to warn, then the bulb is out for 0.8 s.
    // With reduce motion the warning is a steady dim instead of a flicker.
    LaunchedEffect(session.dark) {
        when (session.dark) {
            Dark.Warning -> {
                sfx.play(Sound.Buzz, volume = 0.9f, pitch = 0.85f)
                session.buzz(HapticFeedbackType.SegmentTick)
                if (session.calm) {
                    session.flicker = 0.6f
                } else {
                    try {
                        while (true) {
                            session.flicker = 0.35f
                            delay(60)
                            session.flicker = 1f
                            delay(90)
                        }
                    } finally {
                        session.flicker = 1f
                    }
                }
            }
            Dark.Out -> {
                session.flicker = 0.04f
                sfx.play(Sound.Pop, volume = 0.35f, pitch = 0.7f)
                session.buzz(HapticFeedbackType.Reject)
            }
            Dark.Lit -> session.flicker = 1f
        }
    }
    LaunchedEffect(round.flashing) {
        if (round.flashing) {
            delay(FLASH_MS)
            session.round = session.round.unflash()
        }
    }
    // The last five seconds tick out loud, and the bulb flickers two to four times a second.
    val secondsLeft = ((round.left + 999) / 1000).toInt()
    LaunchedEffect(secondsLeft, round.phase) {
        if (round.phase == Phase.Play && secondsLeft in 1..5) sfx.play(Sound.Tick)
    }
    LaunchedEffect(low) {
        if (low) {
            try {
                while (true) {
                    delay(Random.nextLong(250, 500))
                    session.flicker = 0.3f
                    sfx.play(Sound.Buzz, vary = 0.08f, volume = 0.8f)
                    delay(Random.nextLong(40, 90))
                    session.flicker = 1f
                }
            } finally {
                session.flicker = 1f
            }
        }
    }

    // The very first launch opens on the tutorial.
    LaunchedEffect(Unit) {
        if (!store.progress.tutorialDone) session.startTutorial()
    }

    // Back from a round asks first; back from the results goes to the porch; back from the porch exits.
    BackHandler(session.screen != Screen.Porch || session.leaving) {
        when {
            session.leaving -> session.leaving = false
            session.screen.isMenu -> session.toPorch()
            round.tutorial -> session.endTutorial()
            session.screen == Screen.Round && round.running -> session.leaving = true
            else -> session.toPorch()
        }
    }

    val burning = round.running || round.phase == Phase.Won
    val on by animateFloatAsState(
        when {
            session.screen == Screen.Porch -> if (session.porchDark) 0.04f else 0.9f
            // Dark while the cards are dealt; it comes on with the warm-up.
            round.phase == Phase.Peek && session.peekStage == PeekStage.Deal -> 0f
            burning -> 1f
            else -> 0f
        },
        // The porch light snaps on like a switch; it fades when the round ends.
        tween(if (round.phase == Phase.Lost) 0 else if (burning) 140 else 500),
        label = "on",
    )
    val tighten by animateFloatAsState(if (low) 1f else 0f, tween(400), label = "tighten")
    session.power = { on * session.flicker + session.flare.value }
    session.tilt = rememberTilt(enabled = !session.calm)
    // The board blurs behind the results sheet, so the sheet reads as frosted glass.
    val behindSheet by animateFloatAsState(
        if (session.screen == Screen.Results) 20f else 0f,
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow),
        label = "behindSheet",
    )

    // The wall's light follows the lamp: its own warm colour by the time left (the peek burns at
    // full warm white), gold in Overdrive, red in the last seconds.
    val overdrive = round.phase == Phase.Play && multiplierAt(round.streak, round.rules) >= MAX_MULTIPLIER
    val hue: Color? = when {
        session.screen.isMenu -> null
        low -> Craft.danger
        overdrive -> Craft.gold
        else -> null
    }
    session.hue = hue

    // Every bare Text in the game reads in the body font at body size, with tabular figures.
    ProvideTextStyle(BodyText) {
    Box(Modifier.fillMaxSize().background(Dusk.wallTop)) {
        NightScene(
            clock = clock,
            tilt = session.tilt,
            calm = session.calm,
            bulb = { session.bulbAt },
            light = { on * session.flicker * (0.4f + 0.6f * session.timeLeft) + session.flare.value },
            reach = { session.bulbWidth * (1.7f + 2.9f * session.timeLeft) * (0.6f + 0.4f * on) },
            warmth = { session.timeLeft },
            hue = { session.hue },
            danger = { tighten },
            tighten = { tighten },
        )
        // Screens change with a short fade through the wall, and the things two screens share fly
        // across instead (see Flight.kt). A round and its results are one place.
        SharedTransitionLayout {
        AnimatedContent(
            targetState = if (session.screen == Screen.Results) Screen.Round else session.screen,
            transitionSpec = {
                val ms = if (session.calm) 0 else 220
                fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
            },
            label = "screen",
        ) { screen ->
            CompositionLocalProvider(LocalFlights provides if (session.calm) null else Flights(this@SharedTransitionLayout, this)) {
                when (screen) {
                    Screen.Porch -> Porch(session)
                    Screen.Levels -> LevelsScreen(session)
                    Screen.Daily -> DailyScreen(session)
                    Screen.Moths -> MothsScreen(session)
                    Screen.Badges -> BadgesScreen(session)
                    Screen.Settings -> SettingsScreen(session)
                    Screen.Round, Screen.Results -> {
                        Box(Modifier.fillMaxSize().graphicsLayer { renderEffect = blurOf(behindSheet * density) }) {
                            RoundScreen(session)
                        }
                    }
                }
            }
        }
        Bloom(session)
        // The scorecard keeps its own scope, so its level emblem can fly home to the porch.
        val report = session.report
        var lastReport by remember { mutableStateOf(report) }
        if (report != null) lastReport = report
        AnimatedVisibility(
            visible = session.screen == Screen.Results && report != null,
            enter = EnterTransition.None,
            exit = fadeOut(tween(if (session.calm) 0 else 90)),
            label = "results",
        ) {
            CompositionLocalProvider(LocalFlights provides if (session.calm) null else Flights(this@SharedTransitionLayout, this)) {
                lastReport?.let { Results(session, it) }
            }
        }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = session.pop.value }.background(Color.White))
        if (session.leaving) LeaveNotice(session)
        session.sharing?.let { card ->
            ShareSheet(card, gold = card.lead in session.store.progress.mastered, level = session.store.progress.level) { session.sharing = null }
        }
    }
    }
}

/** Reports where the bulb's glass is drawn so the scene can light the wall from it. */
internal fun Modifier.tracksBulb(session: Session) = onGloballyPositioned {
    val bounds = it.boundsInRoot()
    session.bulbAt = Offset(bounds.center.x, bounds.top + bounds.height * BULB_GLASS_Y)
    session.bulbWidth = bounds.width
}

/** The daily porch's seed for today, or [daysAgo] days before. */
internal fun todaySeed(daysAgo: Int = 0): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, daysAgo)
    return dailySeed(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

/** The step the time readout shows: whole seconds, or tenths under ten seconds. */
private fun readout(ms: Long): Long = if (ms < 10_000) ms / 100 else 100 + ms / 1000

/**
 * The opening of a round, up to the moment the peek ends: the deal, the warm-up and the counted
 * peek. A pause holds the countdown where it is; tapping skips the deal and warm-up.
 */
private suspend fun Session.openRound(round: Round, sfx: Sfx) {
    val rules = round.rules
    peekPaused = false
    if (!round.tutorial) {
        // Deal: the bulb is dark and the cards drop in one by one, each tap a little higher.
        skipIntro = false
        peekStage = PeekStage.Deal
        dealt += 1
        val cards = round.deck.size
        var spent = 0L
        for (i in 0 until cards) {
            if (skipIntro) break
            sfx.play(Sound.Tap, volume = 0.5f, pitch = 0.85f + 0.45f * i / cards)
            delay(DEAL_STAGGER_MS)
            spent += DEAL_STAGGER_MS
        }
        while (!skipIntro && spent < DEAL_MS) {
            delay(30)
            if (!peekPaused) spent += 30
        }
        // Warm-up: two flickers, then the bulb holds and its light sweeps over the board.
        peekStage = PeekStage.Warm
        if (!skipIntro && !calm) {
            for (k in 0 until 2) {
                flicker = 0.25f
                delay(70)
                flicker = 1f
                delay(110)
            }
        }
        flicker = 1f
        sfx.play(Sound.Buzz, volume = 0.8f)
        var warm = 0L
        while (!skipIntro && warm < WARM_MS - 360) {
            delay(30)
            if (!peekPaused) warm += 30
        }
    }

    // The peek: counted down by the screen, frame by frame, so a pause can hold it exactly.
    peekStage = PeekStage.Look
    var left = rules.peekMs
    peekLeft = left
    var shown = peekNumber(left)
    tickFor(shown, sfx)
    var last = withFrameMillis { it }
    while (left > 0) {
        val frame = withFrameMillis { it }
        if (!peekPaused) left -= frame - last
        last = frame
        peekLeft = left.coerceAtLeast(0)
        val number = peekNumber(left)
        if (number != shown && number > 0) {
            shown = number
            tickFor(number, sfx)
        }
    }
}

/** A soft tick and a light buzz for each number; the last one ticks a little lower. */
private fun Session.tickFor(number: Int, sfx: Sfx) {
    sfx.play(Sound.Tick, volume = 0.55f, pitch = if (number == 1) 0.8f else 1f)
    buzz(HapticFeedbackType.SegmentTick)
}
