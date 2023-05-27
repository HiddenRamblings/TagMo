package com.hiddenramblings.tagmo.eightbit.request

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hiddenramblings.tagmo.R

object ImageTarget {
    val bitmapHeight by lazy { Resources.getSystem().displayMetrics.heightPixels / 4 }

    fun getTarget(imageAmiibo: AppCompatImageView) : CustomTarget<Bitmap?> {
        return object : CustomTarget<Bitmap?>() {
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo.setImageBitmap(resource)
            }
        }
    }

    fun getTargetR(imageAmiibo: AppCompatImageView) : CustomTarget<Bitmap?> {
        return object : CustomTarget<Bitmap?>() {
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo.maxHeight = bitmapHeight
                imageAmiibo.requestLayout()
                imageAmiibo.setImageBitmap(resource)
            }
        }
    }

    fun getTargetHR(imageAmiibo: AppCompatImageView) : CustomTarget<Bitmap?> {
        return object : CustomTarget<Bitmap?>() {
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo.setImageResource(0)
                imageAmiibo.isGone = true
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo.setImageResource(0)
                imageAmiibo.isGone = true
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo.maxHeight = bitmapHeight
                imageAmiibo.requestLayout()
                imageAmiibo.setImageBitmap(resource)
                imageAmiibo.isVisible = true
            }
        }
    }
}