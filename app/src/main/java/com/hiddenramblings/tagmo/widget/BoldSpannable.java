package com.hiddenramblings.tagmo.widget;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

public class BoldSpannable {

    public SpannableStringBuilder IndexOf(String text, String query) {
        SpannableStringBuilder str = new SpannableStringBuilder(text);
        if (TextUtils.isEmpty(query)) return str;

        text = text.toLowerCase();
        int j = 0;
        while (j < text.length()) {
            int i = text.indexOf(query, j);
            if (i == -1)
                break;

            j = i + query.length();
            str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return str;
    }

    public SpannableStringBuilder StartsWith(String text, String query) {
        SpannableStringBuilder str = new SpannableStringBuilder(text);
        if (!TextUtils.isEmpty(query) && text.toLowerCase().startsWith(query)) {
            str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return str;
    }
}
