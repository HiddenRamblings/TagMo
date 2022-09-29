package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchaseHistoryParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class DonationHandler {

    private BrowserActivity activity;

    private BillingClient billingClient;
    private final ArrayList<ProductDetails> iapSkuDetails = new ArrayList<>();
    private final ArrayList<ProductDetails> subSkuDetails = new ArrayList<>();

    DonationHandler(BrowserActivity activity) {
        this.activity = activity;
    }

    private String getIAP(int amount) {
        return String.format(Locale.ROOT, "subscription_%02d", amount);
    }

    private String getSub(int amount) {
        return String.format(Locale.ROOT, "monthly_%02d", amount);
    }

    private final ArrayList<String> iapList = new ArrayList<>();
    private final ArrayList<String> subList = new ArrayList<>();

    private final ConsumeResponseListener consumeResponseListener = (billingResult, s)
            -> new IconifiedSnackbar(activity).buildTickerBar(R.string.donation_thanks).show();

    private void handlePurchaseIAP(Purchase purchase) {
        ConsumeParams.Builder consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken());
        billingClient.consumeAsync(consumeParams.build(), consumeResponseListener);
    }

    private final AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = billingResult
            -> new IconifiedSnackbar(activity).buildTickerBar(R.string.donation_thanks).show();

    private void handlePurchaseSub(Purchase purchase) {
        AcknowledgePurchaseParams.Builder acknowledgePurchaseParams = AcknowledgePurchaseParams
                .newBuilder().setPurchaseToken(purchase.getPurchaseToken());
        billingClient.acknowledgePurchase(acknowledgePurchaseParams.build(),
                acknowledgePurchaseResponseListener);
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                for (String iap : iapList) {
                    if (purchase.getProducts().contains(iap))
                        handlePurchaseIAP(purchase);
                }
                for (String sub : subList) {
                    if (purchase.getProducts().contains(sub))
                        handlePurchaseSub(purchase);
                }
            }
        }
    }

    private final PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        }
    };

    private final ArrayList<String> subsPurchased = new ArrayList<>();

    private final PurchasesResponseListener subsOwnedListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            for (Purchase purchase : purchases) {
                for (String sku : purchase.getProducts()) {
                    if (subsPurchased.contains(sku)) {
                        break;
                    }
                }
            }
        }
    };

    private final PurchaseHistoryResponseListener subHistoryListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (PurchaseHistoryRecord purchase : purchases)
                subsPurchased.addAll(purchase.getProducts());
            billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS).build(), subsOwnedListener);
        }
    };

    private final PurchaseHistoryResponseListener iapHistoryListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (PurchaseHistoryRecord purchase : purchases) {
                for (String sku : purchase.getProducts()) {
                    if (Integer.parseInt(sku.split("_")[1]) >= 10) {
                        break;
                    }
                }
            }
        }
    };

    void retrieveDonationMenu() {
        Executors.newSingleThreadExecutor().execute(() -> {
            billingClient = BillingClient.newBuilder(activity)
                    .setListener(purchasesUpdatedListener).enablePendingPurchases().build();

            iapSkuDetails.clear();
            subSkuDetails.clear();

            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingServiceDisconnected() {
                }

                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        iapList.add(getIAP(1));
                        iapList.add(getIAP(5));
                        iapList.add(getIAP(10));
                        iapList.add(getIAP(25));
                        iapList.add(getIAP(50));
                        iapList.add(getIAP(75));
                        iapList.add(getIAP(99));
                        for (String productId : iapList) {
                            QueryProductDetailsParams.Product productList = QueryProductDetailsParams
                                    .Product.newBuilder().setProductId(productId)
                                    .setProductType(BillingClient.ProductType.INAPP).build();
                            QueryProductDetailsParams.Builder params = QueryProductDetailsParams
                                    .newBuilder().setProductList(List.of(productList));
                            billingClient.queryProductDetailsAsync(params.build(),
                                    (billingResult1, productDetailsList) -> {
                                        iapSkuDetails.addAll(productDetailsList);
                                        billingClient.queryPurchaseHistoryAsync(
                                                QueryPurchaseHistoryParams.newBuilder().setProductType(
                                                        BillingClient.ProductType.INAPP
                                                ).build(), iapHistoryListener
                                        );
                                    });

                        }
                    }
                    subList.add(getSub(1));
                    subList.add(getSub(5));
                    subList.add(getSub(10));
                    subList.add(getSub(25));
                    subList.add(getSub(50));
                    subList.add(getSub(75));
                    subList.add(getSub(99));
                    for (String productId : subList) {
                        QueryProductDetailsParams.Product productList = QueryProductDetailsParams
                                .Product.newBuilder().setProductId(productId)
                                .setProductType(BillingClient.ProductType.SUBS).build();
                        QueryProductDetailsParams.Builder params = QueryProductDetailsParams
                                .newBuilder().setProductList(List.of(productList));
                        billingClient.queryProductDetailsAsync(params.build(),
                                (billingResult1, productDetailsList) -> {
                                    subSkuDetails.addAll(productDetailsList);
                                    billingClient.queryPurchaseHistoryAsync(
                                            QueryPurchaseHistoryParams.newBuilder().setProductType(
                                                    BillingClient.ProductType.SUBS
                                            ).build(), subHistoryListener
                                    );
                                });
                    }
                }
            });
        });
    }

    @SuppressWarnings("ConstantConditions")
    private Button getDonationButton(ProductDetails skuDetail) {
        Button button = new Button(activity.getApplicationContext());
        button.setBackgroundResource(R.drawable.rounded_view);
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            button.setElevation(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    10f,
                    Resources.getSystem().getDisplayMetrics()
            ));
        }
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                Resources.getSystem().getDisplayMetrics()
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, padding, 0, padding);
        button.setLayoutParams(params);
        button.setTextColor(ContextCompat.getColor(activity, android.R.color.white));
        button.setText(activity.getString(R.string.iap_button, skuDetail
                .getOneTimePurchaseOfferDetails().getFormattedPrice()));
        button.setOnClickListener(view1 -> {
            BillingFlowParams.ProductDetailsParams productDetailsParamsList
                    = BillingFlowParams.ProductDetailsParams
                    .newBuilder().setProductDetails(skuDetail).build();
            billingClient.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(List.of(productDetailsParamsList)).build()
            );
        });
        return button;
    }

    @SuppressWarnings("ConstantConditions")
    private Button getSubscriptionButton(ProductDetails skuDetail) {
        Button button = new Button(activity.getApplicationContext());
        button.setBackgroundResource(R.drawable.rounded_view);
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            button.setElevation(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    10f,
                    Resources.getSystem().getDisplayMetrics()
            ));
        }
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                Resources.getSystem().getDisplayMetrics()
        );
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, padding, 0, padding);
        button.setLayoutParams(params);
        button.setTextColor(ContextCompat.getColor(activity, android.R.color.white));
        button.setText(activity.getString(R.string.sub_button, skuDetail
                .getSubscriptionOfferDetails().get(0).getPricingPhases()
                .getPricingPhaseList().get(0).getFormattedPrice()));
        button.setOnClickListener(view1 -> {
            BillingFlowParams.ProductDetailsParams productDetailsParamsList
                    = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setOfferToken(skuDetail.getSubscriptionOfferDetails().get(0).getOfferToken())
                    .setProductDetails(skuDetail).build();
            billingClient.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(List.of(productDetailsParamsList)).build()
            );
        });
        return button;
    }

    void onSendDonationClicked() {
        if (TagMo.isCompatBuild()) {
            LinearLayout layout = (LinearLayout) activity.getLayoutInflater()
                    .inflate(R.layout.donation_layout, null);
            AlertDialog.Builder dialog = new AlertDialog.Builder(new ContextThemeWrapper(
                    activity, R.style.DialogTheme_NoActionBar
            ));
            LinearLayout donations = layout.findViewById(R.id.donation_layout);
            Collections.sort(iapSkuDetails, (obj1, obj2) ->
                    obj1.getProductId().compareToIgnoreCase(obj2.getProductId()));
            for (ProductDetails skuDetail : iapSkuDetails) {
                if (null == skuDetail.getOneTimePurchaseOfferDetails()) continue;
                donations.addView(getDonationButton(skuDetail));
            }
            LinearLayout subscriptions = layout.findViewById(R.id.subscription_layout);
            Collections.sort(subSkuDetails, (obj1, obj2) ->
                    obj1.getProductId().compareToIgnoreCase(obj2.getProductId()));
            for (ProductDetails skuDetail : subSkuDetails) {
                if (null == skuDetail.getSubscriptionOfferDetails()) continue;
                subscriptions.addView(getSubscriptionButton(skuDetail));
            }
            dialog.setOnCancelListener(dialogInterface -> {
                donations.removeAllViewsInLayout();
                subscriptions.removeAllViewsInLayout();
            });
            dialog.setOnDismissListener(dialogInterface -> {
                donations.removeAllViewsInLayout();
                subscriptions.removeAllViewsInLayout();
            });
            Dialog donateDialog = dialog.setView(layout).show();
            if (!TagMo.isGooglePlay()) {
                @SuppressLint("InflateParams")
                View paypal = activity.getLayoutInflater().inflate(R.layout.button_paypal, null);
                paypal.setOnClickListener(view -> {
                    activity.closePrefsDrawer();
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
                    )));
                    donateDialog.cancel();
                });
                layout.addView(paypal);
            }
            donateDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            activity.closePrefsDrawer();
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
            )));
        }
    }
}
