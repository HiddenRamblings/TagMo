package com.hiddenramblings.tagmo;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import com.hiddenramblings.tagmo.browser.BrowserActivity;

import java.net.URISyntaxException;

public class UpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        intent.setPackage(context.getPackageName());
        intent.setFlags(0);
        intent.setData(null);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Intent mainIntent = new Intent(context, BrowserActivity.class);
            try {
                mainIntent = context.getPackageManager()
                        .getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
            } catch (Exception ignored) { }
            startLauncherActivity(context, mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (TagMo.isGooglePlay()) return;
            switch(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    Intent activityIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (null != activityIntent) {
                        try {
                            String intentUri = activityIntent.toUri(0);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                startLauncherActivity(context, Intent.parseUri(
                                        intentUri, Intent.URI_ALLOW_UNSAFE
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } else {
                                startLauncherActivity(context, Intent.parseUri(intentUri, 0)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            }
                        } catch (URISyntaxException ignored) { }
                    }
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

    private void startLauncherActivity(Context context, Intent intent) {
        context.startActivity(intent);
    }
}
