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
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.parcelable
import com.hiddenramblings.tagmo.widget.Toasty
import java.net.URISyntaxException
import com.hiddenramblings.tagmo.R

class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        intent.setPackage(context.packageName)
        intent.flags = 0
        intent.data = null
        if (!BuildConfig.GOOGLE_PLAY && Version.isLollipop) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    intent.parcelable<Intent>(Intent.EXTRA_INTENT)?.let {
                        try {
                            startLauncherActivity(context, Intent.parseUri(
                                it.toUri(0),
                                if (Version.isLollipopMR) Intent.URI_ALLOW_UNSAFE else 0
                            ))
                        } catch (_: URISyntaxException) { }
                    } ?: Toasty(context).Long(R.string.install_rejected)
                }
                PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                    Toasty(context).Long(R.string.install_blocked)
                }
                PackageInstaller.STATUS_FAILURE_STORAGE -> {
                    Toasty(context).Long(R.string.install_storage)
                }
                PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                    Toasty(context).Long(R.string.install_conflict)
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    Toasty(context).Long(R.string.install_aborted)
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