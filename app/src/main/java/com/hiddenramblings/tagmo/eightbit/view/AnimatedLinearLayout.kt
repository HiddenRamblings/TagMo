/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
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

    private var mAnimationListener: AnimationListener? = null
    fun setAnimationListener(listener: AnimationListener?) {
        mAnimationListener = listener
    }

    override fun onAnimationStart() {
        super.onAnimationStart()
        mAnimationListener?.onAnimationStart(this)
    }

    override fun onAnimationEnd() {
        super.onAnimationEnd()
        mAnimationListener?.onAnimationEnd(this)
    }
}