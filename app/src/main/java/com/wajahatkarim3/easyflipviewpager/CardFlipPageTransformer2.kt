/*
Copyright 2020 Wajahat Karim
Copyright 2022 AbandonedCart

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.wajahatkarim3.easyflipviewpager

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs

/**
 * A card based page flip animation PageTransformer implementation for ViewPager2
 *
 * Set the object of this transformer to any ViewPager2 object.
 * For example, myViewPager.setPageTransformer(true, new CardFlipPageTransformer());
 *
 * @see [CardFlipPageTransformer2](http://github.com/wajahatkarim3/EasyFlipViewPager)
 *
 *
 * @author Wajahat Karim (http://wajahatkarim.com)
 */
class CardFlipPageTransformer2 : ViewPager2.PageTransformer {
    var isScalable = false
    override fun transformPage(page: View, position: Float) {
        val percentage = 1 - abs(position)
        page.cameraDistance = 30000f
        setVisibility(page, position)
        setTranslation(page)
        setSize(page, position, percentage)
        setRotation(page, position, percentage)
    }

    private fun setVisibility(page: View, position: Float) {
        if (position < 0.5 && position > -0.5) {
            page.visibility = View.VISIBLE
        } else {
            page.visibility = View.INVISIBLE
        }
    }

    private fun setTranslation(page: View) {
        val viewPager = requireViewPager(page)
        if (viewPager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
            val scroll = viewPager.scrollX - page.left
            page.translationX = scroll.toFloat()
        } else {
            val scroll = viewPager.scrollY - page.top
            page.translationY = scroll.toFloat()
        }
    }

    private fun setSize(page: View, position: Float, percentage: Float) {
        // Do nothing, if its not scalable
        if (!isScalable) return
        val scale: Float = if (position != 0f && position != 1f) percentage else 1f
        page.scaleX = scale
        page.scaleY = scale
    }

    private fun setRotation(page: View, position: Float, percentage: Float) {
        val viewPager = requireViewPager(page)
        if (viewPager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
            if (position > 0) page.rotationY = -180 * (percentage + 1) else page.rotationY =
                180 * (percentage + 1)
        } else {
            if (position > 0) page.rotationX = -180 * (percentage + 1) else page.rotationX =
                180 * (percentage + 1)
        }
    }

    private fun requireViewPager(page: View): ViewPager2 {
        val parent = page.parent
        val parentParent = parent.parent
        if (parent is RecyclerView && parentParent is ViewPager2) return parentParent
        throw IllegalStateException(
            "Expected page view to be managed by a ViewPager2 instance."
        )
    }
}