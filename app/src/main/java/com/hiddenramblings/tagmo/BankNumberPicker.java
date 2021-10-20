package com.hiddenramblings.tagmo;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.NumberPicker;

import androidx.annotation.RequiresApi;

public class BankNumberPicker extends NumberPicker {

    public BankNumberPicker(Context context) {
        super(context);
    }

    public BankNumberPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        processXmlAttributes(attrs, 0, 0);
    }

    public BankNumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        processXmlAttributes(attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) @SuppressWarnings("unused")
    public BankNumberPicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        processXmlAttributes(attrs, defStyleAttr, defStyleRes);
    }

    private void processXmlAttributes(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray attributes = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.BankNumberPicker, defStyleAttr, defStyleRes);

        try {
            this.setMinValue(attributes.getInt(
                    R.styleable.BankNumberPicker_minValue, 1));
            this.setMaxValue(attributes.getInt(
                    R.styleable.BankNumberPicker_maxValue, 200));
            this.setValue(attributes.getInt(
                    R.styleable.BankNumberPicker_startValue, 1));
        } finally {
            attributes.recycle();
        }
    }

}