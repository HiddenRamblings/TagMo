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
import android.os.Build
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlin.random.Random

class UpdateManager internal constructor(activity: BrowserActivity) {
    private var listener: UpdateListener? = null
    private var appUpdateManager: AppUpdateManager? = null
    private val browserActivity: BrowserActivity = activity
    private var isUpdateAvailable = false

    private var updateUrl: String? = null
    private var appUpdate: AppUpdateInfo? = null

    init {
        if (BuildConfig.GOOGLE_PLAY) {
            configurePlay(activity)
        } else {
            if (Version.isLollipop) {
                activity.applicationContext.packageManager.packageInstaller.run {
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

    fun refreshUpdateStatus(activity: BrowserActivity) {
        if (BuildConfig.GOOGLE_PLAY) configurePlay(activity) else configureGit()
    }

    private fun configurePlay(activity: BrowserActivity) {
        if (null == appUpdateManager) appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager?.appUpdateInfo
        appUpdateInfoTask?.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            isUpdateAvailable = (appUpdateInfo.updateAvailability()
                    == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
            if (isUpdateAvailable) {
                appUpdate = appUpdateInfo
                listener?.onPlayUpdateFound()
            }
        }
    }

    private fun configureGit() {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            URL(TAGMO_GIT_API).readText().also { parseUpdateJSON(it) }
        }
    }

    fun installDownload(apkUrl: String?) {
        if (apkUrl.isNullOrEmpty()) return
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val apk = File(
                browserActivity.externalCacheDir,
                apkUrl.substring(apkUrl.lastIndexOf(File.separator) + 1)
            )
            URL(apkUrl).openStream().use { stream ->
                FileOutputStream(apk).use {
                    stream.copyTo(it)
                }
            }
            try {
                if (!apk.name.lowercase().endsWith(".apk")) apk.delete()
                browserActivity.run {
                    if (Version.isNougat) {
                        val apkUri = Storage.getFileUri(apk)
                        applicationContext.contentResolver.openInputStream(apkUri).use { apkStream ->
                            val session = applicationContext.packageManager.packageInstaller.run {
                                val params = PackageInstaller.SessionParams(
                                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                                )
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
                                else PendingIntent.FLAG_UPDATE_CURRENT
                            )
                            session.commit(pi.intentSender)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                            setDataAndType(Storage.getFileUri(apk),
                                browserActivity.getString(R.string.mimetype_apk)
                            )
                            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                            putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationInfo.packageName)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            )
            val assets = jsonObject["assets"] as JSONArray
            val asset = assets[0] as JSONObject
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable) {
                updateUrl = asset["browser_download_url"] as String
                listener?.onUpdateFound()
            }
        } catch (e: JSONException) {
            Debug.warn(e)
        }
    }

    private fun startPlayUpdateFlow(appUpdateInfo: AppUpdateInfo?) {
        try {
            appUpdateManager?.startUpdateFlowForResult( // Pass the intent that is returned by 'getAppUpdateInfo()'.
                appUpdateInfo!!,  // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                AppUpdateType.IMMEDIATE,  // The current activity making the update request.
                browserActivity,  // Include a request code to later monitor this update request.
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
        this.listener = listener
    }

    interface UpdateListener {
        fun onUpdateFound()
        fun onPlayUpdateFound()
    }

    companion object {
        private const val TAGMO_GIT_API = "https://api.github.com/repos/HiddenRamblings/TagMo/releases/tags/master"
    }
}