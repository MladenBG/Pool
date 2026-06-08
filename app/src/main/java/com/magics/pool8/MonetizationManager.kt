package com.magics.pool8

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonetizationManager(private val context: Context) : PurchasesUpdatedListener {

    private val PREFS_NAME = "magics_monetization_prefs"
    private val KEY_IS_PREMIUM = "is_premium"
    private val PRODUCT_LIFETIME_PREMIUM = "lifetime_premium"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Observable premium state for Compose UI
    var isPremium by mutableStateOf(false)
        private set

    private lateinit var billingClient: BillingClient

    // AdMob Ads Instances
    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var isAppOpenAdLoading = false
    private var isInterstitialAdLoading = false

    init {
        // Load premium state cached in prefs
        isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)

        // Initialize AdMob Mobile Ads SDK
        CoroutineScope(Dispatchers.Main).launch {
            MobileAds.initialize(context) {
                if (!isPremium) {
                    loadAppOpenAd()
                    loadInterstitialAd()
                }
            }
        }

        // Initialize Google Play Billing Client
        setupBillingClient()
    }

    // --- GOOGLE PLAY BILLING SERVICE ---

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        startBillingConnection()
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Query purchases to update state on startup
                    queryPurchasesHistory()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection after delay
                CoroutineScope(Dispatchers.Default).launch {
                    kotlinx.coroutines.delay(5000L)
                    startBillingConnection()
                }
            }
        })
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        setPremiumStatus(true)
                    }
                }
            } else {
                setPremiumStatus(true)
            }
        }
    }

    private fun queryPurchasesHistory() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasPremium = false
                for (purchase in purchasesList) {
                    if (purchase.products.contains(PRODUCT_LIFETIME_PREMIUM) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasPremium = true
                        handlePurchase(purchase)
                    }
                }
                if (!hasPremium) {
                    setPremiumStatus(false)
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME_PREMIUM)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    private fun setPremiumStatus(active: Boolean) {
        isPremium = active
        prefs.edit().putBoolean(KEY_IS_PREMIUM, active).apply()
        if (active) {
            // Cancel ads loading if premium purchased
            appOpenAd = null
            interstitialAd = null
        }
    }

    // --- GOOGLE ADMOB ADS SDK ---

    // 1. App Open Ad Loader
    fun loadAppOpenAd() {
        if (isPremium || isAppOpenAdLoading) return
        isAppOpenAdLoading = true

        val request = AdRequest.Builder().build()
        // AdMob Test App Open Ad Unit ID
        AppOpenAd.load(
            context,
            "ca-app-pub-3940256099942544/9257395921",
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isAppOpenAdLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    isAppOpenAdLoading = false
                }
            }
        )
    }

    fun showAppOpenAd(activity: Activity) {
        if (isPremium) return
        appOpenAd?.let {
            it.show(activity)
            appOpenAd = null // Consume the ad
            loadAppOpenAd() // Pre-load next
        } ?: loadAppOpenAd()
    }

    // 2. Interstitial Ad Loader
    fun loadInterstitialAd() {
        if (isPremium || isInterstitialAdLoading) return
        isInterstitialAdLoading = true

        val request = AdRequest.Builder().build()
        // AdMob Test Interstitial Ad Unit ID
        InterstitialAd.load(
            context,
            "ca-app-pub-3940256099942544/1033173712",
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAdInstance: InterstitialAd) {
                    interstitialAd = interstitialAdInstance
                    isInterstitialAdLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialAdLoading = false
                }
            }
        )
    }

    fun showInterstitialAd(activity: Activity) {
        if (isPremium) return
        interstitialAd?.let {
            it.show(activity)
            interstitialAd = null // Consume the ad
            loadInterstitialAd() // Pre-load next
        } ?: loadInterstitialAd()
    }

    // 3. Banner Ad Constructor for Compose AndroidView wrapper
    fun createBannerAdView(activityActivity: Activity): AdView {
        val adView = AdView(activityActivity)
        adView.setAdSize(AdSize.BANNER)
        // AdMob Test Banner Ad Unit ID
        adView.adUnitId = "ca-app-pub-3940256099942544/6300978111"
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }
}
