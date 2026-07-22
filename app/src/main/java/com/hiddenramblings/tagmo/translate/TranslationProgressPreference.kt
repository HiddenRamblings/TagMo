package com.hiddenramblings.tagmo.translate

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference

class TranslationProgressPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    init {
        isPersistent = false
        isSelectable = false
        isVisible = false
    }
}
