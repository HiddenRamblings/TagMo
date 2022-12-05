package myinnos.indexfastscrollrecycler

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.R

/*
 * Created by MyInnos on 31-01-2017.
 * Updated by AbandonedCart 07-2022.
 */
@Suppress("UNUSED")
class IndexFastScrollRecyclerView : RecyclerView {
    private var mScroller: IndexFastScrollRecyclerSection? = null
    private var mGestureDetector: GestureDetector? = null
    private var mEnabled = true
    private var mTransient = false
    var setIndexTextSize = 12
    var mIndexbarWidth = 20f
    var mIndexbarMarginLeft = 2f
    var mIndexbarMarginRight = 2f
    var mIndexbarMarginTop = 2f
    var mIndexbarMarginBottom = 2f
    var mPreviewPadding = 5
    var mIndexBarCornerRadius = 5
    var mIndexBarTransparentValue = 0.6.toFloat()
    var mIndexBarStrokeWidth = 2

    @ColorInt
    var mSetIndexBarStrokeColor = Color.BLACK

    @ColorInt
    var mIndexbarBackgroudColor = Color.BLACK

    @ColorInt
    var mIndexbarTextColor = Color.WHITE

    @ColorInt
    var indexbarHighlightTextColor = Color.BLACK
    var mPreviewTextSize = 50

    @ColorInt
    var mPreviewBackgroudColor = Color.BLACK

    @ColorInt
    var mPreviewTextColor = Color.WHITE
    var mPreviewTransparentValue = 0.4.toFloat()

    constructor(context: Context?) : super(context!!)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val typedArray =
                context.obtainStyledAttributes(attrs, R.styleable.IndexFastScrollRecyclerView)
            try {
                setIndexTextSize = typedArray.getInt(
                    R.styleable.IndexFastScrollRecyclerView_setIndexTextSize,
                    setIndexTextSize
                )
                mIndexbarWidth = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setIndexbarWidth,
                    mIndexbarWidth
                )
                mIndexbarMarginLeft = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin,
                    mIndexbarMarginLeft
                )
                mIndexbarMarginRight = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin,
                    mIndexbarMarginRight
                )
                mIndexbarMarginTop = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin,
                    mIndexbarMarginTop
                )
                mIndexbarMarginBottom = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setIndexbarMargin,
                    mIndexbarMarginBottom
                )
                mPreviewPadding = typedArray.getInt(
                    R.styleable.IndexFastScrollRecyclerView_setPreviewPadding,
                    mPreviewPadding
                )
                mIndexBarCornerRadius = typedArray.getInt(
                    R.styleable.IndexFastScrollRecyclerView_setIndexBarCornerRadius,
                    mIndexBarCornerRadius
                )
                mIndexBarTransparentValue = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setIndexBarTransparentValue,
                    mIndexBarTransparentValue
                )
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarShown)) {
                    mEnabled = typedArray.getBoolean(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarShown,
                        mEnabled
                    )
                }
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setTransientIndexBar)) {
                    mTransient = typedArray.getBoolean(
                        R.styleable.IndexFastScrollRecyclerView_setTransientIndexBar,
                        mTransient
                    )
                }
                setTransientIndexBar(mTransient)
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor)) {
                    val tv = TypedValue()
                    typedArray.getValue(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                        tv
                    )
                    mIndexbarBackgroudColor = if (tv.type == TypedValue.TYPE_STRING) {
                        Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarColor))
                    } else {
                        typedArray.getColor(
                            R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                            mIndexbarBackgroudColor
                        )
                    }
                }
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarTextColor)) {
                    val tv = TypedValue()
                    typedArray.getValue(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                        tv
                    )
                    mIndexbarTextColor = if (tv.type == TypedValue.TYPE_STRING) {
                        Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarTextColor))
                    } else {
                        typedArray.getColor(
                            R.styleable.IndexFastScrollRecyclerView_setIndexBarTextColor,
                            mIndexbarTextColor
                        )
                    }
                }
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarHighlightTextColor)) {
                    val tv = TypedValue()
                    typedArray.getValue(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                        tv
                    )
                    indexbarHighlightTextColor = if (tv.type == TypedValue.TYPE_STRING) {
                        Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarHighlightTextColor))
                    } else {
                        typedArray.getColor(
                            R.styleable.IndexFastScrollRecyclerView_setIndexBarHighlightTextColor,
                            indexbarHighlightTextColor
                        )
                    }
                }
                mPreviewTextSize = typedArray.getInt(
                    R.styleable.IndexFastScrollRecyclerView_setPreviewTextSize,
                    mPreviewTextSize
                )
                mPreviewTransparentValue = typedArray.getFloat(
                    R.styleable.IndexFastScrollRecyclerView_setPreviewTransparentValue,
                    mPreviewTransparentValue
                )
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setPreviewColor)) {
                    val tv = TypedValue()
                    typedArray.getValue(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                        tv
                    )
                    mPreviewBackgroudColor = if (tv.type == TypedValue.TYPE_STRING) {
                        Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setPreviewColor))
                    } else {
                        typedArray.getColor(
                            R.styleable.IndexFastScrollRecyclerView_setPreviewColor,
                            mPreviewBackgroudColor
                        )
                    }
                }
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setPreviewTextColor)) {
                    val tv = TypedValue()
                    typedArray.getValue(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                        tv
                    )
                    mPreviewTextColor = if (tv.type == TypedValue.TYPE_STRING) {
                        Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setPreviewTextColor))
                    } else {
                        typedArray.getColor(
                            R.styleable.IndexFastScrollRecyclerView_setPreviewTextColor,
                            mPreviewTextColor
                        )
                    }
                }
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeWidth)) {
                    mIndexBarStrokeWidth = typedArray.getInt(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeWidth,
                        mIndexBarStrokeWidth
                    )
                }
                if (typedArray.hasValue(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeColor)) {
                    val tv = TypedValue()
                    typedArray.getValue(
                        R.styleable.IndexFastScrollRecyclerView_setIndexBarColor,
                        tv
                    )
                    mSetIndexBarStrokeColor = if (tv.type == TypedValue.TYPE_STRING) {
                        Color.parseColor(typedArray.getString(R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeColor))
                    } else {
                        typedArray.getColor(
                            R.styleable.IndexFastScrollRecyclerView_setIndexBarStrokeColor,
                            mSetIndexBarStrokeColor
                        )
                    }
                }
            } finally {
                typedArray.recycle()
            }

            // This line here is neccesary else the attributes won't be updated if a value is passed from XML
            mScroller = IndexFastScrollRecyclerSection(context, this)
            mScroller!!.setIndexBarVisibility(mEnabled)
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Overlay index bar
        if (mScroller != null) mScroller!!.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (mEnabled) {
            // Intercept ListView's touch event
            if (mScroller != null && mScroller!!.onTouchEvent(ev)) return true
            if (mGestureDetector == null) {
                mGestureDetector = GestureDetector(context, object : SimpleOnGestureListener() { })
            }
            mGestureDetector!!.onTouchEvent(ev)
        }
        return super.onTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return if (mEnabled && mScroller != null && mScroller!!.contains(
                ev.x,
                ev.y
            )
        ) true else super.onInterceptTouchEvent(ev)
    }

    override fun setAdapter(adapter: Adapter<ViewHolder?>?) {
        super.setAdapter(adapter)
        if (null != mScroller) mScroller!!.setAdapter(adapter)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (mScroller != null) mScroller!!.onSizeChanged(w, h)
    }

    /**
     * @param value int to set the text size of the index bar
     */
    fun setIndexTextSize(value: Int) {
        mScroller!!.setIndexTextSize(value)
    }

    /**
     * @param value float to set the width of the index bar
     */
    fun setIndexbarWidth(value: Float) {
        mScroller!!.setIndexbarWidth(value)
    }

    /**
     * @param value float to set the margin of the index bar
     */
    fun setIndexbarMargin(value: Float) {
        mScroller!!.setIndexbarMargin(value)
    }

    /**
     * @param value float to set the top margin of the index bar
     */
    fun setIndexbarTopMargin(value: Float) {
        mScroller!!.setIndexbarTopMargin(value)
    }

    /**
     * @param value float to set the bottom margin of the index bar
     */
    fun setIndexbarBottomMargin(value: Float) {
        mScroller!!.setIndexbarBottomMargin(value)
    }

    /**
     * @param value float to set the Horizontal margin of the index bar
     */
    fun setIndexbarHorizontalMargin(value: Float) {
        mScroller!!.setIndexbarHorizontalMargin(value)
    }

    /**
     * @param value float to set the Vertical margin of the index bar
     */
    fun setIndexbarVerticalMargin(value: Float) {
        mScroller!!.setIndexbarVerticalMargin(value)
    }

    /**
     * @param value int to set the preview padding
     */
    fun setPreviewPadding(value: Int) {
        mScroller!!.setPreviewPadding(value)
    }

    /**
     * @param value int to set the corner radius of the index bar
     */
    fun setIndexBarCornerRadius(value: Int) {
        mScroller!!.setIndexBarCornerRadius(value)
    }

    /**
     * @param value float to set the transparency value of the index bar
     */
    fun setIndexBarTransparentValue(value: Float) {
        mScroller!!.setIndexBarTransparentValue(value)
    }

    /**
     * @param typeface Typeface to set the typeface of the preview & the index bar
     */
    fun setTypeface(typeface: Typeface?) {
        mScroller!!.setTypeface(typeface)
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    fun setIndexBarVisibility(shown: Boolean) {
        mScroller!!.setIndexBarVisibility(shown)
        mEnabled = shown
    }

    private val scrollRunnable = Runnable {
        mScroller!!.setIndexBarVisibility(false)
        invalidate()
    }
    private val scrollListener: OnScrollListener = object : OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == SCROLL_STATE_DRAGGING) {
                recyclerView.removeCallbacks(scrollRunnable)
                mScroller!!.setIndexBarVisibility(true)
            } else if (newState == SCROLL_STATE_IDLE) {
                recyclerView.postDelayed(scrollRunnable, 1000)
            }
        }
    }

    fun setTransientIndexBar(enabled: Boolean) {
        if (enabled) {
            @Suppress("DEPRECATION")
            setOnScrollListener(scrollListener)
        } else {
            removeCallbacks(scrollRunnable)
            removeOnScrollListener(scrollListener)
        }
        mTransient = enabled
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    fun setIndexBarStrokeVisibility(shown: Boolean) {
        mScroller!!.setIndexBarStrokeVisibility(shown)
    }

    /**
     * @param color The color for the index bar
     */
    fun setIndexBarStrokeColor(color: String?) {
        mScroller!!.setIndexBarStrokeColor(Color.parseColor(color))
    }

    /**
     * @param color The color for the preview box
     */
    fun setIndexBarStrokeColor(@ColorRes color: Int) {
        mScroller!!.setIndexBarStrokeColor(ContextCompat.getColor(context, color))
    }

    /**
     * @param value int to set the text size of the preview box
     */
    fun setIndexBarStrokeWidth(value: Int) {
        mScroller!!.setIndexBarStrokeWidth(value)
    }

    /**
     * @param shown boolean to show or hide the preview
     */
    fun setPreviewVisibility(shown: Boolean) {
        mScroller!!.setPreviewVisibility(shown)
    }

    /**
     * @param value int to set the text size of the preview box
     */
    fun setPreviewTextSize(value: Int) {
        mScroller!!.setPreviewTextSize(value)
    }

    /**
     * @param color The color for the preview box
     */
    fun setPreviewColor(@ColorRes color: Int) {
        mScroller!!.setPreviewColor(ContextCompat.getColor(context, color))
    }

    /**
     * @param color The color for the preview box
     */
    fun setPreviewColor(color: String?) {
        mScroller!!.setPreviewColor(Color.parseColor(color))
    }

    /**
     * @param color The text color for the preview box
     */
    fun setPreviewTextColor(@ColorRes color: Int) {
        mScroller!!.setPreviewTextColor(ContextCompat.getColor(context, color))
    }

    /**
     * @param value float to set the transparency value of the preview box
     */
    fun setPreviewTransparentValue(value: Float) {
        mScroller!!.setPreviewTransparentValue(value)
    }

    /**
     * @param color The text color for the preview box
     */
    fun setPreviewTextColor(color: String?) {
        mScroller!!.setPreviewTextColor(Color.parseColor(color))
    }

    /**
     * @param color The color for the index bar
     */
    fun setIndexBarColor(color: String?) {
        mScroller!!.setIndexBarColor(Color.parseColor(color))
    }

    /**
     * @param color The color for the index bar
     */
    fun setIndexBarColor(@ColorRes color: Int) {
        mScroller!!.setIndexBarColor(ContextCompat.getColor(context, color))
    }

    /**
     * @param color The text color for the index bar
     */
    fun setIndexBarTextColor(color: String?) {
        mScroller!!.setIndexBarTextColor(Color.parseColor(color))
    }

    /**
     * @param color The text color for the index bar
     */
    fun setIndexBarTextColor(@ColorRes color: Int) {
        mScroller!!.setIndexBarTextColor(ContextCompat.getColor(context, color))
    }

    /**
     * @param color The text color for the index bar
     */
    fun setIndexbarHighLightTextColor(color: String?) {
        mScroller!!.setIndexbarHighLightTextColor(Color.parseColor(color))
    }

    /**
     * @param color The text color for the index bar
     */
    fun setIndexbarHighLightTextColor(@ColorRes color: Int) {
        mScroller!!.setIndexbarHighLightTextColor(ContextCompat.getColor(context, color))
    }

    /**
     * @param shown boolean to show or hide the index bar
     */
    fun setIndexBarHighLightTextVisibility(shown: Boolean) {
        mScroller!!.setIndexBarHighLightTextVisibility(shown)
    }

    fun updateSections() {
        mScroller!!.updateSections()
    }
}