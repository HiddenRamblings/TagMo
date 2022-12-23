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
package com.hiddenramblings.tagmo.eightbit.io

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.Log
import com.heinrichreimersoftware.androidissuereporter.IssueReporterLauncher
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.browser.Preferences
import java.io.*
import java.util.*

@Suppress("UNUSED")
object Debug {
    private val context: Context
        get() = TagMo.appContext
    private val mPrefs = Preferences(context)

    @JvmStatic
    fun isNewer(versionCode: Int): Boolean {
        return Build.VERSION.SDK_INT >= versionCode
    }

    @JvmStatic
    fun isOlder(versionCode: Int): Boolean {
        return Build.VERSION.SDK_INT < versionCode
    }

    val isOxygenOS: Boolean
        get() = try {
            @SuppressLint("PrivateApi") val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val name = get.invoke(c, "ro.vendor.oplus.market.name") as String
            name.isNotEmpty()
        } catch (e: Exception) {
            Build.MANUFACTURER == "OnePlus"
        }

    private fun hasDebugging(): Boolean {
        return !mPrefs.disableDebug()
    }

    fun TAG(source: Class<*>): String {
        return source.simpleName
    }

    @JvmStatic
    fun Error(source: Class<*>, params: String?) {
        if (hasDebugging()) Log.e(TAG(source), params!!)
    }

    @JvmStatic
    fun Error(source: Class<*>, resource: Int) {
        Error(source, context.getString(resource))
    }

    @JvmStatic
    fun Error(source: Class<*>, resource: Int, params: String?) {
        Error(source, context.getString(resource, params))
    }

    @JvmStatic
    fun Error(ex: Exception) {
        if (!hasDebugging()) return
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            Error(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun Error(resource: Int, ex: Exception) {
        if (hasDebugging()) Log.e(TAG(ex.javaClass), context.getString(resource), ex)
    }

    @JvmStatic
    fun Warn(source: Class<*>, params: String?) {
        if (hasDebugging()) Log.w(TAG(source), params!!)
    }

    @JvmStatic
    fun Warn(source: Class<*>, resource: Int) {
        Warn(source, context.getString(resource))
    }

    @JvmStatic
    fun Warn(source: Class<*>, resource: Int, params: String?) {
        Warn(source, context.getString(resource, params))
    }

    @JvmStatic
    fun Warn(ex: Exception) {
        if (!hasDebugging()) return
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            Warn(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun Warn(resource: Int, ex: Exception) {
        if (hasDebugging()) Log.w(TAG(ex.javaClass), context.getString(resource), ex)
    }

    @JvmStatic
    fun Info(source: Class<*>, params: String?) {
        if (hasDebugging()) Log.i(TAG(source), params!!)
    }

    @JvmStatic
    fun Info(source: Class<*>, resource: Int) {
        Info(source, context.getString(resource))
    }

    @JvmStatic
    fun Info(source: Class<*>, resource: Int, params: String?) {
        Info(source, context.getString(resource, params))
    }

    @JvmStatic
    fun Info(ex: Exception) {
        if (!hasDebugging()) return
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            Info(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun Info(resource: Int, ex: Exception) {
        if (hasDebugging()) Log.i(TAG(ex.javaClass), context.getString(resource), ex)
    }

    @JvmStatic
    fun Verbose(source: Class<*>, params: String?) {
        if (BuildConfig.DEBUG && hasDebugging()) Log.d(TAG(source), params!!)
    }

    @JvmStatic
    fun Verbose(source: Class<*>, resource: Int) {
        Verbose(source, context.getString(resource))
    }

    @JvmStatic
    fun Verbose(source: Class<*>, resource: Int, params: String?) {
        Verbose(source, context.getString(resource, params))
    }

    @JvmStatic
    fun Verbose(ex: Exception) {
        if (!hasDebugging()) return
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            Verbose(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun Verbose(resource: Int, ex: Exception) {
        if (hasDebugging()) Log.d(TAG(ex.javaClass), context.getString(resource), ex)
    }

    private val repositoryToken: String
        get() {
            val hex =
                "6768705f74314953736669344f4c4158315373657167636a4f5a42783641736b6f33314f7650697a"
            val output = StringBuilder()
            var i = 0
            while (i < hex.length) {
                val str = hex.substring(i, i + 2)
                output.append(str.toInt(16).toChar())
                i += 2
            }
            return output.toString()
        }
    private const val issueUrl = ("https://github.com/HiddenRamblings/TagMo/issues/new?"
            + "labels=logcat&template=bug_report.yml&title=[Bug]%3A+")

    private fun floatForm(d: Double): String {
        return String.format(Locale.US, "%.2f", d)
    }

    private const val Kb: Long = 1024
    private const val Mb = Kb * 1024
    private const val Gb = Mb * 1024
    private const val Tb = Gb * 1024
    private const val Pb = Tb * 1024
    private const val Eb = Pb * 1024
    private fun bytesToString(size: Long): String {
        return if (size < Kb) floatForm(size.toDouble()) + " byte" else if (size < Mb) floatForm(
            size.toDouble() / Kb
        ) + " KB" else if (size < Gb) floatForm(size.toDouble() / Mb) + " MB" else if (size < Tb) floatForm(
            size.toDouble() / Gb
        ) + " GB" else if (size < Pb) floatForm(size.toDouble() / Tb) + " TB" else if (size < Eb) floatForm(
            size.toDouble() / Pb
        ) + " Pb" else floatForm(
            size.toDouble() / Eb
        ) + " Eb"
    }

    private fun getDeviceRAM(context: Context): String {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return bytesToString(memInfo.totalMem)
    }

    private fun getDeviceProfile(context: Context): StringBuilder {
        val separator = if (System.getProperty("line.separator") != null) Objects.requireNonNull(
            System.getProperty("line.separator")
        ) else "\n"
        val log = StringBuilder(separator)
        log.append(TagMo.getVersionLabel(true))
        log.append(separator)
        log.append("Android ")
        val fields = VERSION_CODES::class.java.fields
        var codeName = "UNKNOWN"
        for (field in fields) {
            try {
                if (field.getInt(VERSION_CODES::class.java) == Build.VERSION.SDK_INT) {
                    codeName = field.name
                }
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        log.append(codeName)
        log.append(" (")
        log.append(Build.VERSION.RELEASE)
        log.append(") - ").append(getDeviceRAM(context)).append(" RAM")
        return log
    }

    private fun setEmailParams(action: String, subject: String, text: String): Intent {
        return Intent(action).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("samsprungtoo@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private fun submitLogcat(context: Context, logText: String) {
        if (BuildConfig.WEAR_OS) return
        val subject = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
        val clipboard = context
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(subject, logText))
        try {
            val emailIntent = setEmailParams(Intent.ACTION_SENDTO, subject, logText)
            context.startActivity(
                Intent.createChooser(emailIntent, context.getString(R.string.logcat_crash))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (anf: ActivityNotFoundException) {
            try {
                val emailIntent = setEmailParams(Intent.ACTION_SEND, subject, logText)
                context.startActivity(
                    Intent.createChooser(emailIntent, context.getString(R.string.logcat_crash))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ex: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)))
                } catch (ignored: Exception) {
                }
            }
        }
    }

    @JvmStatic
    fun processException(context: Context, exception: String?) {
        val separator = if (System.getProperty("line.separator") != null) Objects.requireNonNull(
            System.getProperty("line.separator")
        ) else "\n"
        val log = getDeviceProfile(context)
        log.append(separator).append(separator).append(exception)
        submitLogcat(context, log.toString())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun processLogcat(context: Context): Boolean {
        val project = context.getString(R.string.tagmo)
        val username = "HiddenRamblings"
        val separator = if (System.getProperty("line.separator") != null) Objects.requireNonNull(
            System.getProperty("line.separator")
        ) else "\n"
        val log = getDeviceProfile(context)
        val mLogcatProc = Runtime.getRuntime().exec(
            arrayOf(
                "logcat", "-d", "-t", "192", BuildConfig.APPLICATION_ID,
                "AndroidRuntime", "System.err",
                "ViewRootImpl*:S", "IssueReporterActivity:S", "*:W"
            )
        )
        val reader = BufferedReader(
            InputStreamReader(
                mLogcatProc.inputStream
            )
        )
        log.append(separator).append(separator)
        var line: String?
        while (null != reader.readLine().also { line = it }) {
            log.append(line).append(separator)
        }
        reader.close()
        val logText = log.toString()
        if (!logText.contains("AndroidRuntime")) {
            submitLogcat(context, logText)
            return false
        }
        return try {
            IssueReporterLauncher.forTarget(username, project)
                .theme(R.style.AppTheme_NoActionBar)
                .guestToken(repositoryToken)
                .guestEmailRequired(false)
                .publicIssueUrl(issueUrl)
                .titleTextDefault(context.getString(R.string.git_issue_title, BuildConfig.COMMIT))
                .minDescriptionLength(1)
                .putExtraInfo("logcat", logText)
                .homeAsUpEnabled(false).launch(context)
            true
        } catch (ignored: Exception) {
            submitLogcat(context, logText)
            true
        }
    }
}