package com.hiddenramblings.tagmo.browser

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import kotlinx.coroutines.*
import java.util.*

class DonationManager internal constructor(private val activity: BrowserActivity) {
    private var billingClient: BillingClient? = null
    private val iapSkuDetails = ArrayList<ProductDetails>()
    private val subSkuDetails = ArrayList<ProductDetails>()

    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    private fun getIAP(amount: Int): String {
        return String.format(Locale.ROOT, "subscription_%02d", amount)
    }

    private fun getSub(amount: Int): String {
        return String.format(Locale.ROOT, "monthly_%02d", amount)
    }

    private val iapList = ArrayList<String>()
    private val subList = ArrayList<String>()
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
                for (iap in iapList) {
                    if (purchase.products.contains(iap)) handlePurchaseIAP(purchase)
                }
                for (sub in subList) {
                    if (purchase.products.contains(sub)) handlePurchaseSub(purchase)
                }
            }
        }
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult: BillingResult, purchases: List<Purchase>? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && null != purchases) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
    private val subsPurchased = ArrayList<String>()
    private val subsOwnedListener = PurchasesResponseListener {
            billingResult: BillingResult, purchases: List<Purchase> ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    for (sku in purchase.products) {
                        if (subsPurchased.contains(sku)) {
                            TagMo.hasSubscription = true
                            break
                        }
                    }
                }
            }
        }
    private val subHistoryListener = PurchaseHistoryResponseListener {
            billingResult: BillingResult, purchases: List<PurchaseHistoryRecord>? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && null != purchases) {
                for (purchase in purchases) subsPurchased.addAll(purchase.products)
                billingClient?.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build(), subsOwnedListener
                )
            }
        }
    private val iapHistoryListener = PurchaseHistoryResponseListener {
            billingResult: BillingResult, purchases: List<PurchaseHistoryRecord>? ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && null != purchases) {
                for (purchase in purchases) {
                    for (sku in purchase.products) {
                        if (sku.split("_").toTypedArray()[1].toInt() >= 10) {
                            break
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
                    if (BuildConfig.GOOGLE_PLAY) return@launch
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
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
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
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            Resources.getSystem().displayMetrics
        )
        button.text = activity.getString(
            R.string.iap_button, skuDetail
                .oneTimePurchaseOfferDetails!!.formattedPrice
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
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
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
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            Resources.getSystem().displayMetrics
        )
        button.text = activity.getString(
            R.string.sub_button, skuDetail
                .subscriptionOfferDetails!![0].pricingPhases
                .pricingPhaseList[0].formattedPrice
        )
        button.setOnClickListener {
            val productDetailsParamsList = ProductDetailsParams.newBuilder()
                .setOfferToken(skuDetail.subscriptionOfferDetails!![0].offerToken)
                .setProductDetails(skuDetail).build()
            billingClient?.launchBillingFlow(
                activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParamsList))
                    .build()
            )
        }
        return button
    }

    fun onSendDonationClicked() {
        val layout = activity.layoutInflater
            .inflate(R.layout.donation_layout, null) as LinearLayout
        val dialog = AlertDialog.Builder(
            ContextThemeWrapper(activity, R.style.DialogTheme_NoActionBar)
        )
        val donations = layout.findViewById<LinearLayout>(R.id.donation_layout)
        donations.removeAllViewsInLayout()
        iapSkuDetails.sortWith { obj1: ProductDetails, obj2: ProductDetails ->
            obj1.productId.compareTo(obj2.productId, ignoreCase = true)
        }
        for (skuDetail in iapSkuDetails) {
            if (null == skuDetail.oneTimePurchaseOfferDetails) continue
            donations.addView(getDonationButton(skuDetail))
        }
        val subscriptions = layout.findViewById<LinearLayout>(R.id.subscription_layout)
        if (BuildConfig.GOOGLE_PLAY) {
            subscriptions.isGone = true
        } else {
            subscriptions.isVisible = true
            subscriptions.removeAllViewsInLayout()
            subSkuDetails.sortWith { obj1: ProductDetails, obj2: ProductDetails ->
                obj1.productId.compareTo(obj2.productId, ignoreCase = true)
            }
            for (skuDetail in subSkuDetails) {
                if (null == skuDetail.subscriptionOfferDetails) continue
                subscriptions.addView(getSubscriptionButton(skuDetail))
            }
        }
        dialog.setOnCancelListener {
            donations.removeAllViewsInLayout()
            if (!BuildConfig.GOOGLE_PLAY) subscriptions.removeAllViewsInLayout()
        }
        dialog.setOnDismissListener {
            donations.removeAllViewsInLayout()
            if (!BuildConfig.GOOGLE_PLAY) subscriptions.removeAllViewsInLayout()
        }
        val donateDialog: Dialog = dialog.setView(layout).show()

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

        if (!BuildConfig.GOOGLE_PLAY) {
            @SuppressLint("InflateParams") val manage =
                activity.layoutInflater.inflate(R.layout.button_cancel_sub, null)
            manage.setOnClickListener {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://support.google.com/googleplay/workflow/9827184"
                )))
                donateDialog.cancel()
            }
            manage.layoutParams = params
            layout.addView(manage)

//        if (!BuildConfig.GOOGLE_PLAY) {
            @SuppressLint("InflateParams") val sponsor =
                activity.layoutInflater.inflate(R.layout.button_sponsor, null)
            sponsor.setOnClickListener {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/sponsors/AbandonedCart"
                )))
                donateDialog.cancel()
            }
            sponsor.layoutParams = params
            layout.addView(sponsor)

            @SuppressLint("InflateParams") val paypal =
                activity.layoutInflater.inflate(R.layout.button_paypal, null)
            paypal.setOnClickListener {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
                )))
                donateDialog.cancel()
            }
            paypal.layoutParams = params
            layout.addView(paypal)
        }
        donateDialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}