/*
 * ====================================================================
 * Copyright (c) 2023 AbandonedCart.  All rights reserved.
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

package com.hiddenramblings.tagmo.eightbit.os

import android.os.Build

object Version {
    @JvmStatic
    fun isOlder(versionCode: Int): Boolean {
        return Build.VERSION.SDK_INT < versionCode
    }

    @JvmStatic
    val isJellyBeanMR: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
    }

    @JvmStatic
    val isJellyBeanMR2: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
    }

    @JvmStatic
    val isKitKat: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    }

    @JvmStatic
    val isLollipop: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    @JvmStatic
    val isLollipopMR: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
    }

    @JvmStatic
    val isMarshmallow: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    @JvmStatic
    val isNougat: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    @JvmStatic
    val isOreo: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    @JvmStatic
    val isPie: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    @JvmStatic
    val isAndroid10: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    @JvmStatic
    val isRedVelvet: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    @JvmStatic
    val isSnowCone: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    @JvmStatic
    val isTiramisu: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}