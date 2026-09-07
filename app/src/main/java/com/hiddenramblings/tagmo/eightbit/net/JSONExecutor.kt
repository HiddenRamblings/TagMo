/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart
 *
 * LICENSE.AbandonedCart
 * ====================================================================
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class JSONExecutor(activity: Activity, server: String, path: String? = null) {

    var jsonListener: ResultListener? = null
    var dbListener: DatabaseListener? = null
    private var hasPendingResult = false
    private var pendingResult: String? = null
    private var pendingException: Exception? = null

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
                onResults(null)
            }
        })
    }

    @get:Throws(IOException::class)
    private val URL.asConnection get() : HttpsURLConnection {
        return (openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            defaultUseCaches = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
    }

    fun retrieveJSON(server: String, path: String?) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val url = path?.let { "$server/$path" } ?: server
            var lastException: IOException? = null
            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    onResults(readJSON(url))
                    return@launch
                } catch (e: HttpStatusException) {
                    Debug.warn(e)
                    onException(e)
                    return@launch
                } catch (e: IOException) {
                    lastException = e
                    Debug.warn(e)
                    if (attempt < MAX_ATTEMPTS - 1) {
                        delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }
            }
            lastException?.let(::onException)
        }
    }

    private fun readJSON(url: String): String {
        var conn: HttpsURLConnection = URL(url).asConnection
        try {
            var statusCode = conn.responseCode
            if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM
                || statusCode == HttpsURLConnection.HTTP_MOVED_TEMP
            ) {
                val address = conn.getHeaderField("Location")
                    ?: throw IOException("Redirect without a Location header")
                conn.disconnect()
                conn = URL(address).asConnection
                statusCode = conn.responseCode
            } else if (statusCode != HttpsURLConnection.HTTP_OK && isRawJSON(conn)) {
                conn.disconnect()
                conn = URL("${AmiiboManager.AMIIBO_RAW}/database/amiibo.json").asConnection
                statusCode = conn.responseCode
            }
            if (statusCode != HttpsURLConnection.HTTP_OK) {
                throw HttpStatusException(statusCode)
            }
            return BufferedReader(
                InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)
            ).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private class HttpStatusException(statusCode: Int) : IOException(
        "Unexpected HTTP response: $statusCode"
    )

    private fun onResults(result: String?) {
        dbListener?.onResults(result) ?: jsonListener?.onResults(result) ?: run {
            pendingResult = result
            hasPendingResult = true
        }
    }

    private fun onException(e: Exception) {
        dbListener?.onException(e) ?: jsonListener?.onException(e) ?: run {
            pendingException = e
        }
    }

    private fun dispatchPending() {
        if (hasPendingResult) {
            hasPendingResult = false
            onResults(pendingResult)
            pendingResult = null
        }
        pendingException?.let {
            pendingException = null
            onException(it)
        }
    }

    private fun isRawJSON(url: String): Boolean {
        return url.startsWith(AmiiboManager.RENDER_RAW)
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
        dispatchPending()
    }

    interface DatabaseListener {
        fun onResults(result: String?)
        fun onException(e: Exception)
    }

    fun setDatabaseListener(listener: DatabaseListener?) {
        dbListener = listener
        dispatchPending()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_000L
    }
}
