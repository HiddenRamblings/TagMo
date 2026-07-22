package com.hiddenramblings.tagmo.translate

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

internal fun AttributeSet.resourceValue(name: String): Int =
    getAttributeResourceValue(ANDROID_NAMESPACE, name, 0)

internal fun bindPreferenceText(
    holder: PreferenceViewHolder,
    titleRes: Int,
    summaryRes: Int
) {
    holder.itemView.findViewById<TextView>(android.R.id.title)?.let { title ->
        if (titleRes != 0) DynamicTranslationManager.bindResource(title, titleRes)
    }
    holder.itemView.findViewById<TextView>(android.R.id.summary)?.let { summary ->
        if (summaryRes != 0) DynamicTranslationManager.bindResource(summary, summaryRes)
    }
}

class TranslatablePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
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
