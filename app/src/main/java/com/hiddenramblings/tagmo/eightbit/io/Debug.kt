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
package com.hiddenramblings.tagmo.eightbit.io

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import com.hiddenramblings.tagmo.BrowserActivity
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar
import com.hiddenramblings.tagmo.widget.Toasty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*

@Suppress("unused")
object Debug {
    private val guideUri = Uri.parse("https://tagmo.gitlab.io")
    private val context: Context
        get() = TagMo.appContext
    private val mPrefs = Preferences(context)

    private val manufacturer: String by lazy {
        try {
            @SuppressLint("PrivateApi")
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val name = get.invoke(c, "ro.product.manufacturer") as String
            name.ifEmpty { "Unknown" }
        } catch (e: Exception) {
            Build.MANUFACTURER
        }
    }

    val separator = System.getProperty("line.separator") ?: "\n"

    private fun hasDebugging(): Boolean {
        return !mPrefs.disableDebug()
    }

    fun getExceptionCause(e: Exception): String? {
        val description =  e.message ?: e.cause?.toString()
        return if (null != description && description.contains(" : "))
            description.substring(description.indexOf(":") + 2)
        else description
    }
    
    fun getExceptionClass(e: Exception) : String {
        return e.cause?.javaClass?.name ?: e.javaClass.name
    }

    fun hasException(e: Exception, className: String, methodName: String): Boolean {
        return !e.stackTrace.isNullOrEmpty() && e.stackTrace.any {
            it.className.endsWith(className) && it.methodName == methodName
        }
    }

    @JvmStatic
    fun error(source: Class<*>, params: String?) {
        params?.let { Log.e(source.simpleName, it) }
    }

    @JvmStatic
    fun error(source: Class<*>, resource: Int) {
        error(source, context.getString(resource))
    }

    @JvmStatic
    fun error(source: Class<*>, resource: Int, params: String?) {
        error(source, context.getString(resource, params))
    }

    @JvmStatic
    fun error(ex: Exception) {
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            error(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun error(resource: Int, ex: Exception) {
        Log.e(ex.javaClass.simpleName, context.getString(resource), ex)
    }

    @JvmStatic
    fun warn(source: Class<*>, params: String?) {
        params?.let { Log.w(source.simpleName, it) }
    }

    @JvmStatic
    fun warn(source: Class<*>, resource: Int) {
        warn(source, context.getString(resource))
    }

    @JvmStatic
    fun warn(source: Class<*>, resource: Int, params: String?) {
        warn(source, context.getString(resource, params))
    }

    @JvmStatic
    fun warn(ex: Exception) {
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            warn(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun warn(resource: Int, ex: Exception) {
        Log.w(ex.javaClass.simpleName, context.getString(resource), ex)
    }

    @JvmStatic
    fun info(source: Class<*>, params: String?) {
        params?.let { Log.i(source.simpleName, it) }
    }

    @JvmStatic
    fun info(source: Class<*>, resource: Int) {
        info(source, context.getString(resource))
    }

    @JvmStatic
    fun info(source: Class<*>, resource: Int, params: String?) {
        info(source, context.getString(resource, params))
    }

    @JvmStatic
    fun info(ex: Exception) {
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            info(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun info(resource: Int, ex: Exception) {
        Log.i(ex.javaClass.simpleName, context.getString(resource), ex)
    }

    @JvmStatic
    fun verbose(source: Class<*>, params: String?) {
        if (hasDebugging()) params?.let { Log.d(source.simpleName, it) }
    }

    @JvmStatic
    fun verbose(source: Class<*>, resource: Int) {
        verbose(source, context.getString(resource))
    }

    @JvmStatic
    fun verbose(source: Class<*>, resource: Int, params: String?) {
        verbose(source, context.getString(resource, params))
    }

    @JvmStatic
    fun verbose(ex: Exception) {
        if (!hasDebugging()) return
        if (ex.stackTrace.isNotEmpty()) {
            val exception = StringWriter()
            ex.printStackTrace(PrintWriter(exception))
            verbose(ex.javaClass, exception.toString())
        }
    }

    @JvmStatic
    fun verbose(resource: Int, ex: Exception) {
        if (hasDebugging()) Log.d(ex.javaClass.simpleName, context.getString(resource), ex)
    }

    private const val issueUrl = ("https://github.com/HiddenRamblings/TagMo/issues/new?"
            + "labels=logcat&template=bug_report.yml&title=[Bug]%3A+")

    private fun getDeviceProfile(context: Context): StringBuilder {
        val log = StringBuilder(separator)
        log.append(TagMo.versionLabel)
        log.append(separator)
        log.append(manufacturer)
        log.append(" ")
        var codeName = "UNKNOWN"
        for (field in Build.VERSION_CODES::class.java.fields) {
            try {
                if (field.getInt(Build.VERSION_CODES::class.java) == Build.VERSION.SDK_INT)
                    codeName = field.name
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
        log.append(codeName)
        log.append(" (")
        log.append(Build.VERSION.RELEASE)
        log.append(") - ").append(Memory.getDeviceRAM()).append(" RAM")
        if (KeyManager(context).isKeyMissing)
            log.append(separator).append(context.getString(R.string.log_keymanager))
        return log
    }

    private fun setEmailParams(action: String, subject: String, text: String): Intent {
        return Intent(action).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("tagmo.git@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private fun submitLogcat(context: Context, logText: String) {
        mPrefs.lastBugReport(System.currentTimeMillis())
        val subject = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
        val selectionTitle = if (logText.contains("AndroidRuntime"))
            context.getString(R.string.logcat_crash)
        else
            subject
        try {
            val emailIntent = setEmailParams(Intent.ACTION_SENDTO, subject, logText)
            context.startActivity(Intent.createChooser(
                emailIntent, selectionTitle
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (anf: ActivityNotFoundException) {
            try {
                val emailIntent = setEmailParams(Intent.ACTION_SEND, subject, logText)
                context.startActivity(Intent.createChooser(
                    emailIntent, selectionTitle
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ex: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)))
                } catch (ignored: Exception) { }
            }
        }
    }

    private fun setClipboard(context: Context, subject: String, logText: String) {
        with (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager) {
            setPrimaryClip(ClipData.newPlainText(subject, logText))
        }
    }

    @JvmStatic
    fun processException(context: Context, exception: String?, clipboard: Boolean) {
        val log = getDeviceProfile(context)
        log.append(separator).append(separator).append(exception)
        val subject = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
        setClipboard(context, subject, log.toString())
        when {
            clipboard -> {}
            System.currentTimeMillis() < mPrefs.lastBugReport() + 900000 -> {
                Toasty(context).Long(R.string.duplicate_reports)
            }
            else -> { submitLogcat(context, log.toString()) }
        }
    }

    @JvmStatic
    fun clipException(context: Context, exception: String?) {
        processException(context, exception, true)
    }

    @JvmStatic
    fun sendException(context: Context, exception: String?) {
        processException(context, exception, false)
    }

    private fun showGuideBanner(context: Context) {
        if (context is Activity) {
            IconifiedSnackbar(context).buildSnackbar(
                R.string.menu_guides,
                R.drawable.ic_support_required_menu,
                Snackbar.LENGTH_LONG
            ).also { guides ->
                guides.setAction(R.string.view) {
                    if (context is BrowserActivity) {
                        context.showWebsite(null)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, guideUri))
                    }
                    guides.dismiss()
                }
                guides.show()
            }
        } else {
            Toasty(context).Long(R.string.guide_suggested)
            context.startActivity(Intent(Intent.ACTION_VIEW, guideUri))
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun processLogcat(context: Context) {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val log = getDeviceProfile(context)
            val mLogcatProc = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d", "-t", "256", "--pid=${android.os.Process.myPid()}",
                    "AppIconSolution*:S", "ViewRootImpl*:S",
                    "*:W", "AndroidRuntime", "System.err"
            ))
            val reader = BufferedReader(InputStreamReader(mLogcatProc.inputStream))
            log.append(separator).append(separator)
            var line: String?
            while (null != reader.readLine().also { line = it }) {
                log.append(line).append(separator)
            }
            reader.close()
            log.append(separator)
            val logText = log.toString()
            log.setLength(0)
            withContext(Dispatchers.Main) {
                val subject = context.getString(R.string.git_issue_title, BuildConfig.COMMIT)
                setClipboard(context, subject, logText)
                when {
                    KeyManager(context).isKeyMissing || !hasDebugging() -> {
                        showGuideBanner(context)
                    }
                    System.currentTimeMillis() < mPrefs.lastBugReport() + 900000 -> {
                        if (context is Activity) {
                            IconifiedSnackbar(context).buildSnackbar(
                                R.string.duplicate_reports,
                                R.drawable.ic_support_required_menu,
                                Snackbar.LENGTH_LONG
                            ).also { status ->
                                status.addCallback(object: Snackbar.Callback() {
                                    override fun onDismissed(snackbar: Snackbar?, event: Int) {
                                        setClipboard(context, subject, logText)
                                    }
                                })
                                status.show()
                            }
                        }
                    }
                    context is Activity -> {
                        IconifiedSnackbar(context).buildSnackbar(
                            subject,
                            R.drawable.ic_support_required_menu,
                            Snackbar.LENGTH_INDEFINITE
                        ).also { status ->
                            status.addCallback(object: Snackbar.Callback() {
                                override fun onDismissed(snackbar: Snackbar?, event: Int) {
                                    if (event != DISMISS_EVENT_ACTION
                                        && !logText.contains("AndroidRuntime")) {
                                        showGuideBanner(context)
                                    }
                                }
                            })
                            status.setAction(R.string.submit) { submitLogcat(context, logText) }
                            status.show()
                        }
                    }
                    else -> { submitLogcat(Debug.context, logText) }
                }
            }
        }
    }
}