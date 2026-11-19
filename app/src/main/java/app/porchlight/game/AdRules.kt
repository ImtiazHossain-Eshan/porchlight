package app.porchlight.game

/**
 * The rules for rewarded ads, as pure functions: who may see what kind of ad, and how many a day.
 * Ads only ever play when the player taps a button that says what it gives. See Ads.kt for the SDK.
 */

/** The three opt-in buttons, each with its own daily cap from [Economy]. */
enum class Placement(val perDay: Int) {
    Shop(Economy.AD_SHOP_PER_DAY),
    Double(Economy.AD_DOUBLE_PER_DAY),
    Relight(Economy.AD_RELIGHT_PER_DAY),
}

/** Who the ads are for, from the year of birth the player gave on the age screen. */
enum class Audience { Child, Teen, Adult }

/**
 * The audience for a [birthYear], counting from the youngest the player could be this [year] (their
 * birthday may still be to come). Null until a year has been given: no ad is ever asked for before.
 */
fun audienceFor(birthYear: Int, year: Int): Audience? {
    if (birthYear <= 0 || birthYear > year) return null
    val youngest = year - birthYear - 1
    return when {
        youngest < Economy.CHILD_AGE -> Audience.Child
        youngest < Economy.CONSENT_AGE -> Audience.Teen
        else -> Audience.Adult
    }
}

/**
 * How ads are asked for, for an [Audience].
 *
 * - Child (under 13): child-directed treatment, under the age of consent, rated G at most, never
 *   personalised, and no consent form (a child can't give consent).
 * - Teen (13 to 15): under the age of consent, rated PG at most, never personalised. The consent
 *   check still runs where the law needs it, flagged as under age, so no form is shown.
 * - Adult (16 and over): rated PG at most. Where the law needs consent (EEA, UK) Google's consent
 *   form asks for it and its answer decides; anywhere else ads are non-personalised.
 */
data class AdPolicy(
    val childDirected: Boolean,
    val underAgeOfConsent: Boolean,
    val maxRating: String,
    /** Personalised ads are allowed only where the consent form ran and the player agreed. */
    val personalisedIfConsented: Boolean,
    /** Run Google's consent check (UMP) at all. */
    val consentCheck: Boolean,
)

fun policyFor(audience: Audience): AdPolicy = when (audience) {
    Audience.Child -> AdPolicy(childDirected = true, underAgeOfConsent = true, maxRating = "G", personalisedIfConsented = false, consentCheck = false)
    Audience.Teen -> AdPolicy(childDirected = false, underAgeOfConsent = true, maxRating = "PG", personalisedIfConsented = false, consentCheck = true)
    Audience.Adult -> AdPolicy(childDirected = false, underAgeOfConsent = false, maxRating = "PG", personalisedIfConsented = true, consentCheck = true)
}

/** Ads of [placement] the player may still watch on [day]. */
fun adsLeft(progress: Progress, placement: Placement, day: Long): Int {
    val used = if (progress.adDay == day) progress.adsWatched[placement.name] ?: 0 else 0
    return (placement.perDay - used).coerceAtLeast(0)
}

/** Counts one ad of [placement] watched on [day]; a new day starts every count again. */
fun watched(progress: Progress, placement: Placement, day: Long): Progress {
    val counts = if (progress.adDay == day) progress.adsWatched else emptyMap()
    return progress.copy(adDay = day, adsWatched = counts + (placement.name to (counts[placement.name] ?: 0) + 1))
}

/** The shop's ad was watched to the end: its Fireflies, if today's cap allows. Null if it doesn't. */
fun rewardShopAd(progress: Progress, day: Long, now: Long): Progress? {
    if (adsLeft(progress, Placement.Shop, day) <= 0) return null
    val counted = watched(progress, Placement.Shop, day)
    return counted.copy(wallet = counted.wallet.credit(Source.Ad.name, Economy.AD_SHOP_REWARD, now))
}

/**
 * The results' ad was watched to the end: the round's Fireflies again. Null if today's cap is used
 * or the round paid none. The caller asks once per round (see [Session]).
 */
fun rewardDouble(progress: Progress, report: Report, day: Long, now: Long): Progress? {
    if (report.fireflies <= 0 || adsLeft(progress, Placement.Double, day) <= 0) return null
    val counted = watched(progress, Placement.Double, day)
    return counted.copy(wallet = counted.wallet.credit(Source.Doubled.name, report.fireflies, now))
}

/** The relight ad was watched to the end: counted against today's cap. Null if it is used up. */
fun relightByAd(progress: Progress, day: Long): Progress? =
    if (adsLeft(progress, Placement.Relight, day) <= 0) null else watched(progress, Placement.Relight, day)
