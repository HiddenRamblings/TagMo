package myinnos.indexfastscrollrecycler;

/*
 * Created by MyInnos on 31-01-2017.
 * Updated by AbandonedCart 07-2022.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.R;

@SuppressWarnings("unused")
public class IndexFastScrollRecyclerView extends RecyclerView {

    private IndexFastScrollRecyclerSection mScroller = null;
    private GestureDetector mGestureDetector = null;

    private boolean mEnabled = true;

    public int setIndexTextSize = 12;
    public float mIndexbarWidth = 20;
    public float mIndexbarMarginLeft = 2;
    public float mIndexbarMarginRight = 2;
    public float mIndexbarMarginTop = 2;
    public float mIndexbarMarginBottom = 2;
    public int mPreviewPadding = 5;
    public int mIndexBarCornerRadius = 5;
    public float mIndexBarTransparentValue = (float) 0.6;
    public int mIndexBarStrokeWidth = 2;
    public @ColorInt
    int mSetIndexBarStrokeColor = Color.BLACK;
    public @ColorInt
    int mIndexbarBackgroudColor = Color.BLACK;
    public @ColorInt
    int mIndexbarTextColor = Color.WHITE;
    public @ColorInt
    int indexbarHighLightTextColor = Color.BLACK;

    public int mPreviewTextSize = 50;
    public @ColorInt
    int mPreviewBackgroudColor = Color.BLACK;
    public @ColorInt
    int mPreviewTextColor = Color.WHITE;
    public float mPreviewTransparentValue = (float) 0.4;

    public IndexFastScrollRecyclerView(Context context) {
        super(context);
    }

    public IndexFastScrollRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public IndexFastScrollRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.IndexFastScrollRecyclerView);
            
                try {
                    setIndexTextSize = typedArray.getInt(R.styleable.IndexFastScrollRecyclerView_setIndexTextSize, setIndexTextSize);
                    mIndexbarWidth = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setIndexbarWidth, mIndexbarWidth);
                    mIndexbarMarginLeft = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin, mIndexbarMarginLeft);
                    mIndexbarMarginRight = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin, mIndexbarMarginRight);
                    mIndexbarMarginTop = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin, mIndexbarMarginTop);
                    mIndexbarMarginBottom = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin, mIndexbarMarginBottom);
                    mPreviewPadding = typedArray.getInt(R.styleable.IndexFastScrollRecyclerView_setPreviewPadding, mPreviewPadding);
                    mIndexBarCornerRadius = typedArray.getInt(R.styleable.IndexFastScrollRecyclerView_setIndexBarCornerRadius, mIndexBarCornerRadius);
                    mIndexBarTransparentValue = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setIndexBarTransparentValue, mIndexBarTransparentValue);

                    mEnabled = true;
                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarShown)) {
                        mEnabled = typedArray.getBoolean(R.styleable.IndexFastScrollRecyclerView_setIndexBarShown, mEnabled);
                    }

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor)) {
                        TypedValue tv = new TypedValue();
                        typedArray.getValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, tv);
                        if (tv.type == TypedValue.TYPE_STRING) {
                            mIndexbarBackgroudColor = Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor));
                        } else {
                            mIndexbarBackgroudColor = typedArray.getColor(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, mIndexbarBackgroudColor);
                        }
                    }

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarTextColor)) {
                        TypedValue tv = new TypedValue();
                        typedArray.getValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, tv);
                        if (tv.type == TypedValue.TYPE_STRING) {
                            mIndexbarTextColor = Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarTextColor));
                        } else {
                            mIndexbarTextColor = typedArray.getColor(R.styleable.IndexFastScrollRecyclerView_setIndexBarTextColor, mIndexbarTextColor);
                        }
                    }

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarHighlightTextColor)) {
                        TypedValue tv = new TypedValue();
                        typedArray.getValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, tv);
                        if (tv.type == TypedValue.TYPE_STRING) {
                            indexbarHighLightTextColor = Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarHighlightTextColor));
                        } else {
                            indexbarHighLightTextColor = typedArray.getColor(R.styleable.IndexFastScrollRecyclerView_setIndexBarHighlightTextColor, indexbarHighLightTextColor);
                        }
                    }

                    mPreviewTextSize = typedArray.getInt(R.styleable.IndexFastScrollRecyclerView_setPreviewTextSize, mPreviewTextSize);
                    mPreviewTransparentValue = typedArray.getFloat(R.styleable.IndexFastScrollRecyclerView_setPreviewTransparentValue, mPreviewTransparentValue);

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setPreviewColor)) {
                        TypedValue tv = new TypedValue();
                        typedArray.getValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, tv);
                        if (tv.type == TypedValue.TYPE_STRING) {
                            mPreviewBackgroudColor = Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setPreviewColor));
                        } else {
                            mPreviewBackgroudColor = typedArray.getColor(R.styleable.IndexFastScrollRecyclerView_setPreviewColor, mPreviewBackgroudColor);
                        }
                    }

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setPreviewTextColor)) {
                        TypedValue tv = new TypedValue();
                        typedArray.getValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, tv);
                        if (tv.type == TypedValue.TYPE_STRING) {
                            mPreviewTextColor = Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setPreviewTextColor));
                        } else {
                            mPreviewTextColor = typedArray.getColor(R.styleable.IndexFastScrollRecyclerView_setPreviewTextColor, mPreviewTextColor);
                        }
                    }

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeWidth)) {
                        mIndexBarStrokeWidth = typedArray.getInt(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeWidth, mIndexBarStrokeWidth);
                    }

                    if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeColor)) {
                        TypedValue tv = new TypedValue();
                        typedArray.getValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor, tv);
                        if (tv.type == TypedValue.TYPE_STRING) {
                            mSetIndexBarStrokeColor = Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeColor));
                        } else {
                            mSetIndexBarStrokeColor = typedArray.getColor(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeColor, mSetIndexBarStrokeColor);
                        }
                    }

                } finally {
                    typedArray.recycle();
                }
                
                // This line here is neccesary else the attributes won't be updated if a value is passed from XML
                mScroller = new IndexFastScrollRecyclerSection(context, this);
                mScroller.setIndexBarVisibility(mEnabled);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        // Overlay index bar
        if (mScroller != null)
            mScroller.draw(canvas);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mEnabled) {
            // Intercept ListView's touch event
            if (mScroller != null && mScroller.onTouchEvent(ev))
                return true;

            if (mGestureDetector == null) {
                mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        return super.onFling(e1, e2, velocityX, velocityY);
                    }

                });
            }
            mGestureDetector.onTouchEvent(ev);
        }

        return super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mEnabled && mScroller != null && mScroller.contains(ev.getX(), ev.getY()))
            return true;

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        if (mScroller != null)
            //noinspection unchecked
            mScroller.setAdapter(adapter);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mScroller != null)
            mScroller.onSizeChanged(w, h);
    }

    /**
     * @param value int to set the text size of the index bar
     */
    public void setIndexTextSize(int value) {
        mScroller.setIndexTextSize(value);
    }

    /**
     * @param value float to set the width of the index bar
     */
    public void setIndexbarWidth(float value) {
        mScroller.setIndexbarWidth(value);
    }

    /**
     * @param value float to set the margin of the index bar
     */
    public void setIndexbarMargin(float value) {
        mScroller.setIndexbarMargin(value);
    }

    /**
     * @param value float to set the top margin of the index bar
     */
    public void setIndexbarTopMargin(float value) {
        mScroller.setIndexbarTopMargin(value);
    }

    /**
     * @param value float to set the bottom margin of the index bar
     */
    public void setIndexbarBottomMargin(float value) {
        mScroller.setIndexbarBottomMargin(value);
    }

    /**
     * @param value float to set the Horizontal margin of the index bar
     */
    public void setIndexbarHorizontalMargin(float value) {
        mScroller.setIndexbarHorizontalMargin(value);
    }

    /**
     * @param value float to set the Vertical margin of the index bar
     */
    public void setIndexbarVerticalMargin(float value) {
        mScroller.setIndexbarVerticalMargin(value);
    }

    /**
     * @param value int to set the preview padding
     */
    public void setPreviewPadding(int value) {
        mScroller.setPreviewPadding(value);
    }

    /**
     * @param value int to set the corner radius of the index bar
     */
    public void setIndexBarCornerRadius(int value) {
        mScroller.setIndexBarCornerRadius(value);
    }

    /**
     * @param value float to set the transparency value of the index bar
     */
    public void setIndexBarTransparentValue(float value) {
        mScroller.setIndexBarTransparentValue(value);
    }

    /**
     * @param typeface Typeface to set the typeface of the preview & the index bar
     */
    public void setTypeface(Typeface typeface) {
        mScroller.setTypeface(typeface);
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    public void setIndexBarVisibility(boolean shown) {
        mScroller.setIndexBarVisibility(shown);
        mEnabled = shown;
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    public void setIndexBarStrokeVisibility(boolean shown) {
        mScroller.setIndexBarStrokeVisibility(shown);
    }

    /**
     * @param color The color for the index bar
     */
    public void setIndexBarStrokeColor(String color) {
        mScroller.setIndexBarStrokeColor(Color.parseColor(color));
    }

    /**
     * @param color The color for the preview box
     */
    public void setIndexBarStrokeColor(@ColorRes int color) {
        mScroller.setIndexBarStrokeColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * @param value int to set the text size of the preview box
     */
    public void setIndexBarStrokeWidth(int value) {
        mScroller.setIndexBarStrokeWidth(value);
    }


    /**
     * @param shown boolean to show or hide the preview
     */
    public void setPreviewVisibility(boolean shown) {
        mScroller.setPreviewVisibility(shown);
    }

    /**
     * @param value int to set the text size of the preview box
     */
    public void setPreviewTextSize(int value) {
        mScroller.setPreviewTextSize(value);
    }

    /**
     * @param color The color for the preview box
     */
    public void setPreviewColor(@ColorRes int color) {
        mScroller.setPreviewColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * @param color The color for the preview box
     */
    public void setPreviewColor(String color) {
        mScroller.setPreviewColor(Color.parseColor(color));
    }

    /**
     * @param color The text color for the preview box
     */
    public void setPreviewTextColor(@ColorRes int color) {
        mScroller.setPreviewTextColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * @param value float to set the transparency value of the preview box
     */
    public void setPreviewTransparentValue(float value) {
        mScroller.setPreviewTransparentValue(value);
    }

    /**
     * @param color The text color for the preview box
     */
    public void setPreviewTextColor(String color) {
        mScroller.setPreviewTextColor(Color.parseColor(color));
    }

    /**
     * @param color The color for the index bar
     */
    public void setIndexBarColor(String color) {
        mScroller.setIndexBarColor(Color.parseColor(color));
    }

    /**
     * @param color The color for the index bar
     */
    public void setIndexBarColor(@ColorRes int color) {
        mScroller.setIndexBarColor(ContextCompat.getColor(getContext(), color));
    }


    /**
     * @param color The text color for the index bar
     */
    public void setIndexBarTextColor(String color) {
        mScroller.setIndexBarTextColor(Color.parseColor(color));
    }

    /**
     * @param color The text color for the index bar
     */
    public void setIndexBarTextColor(@ColorRes int color) {
        mScroller.setIndexBarTextColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * @param color The text color for the index bar
     */
    public void setIndexbarHighLightTextColor(String color) {
        mScroller.setIndexbarHighLightTextColor(Color.parseColor(color));
    }

    /**
     * @param color The text color for the index bar
     */
    public void setIndexbarHighLightTextColor(@ColorRes int color) {
        mScroller.setIndexbarHighLightTextColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    public void setIndexBarHighLightTextVisibility(boolean shown) {
        mScroller.setIndexBarHighLightTextVisibility(shown);
    }

    public void updateSections() {
        mScroller.updateSections();
    }
}
