package com.hiddenramblings.tagmo.hexcode

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.R


class HexAdapter(tagData: ByteArray) : RecyclerView.Adapter<HexAdapter.ViewHolder>() {
    private var recyclerView: RecyclerView? = null
    private val data: Array<Array<HexItem?>>

    init {
        setHasStableIds(true)
        val rowCount = (tagData.size - 1) / HEX + 2
        data = Array(rowCount) { arrayOfNulls(HEX + 1) }
        for (rowIndex in -1 until data.size - 1) {
            val row = data[rowIndex + 1]
            for (columnIndex in -1 until row.size - 1) {
                var hexItem: HexItem?
                if (rowIndex == -1 && columnIndex == -1) {
                    hexItem = null
                } else if (rowIndex == -1) {
                    hexItem = HexHeader(
                        String.format("%02X", columnIndex), Color.TRANSPARENT
                    )
                } else if (columnIndex == -1) {
                    hexItem = HexHeader(
                        String.format("%04X", rowIndex * HEX), Color.TRANSPARENT
                    )
                } else {
                    val index = rowIndex * HEX + columnIndex
                    hexItem = if (index >= tagData.size) {
                        null
                    } else {
                        val text = String.format(
                            "%02X", java.lang.Byte.valueOf(tagData[index]).toInt() and 0xFF
                        )
                        val color = TagMap.getTagMap.find {
                            it.index <= index
                        }?.color ?: Color.WHITE
                        HexItem(text, color)
                    }
                }
                row[columnIndex + 1] = hexItem
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    private fun getItem(i: Int): Array<HexItem?> {
        return data[i]
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.hexcode_line, parent, false)
        )
    }

    @SuppressLint("WrongConstant")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        holder.textView.forEachIndexed { i, view ->
            view?.let {
                val hexItem = row[i]
                if (hexItem == null) {
                    it.isEnabled = false
                    it.setTextColor(Color.TRANSPARENT)
                    it.setBackgroundColor(Color.TRANSPARENT)
                } else {
                    it.setText(hexItem.text)
                    if (hexItem is HexHeader) {
                        it.isEnabled = false
                        it.isLongClickable = false
                    } else {
                        it.setTextColor(Color.BLACK)
                        it.addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence, start: Int, count: Int, after: Int
                            ) { }

                            override fun onTextChanged(
                                s: CharSequence, start: Int, before: Int, count: Int
                            ) {
                                hexItem.text = s.toString()
                                data[holder.absoluteAdapterPosition][i] = hexItem
                            }

                            override fun afterTextChanged(s: Editable?) { }
                        })
                    }
                    it.setTypeface(Typeface.MONOSPACE, hexItem.textStyle)
                    it.setBackgroundColor(hexItem.backgroundColor)
                    it.isVisible = true
                }
            }
        }
    }

    class ViewHolder internal constructor(var view: View) : RecyclerView.ViewHolder(view) {
        var textView = arrayOfNulls<EditText>(16 + 1)

        init {
            textView[0] = view.findViewById(R.id.textViewRow)
            textView[1] = view.findViewById(R.id.textView1)
            textView[2] = view.findViewById(R.id.textView2)
            textView[3] = view.findViewById(R.id.textView3)
            textView[4] = view.findViewById(R.id.textView4)
            textView[5] = view.findViewById(R.id.textView5)
            textView[6] = view.findViewById(R.id.textView6)
            textView[7] = view.findViewById(R.id.textView7)
            textView[8] = view.findViewById(R.id.textView8)
            textView[9] = view.findViewById(R.id.textView9)
            textView[10] = view.findViewById(R.id.textView10)
            textView[11] = view.findViewById(R.id.textView11)
            textView[12] = view.findViewById(R.id.textView12)
            textView[13] = view.findViewById(R.id.textView13)
            textView[14] = view.findViewById(R.id.textView14)
            textView[15] = view.findViewById(R.id.textView15)
            textView[16] = view.findViewById(R.id.textView16)
        }
    }

    companion object {
        private const val HEX = 16
    }
}