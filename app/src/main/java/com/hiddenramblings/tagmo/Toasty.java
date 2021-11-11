package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class Toasty {

    private final WeakReference<Activity> mActivity;

    public Toasty(Activity activity) {
        mActivity = new WeakReference<>(activity);
    }

    private void show(int msgRes, int length) {
        mActivity.get().runOnUiThread(() -> Toast.makeText(mActivity.get(), msgRes, length).show());
    }

    @SuppressWarnings("unused")
    public void Long(int msgRes) {
        show(msgRes, Toast.LENGTH_LONG);
    }

    public void Short(int msgRes) {
        show(msgRes, Toast.LENGTH_SHORT);
    }

    private void show(String msg, int length) {
        mActivity.get().runOnUiThread(() -> Toast.makeText(mActivity.get(), msg, length).show());
    }

    public void Long(String msg) {
        show(msg, Toast.LENGTH_LONG);
    }

    public void Short(String msg) {
        show(msg, Toast.LENGTH_SHORT);
    }
}
