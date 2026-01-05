/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 */
package com.hiddenramblings.tagmo.eightbit.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class AnimatedLinearLayout : LinearLayout {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    )

    interface AnimationListener {
        fun onAnimationStart(layout: AnimatedLinearLayout?)
        fun onAnimationEnd(layout: AnimatedLinearLayout?)
    }

    private var animationListener: AnimationListener? = null
    fun setAnimationListener(listener: AnimationListener?) {
        animationListener = listener
    }

    override fun onAnimationStart() {
        super.onAnimationStart()
        animationListener?.onAnimationStart(this)
    }

    override fun onAnimationEnd() {
        super.onAnimationEnd()
        animationListener?.onAnimationEnd(this)
    }
}