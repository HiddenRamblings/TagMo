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
    fun isLowerThan(versionCode: Int): Boolean {
        return Build.VERSION.SDK_INT < versionCode
    }

    /**
     * Android 5.0, API 21
     */
    @JvmStatic
    val isLollipop: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    /**
     * Android 5.1, API 22
     */
    @JvmStatic
    val isLollipopMR: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
    }

    /**
     * Android 6.0, API 23
     */
    @JvmStatic
    val isMarshmallow: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    /**
     * Android 7.0, API 24
     */
    @JvmStatic
    val isNougat: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }

    /**
     * Android 8.0, API 26
     */
    @JvmStatic
    val isOreo: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }

    /**
     * Android 9, API 28
     */
    @JvmStatic
    val isPie: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    /**
     * Android 10, API 29
     */
    @JvmStatic
    val isQuinceTart: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    /**
     * Android 11, API 30
     */
    @JvmStatic
    val isRedVelvet: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Android 12, API 31
     */
    @JvmStatic
    val isSnowCone: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * Android 13, API 33
     */
    @JvmStatic
    val isTiramisu: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * Android 14, API 34
     */
    @JvmStatic
    val isUpsideDownCake: Boolean get() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}