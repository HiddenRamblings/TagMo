/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
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
 *    "This product includes software developed by AbandonedCart"
 *
 * 4. The names "8-Bit Dream", "TwistedUmbrella" and "AbandonedCart"
 *    must not be used in any form to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called "8-Bit Dream",
 *    "TwistedUmbrella" or "AbandonedCart" nor may these labels appear
 *    in their names without prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart ``AS IS'' AND ANY
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

package com.hiddenramblings.tagmo.eightbit.content;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public class ScaledContext extends ContextWrapper {

    private static int fullscreen = -1;
    private static int layout;

    public ScaledContext(Context base) {
        super(base);
    }

    public static void setBaseline(int orientation) {
        layout = orientation;
    }

    private static int[] getDisplayParams(Context context) {
        WindowManager mWindowManager = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect metrics = mWindowManager.getCurrentWindowMetrics().getBounds();
            return new int[] { metrics.width(), metrics.height() };
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
            else
                mWindowManager.getDefaultDisplay().getMetrics(metrics);
            return new int[]{metrics.widthPixels, metrics.heightPixels};
        }
    }

    public static ScaledContext wrap(Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        int[] displayParams = getDisplayParams(context);

        int orientation = layout != -1 ? layout : resources.getConfiguration().orientation;

        if (fullscreen == -1) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                fullscreen = (float) displayParams[1] / displayParams[0] > 2.05 ? 1 : 0;
            }
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                fullscreen = (float) displayParams[0] / displayParams[1] > 2.05 ? 1 : 0;
            }
        }

        metrics.density = 2.3f;
        metrics.densityDpi = 320;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            metrics.heightPixels = fullscreen != 0 ? 2960 : 2560;
            metrics.widthPixels = 1440;
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            metrics.heightPixels = 1440;
            metrics.widthPixels = fullscreen != 0 ? 2960 : 2560;
        }
        metrics.scaledDensity = 2.3f;
        metrics.xdpi = 521.0f;
        metrics.ydpi = 521.0f;
        metrics.setTo(metrics);

        return new ScaledContext(context);
    }

    public static Context restore(Context context) {
        context.getResources().getDisplayMetrics().setToDefaults();
        return context;
    }
}