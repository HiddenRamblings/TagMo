/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-Bit Dream", "TwistedUmbrella",
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

package com.hiddenramblings.tagmo.eightbit.material;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
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

    public Snackbar buildSnackbar(ViewGroup parent, String msg, int drawable, int length, View anchor) {
        if (null == parent) parent = mActivity.get().findViewById(R.id.coordinator);
        Snackbar snackbar = Snackbar.make(parent, msg, length);
        View snackbarLayout = snackbar.getView();
        TextView textView = snackbarLayout.findViewById(
                com.google.android.material.R.id.snackbar_text);
        switch (mActivity.get().getResources().getConfiguration()
                .uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
                snackbar.setBackgroundTint(ContextCompat.getColor(
                        mActivity.get(), R.color.snackbar_dark
                ));
                textView.setTextColor(ContextCompat.getColor(
                        mActivity.get(), R.color.primary_text_dark
                ));
                break;
            case Configuration.UI_MODE_NIGHT_NO:
                snackbar.setBackgroundTint(ContextCompat.getColor(
                        mActivity.get(), android.R.color.darker_gray
                ));
                textView.setTextColor(ContextCompat.getColor(
                        mActivity.get(), R.color.primary_text_light
                ));
                break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    drawable, 0, 0, 0);
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(
                    drawable, 0, 0, 0);
        }
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setCompoundDrawablePadding(textView.getResources()
                .getDimensionPixelOffset(R.dimen.snackbar_icon_padding));
        textView.setMaxLines(3);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                snackbarLayout.getLayoutParams();
        params.width = CoordinatorLayout.LayoutParams.MATCH_PARENT;
        snackbar.getView().setLayoutParams(params);
        snackbar.setAnchorView(anchor);
        return snackbar;
    }

    public Snackbar buildSnackbar(int msgRes, int drawable, int length, View anchor) {
        return buildSnackbar(null, mActivity.get()
                .getString(msgRes), drawable, length, anchor);
    }

    public Snackbar buildSnackbar(String msg, int drawable, int length) {
        return buildSnackbar(null, msg, drawable, length, null);
    }

    public Snackbar buildSnackbar(int msgRes, int drawable, int length) {
        return buildSnackbar(null, mActivity.get()
                .getString(msgRes), drawable, length, null);
    }

    public Snackbar buildSnackbar(ViewGroup parent, String msg, int length) {
        return buildSnackbar(parent, msg, R.drawable.ic_stat_notice_24dp, length, null);
    }

    public Snackbar buildSnackbar(ViewGroup parent, int msgRes, int length) {
        return buildSnackbar(parent, mActivity.get().getString(msgRes),
                R.drawable.ic_stat_notice_24dp, length, null);
    }

    public Snackbar buildSnackbar(String msg, int length) {
        return buildSnackbar(null, msg, R.drawable.ic_stat_notice_24dp, length, null);
    }

    public Snackbar buildSnackbar(int msgRes, int length) {
        return buildSnackbar(null, mActivity.get().getString(msgRes),
                R.drawable.ic_stat_notice_24dp, length, null);
    }

    public Snackbar buildTickerBar(String msg, int drawable, int length) {
        Snackbar snackbar = buildSnackbar(null, msg, drawable, length, null)
                .addCallback(new Snackbar.Callback() {
            final int top = null != layout ? layout.getPaddingTop() : 0;
            final int bottom = null != layout ? layout.getPaddingBottom() : 0;

            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (null == layout) {
                    super.onDismissed(snackbar, event);
                    return;
                }
                TransitionManager.beginDelayedTransition(layout);
                layout.setPadding(0, top, 0, bottom);
                super.onDismissed(snackbar, event);
            }

            @Override
            public void onShown(Snackbar snackbar) {
                if (null == layout) {
                    super.onShown(snackbar);
                    return;
                }
                int adjusted = top + snackbar.getView().getMeasuredHeight();
                layout.setPadding(0, adjusted, 0, bottom);
                super.onShown(snackbar);
            }
        });
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams)
                snackbar.getView().getLayoutParams();
        params.gravity = Gravity.TOP;
        snackbar.getView().setLayoutParams(params);
        return snackbar;
    }

    public Snackbar buildTickerBar(int msgRes) {
        return buildTickerBar(mActivity.get().getString(msgRes),
                R.drawable.ic_stat_notice_24dp, Snackbar.LENGTH_INDEFINITE);
    }
}
