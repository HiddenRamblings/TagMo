package eightbitlab.com.blurview

import android.graphics.Canvas
import android.graphics.drawable.Drawable

//Used in edit mode and in case if no BlurController was set
internal class NoOpController : BlurController {
    override fun draw(canvas: Canvas): Boolean {
        return true
    }

    override fun updateBlurViewSize() {}
    override fun destroy() {}
    override fun setBlurRadius(radius: Float): BlurViewFacade {
        return this
    }

    override fun setOverlayColor(overlayColor: Int): BlurViewFacade {
        return this
    }

    override fun setFrameClearDrawable(frameClearDrawable: Drawable?): BlurViewFacade {
        return this
    }

    override fun setBlurEnabled(enabled: Boolean): BlurViewFacade {
        return this
    }

    override fun setBlurAutoUpdate(enabled: Boolean): BlurViewFacade {
        return this
    }
}