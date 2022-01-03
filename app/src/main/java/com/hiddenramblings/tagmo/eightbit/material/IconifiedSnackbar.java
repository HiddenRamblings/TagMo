/* ====================================================================
 * Copyright (c) 2012-2021 AbandonedCart.  All rights reserved.
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

package com.hiddenramblings.tagmo.eightbit.material;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.transition.TransitionManager;

import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.R;

import java.lang.ref.WeakReference;

public class IconifiedSnackbar {

    private final WeakReference<Activity> mActivity;
    private final ViewGroup layout;

    public IconifiedSnackbar(Activity activity, ViewGroup layout) {
        mActivity = new WeakReference<>(activity);
        this.layout = layout;
    }

    public IconifiedSnackbar(Activity activity) {
        this(activity, null);
    }

    public Snackbar buildSnackbar(String msg, int length, View anchor) {
        Snackbar snackbar = Snackbar.make(mActivity.get()
                .findViewById(R.id.coordinator), msg, length);
        View snackbarLayout = snackbar.getView();
        TextView textView = snackbarLayout.findViewById(
                com.google.android.material.R.id.snackbar_text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_stat_notice, 0, 0, 0);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(
                    R.drawable.ic_stat_notice, 0, 0, 0);
        }
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setCompoundDrawablePadding(textView.getResources()
                .getDimensionPixelOffset(R.dimen.snackbar_icon_padding));
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                snackbarLayout.getLayoutParams();
        params.width = CoordinatorLayout.LayoutParams.MATCH_PARENT;
        snackbar.getView().setLayoutParams(params);
        snackbar.setAnchorView(anchor);
        return snackbar;
    }

    public Snackbar buildSnackbar(int msgRes, int length, View anchor) {
        return buildSnackbar(mActivity.get().getString(msgRes), length, anchor);
    }

    public Snackbar buildTickerBar(String msg) {
        Snackbar snackbar = buildSnackbar(msg, Snackbar.LENGTH_INDEFINITE, null)
                .addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (null == layout) {
                    super.onDismissed(snackbar, event);
                    return;
                }
                TransitionManager.beginDelayedTransition(layout);
                layout.setPadding(0, 0, 0, 0);
                super.onDismissed(snackbar, event);
            }

            @Override
            public void onShown(Snackbar snackbar) {
                if (null == layout) {
                    super.onShown(snackbar);
                    return;
                }
                layout.setPadding(0, snackbar.getView().getMeasuredHeight(),
                        0, 0);
                super.onShown(snackbar);
            }
        });
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                snackbar.getView().getLayoutParams();
        params.gravity = Gravity.TOP;
        snackbar.getView().setLayoutParams(params);
        return snackbar;
    }
}
