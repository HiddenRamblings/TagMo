/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
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
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor
import com.hiddenramblings.tagmo.eightbit.net.JSONExecutor.ResultListener
import com.hiddenramblings.tagmo.eightbit.os.Storage
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
    private var listenerGit: GitUpdateListener? = null
    private var listenerPlay: PlayUpdateListener? = null
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
        scopeIO.launch(Dispatchers.IO) {
            if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
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
                URL(apkUrl).openStream().use { stream ->
                    FileOutputStream(apk).use {
                        stream.copyTo(it)
                    }
                }
                if (!apk.name.lowercase().endsWith(".apk")) apk.delete()
                browserActivity.run {
                    if (Debug.isNewer(Build.VERSION_CODES.N)) {
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
                                if (Debug.isNewer(Build.VERSION_CODES.S))
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                                else PendingIntent.FLAG_UPDATE_CURRENT
                            )
                            session.commit(pi.intentSender)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
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
                        }
                        try {
                            startActivity(installIntent)
                        } catch (anf: ActivityNotFoundException) {
                            try {
                                startActivity(installIntent.setAction(Intent.ACTION_VIEW))
                            } catch (ignored: ActivityNotFoundException) { }
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
            if (browserActivity.packageManager.canRequestPackageInstalls()) {
                installUpdateTask(apkUrl)
            } else {
                Preferences(browserActivity.applicationContext).downloadUrl(apkUrl)
                browserActivity.onRequestInstall.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse(String.format("package:%s", browserActivity.packageName))
                    }
                )
            }
        } else {
            installUpdateTask(apkUrl)
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
            val downloadUrl = asset["browser_download_url"] as String
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable) listenerGit?.onUpdateFound(downloadUrl)
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
                Random.nextInt()
            )
        } catch (ex: SendIntentException) {
            Debug.warn(ex)
        }
    }

    fun hasPendingUpdate(): Boolean {
        return isUpdateAvailable
    }

    fun setUpdateListener(listener: GitUpdateListener?) {
        this.listenerGit = listener
    }

    interface GitUpdateListener {
        fun onUpdateFound(downloadUrl: String?)
    }

    fun setPlayUpdateListener(listenerPlay: PlayUpdateListener?) {
        this.listenerPlay = listenerPlay
    }

    interface PlayUpdateListener {
        fun onPlayUpdateFound(appUpdateInfo: AppUpdateInfo?)
    }

    companion object {
        private const val TAGMO_GIT_API = "https://api.github.com/repos/HiddenRamblings/TagMo/"
    }
}