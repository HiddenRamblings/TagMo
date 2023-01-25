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
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor.ResultListener
import com.hiddenramblings.tagmo.eightbit.os.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL


class UpdateManager internal constructor(activity: BrowserActivity) {
    private var listener: CheckUpdateListener? = null
    private var listenerPlay: CheckPlayUpdateListener? = null
    private var appUpdateManager: AppUpdateManager? = null
    private val browserActivity: BrowserActivity = activity
    private var isUpdateAvailable = false

    private val scopeIO = CoroutineScope(Dispatchers.IO)

    init {
        if (!BuildConfig.WEAR_OS) {
            if (BuildConfig.GOOGLE_PLAY) configureManager(activity) else configureUpdates(activity)
        }
    }

    private fun configureManager(activity: BrowserActivity) {
        if (null == appUpdateManager) appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager!!.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            isUpdateAvailable = (appUpdateInfo.updateAvailability()
                    == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
            if (isUpdateAvailable) listenerPlay?.onPlayUpdateFound(appUpdateInfo)
        }
    }

    private fun configureUpdates(activity: BrowserActivity) {
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            val installer = activity.applicationContext.packageManager.packageInstaller
            installer.mySessions.forEach {
                try {
                    installer.abandonSession(it.sessionId)
                } catch (ignored: Exception) { }
            }
        }
        scopeIO.launch {
            val files = activity.externalCacheDir!!.listFiles {
                    _: File?, name: String -> name.lowercase().endsWith(".apk")
            }
            files?.forEach { if (!it.isDirectory) it.delete() }
            JSONExecutor(activity, TAGMO_GIT_API, "releases/tags/master")
                .setResultListener(object : ResultListener {
                override fun onResults(result: String?) {
                    result?.let { parseUpdateJSON(it) }
                }
            })
        }
    }

    fun installUpdateTask(apkUrl: String?) {
        if (null == apkUrl) return
        scopeIO.launch(Dispatchers.IO) {
            val apk = File(
                browserActivity.externalCacheDir,
                apkUrl.substring(apkUrl.lastIndexOf(File.separator) + 1)
            )
            try {
                URL(apkUrl).openStream().use { urlStream ->
                    DataInputStream(urlStream).use { dis ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        FileOutputStream(apk).use { fos ->
                            while (dis.read(buffer).also { length = it } > 0)
                                fos.write(buffer, 0, length)
                        }
                    }
                }
                if (!apk.name.lowercase().endsWith(".apk")) apk.delete()
                val applicationContext = browserActivity.applicationContext
                if (Debug.isNewer(Build.VERSION_CODES.N)) {
                    val installer = applicationContext.packageManager.packageInstaller
                    val resolver = applicationContext.contentResolver
                    val apkUri = Storage.getFileUri(apk)
                    resolver.openInputStream(apkUri).use { apkStream ->
                        val params = SessionParams(SessionParams.MODE_FULL_INSTALL)
                        val sessionId = installer.createSession(params)
                        val session = installer.openSession(sessionId)
                        val document = DocumentFile.fromSingleUri(applicationContext, apkUri)
                            ?: throw IOException(browserActivity.getString(R.string.fail_invalid_size))
                        session.openWrite("NAME", 0, document.length()).use { sessionStream ->
                            val buf = ByteArray(8192)
                            var size: Int
                            while (apkStream!!.read(buf).also { size = it } > 0) {
                                sessionStream.write(buf, 0, size)
                            }
                            session.fsync(sessionStream)
                        }
                        val pi = PendingIntent.getBroadcast(
                            applicationContext, 8675309,
                            Intent(applicationContext, UpdateReceiver::class.java),
                            if (Debug.isNewer(Build.VERSION_CODES.S))
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                            else PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        session.commit(pi.intentSender)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                    intent.setDataAndType(
                        Storage.getFileUri(apk),
                        browserActivity.getString(R.string.mimetype_apk)
                    )
                    intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    intent.putExtra(
                        Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                        browserActivity.applicationInfo.packageName
                    )
                    try {
                        browserActivity.startActivity(NFCIntent.getIntent(intent))
                    } catch (anf: ActivityNotFoundException) {
                        try {
                            browserActivity.startActivity(intent.setAction(Intent.ACTION_VIEW))
                        } catch (ignored: ActivityNotFoundException) { }
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
            if (browserActivity.packageManager.canRequestPackageInstalls()) {
                installUpdateTask(apkUrl)
            } else {
                Preferences(browserActivity.applicationContext).downloadUrl(apkUrl)
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse(String.format(
                        "package:%s", browserActivity.packageName
                ))
                browserActivity.onRequestInstall.launch(intent)
            }
        } else {
            installUpdateTask(apkUrl)
        }
    }

    private fun parseUpdateJSON(result: String) {
        val offset = browserActivity.getString(R.string.tagmo).length + 1
        try {
            val jsonObject = JSONTokener(result).nextValue() as JSONObject
            val lastCommit = (jsonObject["name"] as String).substring(offset)
            val assets = jsonObject["assets"] as JSONArray
            val asset = assets[0] as JSONObject
            val downloadUrl = asset["browser_download_url"] as String
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable) listener?.onUpdateFound(downloadUrl)
        } catch (e: JSONException) {
            Debug.warn(e)
        }
    }

    fun downloadPlayUpdate(appUpdateInfo: AppUpdateInfo?) {
        try {
            appUpdateManager?.startUpdateFlowForResult( // Pass the intent that is returned by 'getAppUpdateInfo()'.
                appUpdateInfo!!,  // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                AppUpdateType.IMMEDIATE,  // The current activity making the update request.
                browserActivity,  // Include a request code to later monitor this update request.
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