/*
 * ====================================================================
 * https://stackoverflow.com/a/46373935/461982
 * Copyright (c) 2023 AbandonedCart.  All rights reserved.
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

package com.hiddenramblings.tagmo.eightbit.widget

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup.MarginLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs

class FABulous : FloatingActionButton, OnTouchListener {

    private var downRawX = 0f
    private var downRawY = 0f
    private var dX = 0f
    private var dY = 0f

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!, attrs, defStyleAttr
    ) {
        init()
    }

    private fun init() {
        setOnTouchListener(this)
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        val layoutParams = view.layoutParams as MarginLayoutParams
        val action = motionEvent.action
        return if (action == MotionEvent.ACTION_DOWN) {
            downRawX = motionEvent.rawX
            downRawY = motionEvent.rawY
            dX = view.x - downRawX
            dY = view.y - downRawY
            true // Consumed
        } else if (action == MotionEvent.ACTION_MOVE) {
            val viewWidth = view.width
            val viewHeight = view.height
            val viewParent = view.parent as View
            val parentWidth = viewParent.width
            val parentHeight = viewParent.height
            var newX = motionEvent.rawX + dX
            // Don't allow the FAB past the left hand side of the parent
            newX = layoutParams.leftMargin.toFloat().coerceAtLeast(newX)
            // Don't allow the FAB past the right hand side of the parent
            newX = (parentWidth - viewWidth - layoutParams.rightMargin).toFloat().coerceAtMost(newX)
            var newY = motionEvent.rawY + dY
            // Don't allow the FAB past the top of the parent
            newY = layoutParams.topMargin.toFloat().coerceAtLeast(newY)
            // Don't allow the FAB past the bottom of the parent
            newY = (parentHeight - viewHeight - layoutParams.bottomMargin).toFloat().coerceAtMost(newY)
            view.animate()
                .x(newX)
                .y(newY)
                .setDuration(0)
                .setListener(object : AnimatorListener{
                    override fun onAnimationStart(p0: Animator) { }

                    override fun onAnimationEnd(p0: Animator) {
                        viewMoveListener?.onActionMove(view.x, view.y)
                    }

                    override fun onAnimationCancel(p0: Animator) { }
                    override fun onAnimationRepeat(p0: Animator) { }
                })
                .start()
            true // Consumed
        } else if (action == MotionEvent.ACTION_UP) {
            val upRawX = motionEvent.rawX
            val upRawY = motionEvent.rawY
            val upDX = upRawX - downRawX
            val upDY = upRawY - downRawY
            if (abs(upDX) < CLICK_DRAG_TOLERANCE && abs(upDY) < CLICK_DRAG_TOLERANCE) { // A click
                performClick()
            } else { // Drag
                true // Consumed
            }
        } else {
            super.onTouchEvent(motionEvent)
        }
    }

    interface OnViewMovedListener {
        fun onActionMove(x: Float, y: Float)
    }

    private var viewMoveListener: OnViewMovedListener? = null
    fun setOnMoveListener(listener: OnViewMovedListener?) {
        this.viewMoveListener = listener
    }

    companion object {
        // Account for a possible unintentional drag when FAB is tapped.
        private const val CLICK_DRAG_TOLERANCE = 10f
    }
}
