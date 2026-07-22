package com.hiddenramblings.tagmo.translate

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.app.LocaleManagerCompat
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Translates resource-backed UI text from English while preserving the existing
 * localized resource as the immediate and failure fallback.
 */
object DynamicTranslationManager {
    private const val SOURCE_LANGUAGE = "en"

    private val preferences = Preferences(TagMo.appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val translationMutex = Mutex()
    private val cache = ConcurrentHashMap<String, String>()
    private val stateListeners = CopyOnWriteArrayList<() -> Unit>()

    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()

    fun isEnabled(): Boolean = preferences.dynamicTranslation()

    fun setEnabled(enabled: Boolean) {
        preferences.dynamicTranslation(enabled)
        cache.clear()
        stateListeners.forEach { it.invoke() }
    }

    fun addStateListener(listener: () -> Unit): () -> Unit {
        stateListeners.add(listener)
        return { stateListeners.remove(listener) }
    }

    fun bindResource(view: TextView, @StringRes resId: Int) {
        val fallback = view.context.getString(resId)
        val source = getEnglishString(view.context, resId)
        view.setTag(R.id.dynamic_translation_resource, resId)
        view.setTag(R.id.dynamic_translation_fallback, fallback)
        bind(view, fallback, source)
    }

    fun bind(view: TextView, fallback: CharSequence?, source: String? = fallback?.toString()) {
        val displayText = (
            view.getTag(R.id.dynamic_translation_fallback) as? String
                ?: fallback?.toString()
        ).orEmpty()
        view.setTag(R.id.dynamic_translation_fallback, displayText)
        view.text = displayText

        val enabled = isEnabled()
        val targetLanguage = getTargetLanguage(view.context)
        val requestToken = "$enabled|$targetLanguage|$source"
        view.setTag(R.id.dynamic_translation_source, requestToken)

        if (!enabled || targetLanguage == null || targetLanguage == SOURCE_LANGUAGE
            || source.isNullOrBlank()
        ) return

        val cacheKey = "$targetLanguage|$source"
        cache[cacheKey]?.let { translated ->
            view.text = translated
            return
        }

        scope.launch {
            val translated = translationMutex.withLock {
                MLKitTranslationHelper.translate(
                    text = source,
                    from = SOURCE_LANGUAGE,
                    to = targetLanguage,
                    onDownloadStateChanged = ::updateDownloadState
                ) ?: displayText
            }
            cache[cacheKey] = translated
            withContext(Dispatchers.Main) {
                if (isEnabled() && view.getTag(R.id.dynamic_translation_source) == requestToken) {
                    view.text = translated
                }
            }
        }
    }

    /**
     * Applies dynamic translation to visible text whose source is not tied to
     * a string resource. Resource-backed views are handled by bindResource().
     */
    fun bindTextTree(root: View) {
        if (root is TextView && root.getTag(R.id.dynamic_translation_resource) == null) {
            root.text?.takeIf { it.isNotBlank() }?.let { bind(root, it) }
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) bindTextTree(root.getChildAt(index))
        }
    }

    private fun updateDownloadState(isDownloading: Boolean) {
        if (_isDownloadingModel.value == isDownloading) return
        _isDownloadingModel.value = isDownloading
        stateListeners.forEach { it.invoke() }
    }

    private fun getTargetLanguage(context: Context): String? {
        val locale = getCurrentLocale(context)
        return com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(
            locale.toLanguageTag()
        ) ?: com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(locale.language)
    }

    private fun getCurrentLocale(context: Context): Locale {
        LocaleManagerCompat.getApplicationLocales(context).get(0)?.let { return it }
        val configuration = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }

    private fun getEnglishString(context: Context, @StringRes resId: Int): String {
        return try {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.ENGLISH)
            context.createConfigurationContext(configuration).getString(resId)
        } catch (error: Exception) {
            Log.w("DynamicTranslation", "Unable to load English source text", error)
            context.getString(resId)
        }
    }
}
