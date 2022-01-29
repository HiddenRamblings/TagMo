package com.hiddenramblings.tagmo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            context.startActivity(context.getPackageManager()
                    .getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
