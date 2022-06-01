package com.hiddenramblings.tagmo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public class UpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            context.startActivity(context.getPackageManager()
                    .getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {
            if (intent.getData().getSchemeSpecificPart().equals(context.getPackageName())) {
                context.startActivity(context.getPackageManager()
                        .getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            switch(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Intent activityIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (null != activityIntent)
                        context.startActivity(activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;
                case PackageInstaller.STATUS_SUCCESS:
                    // Installation was successful
                    break;
                default:
                    String error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    if (!error.contains("Session was abandoned"))
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }
}
