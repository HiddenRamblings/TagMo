package myinnos.indexfastscrollrecycler;

/*
 * Created by MyInnos on 31-01-2017.
 * Updated by AbandonedCart 07-2022.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.SectionIndexer;

import androidx.annotation.ColorInt;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

public class IndexFastScrollRecyclerSection extends RecyclerView.AdapterDataObserver {

    private float mIndexbarWidth;
    private float mIndexbarMarginLeft;
    private float mIndexbarMarginRight;
    private float mIndexbarMarginTop;
    private float mIndexbarMarginBottom;
    private final float mPreviewPadding;
    private final float mDensity;
    private final float mScaledDensity;
    private int mListViewWidth;
    private int mListViewHeight;
    private int mCurrentSection = -1;
    private boolean mIsIndexing = false;
    private RecyclerView mRecyclerView;
    private SectionIndexer mIndexer = null;
    private String[] mSections = null;
    private RectF mIndexbarRect;

    private int setIndexTextSize;
    private int setPreviewPadding;
    private boolean previewVisibility = true;
    private int setIndexBarCornerRadius;
    private Typeface setTypeface = null;
    private Boolean setIndexBarVisibility = true;
    private Boolean setSetIndexBarHighLightTextVisibility = false;
    private Boolean setIndexBarStrokeVisibility = true;
    public int mIndexBarStrokeWidth;
    private @ColorInt
    int mIndexBarStrokeColor;
    private @ColorInt
    int indexbarBackgroudColor;
    private @ColorInt
    int indexbarTextColor;
    private @ColorInt
    int indexbarHighLightTextColor;

    private int setPreviewTextSize;
    private @ColorInt
    int previewBackgroundColor;
    private @ColorInt
    int previewTextColor;
    private int previewBackgroudAlpha;
    private int indexbarBackgroudAlpha;

    public IndexFastScrollRecyclerSection(Context context, IndexFastScrollRecyclerView recyclerView) {

        setIndexTextSize = recyclerView.setIndexTextSize;
        float setIndexbarWidth = recyclerView.mIndexbarWidth;
        float setIndexbarMarginLeft = recyclerView.mIndexbarMarginLeft;
        float setIndexbarMarginRight = recyclerView.mIndexbarMarginRight;
        float setIndexbarMarginTop = recyclerView.mIndexbarMarginTop;
        float setIndexbarMarginBottom = recyclerView.mIndexbarMarginBottom;
        setPreviewPadding = recyclerView.mPreviewPadding;
        setPreviewTextSize = recyclerView.mPreviewTextSize;
        previewBackgroundColor = recyclerView.mPreviewBackgroudColor;
        previewTextColor = recyclerView.mPreviewTextColor;
        previewBackgroudAlpha = convertTransparentValueToBackgroundAlpha(recyclerView.mPreviewTransparentValue);

        mIndexBarStrokeColor = recyclerView.mSetIndexBarStrokeColor;
        mIndexBarStrokeWidth = recyclerView.mIndexBarStrokeWidth;

        setIndexBarCornerRadius = recyclerView.mIndexBarCornerRadius;
        indexbarBackgroudColor = recyclerView.mIndexbarBackgroudColor;
        indexbarTextColor = recyclerView.mIndexbarTextColor;
        indexbarHighLightTextColor = recyclerView.indexbarHighLightTextColor;

        indexbarBackgroudAlpha = convertTransparentValueToBackgroundAlpha(recyclerView.mIndexBarTransparentValue);

        mDensity = context.getResources().getDisplayMetrics().density;
        mScaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        mRecyclerView = recyclerView;
        //noinspection unchecked
        setAdapter(mRecyclerView.getAdapter());

        mIndexbarWidth = setIndexbarWidth * mDensity;
        mIndexbarMarginLeft = setIndexbarMarginLeft * mDensity;
        mIndexbarMarginRight = setIndexbarMarginRight * mDensity;
        mIndexbarMarginTop = setIndexbarMarginTop * mDensity;
        mIndexbarMarginBottom = setIndexbarMarginBottom * mDensity;
        mPreviewPadding = setPreviewPadding * mDensity;
    }

    public void draw(Canvas canvas) {

        if (setIndexBarVisibility) {

            Paint indexbarPaint = new Paint();

            indexbarPaint.setColor(indexbarBackgroudColor);
            indexbarPaint.setAlpha(indexbarBackgroudAlpha);
            indexbarPaint.setAntiAlias(true);
            canvas.drawRoundRect(mIndexbarRect, setIndexBarCornerRadius * mDensity, setIndexBarCornerRadius * mDensity, indexbarPaint);

            if (setIndexBarStrokeVisibility) {
                indexbarPaint.setStyle(Paint.Style.STROKE);
                indexbarPaint.setColor(mIndexBarStrokeColor);
                indexbarPaint.setStrokeWidth(mIndexBarStrokeWidth); // set stroke width
                canvas.drawRoundRect(mIndexbarRect, setIndexBarCornerRadius * mDensity,
                        setIndexBarCornerRadius * mDensity, indexbarPaint);
            }

            if (mSections != null && mSections.length > 0) {
                // Preview is shown when mCurrentSection is set
                if (previewVisibility && mCurrentSection >= 0
                        && !Objects.equals(mSections[mCurrentSection], "")) {
                    Paint previewPaint = new Paint();
                    previewPaint.setColor(previewBackgroundColor);
                    previewPaint.setAlpha(previewBackgroudAlpha);
                    previewPaint.setAntiAlias(true);
                    previewPaint.setShadowLayer(3, 0, 0,
                            Color.argb(64, 0, 0, 0));

                    Paint previewTextPaint = new Paint();
                    previewTextPaint.setColor(previewTextColor);
                    previewTextPaint.setAntiAlias(true);
                    previewTextPaint.setTextSize(setPreviewTextSize * mScaledDensity);
                    previewTextPaint.setTypeface(setTypeface);

                    float previewTextWidth = previewTextPaint.measureText(mSections[mCurrentSection]);
                    float previewSize = 2 * mPreviewPadding + previewTextPaint.descent() - previewTextPaint.ascent();
                    previewSize = Math.max(previewSize, previewTextWidth + 2 * mPreviewPadding);
                    RectF previewRect = new RectF((mListViewWidth - previewSize) / 2
                            , (mListViewHeight - previewSize) / 2
                            , (mListViewWidth - previewSize) / 2 + previewSize
                            , (mListViewHeight - previewSize) / 2 + previewSize);

                    canvas.drawRoundRect(previewRect, 5 * mDensity, 5 * mDensity, previewPaint);
                    canvas.drawText(mSections[mCurrentSection], previewRect.left + (previewSize - previewTextWidth) / 2 - 1
                            , previewRect.top + (previewSize - (previewTextPaint.descent() - previewTextPaint.ascent())) / 2 - previewTextPaint.ascent(), previewTextPaint);
                    setFadeTimeout(300);
                }

                Paint indexPaint = new Paint();
                indexPaint.setColor(indexbarTextColor);
                indexPaint.setAntiAlias(true);
                indexPaint.setTextSize(setIndexTextSize * mScaledDensity);
                indexPaint.setTypeface(setTypeface);

                float sectionHeight = (mIndexbarRect.height() - mIndexbarMarginTop - mIndexbarMarginBottom) / mSections.length;
                float paddingTop = (sectionHeight - (indexPaint.descent() - indexPaint.ascent())) / 2;
                for (int i = 0; i < mSections.length; i++) {

                    if (setSetIndexBarHighLightTextVisibility) {

                        if (mCurrentSection > -1 && i == mCurrentSection) {
                            indexPaint.setTypeface(Typeface.create(setTypeface, Typeface.BOLD));
                            indexPaint.setTextSize((setIndexTextSize + 3) * mScaledDensity);
                            indexPaint.setColor(indexbarHighLightTextColor);
                        } else {
                            indexPaint.setTypeface(setTypeface);
                            indexPaint.setTextSize(setIndexTextSize * mScaledDensity);
                            indexPaint.setColor(indexbarTextColor);
                        }
                        float paddingLeft = (mIndexbarWidth - indexPaint.measureText(mSections[i])) / 2;
                        canvas.drawText(mSections[i], mIndexbarRect.left + paddingLeft
                                , mIndexbarRect.top + mIndexbarMarginTop + sectionHeight * i + paddingTop - indexPaint.ascent(), indexPaint);


                    } else {
                        float paddingLeft = (mIndexbarWidth - indexPaint.measureText(mSections[i])) / 2;
                        canvas.drawText(mSections[i], mIndexbarRect.left + paddingLeft
                                , mIndexbarRect.top + mIndexbarMarginTop + sectionHeight * i + paddingTop - indexPaint.ascent(), indexPaint);
                    }

                }
            }
        }
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // If down event occurs inside index bar region, start indexing
                if (contains(ev.getX(), ev.getY())) {

                    // It demonstrates that the motion event started from index bar
                    mIsIndexing = true;
                    // Determine which section the point is in, and move the list to that section
                    mCurrentSection = getSectionByPoint(ev.getY());
                    scrollToPosition();
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mIsIndexing) {
                    // If this event moves inside index bar
                    if (contains(ev.getX(), ev.getY())) {
                        // Determine which section the point is in, and move the list to that section
                        mCurrentSection = getSectionByPoint(ev.getY());
                        scrollToPosition();
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsIndexing) {
                    mIsIndexing = false;
                    mCurrentSection = -1;
                }
                break;
        }
        return false;
    }

    private void scrollToPosition() {
        try {
            int position = mIndexer.getPositionForSection(mCurrentSection);
            RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(position, 0);
            } else if (null != layoutManager) {
                layoutManager.scrollToPosition(position);
            }
        } catch (Exception e) {
            Log.d("INDEX_BAR", "Data size returns null");
        }
    }

    public void onSizeChanged(int w, int h) {
        mListViewWidth = w;
        mListViewHeight = h;
        mIndexbarRect = new RectF(
                w - mIndexbarMarginLeft - mIndexbarWidth,
                mIndexbarMarginTop,
                w - mIndexbarMarginRight,
                h - mIndexbarMarginBottom - (mRecyclerView.getClipToPadding()
                        ? 0 : mRecyclerView.getPaddingBottom())
        );
    }

    public void setAdapter(RecyclerView.Adapter<RecyclerView.ViewHolder> adapter) {
        if (adapter instanceof SectionIndexer) {
            adapter.registerAdapterDataObserver(this);
            mIndexer = (SectionIndexer) adapter;
            mSections = (String[]) mIndexer.getSections();
        }
    }

    @Override
    public void onChanged() {
        super.onChanged();
        updateSections();
    }

    public void updateSections() {
        mSections = (String[]) mIndexer.getSections();
    }

    public boolean contains(float x, float y) {
        // Determine if the point is in index bar region, which includes the right margin of the bar
        return (x >= mIndexbarRect.left && y >= mIndexbarRect.top && y <= mIndexbarRect.top + mIndexbarRect.height());
    }

    private int getSectionByPoint(float y) {
        if (mSections == null || mSections.length == 0)
            return 0;
        if (y < mIndexbarRect.top + mIndexbarMarginTop)
            return 0;
        if (y >= mIndexbarRect.top + mIndexbarRect.height() - mIndexbarMarginTop)
            return mSections.length - 1;
        return (int) ((y - mIndexbarRect.top - mIndexbarMarginTop) / ((mIndexbarRect.height() - mIndexbarMarginBottom - mIndexbarMarginTop) / mSections.length));
    }

    private Runnable mLastFadeRunnable = null;

    private void setFadeTimeout(long delay) {
        if (mRecyclerView != null) {
            if (mLastFadeRunnable != null) {
                mRecyclerView.removeCallbacks(mLastFadeRunnable);
            }
            mLastFadeRunnable = () -> mRecyclerView.invalidate();
            mRecyclerView.postDelayed(mLastFadeRunnable, delay);
        }
    }

    private int convertTransparentValueToBackgroundAlpha(float value) {
        return (int) (255 * value);
    }

    /**
     * @param value int to set the text size of the index bar
     */
    public void setIndexTextSize(int value) {
        setIndexTextSize = value;
    }

    /**
     * @param value float to set the width of the index bar
     */
    public void setIndexbarWidth(float value) {
        mIndexbarWidth = value;
    }

    /**
     * @param value float to set the margin of the index bar
     */
    public void setIndexbarMargin(float value) {
        mIndexbarMarginLeft = value;
        mIndexbarMarginRight = value;
        mIndexbarMarginTop = value;
        mIndexbarMarginBottom = value;
    }

    /**
     * @param value float to set the top margin of the index bar
     */
    public void setIndexbarTopMargin(float value) {
        mIndexbarMarginTop = value;
    }

    /**
     * @param value float to set the bottom margin of the index bar
     */
    public void setIndexbarBottomMargin(float value) {
        mIndexbarMarginBottom = value;
    }

    /**
     * @param value float to set the left margin of the index bar
     */
    public void setIndexbarHorizontalMargin(float value) {
        mIndexbarMarginLeft = value;
        mIndexbarMarginRight = value;
    }

    /**
     * @param value float to set the right margin of the index bar
     */
    public void setIndexbarVerticalMargin(float value) {
        mIndexbarMarginTop = value;
        mIndexbarMarginBottom = value;
    }

    /**
     * @param value int to set preview padding
     */
    public void setPreviewPadding(int value) {
        setPreviewPadding = value;
    }

    /**
     * @param value int to set the radius of the index bar
     */
    public void setIndexBarCornerRadius(int value) {
        setIndexBarCornerRadius = value;
    }

    /**
     * @param value float to set the transparency of the color for index bar
     */
    public void setIndexBarTransparentValue(float value) {
        indexbarBackgroudAlpha = convertTransparentValueToBackgroundAlpha(value);
    }

    /**
     * @param typeface Typeface to set the typeface of the preview & the index bar
     */
    public void setTypeface(Typeface typeface) {
        setTypeface = typeface;
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    public void setIndexBarVisibility(boolean shown) {
        setIndexBarVisibility = shown;
    }


    /**
     * @param shown boolean to show or hide the index bar
     */
    public void setIndexBarStrokeVisibility(boolean shown) {
        setIndexBarStrokeVisibility = shown;
    }

    /**
     * @param shown boolean to show or hide the preview box
     */
    public void setPreviewVisibility(boolean shown) {
        previewVisibility = shown;
    }

    /**
     * @param value int to set the text size of the preview box
     */
    public void setIndexBarStrokeWidth(int value) {
        mIndexBarStrokeWidth = value;
    }


    /**
     * @param value int to set the text size of the preview box
     */
    public void setPreviewTextSize(int value) {
        setPreviewTextSize = value;
    }

    /**
     * @param color The color for the preview box
     */
    public void setPreviewColor(@ColorInt int color) {
        previewBackgroundColor = color;
    }

    /**
     * @param color The text color for the preview box
     */
    public void setPreviewTextColor(@ColorInt int color) {
        previewTextColor = color;
    }

    /**
     * @param value float to set the transparency value of the preview box
     */
    public void setPreviewTransparentValue(float value) {
        previewBackgroudAlpha = convertTransparentValueToBackgroundAlpha(value);
    }

    /**
     * @param color The color for the scroll track
     */
    public void setIndexBarColor(@ColorInt int color) {
        indexbarBackgroudColor = color;
    }

    /**
     * @param color The text color for the index bar
     */
    public void setIndexBarTextColor(@ColorInt int color) {
        indexbarTextColor = color;
    }

    /**
     * @param color The text color for the index bar
     */
    public void setIndexBarStrokeColor(@ColorInt int color) {
        mIndexBarStrokeColor = color;
    }


    /**
     * @param color The text color for the index bar
     */
    public void setIndexbarHighLightTextColor(@ColorInt int color) {
        indexbarHighLightTextColor = color;
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    public void setIndexBarHighLightTextVisibility(boolean shown) {
        setSetIndexBarHighLightTextVisibility = shown;
    }

}
