package eightbitlab.com.blurview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Leverages the new RenderEffect.createBlurEffect API to perform blur.
 * Hardware accelerated.
 * Blur is performed on a separate thread - native RenderThread.
 * It doesn't block the Main thread, however it can still cause an FPS drop,
 * because it's just in a different part of the rendering pipeline.
 */
@RequiresApi(Build.VERSION_CODES.S)
class RenderEffectBlur : BlurAlgorithm {
    private val node = RenderNode("BlurViewNode")
    private var height = 0
    private var width = 0
    private var lastBlurRadius = 1f
    var fallbackAlgorithm: BlurAlgorithm? = null
    private var context: Context? = null
    override fun blur(bitmap: Bitmap?, blurRadius: Float): Bitmap {
        lastBlurRadius = blurRadius
        if (bitmap!!.height != height || bitmap.width != width) {
            height = bitmap.height
            width = bitmap.width
            node.setPosition(0, 0, width, height)
        }
        val canvas: Canvas = node.beginRecording()
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        node.endRecording()
        node.setRenderEffect(
            RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                Shader.TileMode.MIRROR
            )
        )
        // returning not blurred bitmap, because the rendering relies on the RenderNode
        return bitmap
    }

    override fun destroy() {
        node.discardDisplayList()
        fallbackAlgorithm?.destroy()
    }

    override fun canModifyBitmap(): Boolean {
        return true
    }

    override val supportedBitmapConfig: Bitmap.Config
        get() = Bitmap.Config.ARGB_8888

    override fun scaleFactor(): Float {
        return BlurController.DEFAULT_SCALE_FACTOR
    }

    override fun render(canvas: Canvas, bitmap: Bitmap) {
        if (canvas.isHardwareAccelerated) {
            canvas.drawRenderNode(node)
        } else {
            if (fallbackAlgorithm == null) {
                fallbackAlgorithm = RenderScriptBlur(context!!)
            }
            fallbackAlgorithm!!.blur(bitmap, lastBlurRadius)
            fallbackAlgorithm!!.render(canvas, bitmap)
        }
    }

    fun setContext(context: Context) {
        this.context = context
    }
}