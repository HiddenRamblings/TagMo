package com.hiddenramblings.tagmo.widget

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import java.util.*

class BoldSpannable {
    fun indexOf(text: String, query: String?): SpannableStringBuilder {
        val str = SpannableStringBuilder(text)
        if (query.isNullOrEmpty()) return str
        val lower = text.lowercase(Locale.getDefault())
        var j = 0
        while (j < lower.length) {
            val i = lower.indexOf(query, j)
            if (i == -1) break
            j = i + query.length
            if (str.isNotEmpty())
                str.setSpan(StyleSpan(Typeface.BOLD), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return str
    }

    fun startsWith(text: String?, query: String?): SpannableStringBuilder {
        val str = SpannableStringBuilder(text)
        if (!query.isNullOrEmpty() && text?.lowercase()?.startsWith(query) == true) {
            str.setSpan(
                StyleSpan(Typeface.BOLD), 0, query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return str
    }
}