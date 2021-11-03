package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;

public class IconifiedSnackbar {

    private final WeakReference<Activity> mActivity;
    private final Resources resources;

    public IconifiedSnackbar(Activity activity) {
        mActivity = new WeakReference<>(activity);
        resources = activity.getResources();
    }

    public Snackbar buildSnackbar(String msg, int length) {
        Snackbar snackbar = Snackbar.make(
                mActivity.get().findViewById(R.id.coordinator), msg, length);
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
        return snackbar;
    }
}
