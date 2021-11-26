package com.hiddenramblings.tagmo.github;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.hiddenramblings.tagmo.TagMo;

public class InstallReceiver extends BroadcastReceiver {

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceive(Context context, Intent intent) {
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
                Toast.makeText(
                        TagMo.getContext(),
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                        Toast.LENGTH_LONG
                ).show();
                break;
        }
    }
}

