package com.hiddenramblings.tagmo.translate

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object MLKitTranslationHelper {
    private val translators = mutableMapOf<String, Translator>()

    suspend fun translate(
        text: String,
        from: String,
        to: String,
        onDownloadStateChanged: (Boolean) -> Unit
    ): String? {
        val sourceLanguage = TranslateLanguage.fromLanguageTag(from) ?: return null
        val targetLanguage = TranslateLanguage.fromLanguageTag(to) ?: return null
        if (sourceLanguage == targetLanguage) return text

        val key = "$sourceLanguage-$targetLanguage"
        val translator = synchronized(translators) {
            translators.getOrPut(key) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(sourceLanguage)
                        .setTargetLanguage(targetLanguage)
                        .build()
                )
            }
        }

        return try {
            val model = TranslateRemoteModel.Builder(targetLanguage).build()
            val isDownloaded = RemoteModelManager.getInstance()
                .isModelDownloaded(model)
                .await()
            if (!isDownloaded) {
                onDownloadStateChanged(true)
                try {
                    translator.downloadModelIfNeeded(
                        DownloadConditions.Builder().requireWifi().build()
                    ).await()
                } finally {
                    onDownloadStateChanged(false)
                }
            }
            translator.translate(text).await()
        } catch (_: Exception) {
            onDownloadStateChanged(false)
            null
        }
    }
}
