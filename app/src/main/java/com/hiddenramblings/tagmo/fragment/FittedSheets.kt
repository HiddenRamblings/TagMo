package com.hiddenramblings.tagmo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.hiddenramblings.tagmo.R


class FittedSheets: BottomSheetDialogFragment() {

    private val viewList = mutableListOf<View>()
    fun addView(view: View) {
        viewList.add(view)
    }

    var title: String? = null
    fun setTitleText(string: String) {
        title = string
    }

    private var negativeText: String? = null
    private var negativeCallback: (() -> Unit)? = null
    fun setNegativeButton(text: String, callback: (() -> Unit)) {
        negativeText = text
        negativeCallback = callback
    }

    private var positiveText: String? = null
    private var positiveCallback: (() -> Unit)? = null
    fun setPositiveButton(text: String, callback: (() -> Unit)) {
        positiveText = text
        positiveCallback = callback
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fitted_sheets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.bottomSheetsTitle).text = title
        viewList.forEach {
            view.findViewById<LinearLayout>(R.id.bottomSheetsContainer).addView(it)
        }

        if (negativeText != null) view.findViewById<Button>(R.id.bottomSheetsNegative).apply {
            visibility = View.VISIBLE
            text = negativeText
            setOnClickListener {
                negativeCallback?.invoke()
                dismiss()
            }
        }

        if (positiveText != null) view.findViewById<Button>(R.id.bottomSheetsPositive).apply {
            visibility = View.VISIBLE
            text = positiveText
            setOnClickListener {
                positiveCallback?.invoke()
                dismiss()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun newInstance() = FittedSheets()
    }

}