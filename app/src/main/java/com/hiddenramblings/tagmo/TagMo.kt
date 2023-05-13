package com.hiddenramblings.tagmo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.text.Spanned
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import com.github.anrwatchdog.ANRError
import com.github.anrwatchdog.ANRWatchDog
import com.hiddenramblings.tagmo.eightbit.content.ScaledContext
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import me.weishu.reflection.Reflection
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Version.isTiramisu -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}
inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? = when {
    Version.isTiramisu -> getParcelableArrayListExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableArrayListExtra(key)
}

class TagMo : Application() {
    private val isWatchingANR = !BuildConfig.GOOGLE_PLAY
    private fun isUncaughtANR(error: Throwable): Boolean {
        return error.cause?.cause is ANRError
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
        if (BuildConfig.WEAR_OS)
            appContext = ScaledContext(this).watch(2f)
        if (Version.isPie)
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/bluetooth/BluetoothHidHost;", "LBluetooth"
            )
        else if (Version.isLollipop) Reflection.unseal(base)
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        if (BuildConfig.WEAR_OS) appContext.setTheme(R.style.AppTheme)
        if (isWatchingANR) {
            ANRWatchDog(20000).setANRListener { error: ANRError ->
                val exception = StringWriter()
                error.printStackTrace(PrintWriter(exception))
                try {
                    Debug.processException(this, exception.toString())
                } catch (ignored: Exception) { }
            }.start()
        }
        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, error: Throwable ->
            if (isWatchingANR && isUncaughtANR(error)) return@setDefaultUncaughtExceptionHandler
            val exception = StringWriter()
            error.printStackTrace(PrintWriter(exception))
            try {
                Debug.processException(this, exception.toString())
            } catch (ignored: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(-1)
        }
        setThemePreference()
    }

    init { appContext = this }

    companion object {
        @JvmStatic
        lateinit var appContext : Context
            private set
        const val uiDelay = 50

        private const val commitHash = "#${BuildConfig.COMMIT}"
        private val versionLabel = "TagMo ${BuildConfig.VERSION_NAME} (${
            if (BuildConfig.GOOGLE_PLAY) "Play" else "GitHub"
        } ${
            when {
                BuildConfig.WEAR_OS -> "Wear OS"
                BuildConfig.BUILD_TYPE == "release" -> "Release"
                else -> "Debug"
            }
        }) $commitHash"
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