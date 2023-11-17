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
import com.hiddenramblings.tagmo.BrowserActivity
import com.hiddenramblings.tagmo.GlideTagModule
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.security.SecurityHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class JSONExecutor(activity: Activity, server: String, path: String? = null) {

    var jsonListener: ResultListener? = null
    var dbListener: DatabaseListener? = null

    init {
        SecurityHandler(activity, object : SecurityHandler.ProviderInstallListener {
            override fun onProviderInstalled() {
                retrieveJSON(server, path)
            }

            override fun onProviderInstallException() {
                retrieveJSON(server, path)
            }

            override fun onProviderInstallFailed() {
                Preferences(activity.applicationContext).imageNetwork(
                    GlideTagModule.IMAGE_NETWORK_NEVER
                )
                if (activity is BrowserActivity)
                    CoroutineScope(Dispatchers.Main).launch { activity.settings?.notifyChanges() }
                jsonListener?.onResults(null)
                    ?: dbListener?.onResults(null, false)
            }
        })
    }

    @get:Throws(IOException::class)
    private val URL.asConnection get() : HttpsURLConnection {
        return (openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            defaultUseCaches = false
        }
    }

    private val HttpsURLConnection.withToken get() : HttpsURLConnection {
        setRequestProperty("Authorization", "Bearer $token")
        return this
    }

    fun retrieveJSON(server: String, path: String?) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val url = path?.let { "$server/$path" } ?: server
            try {
                val result = URL(url).readText()
                dbListener?.onResults(result, isRawJSON(url)) ?: jsonListener?.onResults(result)
                return@launch
            } catch (fnf: FileNotFoundException) {
                Debug.warn(fnf)
                return@launch
            } catch (cne: ConnectException) {
                Debug.warn(cne)
                return@launch
            } catch (ignored: UnknownHostException) { }
            try {
                var conn = URL(url).asConnection.withToken
                var statusCode = conn.responseCode
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    val address = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = URL(address).asConnection.withToken
                    statusCode = conn.responseCode
                } else if (statusCode != HttpsURLConnection.HTTP_OK && isRawJSON(conn)) {
                    conn.disconnect()
                    conn = URL("${AmiiboManager.AMIIBO_API}/amiibo/").asConnection.withToken
                    statusCode = conn.responseCode
                }
                if (statusCode != HttpsURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@launch
                }
                conn.inputStream.use { inStream ->
                    BufferedReader(
                        InputStreamReader(inStream, StandardCharsets.UTF_8)
                    ).use { streamReader ->
                        val responseStrBuilder = StringBuilder()
                        var inputStr: String?
                        while (null != streamReader.readLine().also { inputStr = it })
                            responseStrBuilder.append(inputStr)
                        dbListener?.onResults(
                            responseStrBuilder.toString(), isRawJSON(conn)
                        ) ?: jsonListener?.onResults(
                            responseStrBuilder.toString()
                        )
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                Debug.warn(e)
                dbListener?.onException(e) ?: jsonListener?.onException(e)
            }
        }
    }

    private fun isRawJSON(url: String): Boolean {
        return url == "${AmiiboManager.RENDER_RAW}/database/amiibo.json"
    }

    private fun isRawJSON(urlConnection: HttpsURLConnection): Boolean {
        return isRawJSON(urlConnection.url.toString())
    }

    interface ResultListener {
        fun onResults(result: String?)
        fun onException(e: Exception)
    }

    fun setResultListener(listener: ResultListener?) {
        jsonListener = listener
    }

    interface DatabaseListener {
        fun onResults(result: String?, isRawJSON: Boolean)
        fun onException(e: Exception)
    }

    fun setDatabaseListener(listener: DatabaseListener?) {
        dbListener = listener
    }

    companion object {
        private const val hex = "6769746875625f7061745f313141584d5934334930657947316841416d624a56515f50396e75626f39695a35464f38456e44454147347453513736353638316163426a58526154777579425a4f434d504e56595043536b6739447a6b34"
        private val token: String get() {
            val output = StringBuilder()
            var i = 0
            while (i < hex.length) {
                val str = hex.substring(i, i + 2)
                output.append(str.toInt(16).toChar())
                i += 2
            }
            return output.toString()
        }
    }
}