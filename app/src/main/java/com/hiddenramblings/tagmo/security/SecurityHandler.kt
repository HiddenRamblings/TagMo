/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * See https://github.com/HiddenRamblings/TagMo/LICENSE#L680
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.hiddenramblings.tagmo.security

import android.app.Activity
import android.content.Intent
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import com.hiddenramblings.tagmo.eightbit.os.Version

class SecurityHandler(activity: Activity?, listener: ProviderInstallListener?) {
    init {
        if (Version.isMarshmallow) {
            listener?.onProviderInstalled()
        } else {
            activity?.let {
                ProviderInstaller.installIfNeededAsync(it,
                    object : ProviderInstaller.ProviderInstallListener {
                    override fun onProviderInstalled() {
                        listener?.onProviderInstalled()
                    }

                    override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: Intent?) {
                        val availability = GoogleApiAvailability.getInstance()
                        if (availability.isUserResolvableError(errorCode)) {
                            try {
                                availability.showErrorDialogFragment(
                                    it, errorCode, 7000
                                ) { listener?.onProviderInstallFailed() }
                            } catch (ex: IllegalArgumentException) {
                                listener?.onProviderInstallException()
                            }
                        } else {
                            listener?.onProviderInstallFailed()
                        }
                    }
                })
            }
        }
    }

    interface ProviderInstallListener {
        fun onProviderInstalled()
        fun onProviderInstallException()
        fun onProviderInstallFailed()
    }
}