package com.hiddenramblings.tagmo.translate

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

class TranslatableSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchPreferenceCompat(context, attrs) {
    private val titleRes = attrs?.resourceValue("title") ?: 0
    private val summaryRes = attrs?.resourceValue("summary") ?: 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var removeStateListener: (() -> Unit)? = null

    override fun onAttached() {
        super.onAttached()
        removeStateListener = DynamicTranslationManager.addStateListener {
            mainHandler.post { notifyChanged() }
        }
    }

    override fun onDetached() {
        removeStateListener?.invoke()
        removeStateListener = null
        super.onDetached()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        bindPreferenceText(holder, titleRes, summaryRes)
    }
}
