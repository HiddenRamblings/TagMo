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

package com.wajahatkarim3.easyflipviewpager;

import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * A card based page flip animation PageTransformer implementation for ViewPager2
 *
 * Set the object of this transformer to any ViewPager2 object.
 * For example, myViewPager.setPageTransformer(true, new CardFlipPageTransformer());
 *
 * @see <a href="http://github.com/wajahatkarim3/EasyFlipViewPager">EasyFlipViewPager</a>
 *
 * @author Wajahat Karim (http://wajahatkarim.com)
 */
public class CardFlipPageTransformer2 implements ViewPager2.PageTransformer
{
    private boolean scalable = false;

    @Override
    public void transformPage(View page, float position) {
        float percentage = 1 - Math.abs(position);
        page.setCameraDistance(30000);
        setVisibility(page, position);
        setTranslation(page);
        setSize(page, position, percentage);
        setRotation(page, position, percentage);
    }

    private void setVisibility(View page, float position) {
        if (position < 0.5 && position > -0.5) {
            page.setVisibility(View.VISIBLE);
        } else {
            page.setVisibility(View.INVISIBLE);
        }
    }

    private void setTranslation(View page) {
        ViewPager2 viewPager = requireViewPager(page);
        if (viewPager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL) {
            int scroll = viewPager.getScrollX() - page.getLeft();
            page.setTranslationX(scroll);
        } else {
            int scroll = viewPager.getScrollY() - page.getTop();
            page.setTranslationY(scroll);
        }
    }

    private void setSize(View page, float position, float percentage) {
        // Do nothing, if its not scalable
        if (!scalable) return;

        page.setScaleX((position != 0 && position != 1) ? percentage : 1);
        page.setScaleY((position != 0 && position != 1) ? percentage : 1);
    }

    private void setRotation(View page, float position, float percentage) {
        ViewPager2 viewPager = requireViewPager(page);
        if (viewPager.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL) {
            if (position > 0)
                page.setRotationY(-180 * (percentage + 1));
            else
                page.setRotationY(180 * (percentage + 1));
        } else {
            if (position > 0)
                page.setRotationX(-180 * (percentage + 1));
            else
                page.setRotationX(180 * (percentage + 1));
        }
    }

    private ViewPager2 requireViewPager(@NonNull View page) {
        ViewParent parent = page.getParent();
        ViewParent parentParent = parent.getParent();

        if (parent instanceof RecyclerView && parentParent instanceof ViewPager2)
            return (ViewPager2) parentParent;

        throw new IllegalStateException(
                "Expected page view to be managed by a ViewPager2 instance."
        );
    }

    @SuppressWarnings("unused")
    public boolean isScalable() {
        return scalable;
    }

    public void setScalable(boolean scalable) {
        this.scalable = scalable;
    }
}
