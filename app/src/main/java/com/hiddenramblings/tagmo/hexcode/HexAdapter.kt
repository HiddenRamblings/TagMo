package com.hiddenramblings.tagmo.hexcode

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.R

class HexAdapter(tagData: ByteArray) : RecyclerView.Adapter<HexAdapter.ViewHolder>() {
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
                        String.format(
                            "%02X",
                            columnIndex
                        ), Color.TRANSPARENT
                    )
                } else if (columnIndex == -1) {
                    hexItem = HexHeader(
                        String.format(
                            "%04X",
                            rowIndex * HEX
                        ), Color.TRANSPARENT
                    )
                } else {
                    val index = rowIndex * HEX + columnIndex
                    if (index >= tagData.size) {
                        hexItem = null
                    } else {
                        val text = String.format(
                            "%02X",
                            java.lang.Byte.valueOf(tagData[index]).toInt() and 0xFF
                        )
                        var color = Color.WHITE
                        for (t in TagMap.getTagMap) {
                            if (t.index <= index) {
                                color = t.color
                                break
                            }
                        }
                        hexItem = HexItem(text, color)
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

    fun getItem(i: Int): Array<HexItem?> {
        return data[i]
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
        for (i in holder.textView.indices) {
            val hexItem = row[i]
            val view = holder.textView[i]
            if (hexItem == null) {
                view!!.setTextColor(Color.TRANSPARENT)
                view.setBackgroundColor(Color.TRANSPARENT)
            } else {
                view!!.text = hexItem.text
                if (hexItem !is HexHeader) view.setTextColor(Color.BLACK)
                view.setTypeface(Typeface.MONOSPACE, hexItem.textStyle)
                view.setBackgroundColor(hexItem.backgroundColor)
                view.isVisible = true
            }
        }
    }

    class ViewHolder internal constructor(var view: View) : RecyclerView.ViewHolder(
        view
    ) {
        var textView = arrayOfNulls<TextView>(16 + 1)

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