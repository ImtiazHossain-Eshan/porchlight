package app.porchlight.game

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * The till: where Fireflies packs, the starter pack and Supporter are sold. Google Play does the
 * selling; the till hands each finished purchase to [Store.grantPurchase] (which grants it exactly
 * once, by token) and only then tells Play it is done (consumed, or acknowledged).
 */
interface Till {
    /** Prices as Play shows them, in the player's currency; a product missing here isn't for sale now. */
    val prices: Map<Product, String>

    /** Prices in micro-units, for working out the true bonus of each pack (see [bonusOf]). */
    val micros: Map<Product, Long>

    /** Purchases waiting for the payment to go through (cash, bank transfer...). */
    val pending: Set<Product>

    fun start()

    fun buy(activity: Activity, product: Product)
}

/** Google Play Billing. Grants on the main thread; restores and checks for refunds each time it connects. */
class PlayTill(
    context: Context,
    private val store: () -> Store?,
    /** The app's licence key from Play Console, to check each purchase's signature here. Blank skips the check. */
    private val publicKey: String,
) : Till {
    private val main = Handler(Looper.getMainLooper())
    private var details: Map<Product, ProductDetails> = emptyMap()

    override var prices by mutableStateOf<Map<Product, String>>(emptyMap())
        private set
    override var micros by mutableStateOf<Map<Product, Long>>(emptyMap())
        private set
    override var pending by mutableStateOf<Set<Product>>(emptySet())
        private set

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> main.post { onUpdated(result, purchases.orEmpty()) } }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var connecting = false

    override fun start() {
        if (client.isReady) {
            refresh()
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                main.post { connecting = false }
                if (result.responseCode == BillingClient.BillingResponseCode.OK) main.post { refresh() }
                else Log.i(TAG, "Play Billing isn't available (${result.responseCode})")
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Prices, then whatever the player already owns: restore, finish, and look for refunds. */
    private fun refresh() {
        val products = Product.entries.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(BillingClient.ProductType.INAPP).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, found ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "No products from Play (${result.responseCode})")
                return@queryProductDetailsAsync
            }
            val known = found.productDetailsList.mapNotNull { d -> Product.byId(d.productId)?.let { it to d } }.toMap()
            main.post {
                details = known
                prices = known.mapNotNull { (p, d) -> d.oneTimePurchaseOfferDetails?.formattedPrice?.let { p to it } }.toMap()
                micros = known.mapNotNull { (p, d) -> d.oneTimePurchaseOfferDetails?.priceAmountMicros?.let { p to it } }.toMap()
            }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, owned ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            main.post {
                owned.forEach(::handle)
                // One-time purchases granted here but no longer owned at Play were refunded or voided.
                val atPlay = owned.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { p -> p.products.mapNotNull { id -> Product.byId(id)?.let { purchaseKey(it, p.purchaseToken) } } }.toSet()
                val store = store() ?: return@post
                refundedOneTimes(store.progress, atPlay).forEach { (product, token) -> store.revokePurchase(product, token) }
            }
        }
    }

    override fun buy(activity: Activity, product: Product) {
        val d = details[product] ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun onUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.forEach(::handle)
            // Already owned: restore it.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> Unit // Cancelled or failed: nothing was bought, nothing to say.
        }
    }

    private fun handle(purchase: Purchase) {
        val products = purchase.products.mapNotNull(Product::byId)
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> pending = pending + products
            Purchase.PurchaseState.PURCHASED -> {
                pending = pending - products.toSet()
                if (!verified(purchase)) {
                    Log.w(TAG, "A purchase failed its signature check and was not granted")
                    return
                }
                val store = store() ?: return
                // Granted first (and saved), then finished at Play. If finishing fails, Play hands the
                // purchase back next time and the token stops it being granted twice.
                products.forEach { store.grantPurchase(it, purchase.purchaseToken) }
                if (products.any { it.consumable }) {
                    client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { _, _ -> }
                } else if (!purchase.isAcknowledged) {
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { }
                }
            }
            else -> Unit
        }
    }

    /** The purchase's signature, checked against the app's licence key. Local only: see docs/ECONOMY.md. */
    private fun verified(purchase: Purchase): Boolean {
        if (publicKey.isBlank()) return true
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(purchase.originalJson.toByteArray())
                verify(Base64.decode(purchase.signature, Base64.DEFAULT))
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "PorchlightTill"
    }
}

/**
 * Debug builds only (--ez fakeStore true): a stand-in for Google Play on an emulator or a build that
 * isn't on a Play track, where Play has no products to sell. It runs the same grant path as a real
 * purchase, with made-up tokens, and charges nothing. Never in a release build.
 */
class FakeTill(private val store: () -> Store?) : Till {
    override val prices: Map<Product, String> = Product.entries.associateWith { "US$" + "%.2f".format(java.util.Locale.US, it.usdCents / 100.0) + " (test)" }
    override val micros: Map<Product, Long> = Product.entries.associateWith { it.usdCents * 10_000L }
    override var pending by mutableStateOf<Set<Product>>(emptySet())
        private set

    override fun start() = Unit

    override fun buy(activity: Activity, product: Product) {
        val store = store() ?: return
        if (!product.consumable && store.progress.ownsProduct(product)) return
        store.grantPurchase(product, "test-" + java.util.UUID.randomUUID())
    }
}
