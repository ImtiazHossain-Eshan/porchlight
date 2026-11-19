package app.porchlight.game

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the ads on this phone are, in a line, from the year of birth given. */
private fun adsLine(audience: Audience?): String = when (audience) {
    null -> "No ads have been asked for: no year of birth has been given yet."
    Audience.Child -> "Ads here are child-directed: rated G at most, never personalised, and Google is told they are for a child."
    Audience.Teen -> "Ads here are for someone under 16: rated PG at most and never personalised."
    Audience.Adult -> "Ads here are rated PG at most. Where the law asks (the EEA and the UK) Google's consent form lets you choose about personalised ads; everywhere else they are non-personalised."
}

/**
 * Privacy, said plainly: no account, what stays on the phone, what the ads and Google Play do. The
 * full policy and changing the year of birth both sit behind the grown-up check.
 */
@Composable
internal fun PrivacyScreen(session: Session) {
    val progress = session.store.progress
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current
    MenuFrame("Privacy", session) {
        Section(
            "No account",
            "Porchlight has no sign-in and no account. It doesn't ask for your name, email, contacts, photos or location.",
        )
        Section(
            "Kept on this phone",
            "Your levels, stars, badges, Fireflies, looks and the year of birth you gave are kept on this phone, " +
                "and in its Android backup if backup is on, so a new phone can carry on. Porchlight has no server of its own.",
        )
        Section("Ads", "Ads play only when you tap a button to watch one. There are no banners and no ads between rounds. " + adsLine(audienceFor(progress.birthYear, thisYear())) +
            " The ads come from Google AdMob, which receives information about the phone to show and count them.")
        Section(
            "Purchases",
            "Anything bought with money goes through Google Play. Porchlight never sees card or bank details; it keeps only Google Play's receipt so each purchase is handed over once.",
        )
        Spacer(Modifier.height(4.dp))
        if (session.ads?.privacyOptions == true) {
            SecondaryButton("Privacy choices for ads", Modifier.fillMaxWidth()) { activity?.let { session.ads?.showPrivacyOptions(it) } }
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton("Read the full privacy policy", Modifier.fillMaxWidth(), glyph = Glyph.Book) {
            session.askGrownUp("This opens the privacy policy in your web browser.", remember = false) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Change the year of birth", Modifier.fillMaxWidth()) {
            session.askGrownUp("The year of birth decides what kind of ad can be shown.", remember = false) {
                session.store.change { it.copy(birthYear = 0) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp), ambientColor = Dusk.shadow, spotColor = Dusk.shadow)
            .parchment(8.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, color = Craft.parchmentInk, fontSize = TypeScale.Heading, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(2.dp))
        Text(body, color = Craft.parchmentInk, fontSize = TypeScale.Body, lineHeight = 20.sp)
    }
}
