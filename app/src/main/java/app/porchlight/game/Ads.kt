package app.porchlight.game

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rewarded ads, and nothing else: no interstitials, no banners, and an ad only ever plays when the
 * player taps a button that says what it gives. Nothing is loaded or initialised until the player
 * has given a year of birth, and the request follows [policyFor] that audience.
 *
 * One ad is kept loaded at a time. If one can't be loaded, [ready] stays false and the buttons that
 * need it simply don't show; it tries again quietly a minute later, never more often.
 */
class Ads(context: Context, private val unitId: String) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var audience: Audience? = null
    private var ad: RewardedAd? = null
    private var loading = false
    private val initialised = AtomicBoolean(false)
    private var started = false

    /** Bumped when the audience changes, so a load asked for under the old settings is dropped. */
    private var generation = 0

    /** An ad is loaded and can be shown now. */
    var ready by mutableStateOf(false)
        private set

    /** An ad is on screen. */
    var showing by mutableStateOf(false)
        private set

    /** Debug builds only (set from MainActivity): consent as if in the EEA. */
    var pretendEea = false

    /** Google's consent form says the player must be able to change their choices (EEA, UK). */
    var privacyOptions by mutableStateOf(false)
        private set

    /** Starts the ads for [audience]: the consent check where it applies, then the SDK, then one ad. */
    fun start(activity: Activity, audience: Audience) {
        if (started && this.audience == audience) return
        // A different audience (the year of birth was changed): an ad asked for under the old
        // settings is thrown away, never shown.
        if (started) {
            ad = null
            ready = false
            loading = false
            generation += 1
        }
        started = true
        this.audience = audience
        val policy = policyFor(audience)
        if (!policy.consentCheck) {
            begin(policy, personalised = false)
            return
        }
        val consent = UserMessagingPlatform.getConsentInformation(app)
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(policy.underAgeOfConsent)
            .apply {
                // Debug builds only: act as if the phone were in the EEA, to try the consent form.
                if (pretendEea) {
                    setConsentDebugSettings(
                        com.google.android.ump.ConsentDebugSettings.Builder(app)
                            .setDebugGeography(com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .setForceTesting(true)
                            .build(),
                    )
                }
            }
            .build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    afterConsent(consent, policy)
                }
            },
            { error ->
                Log.w(TAG, "Consent check: ${error.message}")
                // Where an earlier answer allows it, ads can still be asked for.
                afterConsent(consent, policy)
            },
        )
    }

    private fun afterConsent(consent: ConsentInformation, policy: AdPolicy) {
        val required = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        privacyOptions = required
        if (!consent.canRequestAds()) return
        // Personalised only where the consent form ran (EEA, UK) and only for adults: its answer then
        // decides. Everywhere else, and for anyone under 16, ads are non-personalised.
        begin(policy, personalised = policy.personalisedIfConsented && required)
    }

    private fun begin(policy: AdPolicy, personalised: Boolean) {
        Log.i(TAG, "Ads for $audience: childDirected=${policy.childDirected} underAge=${policy.underAgeOfConsent} rating=${policy.maxRating} personalisation=${if (personalised) "as the consent form says" else "never"}")
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTagForChildDirectedTreatment(
                    if (policy.childDirected) RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                    else RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE,
                )
                .setTagForUnderAgeOfConsent(
                    if (policy.underAgeOfConsent) RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                    else RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE,
                )
                .setAgeRestrictedTreatment(
                    when (audience) {
                        Audience.Child -> AgeRestrictedTreatment.CHILD
                        Audience.Teen -> AgeRestrictedTreatment.TEEN
                        else -> AgeRestrictedTreatment.UNSPECIFIED
                    },
                )
                .setMaxAdContentRating(if (policy.maxRating == "G") RequestConfiguration.MAX_AD_CONTENT_RATING_G else RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                .setPublisherPrivacyPersonalizationState(
                    if (personalised) RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT
                    else RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED,
                )
                .build(),
        )
        if (initialised.getAndSet(true)) {
            load()
            return
        }
        // The SDK starts on a background thread, as Google recommends, then the first ad loads.
        Thread {
            MobileAds.initialize(app) { main.post { load() } }
        }.start()
    }

    private fun load() {
        if (loading || ad != null || audience == null) return
        loading = true
        val asked = generation
        RewardedAd.load(app, unitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(loaded: RewardedAd) {
                if (asked != generation) return
                loading = false
                ad = loaded
                ready = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (asked != generation) return
                loading = false
                ad = null
                ready = false
                Log.i(TAG, "No ad to show (${error.code}): trying again in a minute")
                main.postDelayed({ load() }, RETRY_MS)
            }
        })
    }

    /**
     * Shows the loaded ad. [onReward] runs only when the SDK says the reward was earned (the ad was
     * watched through); [onDone] runs when it closes either way. Returns false if no ad was ready.
     */
    fun show(activity: Activity, onReward: () -> Unit, onDone: () -> Unit): Boolean {
        val current = ad ?: return false
        ad = null
        ready = false
        showing = true
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                onDone()
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Ad failed to show: ${error.message}")
                showing = false
                onDone()
                load()
            }
        }
        current.show(activity) { onReward() }
        return true
    }

    /** Google's form for changing consent choices, where it is required. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "PorchlightAds"
        private const val RETRY_MS = 60_000L

        @Volatile private var one: Ads? = null

        /** One for the whole app, so a rotation doesn't start the SDK or the consent check again. */
        fun get(context: Context, unitId: String): Ads =
            one ?: synchronized(this) { one ?: Ads(context, unitId).also { one = it } }
    }
}
