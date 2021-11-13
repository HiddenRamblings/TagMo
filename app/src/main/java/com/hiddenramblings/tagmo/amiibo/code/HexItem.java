package com.hiddenramblings.tagmo.amiibo.code;

import android.graphics.Typeface;

public class HexItem {
    String text;
    int textStyle;
    int backgroundColor;

    HexItem(String text, int textStyle, int backgroundColor) {
        this.text = text;
        this.textStyle = textStyle;
        this.backgroundColor = backgroundColor;
    }

    public HexItem(String text, int backgroundColor) {
        this(text, Typeface.BOLD, backgroundColor);
    }

    public String getText() {
        return text;
    }

    public int getTextStyle() {
        return textStyle;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }
}
