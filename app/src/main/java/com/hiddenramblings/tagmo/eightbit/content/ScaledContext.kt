/*
 * ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "SamSprung labels" shall
 * be used to refer to the labels "8-bit Dream", "TwistedUmbrella",
 * "SamSprung" and "AbandonedCart" and these labels should be considered
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
 *    "This product includes software developed for SamSprung by AbandonedCart"
 *
 * 4. The SamSprung labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the SamSprung
 *    labels nor may these labels appear in their names or product information
 *    without prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND SamSprung ``AS IS'' AND ANY
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
