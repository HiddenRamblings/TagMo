package com.hiddenramblings.tagmo.browser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.eightbit.os.Version
import kotlinx.coroutines.*
import java.util.*

class DonationManager internal constructor(private val activity: BrowserActivity) {
    private var billingClient: BillingClient? = null
    private val iapSkuDetails: ArrayList<ProductDetails> = arrayListOf()
    private val subSkuDetails: ArrayList<ProductDetails> = arrayListOf()

    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    private fun getIAP(amount: Int): String {
        return String.format(Locale.ROOT, "subscription_%02d", amount)
    }

    private fun getSub(amount: Int): String {
        return String.format(Locale.ROOT, "monthly_%02d", amount)
    }

    private val iapList: ArrayList<String> = arrayListOf()
    private val subList: ArrayList<String> = arrayListOf()
    private val consumeResponseListener =
        ConsumeResponseListener { _: BillingResult?, _: String? ->
            IconifiedSnackbar(activity).buildTickerBar(R.string.donation_thanks).show()
        }

    private fun handlePurchaseIAP(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
        billingClient?.consumeAsync(consumeParams.build(), consumeResponseListener)
    }

    private val acknowledgePurchaseResponseListener =
        AcknowledgePurchaseResponseListener {
            IconifiedSnackbar(activity).buildTickerBar(R.string.donation_thanks).show()
            TagMo.hasSubscription = true
        }

    private fun handlePurchaseSub(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams
            .newBuilder().setPurchaseToken(purchase.purchaseToken)
        billingClient?.acknowledgePurchase(
            acknowledgePurchaseParams.build(),
            acknowledgePurchaseResponseListener
        )
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                iapList.forEach {
                    if (purchase.products.contains(it)) handlePurchaseIAP(purchase)
                }
                subList.forEach {
                    if (purchase.products.contains(it)) handlePurchaseSub(purchase)
                }
            }
        }
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult: BillingResult, purchases: List<Purchase>? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases?.forEach {
                    handlePurchase(it)
                }
            }
        }
    private val subsPurchased: ArrayList<String> = arrayListOf()
    private val subsOwnedListener = PurchasesResponseListener {
            billingResult: BillingResult, purchases: List<Purchase> ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach {
                    run breaking@{
                        it.products.forEach { sku ->
                            if (subsPurchased.contains(sku)) {
                                TagMo.hasSubscription = true
                                return@breaking
                            }
                        }
                    }
                }
            }
        }
    private val subHistoryListener = PurchaseHistoryResponseListener {
            billingResult: BillingResult, purchases: List<PurchaseHistoryRecord>? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases?.forEach {
                    subsPurchased.addAll(it.products)
                }
                billingClient?.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(
                        BillingClient.ProductType.SUBS
                    ).build(), subsOwnedListener
                )
            }
        }
    private val iapHistoryListener = PurchaseHistoryResponseListener {
            billingResult: BillingResult, purchases: List<PurchaseHistoryRecord>? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases?.forEach {
                    run breaking@{
                        it.products.forEach { sku ->
                            if (sku.split("_").toTypedArray()[1].toInt() >= 10) {
                                return@breaking
                            }
                        }
                    }
                }
            }
        }

    fun retrieveDonationMenu() {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener).enablePendingPurchases().build()
        iapSkuDetails.clear()
        subSkuDetails.clear()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {}
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                backgroundScope.launch(Dispatchers.IO) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        iapList.add(getIAP(1))
                        iapList.add(getIAP(5))
                        iapList.add(getIAP(10))
                        iapList.add(getIAP(25))
                        iapList.add(getIAP(50))
                        iapList.add(getIAP(75))
                        iapList.add(getIAP(99))
                        iapList.forEach {
                            val productList = Product.newBuilder().setProductId(it)
                                .setProductType(BillingClient.ProductType.INAPP).build()
                            val params = QueryProductDetailsParams
                                .newBuilder().setProductList(listOf(productList))
                            billingClient!!.queryProductDetailsAsync(
                                params.build()
                            ) { _: BillingResult?, productDetailsList: List<ProductDetails>? ->
                                iapSkuDetails.addAll(productDetailsList!!)
                                billingClient!!.queryPurchaseHistoryAsync(
                                    QueryPurchaseHistoryParams.newBuilder().setProductType(
                                        BillingClient.ProductType.INAPP
                                    ).build(), iapHistoryListener
                                )
                            }
                        }
                    }
                    subList.add(getSub(1))
                    subList.add(getSub(5))
                    subList.add(getSub(10))
                    subList.add(getSub(25))
                    subList.add(getSub(50))
                    subList.add(getSub(75))
                    subList.add(getSub(99))
                    subList.forEach {
                        val productList = Product.newBuilder().setProductId(it)
                            .setProductType(BillingClient.ProductType.SUBS).build()
                        val params = QueryProductDetailsParams
                            .newBuilder().setProductList(listOf(productList))
                        billingClient!!.queryProductDetailsAsync(
                            params.build()
                        ) { _: BillingResult?, productDetailsList: List<ProductDetails>? ->
                            subSkuDetails.addAll(
                                productDetailsList!!
                            )
                            billingClient!!.queryPurchaseHistoryAsync(
                                QueryPurchaseHistoryParams.newBuilder().setProductType(
                                    BillingClient.ProductType.SUBS
                                ).build(), subHistoryListener
                            )
                        }
                    }
                }
            }
        })
    }

    private fun getDonationButton(skuDetail: ProductDetails): Button {
        val button = Button(activity.applicationContext)
        button.setBackgroundResource(R.drawable.rounded_view)
        if (Version.isLollipop) {
            button.elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10f,
                Resources.getSystem().displayMetrics
            )
        }
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            Resources.getSystem().displayMetrics
        ).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, padding, 0, padding)
        button.layoutParams = params
        button.setTextColor(ContextCompat.getColor(activity, android.R.color.white))
        button.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, Resources.getSystem().displayMetrics
        )
        button.text = activity.getString(
            R.string.iap_button, skuDetail.oneTimePurchaseOfferDetails!!.formattedPrice
        )
        button.setOnClickListener {
            val productDetailsParamsList = ProductDetailsParams
                .newBuilder().setProductDetails(skuDetail).build()
            billingClient?.launchBillingFlow(
                activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParamsList))
                    .build()
            )
        }
        return button
    }

    private fun getSubscriptionButton(skuDetail: ProductDetails): Button {
        val button = Button(activity.applicationContext)
        button.setBackgroundResource(R.drawable.rounded_view)
        if (Version.isLollipop) {
            button.elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                10f,
                Resources.getSystem().displayMetrics
            )
        }
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            Resources.getSystem().displayMetrics
        ).toInt()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, padding, 0, padding)
        button.layoutParams = params
        button.setTextColor(ContextCompat.getColor(activity, android.R.color.white))
        button.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, Resources.getSystem().displayMetrics
        )
        button.text = activity.getString(
            R.string.sub_button, skuDetail
                .subscriptionOfferDetails!![0].pricingPhases
                .pricingPhaseList[0].formattedPrice
        )
        button.setOnClickListener {
            AlertDialog.Builder(activity)
                .setMessage(R.string.subscription_terms)
                .setPositiveButton(R.string.proceed) { _: DialogInterface?, _: Int ->
                    val productDetailsParamsList = ProductDetailsParams.newBuilder()
                        .setOfferToken(skuDetail.subscriptionOfferDetails!![0].offerToken)
                        .setProductDetails(skuDetail).build()
                    billingClient?.launchBillingFlow(
                        activity, BillingFlowParams.newBuilder()
                            .setProductDetailsParamsList(listOf(productDetailsParamsList))
                            .build()
                    )
                }
                .setNegativeButton(R.string.cancel) { _: DialogInterface?, _: Int -> }
                .show()
        }
        return button
    }

    @SuppressLint("InflateParams")
    fun onSendDonationClicked() {
        (activity.layoutInflater.inflate(R.layout.donation_layout, null) as LinearLayout).run {
            val donateDialog: Dialog = AlertDialog.Builder(
                ContextThemeWrapper(activity, R.style.Theme_Overlay_NoActionBar)
            ).apply {
                findViewById<LinearLayout>(R.id.donation_layout).also { layout ->
                    layout.removeAllViewsInLayout()
                    iapSkuDetails.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER) { it.productId }
                    ).forEach { skuDetail ->
                        if (null != skuDetail.oneTimePurchaseOfferDetails)
                            layout.addView(getDonationButton(skuDetail))
                    }
                    findViewById<LinearLayout>(R.id.subscription_layout).run {
                        removeAllViewsInLayout()
                        subSkuDetails.sortedWith(
                            compareBy(String.CASE_INSENSITIVE_ORDER) { it.productId }
                        ).forEach { skuDetail ->
                            if (null != skuDetail.subscriptionOfferDetails)
                                addView(getSubscriptionButton(skuDetail))
                        }
                        this@apply.setOnCancelListener {
                            layout.removeAllViewsInLayout()
                            if (!BuildConfig.GOOGLE_PLAY) removeAllViewsInLayout()
                        }
                        this@apply.setOnDismissListener {
                            layout.removeAllViewsInLayout()
                            if (!BuildConfig.GOOGLE_PLAY) removeAllViewsInLayout()
                        }
                    }
                }
            }.setView(this).show()

            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, Resources.getSystem().displayMetrics
            ).toInt()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, padding, 0, padding)

            if (TagMo.hasSubscription) {
                addView(activity.layoutInflater.inflate(R.layout.button_cancel_sub, null).apply {
                    setOnClickListener {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://support.google.com/googleplay/workflow/9827184"
                        )))
                        donateDialog.cancel()
                    }
                    layoutParams = params
                })
            }

            if (!BuildConfig.GOOGLE_PLAY) {
                addView(activity.layoutInflater.inflate(R.layout.button_sponsor, null).apply {
                    setOnClickListener {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://github.com/sponsors/AbandonedCart"
                        )))
                        donateDialog.cancel()
                    }
                })

                addView(activity.layoutInflater.inflate(R.layout.button_paypal, null).apply {
                    setOnClickListener {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
                        )))
                        donateDialog.cancel()
                    }
                })
            }
            donateDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }
}