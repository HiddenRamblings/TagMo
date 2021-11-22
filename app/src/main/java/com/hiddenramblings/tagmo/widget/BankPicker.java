package com.hiddenramblings.tagmo.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.NumberPicker;

import androidx.annotation.RequiresApi;

import com.hiddenramblings.tagmo.R;

public class BankPicker extends NumberPicker {

    public BankPicker(Context context) {
        super(context);
    }

    public BankPicker(Context context, AttributeSet attrs) {
        super(context, attrs);
        processXmlAttributes(attrs, 0, 0);
    }

    public BankPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        processXmlAttributes(attrs, defStyleAttr, 0);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP) @SuppressWarnings("unused")
    public BankPicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        processXmlAttributes(attrs, defStyleAttr, defStyleRes);
    }

    private void processXmlAttributes(AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray attributes = getContext().getTheme().obtainStyledAttributes(
                attrs, R.styleable.BankPicker, defStyleAttr, defStyleRes);

        try {
            this.setMinValue(attributes.getInt(
                    R.styleable.BankPicker_minValue, 1));
            this.setMaxValue(attributes.getInt(
                    R.styleable.BankPicker_maxValue, 200));
            this.setValue(attributes.getInt(
                    R.styleable.BankPicker_startValue, 1));
        } finally {
            attributes.recycle();
        }
    }

    public int getPosition() {
        return this.getValue() - this.getMinValue();
    }

    public void setPosition(int position) {
        this.setValue(position + this.getMinValue());
    }

    public int getValueForPosition(int value) {
        return value + this.getMinValue();
    }
}