package app.porchlight.game

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** Where each moth rests on the wall, as fractions of the roster area. */
private val SLOTS = listOf(
    Offset(0.14f, 0.20f), Offset(0.86f, 0.20f),
    Offset(0.13f, 0.56f), Offset(0.87f, 0.56f),
    Offset(0.30f, 0.86f), Offset(0.70f, 0.86f),
)
private val LEAD_SPOT = Offset(0.5f, 0.66f)

/** "8 pairs · 40 s · Wanderer" */
internal fun nightLine(night: Night): String =
    "${night.pairs} pairs · ${night.roundMs / 1000} s" + if (night.twist != Twist.None) " · ${night.twist.title}" else ""

/** Home: the porch wall, the bulb, and the moths resting around it. */
@Composable
internal fun Porch(session: Session) {
    val progress = session.store.progress
    var badges by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val today = remember { todaySeed() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (maxWidth > maxHeight) {
            // Landscape and wide tablets: the porch on the left, the controls on the right.
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Roster(session, Modifier.fillMaxHeight().weight(1f))
                Column(
                    Modifier.fillMaxHeight().weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    TopRow(session) { settings = true }
                    Spacer(Modifier.height(12.dp))
                    Controls(session, today, onBadges = { badges = true })
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TopRow(session) { settings = true }
                Roster(session, Modifier.fillMaxWidth().weight(1f))
                Controls(session, today, onBadges = { badges = true })
            }
        }
    }

    if (badges) BadgeSheet(progress) { badges = false }
    if (settings) SettingsSheet(session) { settings = false }
}

@Composable
private fun TopRow(session: Session, onSettings: () -> Unit) {
    val progress = session.store.progress
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LevelBar(progress.xp, Modifier.weight(1f))
        RoundIconButton("☰", "Sound and settings", enabled = true, onClick = onSettings)
    }
}

@Composable
private fun Controls(session: Session, today: Long, onBadges: () -> Unit) {
    val progress = session.store.progress
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PORCHLIGHT", style = Display, fontSize = 30.sp, color = Dusk.ink, letterSpacing = 2.sp)
        Text(
            if (progress.best > 0) "Best ${progress.best.grouped()}" else "Six moths. One bulb. Beat the clock.",
            color = Dusk.inkSoft,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))

        val lead = session.lead
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Tapping a locked moth shows who it is, as a silhouette, and how to meet it.
            session.noticeMoth?.let { MothArt(it, Modifier.size(40.dp), flat = Dusk.locked) }
            Text(
                session.notice ?: "${lead.label} leads · ${lead.perkName}: ${lead.perk}",
                color = if (session.notice != null) Dusk.amber else Dusk.ink,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(8.dp))

        NightPicker(progress) { n -> session.store.change { it.copy(night = n) } }
        Spacer(Modifier.height(8.dp))

        PrimaryButton("Light the porch", Modifier.fillMaxWidth()) { session.start(daily = false) }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val played = progress.playedDaily(today)
            SmallButton(
                if (played) "DAILY ${"★".repeat(progress.dailyStars)}${"☆".repeat(3 - progress.dailyStars)}" else "DAILY PORCH",
                if (progress.dailyStreak > 0 && (played || progress.dailyDay == todaySeed(-1))) "${progress.dailyStreak} day streak" else "Same deal for everyone",
                Modifier.weight(1f),
            ) { session.start(daily = true) }
            SmallButton("BADGES", "${progress.badges.size} of ${Badge.entries.size}", Modifier.weight(1f), onBadges)
        }
    }
}

@Composable
private fun NightPicker(progress: Progress, onPick: (Int) -> Unit) {
    val night = NIGHTS[progress.night - 1]
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Dusk.panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Arrow("‹", "Previous night", progress.night > 1) { onPick(progress.night - 1) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NIGHT ${night.number}", style = Display, fontSize = 17.sp, color = Dusk.ink)
            Text(nightLine(night), color = Dusk.inkSoft, fontSize = 12.sp)
        }
        val next = progress.night + 1
        val open = next <= progress.nights
        Arrow(
            if (open || next > NIGHTS.size) "›" else "🔒",
            if (open) "Next night" else "Next night: beat this one with two stars",
            open,
        ) { onPick(next) }
    }
}

@Composable
private fun Arrow(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Display, fontSize = if (text.length > 1) 14.sp else 26.sp, color = if (enabled) Dusk.amber else Dusk.locked)
    }
}

@Composable
private fun SmallButton(title: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Dusk.panel)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = Label, color = Dusk.amber, maxLines = 1)
        Text(detail, color = Dusk.inkSoft, fontSize = 12.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(session: Session, onClose: () -> Unit) {
    val progress = session.store.progress
    val store = session.store
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Dusk.panel, contentColor = Dusk.ink) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = Display, fontSize = 24.sp, color = Dusk.ink)
            Setting("Sound effects", "Flips, chimes and the bulb.", !progress.muted) { store.setMuted(!progress.muted) }
            Setting("Night sounds", "Crickets and the hum of the bulb.", progress.music) { store.change { it.copy(music = !it.music) } }
            Setting("Vibration", "Buzzes on matches and misses.", progress.haptics) { store.change { it.copy(haptics = !it.haptics) } }
            Setting("Reduce motion", "No shaking, freezing or sparks. Colours still change.", progress.reduceMotion) {
                store.change { it.copy(reduceMotion = !it.reduceMotion) }
            }
            Text(
                "Replay the tutorial",
                color = Dusk.amber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) {
                        onClose()
                        session.startTutorial()
                    }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Setting(title: String, detail: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = if (on) "On" else "Off" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, color = Dusk.ink, fontSize = 16.sp)
            Text(detail, color = Dusk.inkSoft, fontSize = 13.sp)
        }
        Toggle(if (on) "ON" else "OFF", on, onClick)
    }
}

@Composable
internal fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Label,
        color = if (on) Dusk.paperInk else Dusk.inkSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Switch, onClick = onClick)
            .background(if (on) Dusk.amber else Dusk.slot)
            .semantics { stateDescription = if (on) "On" else "Off" }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun Roster(session: Session, modifier: Modifier) {
    val progress = session.store.progress
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        val bulbH = h * 0.36f
        Bulb(
            clock = session.clock,
            timeLeft = { 1f },
            power = session.power,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(bulbH * 0.7f, bulbH)
                .tracksBulb(session)
                .semantics { contentDescription = "The porch bulb, waiting" },
        )

        val fly = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)
        LEADS.forEachIndexed { i, id ->
            val chosen = id == session.lead
            val open = progress.unlocked(id)
            val spot = if (chosen) LEAD_SPOT else SLOTS[i]
            val x by animateDpAsState(w * spot.x, fly, label = "x")
            val y by animateDpAsState(h * spot.y, fly, label = "y")
            // Moths shrink on short screens so they never crowd each other or the bulb.
            val size by animateDpAsState(
                if (chosen) min(96.dp, h * 0.3f) else min(62.dp, h * 0.19f),
                fly,
                label = "size",
            )
            Box(
                Modifier
                    .offset(x - size / 2, y - size / 2)
                    .size(size)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton) { session.pickLead(id) }
                    .semantics {
                        contentDescription =
                            if (open) "${id.label}: ${id.perkName}" else "${id.label}, locked until level ${id.unlockLevel}"
                        stateDescription = if (chosen) "Leading" else "Not leading"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (open) {
                    MothArt(id, Modifier.fillMaxSize())
                } else {
                    MothAtRest(Dusk.locked, Dusk.wallTop, Modifier.fillMaxSize().padding(6.dp))
                    Text(
                        "LV ${id.unlockLevel}",
                        style = Label,
                        fontSize = 10.sp,
                        color = Dusk.ink,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .wrapContentWidth(unbounded = true)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Dusk.panel)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeSheet(progress: Progress, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Dusk.panel, contentColor = Dusk.ink) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Badges", style = Display, fontSize = 24.sp, color = Dusk.ink, modifier = Modifier.weight(1f))
                Text(
                    "${progress.badges.size} of ${Badge.entries.size} · ${progress.rounds} ${if (progress.rounds == 1) "round" else "rounds"}",
                    color = Dusk.inkSoft,
                    fontSize = 13.sp,
                )
            }
            Badge.entries.forEach { badge ->
                val earned = badge in progress.badges
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BadgeMedal(badge, earned, Modifier.size(38.dp))
                    Column {
                        Text(
                            badge.title,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (earned) Dusk.ink else Dusk.inkSoft,
                            fontSize = 16.sp,
                        )
                        Text(badge.detail, color = Dusk.inkSoft, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
