/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.hiddenramblings.tagmo.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.parcelable
import java.net.URISyntaxException

class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        intent.setPackage(context.packageName)
        intent.flags = 0
        intent.data = null
        if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            var mainIntent: Intent? = Intent(context, BrowserActivity::class.java)
            try {
                mainIntent = context.packageManager
                    .getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
            } catch (ignored: Exception) { }
            startLauncherActivity(context, mainIntent)
        } else if (!BuildConfig.GOOGLE_PLAY && Version.isLollipop) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    intent.parcelable<Intent>(Intent.EXTRA_INTENT)?.let {
                        try {
                            startLauncherActivity(context, Intent.parseUri(
                                it.toUri(0),
                                if (Version.isLollipopMR) Intent.URI_ALLOW_UNSAFE else 0
                            ))
                        } catch (ignored: URISyntaxException) { }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {}
                else -> {
                    val error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    if (error?.contains("Session was abandoned") != true)
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startLauncherActivity(context: Context, intent: Intent?) {
        context.startActivity(intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}