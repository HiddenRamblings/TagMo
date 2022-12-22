package eightbitlab.com.blurview

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

interface BlurViewFacade {
    /**
     * Enables/disables the blur. Enabled by default
     *
     * @param enabled true to enable, false otherwise
     * @return [BlurViewFacade]
     */
    fun setBlurEnabled(enabled: Boolean): BlurViewFacade

    /**
     * Can be used to stop blur auto update or resume if it was stopped before.
     * Enabled by default.
     *
     * @return [BlurViewFacade]
     */
    fun setBlurAutoUpdate(enabled: Boolean): BlurViewFacade

    /**
     * @param frameClearDrawable sets the drawable to draw before view hierarchy.
     * Can be used to draw Activity's window background if your root layout doesn't provide any background
     * Optional, by default frame is cleared with a transparent color.
     * @return [BlurViewFacade]
     */
    fun setFrameClearDrawable(frameClearDrawable: Drawable?): BlurViewFacade

    /**
     * @param radius sets the blur radius
     * Default value is [BlurController.DEFAULT_BLUR_RADIUS]
     * @return [BlurViewFacade]
     */
    fun setBlurRadius(radius: Float): BlurViewFacade

    /**
     * Sets the color overlay to be drawn on top of blurred content
     *
     * @param overlayColor int color
     * @return [BlurViewFacade]
     */
    fun setOverlayColor(@ColorInt overlayColor: Int): BlurViewFacade
}