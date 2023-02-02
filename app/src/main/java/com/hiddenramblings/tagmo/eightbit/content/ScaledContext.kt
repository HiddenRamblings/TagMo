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

class ScaledContext(base: Context) : ContextWrapper(base) {

    fun screen(density: Float): ScaledContext {
        val resources = resources
        val metrics = resources.displayMetrics
        val orientation = resources.configuration.orientation
        metrics.density = density // 2
        metrics.densityDpi = 360 // 360
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            metrics.heightPixels = 2640 // 2640
            metrics.widthPixels = 1080 // 1080
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            metrics.heightPixels = 1080 // 1080
            metrics.widthPixels = 2640 // 2640
        }
        metrics.scaledDensity = density // 2
        metrics.xdpi = 425f // 425
        metrics.ydpi = 425f // 425
        metrics.setTo(metrics)
        return ScaledContext(this)
    }

    fun watch(density: Float): ScaledContext {
        val resources = resources
        val metrics = resources.displayMetrics
        metrics.density = density // 1f
        metrics.densityDpi = 160 // 340
        metrics.heightPixels = 450 // 320
        metrics.widthPixels = 450 // 320
        metrics.scaledDensity = density // 1f
        metrics.xdpi = 321f // 302
        metrics.ydpi = 321f // 302
        metrics.setTo(metrics)
        return ScaledContext(this)
    }

    fun restore(): Context {
        resources.displayMetrics.setToDefaults()
        return this

    }
}
