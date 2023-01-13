package com.hiddenramblings.tagmo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Parcelable
import android.widget.Toast
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
import java.net.URISyntaxException

class UpdateReceiver : BroadcastReceiver() {
    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
        isNewer(Build.VERSION_CODES.TIRAMISU) ->
            getParcelableExtra(key, T::class.java)
        else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        intent.setPackage(context.packageName)
        intent.flags = 0
        intent.data = null
        if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            var mainIntent: Intent? = Intent(context, BrowserActivity::class.java)
            try {
                mainIntent = context.packageManager
                    .getLaunchIntentForPackage(BuildConfig.APPLICATION_ID)
            } catch (ignored: Exception) { }
            startLauncherActivity(context, mainIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (!BuildConfig.GOOGLE_PLAY && isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val activityIntent = intent.parcelable<Intent>(Intent.EXTRA_INTENT)
                    if (null != activityIntent) {
                        try {
                            val intentUri = activityIntent.toUri(0)
                            if (isNewer(Build.VERSION_CODES.LOLLIPOP_MR1)) {
                                startLauncherActivity(
                                    context, Intent.parseUri(
                                        intentUri, Intent.URI_ALLOW_UNSAFE
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                startLauncherActivity(
                                    context, Intent.parseUri(intentUri, 0)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } catch (ignored: URISyntaxException) {
                        }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {}
                else -> {
                    val error = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    if (!error!!.contains("Session was abandoned")) Toast.makeText(
                        context, error, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startLauncherActivity(context: Context, intent: Intent) {
        context.startActivity(intent)
    }
}