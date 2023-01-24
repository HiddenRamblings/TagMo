package com.hiddenramblings.tagmo

import android.app.Application
import android.content.Context
import android.os.Build
import android.text.Spanned
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import com.github.anrwatchdog.ANRError
import com.github.anrwatchdog.ANRWatchDog
import com.hiddenramblings.tagmo.eightbit.content.ScaledContext
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
import com.hiddenramblings.tagmo.eightbit.io.Debug.processException
import me.weishu.reflection.Reflection
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class TagMo : Application() {
    private val isWatchingANR = !BuildConfig.DEBUG && !BuildConfig.GOOGLE_PLAY
    private fun isUncaughtANR(error: Throwable): Boolean {
        return null != error.cause && error.cause!!.cause is ANRError
    }

    fun setThemePreference() {
        when (Preferences(applicationContext).applicationTheme()) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (BuildConfig.WEAR_OS) {
            appContext = ScaledContext(appContext).watch(2f)
            appContext.setTheme(R.style.AppTheme)
        }
        if (isNewer(Build.VERSION_CODES.P))
            HiddenApiBypass.addHiddenApiExemptions("LBluetooth")
        else if (isNewer(Build.VERSION_CODES.LOLLIPOP))
            Reflection.unseal(base)
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        if (BuildConfig.WEAR_OS) appContext.setTheme(R.style.AppTheme)
        if (isWatchingANR) {
            ANRWatchDog(30000).setANRListener { error: ANRError ->
                val exception = StringWriter()
                error.printStackTrace(PrintWriter(exception))
                processException(this, exception.toString())
            }.start()
        }
        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, error: Throwable ->
            if (isWatchingANR && isUncaughtANR(error)) return@setDefaultUncaughtExceptionHandler
            val exception = StringWriter()
            error.printStackTrace(PrintWriter(exception))
            try {
                Toast.makeText(this, R.string.logcat_crash, Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) { }
            processException(this, exception.toString())
            exitProcess(0)
        }
        setThemePreference()
    }

    init {
        appContext = this
    }

    companion object {
        @JvmStatic
        lateinit var appContext : Context
            private set
        const val uiDelay = 50
        private const val commitHash = "#" + BuildConfig.COMMIT
        private val versionLabel = ("TagMo " + BuildConfig.VERSION_NAME + " (" + (
                if (BuildConfig.GOOGLE_PLAY)
                    "Google Play"
                else
                    "GitHub"
                ) + " " + (
                if (BuildConfig.WEAR_OS)
                    "Wear OS"
                else if (BuildConfig.BUILD_TYPE == "release")
                    "Release"
                else
                    "Debug"
                ) + ") " + commitHash)
        private const val commitLink = ("<a href=https://github.com/HiddenRamblings/TagMo/commit/"
                + BuildConfig.COMMIT + ">" + commitHash + "</a>")

        fun getVersionLabel(plain: Boolean): Spanned {
            return HtmlCompat.fromHtml(
                if (plain) versionLabel else versionLabel.replace(commitHash, commitLink),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        }

        var hasSubscription = false
    }
}