/*
 * ====================================================================
 * Copyright (c) 2024 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================.
 */

package com.hiddenramblings.tagmo.eightbit.webkit

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.fragment.toPx
import java.net.HttpURLConnection
import java.net.URL


object ChromeIntegration {
    private var mClient: CustomTabsClient? = null
    private var mSession: CustomTabsSession? = null
    var hasCustomTabs = true

    private val mConnection: CustomTabsServiceConnection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(
            name: ComponentName,
            client: CustomTabsClient
        ) {
            mClient = client.apply {
                warmup(0 /* placeholder for future use */)
                mSession = newSession(CustomTabsCallback())?.apply {
                    // Can be individually or as list in ascending priority (last => most)
                    // mayLaunchUrl(Uri.parse("https://www.crunchyroll.com/"), null, null)
                    mayLaunchUrl("https://protools.flashiibo.com/".toUri(), null, listOf(
                        Bundle().apply {
                            putParcelable(
                                CustomTabsService.KEY_URL,
                                "https://www.amazon.com/".toUri()
                            )
                        }
                    ))
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mClient = null
            mSession = null
        }
    }

    fun bindCustomTabService(context: Context) : Boolean {
        if (mClient != null) return false

        val browserPackages: ArrayList<String> = arrayListOf()
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, "https://protools.flashiibo.com/".toUri()),
            PackageManager.MATCH_DEFAULT_ONLY
        ).forEach {
            browserPackages.add(it.resolvePackageName)
        }

        val packageName = CustomTabsClient.getPackageName(
            context, browserPackages.ifEmpty { null }
        ) ?: return false
        return CustomTabsClient.bindCustomTabsService(context, packageName, mConnection)
    }

    // ChromeIntegration.openPortalTab(context, "https://protools.flashiibo.com/")
    fun openPortalTab(context: Context, link: String?) {
        link?.let {
            try {
                CustomTabsIntent.Builder(mSession)
                    .setBackgroundInteractionEnabled(true)
                    .setCloseButtonIcon(BitmapFactory.decodeResource(
                        context.resources, R.drawable.ic_expand_less_24dp
                    ))
                    .setColorScheme(when (Preferences(context).applicationTheme()) {
                        1 -> CustomTabsIntent.COLOR_SCHEME_LIGHT
                        2 -> CustomTabsIntent.COLOR_SCHEME_DARK
                        else -> CustomTabsIntent.COLOR_SCHEME_SYSTEM
                    })
                    .setInitialActivityHeightPx(
                        420.toPx,
                        CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
                    )
                    .setToolbarCornerRadiusDp(16)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .setShowTitle(false)
                    .setUrlBarHidingEnabled(true)
                    .build().launchUrl(context, it.toUri())
            } catch (_: ActivityNotFoundException) {

            }
        }
    }
}

fun URL.isValid() : Boolean {
    with (this.openConnection() as HttpURLConnection) {
        requestMethod = "HEAD"
        connect()
        return getResponseCode() == HttpURLConnection.HTTP_OK
    }
}