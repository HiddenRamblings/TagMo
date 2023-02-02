/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
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
import android.os.Build
import android.os.Parcelable
import android.widget.Toast
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.net.URISyntaxException

class UpdateReceiver : BroadcastReceiver() {
    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        Debug.isNewer(Build.VERSION_CODES.TIRAMISU) ->
            getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }

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
        } else if (!BuildConfig.GOOGLE_PLAY && Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val activityIntent = intent.parcelable<Intent>(Intent.EXTRA_INTENT)
                    if (null != activityIntent) {
                        try {
                            val intentUri = activityIntent.toUri(0)
                            if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP_MR1)) {
                                startLauncherActivity(
                                    context, Intent.parseUri(intentUri, Intent.URI_ALLOW_UNSAFE)
                                )
                            } else {
                                startLauncherActivity(
                                    context, Intent.parseUri(intentUri, 0)
                                )
                            }
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