package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.transition.TransitionManager;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;

public class IconifiedSnackbar {

    private final WeakReference<Activity> mActivity;
    private final Resources resources;
    private final ViewGroup layout;

    public IconifiedSnackbar(Activity activity, ViewGroup layout) {
        mActivity = new WeakReference<>(activity);
        resources = activity.getResources();
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
        textView.setCompoundDrawablePadding(resources.getDimensionPixelOffset(
                R.dimen.snackbar_icon_padding));
        snackbar.setAnchorView(anchor);
        return snackbar;
    }

    public Snackbar buildTickerBar(String msg, int length) {
        Snackbar snackbar = buildSnackbar(msg, length, (View) null)
                .addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (layout == null) {
                    super.onDismissed(snackbar, event);
                    return;
                }
                TransitionManager.beginDelayedTransition(layout);
                layout.setPadding(0, 0, 0, 0);
                super.onDismissed(snackbar, event);
            }

            @Override
            public void onShown(Snackbar snackbar) {
                if (layout == null) {
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
