package eightbitlab.com.blurview

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.os.Version

/**
 * FrameLayout that blurs its underlying content.
 * Can have children and draw them over blurred background.
 */
@Suppress("unused")
class BlurView : FrameLayout {
    private var blurController: BlurController = NoOpController()

    @ColorInt
    private var overlayColor = 0

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
    ) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        init(attrs, defStyleAttr)
    }

    private fun init(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.BlurView, defStyleAttr, 0)
        try {
            overlayColor = a.getColor(
                R.styleable.BlurView_blurOverlayColor,
                PreDrawBlurController.TRANSPARENT
            )
        } finally {
            a.recycle()
        }
        if (Version.isSnowCone) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun draw(canvas: Canvas) {
        val shouldDraw = blurController.draw(canvas)
        if (shouldDraw) {
            super.draw(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        blurController.updateBlurViewSize()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        blurController.setBlurAutoUpdate(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isHardwareAccelerated) {
            Log.e(TAG, "BlurView can't be used in not hardware-accelerated window!")
        } else {
            blurController.setBlurAutoUpdate(true)
        }
    }

    /**
     * @param rootView  root to start blur from.
     * Can be Activity's root content layout (android.R.id.content)
     * or (preferably) some of your layouts. The lower amount of Views are in the root, the better for performance.
     * @param algorithm sets the blur algorithm
     * @return [BlurView] to setup needed params.
     */
    fun setupWith(rootView: ViewGroup, algorithm: BlurAlgorithm): BlurViewFacade {
        blurController.destroy()
        val blurController: BlurController =
            PreDrawBlurController(this, rootView, overlayColor, algorithm)
        this.blurController = blurController
        return blurController
    }

    /**
     * @param rootView root to start blur from.
     * Can be Activity's root content layout (android.R.id.content)
     * or (preferably) some of your layouts. The lower amount of Views are in the root, the better for performance.
     *
     *
     * BlurAlgorithm is automatically picked based on the API version.
     * It uses RenderEffectBlur on API 31+, and RenderScriptBlur on older versions.
     * @return [BlurView] to setup needed params.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun setupWith(rootView: ViewGroup): BlurViewFacade {
        return setupWith(rootView, getBlurAlgorithm())
    }

    // Setters duplicated to be able to conveniently change these settings outside of setupWith chain
    /**
     * @see BlurViewFacade.setBlurRadius
     */
    fun setBlurRadius(radius: Float): BlurViewFacade {
        return blurController.setBlurRadius(radius)
    }

    /**
     * @see BlurViewFacade.setOverlayColor
     */
    fun setOverlayColor(@ColorInt overlayColor: Int): BlurViewFacade {
        this.overlayColor = overlayColor
        return blurController.setOverlayColor(overlayColor)
    }

    /**
     * @see BlurViewFacade.setBlurAutoUpdate
     */
    fun setBlurAutoUpdate(enabled: Boolean): BlurViewFacade {
        return blurController.setBlurAutoUpdate(enabled)
    }

    /**
     * @see BlurViewFacade.setBlurEnabled
     */
    fun setBlurEnabled(enabled: Boolean): BlurViewFacade {
        return blurController.setBlurEnabled(enabled)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun getBlurAlgorithm(): BlurAlgorithm {
        val algorithm: BlurAlgorithm = if (Version.isSnowCone) {
            RenderEffectBlur()
        } else {
            @Suppress("DEPRECATION") RenderScriptBlur(context)
        }
        return algorithm
    }

    companion object {
        private val TAG = BlurView::class.java.simpleName
    }
}