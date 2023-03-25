/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
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

package com.hiddenramblings.tagmo.eightbit.net

import android.app.Activity
import com.hiddenramblings.tagmo.GlideTagModule
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.charset.CharsetCompat
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.security.SecurityHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

class JSONExecutor(activity: Activity, server: String, path: String? = null) {

    var listener: ResultListener? = null
    var listenerDb: DatabaseListener? = null

    init {
        SecurityHandler(activity, object : SecurityHandler.ProviderInstallListener {
            override fun onProviderInstalled() {
                retrieveJSON(server, path)
            }

            override fun onProviderInstallException() {
                retrieveJSON(server, path)
            }

            override fun onProviderInstallFailed() {
                if (activity is BrowserActivity) {
                    Preferences(activity.getApplicationContext())
                        .imageNetwork(GlideTagModule.IMAGE_NETWORK_NEVER)
                    CoroutineScope(Dispatchers.Main).launch { activity.settings?.notifyChanges() }
                }
                listener?.onResults(null)
                    ?: listenerDb?.onResults(null, false)
            }
        })
    }

    @Throws(IOException::class)
    private fun updateConnectionUrl(url: URL): HttpsURLConnection {
        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.requestMethod = "GET"
        urlConnection.useCaches = false
        urlConnection.defaultUseCaches = false
        return urlConnection
    }

    fun retrieveJSON(server: String, path: String?) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val url = path?.let { "$server/$path" } ?: server
            try {
                URL(url).readText().also {
                    listener?.onResults(it) ?: listenerDb?.onResults(it, false)
                    return@launch
                }
            } catch (_: UnknownHostException) { }
            try {
                var conn = URL(url).openConnection() as HttpsURLConnection
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.defaultUseCaches = false
                var statusCode = conn.responseCode
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    val address = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = updateConnectionUrl(URL(address))
                    statusCode = conn.responseCode
                } else if (statusCode != HttpsURLConnection.HTTP_OK && isRawJSON(conn)) {
                    conn.disconnect()
                    conn = updateConnectionUrl(URL("${AmiiboManager.AMIIBO_API}/amiibo/"))
                    statusCode = conn.responseCode
                }
                if (statusCode != HttpsURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@launch
                }
                conn.inputStream.use { inStream ->
                    BufferedReader(
                        InputStreamReader(inStream, CharsetCompat.UTF_8)
                    ).use { streamReader ->
                        val responseStrBuilder = StringBuilder()
                        var inputStr: String?
                        while (null != streamReader.readLine().also { inputStr = it })
                            responseStrBuilder.append(inputStr)
                        listener?.onResults(
                            responseStrBuilder.toString()
                        ) ?: listenerDb?.onResults(
                            responseStrBuilder.toString(), isRawJSON(conn)
                        )
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                Debug.warn(e)
                listenerDb?.onException(e)
            }
        }
    }

    private fun isRawJSON(urlConnection: HttpsURLConnection): Boolean {
        val render = "${AmiiboManager.RENDER_RAW}/database/amiibo.json"
        return render == urlConnection.url.toString()
    }

    interface ResultListener {
        fun onResults(result: String?)
    }

    fun setResultListener(listener: ResultListener?) {
        this.listener = listener
    }

    interface DatabaseListener {
        fun onResults(result: String?, isRawJSON: Boolean)
        fun onException(e: Exception)
    }

    fun setDatabaseListener(listener: DatabaseListener?) {
        this.listenerDb = listener
    }
}