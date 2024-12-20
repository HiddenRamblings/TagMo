/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
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
package com.hiddenramblings.tagmo.eightbit.content

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hiddenramblings.tagmo.eightbit.os.Version

class ScaledContext(base: Context) : ContextWrapper(base) {

    fun getDisplayParams(): IntArray {
        with (getSystemService(WINDOW_SERVICE) as WindowManager) {
            return if (Version.isRedVelvet) {
                val metrics = maximumWindowMetrics.bounds
                intArrayOf(metrics.width(), metrics.height())
            } else {
                val displayMetrics = DisplayMetrics()
                @Suppress("deprecation")
                defaultDisplay.getMetrics(displayMetrics)
                intArrayOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            }
        }
    }

    // Z Flip 3
    /*
     * metrics.density = 2f
     * metrics.densityDpi = 360
     * if (orientation == Configuration.ORIENTATION_PORTRAIT) {
     *     metrics.heightPixels = 2640
     *     metrics.widthPixels = 1080
     * }
     * if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
     *     metrics.heightPixels = 1080
     *     metrics.widthPixels = 2640
     * }
     * metrics.scaledDensity = 2f
     * metrics.xdpi = 425f
     * metrics.ydpi = 425f
     */

    // S22 Ultra
    /*
     * if (orientation == Configuration.ORIENTATION_PORTRAIT) {
     *     metrics.heightPixels = 3088
     *     metrics.widthPixels = 1440
     * }
     * if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
     *     metrics.heightPixels = 1440
     *     metrics.widthPixels = 3088
     * }
     *
     * metrics.xdpi = 500f
     * metrics.ydpi = 500f
     */

    // Galaxy Watch 5
    /*
     * metrics.density = 1f
     * metrics.densityDpi = 340
     * metrics.heightPixels = 320
     * metrics.widthPixels = 320
     * metrics.scaledDensity = 1f
     * metrics.xdpi = 302f
     * metrics.ydpi = 302f
     */

    fun watch(density: Float): ScaledContext {
        val metrics = resources.displayMetrics
        metrics.density = density
        metrics.densityDpi = DisplayMetrics.DENSITY_MEDIUM
        metrics.heightPixels = 450
        metrics.widthPixels = 450
        metrics.scaledDensity = density
        metrics.xdpi = 321f
        metrics.ydpi = 321f
        metrics.setTo(metrics)
        return ScaledContext(this)
    }

    fun restore(): Context {
        resources.displayMetrics.setToDefaults()
        return this

    }
}
