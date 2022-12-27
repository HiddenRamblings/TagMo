package com.hiddenramblings.tagmo.browser

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageInstaller.SessionParams
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.UpdateReceiver
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor.ResultListener
import com.hiddenramblings.tagmo.eightbit.os.Storage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.SoftReference
import java.net.URL
import java.util.concurrent.Executors


class CheckUpdatesTask internal constructor(activity: BrowserActivity) {
    private var listener: CheckUpdateListener? = null
    private var listenerPlay: CheckPlayUpdateListener? = null
    private var appUpdateManager: AppUpdateManager? = null
    private val activity: SoftReference<BrowserActivity>
    private var isUpdateAvailable = false

    init {
        this.activity = SoftReference(activity)
        if (!BuildConfig.WEAR_OS) {
            if (BuildConfig.GOOGLE_PLAY) {
                if (null == appUpdateManager) appUpdateManager =
                    AppUpdateManagerFactory.create(activity)
                val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo
                appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
                    isUpdateAvailable = (appUpdateInfo.updateAvailability()
                            == UpdateAvailability.UPDATE_AVAILABLE
                            && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
                    if (isUpdateAvailable && null != listenerPlay)
                        listenerPlay!!.onPlayUpdateFound(appUpdateInfo)
                }
            } else {
                configureUpdates(activity)
            }
        }
    }

    private fun configureUpdates(activity: BrowserActivity) {
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            val installer = activity.applicationContext
                .packageManager.packageInstaller
            installer.mySessions.forEach {
                try {
                    installer.abandonSession(it.sessionId)
                } catch (ignored: Exception) { }
            }
        }
        Executors.newSingleThreadExecutor().execute {
            val files = activity.externalCacheDir!!.listFiles {
                    _: File?, name: String -> name.lowercase().endsWith(".apk")
            }
            files?.forEach { if (!it.isDirectory) it.delete() }
        }
        Executors.newSingleThreadExecutor().execute {
            JSONExecutor(
                activity, TAGMO_GIT_API, "releases/tags/master"
            ).setResultListener(object : ResultListener {
                override fun onResults(result: String?) {
                    result?.let { parseUpdateJSON(it) }
                }
            })
        }
    }

    fun installUpdateTask(apkUrl: String?) {
        if (null == apkUrl) return
        Executors.newSingleThreadExecutor().execute {
            val apk = File(
                activity.get()!!.externalCacheDir, apkUrl.substring(
                    apkUrl.lastIndexOf(File.separator) + 1
                )
            )
            try {
                val dis = DataInputStream(URL(apkUrl).openStream())
                val buffer = ByteArray(1024)
                var length: Int
                val fos = FileOutputStream(apk)
                while (dis.read(buffer).also { length = it } > 0) {
                    fos.write(buffer, 0, length)
                }
                fos.close()
                if (!apk.name.lowercase().endsWith(".apk")) apk.delete()
                val applicationContext = activity.get()!!.applicationContext
                if (Debug.isNewer(Build.VERSION_CODES.N)) {
                    val installer = applicationContext
                        .packageManager.packageInstaller
                    val resolver = applicationContext.contentResolver
                    val apkUri = Storage.getFileUri(apk)
                    val apkStream = resolver.openInputStream(apkUri)
                    val params = SessionParams(
                        SessionParams.MODE_FULL_INSTALL
                    )
                    val sessionId = installer.createSession(params)
                    val session = installer.openSession(sessionId)
                    val document = DocumentFile.fromSingleUri(applicationContext, apkUri)
                        ?: throw IOException(activity.get()!!.getString(R.string.fail_invalid_size))
                    val sessionStream = session.openWrite(
                        "NAME", 0, document.length()
                    )
                    val buf = ByteArray(8192)
                    var size: Int
                    while (apkStream!!.read(buf).also { size = it } > 0) {
                        sessionStream.write(buf, 0, size)
                    }
                    session.fsync(sessionStream)
                    apkStream.close()
                    sessionStream.close()
                    val pi = PendingIntent.getBroadcast(
                        applicationContext, 8675309,
                        Intent(applicationContext, UpdateReceiver::class.java),
                        if (Debug.isNewer(Build.VERSION_CODES.S)) PendingIntent.FLAG_UPDATE_CURRENT
                                or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    session.commit(pi.intentSender)
                } else {
                    @Suppress("DEPRECATION")
                    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                    intent.setDataAndType(
                        Storage.getFileUri(apk),
                        activity.get()!!.getString(R.string.mimetype_apk)
                    )
                    intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    intent.putExtra(
                        Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                        activity.get()!!.applicationInfo.packageName
                    )
                    try {
                        activity.get()!!.startActivity(NFCIntent.getIntent(intent))
                    } catch (anf: ActivityNotFoundException) {
                        try {
                            activity.get()!!.startActivity(intent.setAction(Intent.ACTION_VIEW))
                        } catch (ignored: ActivityNotFoundException) {
                        }
                    }
                }
            } catch (ex: SecurityException) {
                Debug.warn(ex)
            } catch (ex: IOException) {
                Debug.warn(ex)
            }
        }
    }

    fun installUpdateCompat(apkUrl: String?) {
        if (null == apkUrl) return
        if (Debug.isNewer(Build.VERSION_CODES.O)) {
            if (activity.get()!!.packageManager.canRequestPackageInstalls()) {
                installUpdateTask(apkUrl)
            } else {
                Preferences(activity.get()!!.applicationContext).downloadUrl(apkUrl)
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse(
                    String.format(
                        "package:%s", activity.get()!!
                            .packageName
                    )
                )
                activity.get()!!.onRequestInstall.launch(intent)
            }
        } else {
            installUpdateTask(apkUrl)
        }
    }

    private fun parseUpdateJSON(result: String) {
        val offset = activity.get()!!.getString(R.string.tagmo).length + 1
        try {
            val jsonObject = JSONTokener(result).nextValue() as JSONObject
            val lastCommit = (jsonObject["name"] as String).substring(offset)
            val assets = jsonObject["assets"] as JSONArray
            val asset = assets[0] as JSONObject
            val downloadUrl = asset["browser_download_url"] as String
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable && null != listener) listener!!.onUpdateFound(downloadUrl)
        } catch (e: JSONException) {
            Debug.warn(e)
        }
    }

    fun downloadPlayUpdate(appUpdateInfo: AppUpdateInfo?) {
        try {
            appUpdateManager!!.startUpdateFlowForResult( // Pass the intent that is returned by 'getAppUpdateInfo()'.
                appUpdateInfo!!,  // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                AppUpdateType.IMMEDIATE,  // The current activity making the update request.
                activity.get()!!,  // Include a request code to later monitor this update request.
                8675309
            )
        } catch (ex: SendIntentException) {
            Debug.warn(ex)
        }
    }

    fun hasPendingUpdate(): Boolean {
        return isUpdateAvailable
    }

    fun setUpdateListener(listener: CheckUpdateListener?) {
        this.listener = listener
    }

    interface CheckUpdateListener {
        fun onUpdateFound(downloadUrl: String?)
    }

    fun setPlayUpdateListener(listenerPlay: CheckPlayUpdateListener?) {
        this.listenerPlay = listenerPlay
    }

    interface CheckPlayUpdateListener {
        fun onPlayUpdateFound(appUpdateInfo: AppUpdateInfo?)
    }

    companion object {
        private const val TAGMO_GIT_API = "https://api.github.com/repos/HiddenRamblings/TagMo/"
    }
}