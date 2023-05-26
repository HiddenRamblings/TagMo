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

package com.hiddenramblings.tagmo.eightbit.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.hiddenramblings.tagmo.R


class NumberRecycler : RecyclerView {
    private val Number.toPx get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

    private lateinit var snapHelper: SnapHelper

    private var min: Int = 1
    private var max: Int = 200
    private var initValue: Int = min
    private var fadingEdgeLength: Int = 40.toPx

    private var listener: OnValueChangeListener? = null

    constructor(context: Context) : super(context) {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context, attrs, defStyle
    ) {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int) {
        val attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.NumberRecycler, defStyle, 0
        )
        try {
            max = attributes.getInt(R.styleable.NumberRecycler_nr_max, max)
            min = attributes.getInt(R.styleable.NumberRecycler_nr_min, min)
            initValue = attributes.getInt(R.styleable.NumberRecycler_nr_value, initValue)
            fadingEdgeLength = attributes.getInt(R.styleable.NumberRecycler_nr_fading_edge_length, fadingEdgeLength)
        } finally {
            attributes.recycle()
        }

        setHasFixedSize(true)
        setItemViewCacheSize(10)
        setFadingEdgeLength(fadingEdgeLength)

        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        val intList = arrayListOf<Int>()
        for (i in min until max + 1) {
            intList.add(i)
        }
        adapter = NumberAdapter(intList)

        snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(this)
        val dividerItemDecoration = DividerItemDecoration(
            context, DividerItemDecoration.HORIZONTAL
        )
        dividerItemDecoration.setDrawable(ColorDrawable(Color.WHITE))
        this.addItemDecoration(dividerItemDecoration)

        post { smoothScrollToPosition(initValue - min) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setMin(min: Int) {
        (adapter as NumberAdapter).let {
            it.setMin(min)
            it.notifyDataSetChanged()
        }
    }

    fun setValue(value: Int) {
        post { smoothScrollToPosition(value - min) }
    }

    fun setPosition(position: Int) {
        post { smoothScrollToPosition(position) }
    }

    val value: Int
        get() = snapHelper.findSnapView(layoutManager)?.let {
            this.getChildAdapterPosition(it) + min
        } ?: initValue

    fun setOnValueChangeListener(listener: OnValueChangeListener) {
        this.listener = listener
    }

    fun getValueByPosition(position : Int) : Int {
        return position + min
    }

    fun getPositionByValue(value : Int) : Int {
        return value - min
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setMax(max: Int) {
        (adapter as NumberAdapter).let {
            it.setMax(max)
            it.notifyDataSetChanged()
        }
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        if (state == SCROLL_STATE_IDLE) {
            listener?.onValueChanged(value)
        }
    }

    class NumberAdapter(var data: ArrayList<Int>) : Adapter<NumberAdapter.IntegerViewHolder>() {

        private lateinit var recyclerView: RecyclerView

        override fun getItemCount(): Int {
            return data.size
        }

        override fun getItemId(i: Int): Long {
            return data[i].toLong()
        }

        fun setMin(min: Int) {
            val max = data.size
            data.clear()
            for (i in min until max + 1) {
                data.add(i)
            }
        }

        fun setMax(max: Int) {
            val min = data[0]
            data.clear()
            for (i in min until max + 1) {
                data.add(i)
            }
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            this.recyclerView = recyclerView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntegerViewHolder {
            return NumberViewHolder(parent, recyclerView)
        }

        override fun onBindViewHolder(holder: IntegerViewHolder, position: Int) {
            holder.apply {
                val screen = (recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight) / 3
                itemView.findViewById<TextView>(R.id.number_text).apply {
                    if (screen / 2 > paddingLeft - width) {
                        val paddingTop = paddingTop
                        val paddingBottom = paddingBottom
                        setPadding(screen / 2, paddingTop, screen / 2, paddingBottom)
                    }
                }
            }.bind(holder.bindingAdapterPosition)
        }

        abstract class IntegerViewHolder(itemView: View) : ViewHolder(itemView) {
            private val txtNumber: TextView?

            init {
                txtNumber = itemView.findViewById(R.id.number_text)
            }

            fun bind(item: Int) {
                txtNumber?.text = "$item"
            }
        }

        internal class NumberViewHolder(parent: ViewGroup, recyclerView: RecyclerView) : IntegerViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.number_picker_text, parent, false
            ).apply {
                val screen = (recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight) / 3
                findViewById<TextView>(R.id.number_text).apply {
                    if (screen / 2 > paddingLeft - width) {
                        val paddingTop = paddingTop
                        val paddingBottom = paddingBottom
                        setPadding(screen / 2, paddingTop, screen / 2, paddingBottom)
                    }
                }
            }
        )
    }

    interface OnValueChangeListener {
        fun onValueChanged(value: Int)
    }
}