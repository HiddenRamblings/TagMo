package com.hiddenramblings.tagmo

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Parcelable
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.text.HtmlCompat
import com.hiddenramblings.tagmo.eightbit.content.ScaledContext
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.webkit.ChromeIntegration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.weishu.reflection.Reflection
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter
import kotlin.system.exitProcess

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Version.isTiramisu -> getParcelableExtra(key, T::class.java)
    else -> @Suppress("deprecation") getParcelableExtra(key) as? T
}
inline fun <reified T : Parcelable> Intent.parcelableArrayList(key: String): ArrayList<T>? = when {
    Version.isTiramisu -> getParcelableArrayListExtra(key, T::class.java)
    else -> @Suppress("deprecation") getParcelableArrayListExtra(key)
}

inline fun <reified T : Serializable> Intent.serializable(key: String): T? = when {
    Version.isTiramisu -> getSerializableExtra(key, T::class.java)
    else -> @Suppress("deprecation") getSerializableExtra(key) as? T
}

class TagMo : Application() {

    fun setThemePreference() {
        when (Preferences(applicationContext).applicationTheme()) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (isWearable)
            appContext = ScaledContext(this).watch(2f)
        if (Version.isLollipop) Reflection.unseal(base)
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        val uiModeManager: UiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isWearable = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_WATCH || BuildConfig.WEAR_OS

        if (isWearable)
            appContext.setTheme(R.style.AppTheme)
        else
            setThemePreference()
        Thread.setDefaultUncaughtExceptionHandler { _: Thread?, error: Throwable ->
            val exception = StringWriter().apply {
                error.printStackTrace(PrintWriter(this))
            }
            try {
                Debug.sendException(this, exception.toString())
            } catch (_: Exception) { }
            try {
                Debug.saveException(this, exception.toString())
            } catch (_: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(-1)
        }
        CoroutineScope(Dispatchers.IO).launch {
            ChromeIntegration.hasCustomTabs = ChromeIntegration.bindCustomTabService(this@TagMo)
        }
    }

    init { appContext = this }

    companion object {
        @JvmStatic
        lateinit var appContext : Context
            private set

        var isWearable = false

        const val uiDelay = 50

        val downloadDir by lazy { Storage.getDownloadDir("TagMo") }

        private const val commitHash = "#${BuildConfig.COMMIT}"
        val versionLabel = "TagMo ${BuildConfig.VERSION_NAME} (${
            if (BuildConfig.GOOGLE_PLAY) "Play" else "GitHub"
        } ${
            when {
                isWearable -> "Wear OS"
                BuildConfig.BUILD_TYPE == "release" -> "Release"
                else -> "Debug"
            }
        }) $commitHash"
        private const val commitLink = ("<a href=https://github.com/HiddenRamblings/TagMo/commit/"
                + BuildConfig.COMMIT + ">" + commitHash + "</a>")

        val versionLabelLinked = HtmlCompat.fromHtml(
                versionLabel.removePrefix("TagMo ").replace(commitHash, commitLink),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )

        var hasSubscription = false

        const val isUserInputEnabled = false
    }
}