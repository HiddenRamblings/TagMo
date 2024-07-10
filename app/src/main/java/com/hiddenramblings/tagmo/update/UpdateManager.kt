/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */
package com.hiddenramblings.tagmo.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.hiddenramblings.tagmo.BrowserActivity
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.widget.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlin.random.Random

class UpdateManager internal constructor(activity: BrowserActivity) {
    private var updateListener: UpdateListener? = null
    private var appUpdateManager: AppUpdateManager? = null
    private val browserActivity: BrowserActivity = activity
    private var isUpdateAvailable = false

    private var updateUrl: String? = null
    private var appUpdate: AppUpdateInfo? = null

    init {
        if (BuildConfig.GOOGLE_PLAY) {
            configurePlay()
        } else {
            if (Version.isLollipop) {
                with (activity.applicationContext.packageManager.packageInstaller) {
                    mySessions.forEach {
                        try {
                            abandonSession(it.sessionId)
                        } catch (ignored: Exception) { }
                    }
                }
            }
            activity.externalCacheDir?.listFiles {
                    _: File?, name: String -> name.lowercase().endsWith(".apk")
            }?.forEach { if (!it.isDirectory) it.delete() }
            configureGit()
        }
    }

    fun refreshUpdateStatus() {
        if (BuildConfig.GOOGLE_PLAY) configurePlay() else configureGit()
    }

    private fun configurePlay() {
        if (null == appUpdateManager)
            appUpdateManager = AppUpdateManagerFactory.create(browserActivity)
        val appUpdateInfoTask = appUpdateManager?.appUpdateInfo
        appUpdateInfoTask?.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            isUpdateAvailable = (appUpdateInfo.updateAvailability()
                    == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
            if (isUpdateAvailable) {
                appUpdate = appUpdateInfo
                updateListener?.onUpdateFound()
            }
        }
    }

    private fun configureGit() {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            JSONExecutor(
                browserActivity, TAGMO_GIT_API
            ).setResultListener(object : JSONExecutor.ResultListener {
                override fun onResults(result: String?) {
                    result?.let { parseUpdateJSON(it) }
                }
                override fun onException(e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toasty(browserActivity).Short(R.string.fail_update_git)
                    }
                }
            })
        }
    }

    fun installDownload(apkUrl: String?) {
        if (apkUrl.isNullOrEmpty()) return
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val apk = File(browserActivity.externalCacheDir, apkUrl.substringAfterLast(File.separator))
            try {
                URL(apkUrl).openStream().use { stream ->
                    FileOutputStream(apk).use { stream.copyTo(it) }
                }
            } catch (fnf: FileNotFoundException) {
                Debug.warn(fnf)
                Toasty(browserActivity).Short(R.string.fail_update_url)
                refreshUpdateStatus()
                return@launch
            }
            if (!apk.name.lowercase().endsWith(".apk")) apk.delete()
            try {
                browserActivity.run {
                    if (Version.isNougat) {
                        val apkUri = Storage.getFileUri(apk)
                        applicationContext.contentResolver.openInputStream(apkUri).use { apkStream ->
                            val session = with (applicationContext.packageManager.packageInstaller) {
                                val params = PackageInstaller.SessionParams(
                                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                                )
                                if (!BuildConfig.GOOGLE_PLAY && Version.isSnowCone) {
                                    params.setRequireUserAction(
                                        PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                                    )
                                }
                                openSession(createSession(params))
                            }
                            val document = DocumentFile.fromSingleUri(applicationContext, apkUri)
                                ?: throw IOException(browserActivity.getString(R.string.fail_invalid_size))
                            session.openWrite("NAME", 0, document.length()).use { sessionStream ->
                                apkStream?.copyTo(sessionStream)
                                session.fsync(sessionStream)
                            }
                            val pi = PendingIntent.getBroadcast(
                                applicationContext, Random.nextInt(),
                                Intent(applicationContext, UpdateReceiver::class.java),
                                if (Version.isSnowCone)
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                                else
                                    PendingIntent.FLAG_UPDATE_CURRENT
                            )
                            session.commit(pi.intentSender)
                        }
                    } else {
                        @Suppress("deprecation")
                        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                            setDataAndType(Storage.getFileUri(apk),
                                browserActivity.getString(R.string.mimetype_apk)
                            )
                            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationInfo.packageName)
                            if (Version.isNougat) {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }.also {
                            try {
                                startActivity(it)
                            } catch (anf: ActivityNotFoundException) {
                                try {
                                    startActivity(it.setAction(Intent.ACTION_VIEW))
                                } catch (ignored: ActivityNotFoundException) { }
                            }
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

    private fun requestDownload(apkUrl: String) {
        if (Version.isOreo) {
            if (browserActivity.packageManager.canRequestPackageInstalls()) {
                installDownload(apkUrl)
            } else {
                Preferences(browserActivity.applicationContext).downloadUrl(apkUrl)
                browserActivity.onRequestInstall.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse(String.format("package:%s", browserActivity.packageName))
                    }
                )
            }
        } else {
            installDownload(apkUrl)
        }
    }

    private fun parseUpdateJSON(result: String) {
        try {
            val jsonObject = JSONTokener(result).nextValue() as JSONObject
            val lastCommit = (jsonObject["name"] as String).substring(
                browserActivity.getString(R.string.tagmo).length + 1
            ).also { if (it.length > 7) it.substring(0, 7) }
            val assets = jsonObject["assets"] as JSONArray
            val asset = assets[0] as JSONObject
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable) {
                updateUrl = asset["browser_download_url"] as String
                updateListener?.onUpdateFound()
            }
        } catch (e: JSONException) {
            Debug.warn(e)
        }
    }

    private fun startPlayUpdateFlow(appUpdateInfo: AppUpdateInfo?) {
        try {
            appUpdateManager?.startUpdateFlowForResult( // Pass the intent that is returned by 'getAppUpdateInfo()'.
                appUpdateInfo!!,
                browserActivity,
                AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                Random.nextInt()
            )
        } catch (ex: SendIntentException) {
            Debug.warn(ex)
        }
    }

    fun hasPendingUpdate(): Boolean {
        return isUpdateAvailable
    }

    fun onUpdateRequested() {
        if (BuildConfig.GOOGLE_PLAY) {
            appUpdate?.let { startPlayUpdateFlow(it) }
        } else {
            updateUrl?.let { requestDownload(it) }
        }
    }

    fun setUpdateListener(listener: UpdateListener?) {
        updateListener = listener
    }

    interface UpdateListener {
        fun onUpdateFound()
    }

    companion object {
        private val TAGMO_GIT_API = if (BuildConfig.WEAR_OS)
            "https://api.github.com/repos/HiddenRamblings/TagMo/releases/tags/wearos"
        else
            "https://api.github.com/repos/HiddenRamblings/TagMo/releases/tags/master"
    }
}