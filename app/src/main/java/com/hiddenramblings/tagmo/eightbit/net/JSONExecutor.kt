/*
 * ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
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
package com.hiddenramblings.tagmo.eightbit.net

import android.app.Activity
import com.hiddenramblings.tagmo.GlideTagModule
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.browser.BrowserActivity
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.charset.CharsetCompat
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.security.SecurityHandler
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class JSONExecutor(activity: Activity, server: String, path: String) {
    var listener: ResultListener? = null

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
                    activity.runOnUiThread { activity.settings!!.notifyChanges() }
                }
                listener!!.onResults(null)
            }
        })
    }

    @Throws(IOException::class)
    private fun fixServerLocation(url: URL): HttpsURLConnection {
        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.requestMethod = "GET"
        urlConnection.useCaches = false
        urlConnection.defaultUseCaches = false
        return urlConnection
    }

    fun retrieveJSON(server: String, path: String) {
        Executors.newSingleThreadExecutor().execute {
            try {
                var conn = URL(server + path).openConnection() as HttpsURLConnection
                conn.requestMethod = "GET"
                conn.useCaches = false
                conn.defaultUseCaches = false
                var statusCode = conn.responseCode
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    val address = conn.getHeaderField("Location")
                    conn.disconnect()
                    conn = fixServerLocation(URL(address))
                    statusCode = conn.responseCode
                } else if (statusCode != HttpsURLConnection.HTTP_OK && isRenderAPI(conn)) {
                    conn.disconnect()
                    conn = fixServerLocation(URL(AmiiboManager.AMIIBO_API + "amiibo/"))
                    statusCode = conn.responseCode
                }
                if (statusCode != HttpsURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@execute
                }
                val inStream = conn.inputStream
                val streamReader = BufferedReader(
                    InputStreamReader(inStream, CharsetCompat.UTF_8)
                )
                val responseStrBuilder = StringBuilder()
                var inputStr: String?
                while (null != streamReader.readLine()
                        .also { inputStr = it }
                ) responseStrBuilder.append(inputStr)
                listener?.onResults(responseStrBuilder.toString())
                streamReader.close()
                inStream.close()
                conn.disconnect()
            } catch (e: IOException) {
                Debug.warn(e)
            }
        }
    }

    private fun isRenderAPI(urlConnection: HttpsURLConnection): Boolean {
        val render = AmiiboManager.RENDER_RAW + "database/amiibo.json"
        return render == urlConnection.url.toString()
    }

    interface ResultListener {
        fun onResults(result: String?)
    }

    fun setResultListener(listener: ResultListener?) {
        this.listener = listener
    }
}