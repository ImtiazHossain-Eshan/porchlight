package app.porchlight.game

/**
 * What can be bought with money, and how a purchase is granted, kept and taken back, as pure
 * functions over [Progress]. Google Play does the selling (see Billing.kt); there is no server, so
 * every purchase is granted once, by its token, and the tokens are kept in the wallet.
 */
enum class Product(
    val id: String,
    val title: String,
    val fireflies: Int,
    /** Consumable packs can be bought again and again; the others once. */
    val consumable: Boolean,
    /** The reference price in US cents; the real price is Play's, in the player's own currency. */
    val usdCents: Int,
    val looks: List<String> = emptyList(),
    val supporter: Boolean = false,
) {
    Handful("fireflies_handful", "A handful", Economy.PACK_HANDFUL, consumable = true, usdCents = 99),
    Jar("fireflies_jar", "A jar", Economy.PACK_JAR, consumable = true, usdCents = 499),
    Lantern("fireflies_lantern", "A lantern", Economy.PACK_LANTERN, consumable = true, usdCents = 999),
    Porchful("fireflies_porchful", "A porchful", Economy.PACK_PORCHFUL, consumable = true, usdCents = 1_999),
    Starter("starter_pack", "Starter pack", Economy.STARTER_FIREFLIES, consumable = false, usdCents = 199, looks = listOf(STARTER_LOOK)),
    Supporter("supporter", "Supporter", 0, consumable = false, usdCents = 499, looks = listOf(SUPPORTER_LOOK), supporter = true),
    ;

    /** What it gives, in words. */
    val gives: String get() = when (this) {
        Starter -> "${fireflies(fireflies)} and the Firefly lacquer card back, only in this pack."
        Supporter -> "Your daily gift doubled for good, and the Golden hour porch, only for Supporters."
        else -> fireflies(fireflies)
    }

    companion object {
        val packs = listOf(Handful, Jar, Lantern, Porchful)
        fun byId(id: String): Product? = entries.find { it.id == id }
    }
}

/**
 * How much more a pack gives than the same money spent on handfuls, from the real prices Play shows
 * (so it is true in every currency): 0.10 for 10% more. Null if a price isn't known.
 */
fun bonusOf(product: Product, micros: Map<Product, Long>): Double? {
    if (product !in Product.packs || product == Product.Handful) return null
    val handful = micros[Product.Handful]?.takeIf { it > 0 } ?: return null
    val price = micros[product]?.takeIf { it > 0 } ?: return null
    val asHandfuls = Product.Handful.fireflies * price.toDouble() / handful
    return product.fireflies / asHandfuls - 1.0
}

/** The bonus as a whole percentage, rounded down so it never claims more than is true; null if none. */
fun bonusPercent(product: Product, micros: Map<Product, Long>): Int? =
    bonusOf(product, micros)?.let { kotlin.math.floor(it * 100 + 1e-9).toInt() }?.takeIf { it > 0 }

private const val VOID = "void:"

/** How a purchase is kept in the wallet: its product and its Play token. */
fun purchaseKey(product: Product, token: String) = "${product.id}@$token"

private fun Wallet.granted(key: String) = key in purchases && VOID + key !in purchases

/** Purchases that went through and were not refunded. */
val Wallet.bought: List<String> get() = purchases.filter { !it.startsWith(VOID) && VOID + it !in purchases }

/** A Supporter: the daily gift is doubled and the Golden hour porch is theirs. */
val Progress.supporter: Boolean get() = wallet.bought.any { it.startsWith(Product.Supporter.id + "@") }

/** Owned non-consumables, for the shop to stop offering them. */
fun Progress.ownsProduct(product: Product): Boolean = wallet.bought.any { it.startsWith(product.id + "@") }

/**
 * Grants a purchase Play says went through: its Fireflies, its looks, and Supporter if it is that.
 * Exactly once per token: a token already granted returns null, and nothing changes. A purchase that
 * was refunded and then shows up as bought again (the refund was reversed) is granted again.
 */
fun grant(progress: Progress, product: Product, token: String, now: Long): Progress? {
    val key = purchaseKey(product, token)
    val wallet = progress.wallet
    if (wallet.granted(key)) return null
    val kept = wallet.copy(purchases = wallet.purchases - (VOID + key) + key)
    val paid = kept.credit("${Source.Bought.name}/${product.id}", product.fireflies, now)
    return progress.copy(wallet = paid, owned = progress.owned + product.looks)
}

/**
 * Takes back a refunded or voided purchase: what it gave, as far as it is still there. Fireflies
 * already spent stay spent (the balance never goes below zero); its looks are taken off and given
 * back. A token that was never granted, or already taken back, changes nothing.
 */
fun revoke(progress: Progress, product: Product, token: String, now: Long): Progress {
    val key = purchaseKey(product, token)
    val wallet = progress.wallet
    if (!wallet.granted(key)) return progress
    val take = minOf(product.fireflies, wallet.balance)
    val ledger = if (take > 0) (wallet.ledger + LedgerEntry("Refunded/${product.id}", -take, now)).takeLast(Economy.LEDGER_SIZE) else wallet.ledger
    val after = wallet.copy(
        balance = wallet.balance - take,
        earned = (wallet.earned - take).coerceAtLeast(0),
        purchases = wallet.purchases + (VOID + key),
        ledger = ledger,
    )
    val looks = product.looks.toSet()
    return progress.copy(
        wallet = after,
        owned = progress.owned - looks,
        equipped = progress.equipped.filterValues { it !in looks },
    )
}

/**
 * After Play lists what the player owns: one-time purchases granted here but no longer owned there
 * have been refunded or voided. Consumables can't be checked this way (they are used up at Play).
 */
fun refundedOneTimes(progress: Progress, ownedAtPlay: Set<String>): List<Pair<Product, String>> =
    progress.wallet.bought.mapNotNull { key ->
        val (id, token) = key.split("@", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        val product = Product.byId(id) ?: return@mapNotNull null
        if (product.consumable || key in ownedAtPlay) null else product to token
    }
