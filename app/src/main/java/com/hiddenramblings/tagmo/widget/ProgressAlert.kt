package com.hiddenramblings.tagmo.widget

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import java.lang.ref.SoftReference

object ProgressAlert {
    private val Number.toPx get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt()

    private var dialog: AlertDialog? = null
    private var messageText: SoftReference<TextView>? = null

    fun show(context: Context, message: String) : ProgressAlert {
        if (dialog == null) {
            val llvPadding = 10.toPx
            val llhPadding = 8.toPx
            val ll = LinearLayout(context)
            ll.orientation = LinearLayout.HORIZONTAL
            ll.setPadding(llhPadding, llvPadding, llhPadding, llvPadding)
            ll.gravity = Gravity.CENTER
            var llParam = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            llParam.gravity = Gravity.CENTER
            ll.layoutParams = llParam
            val progressBar = ProgressBar(context)
            progressBar.isIndeterminate = true
            progressBar.setPadding(0, 0, 12.toPx, 0)
            progressBar.layoutParams = llParam
            llParam = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            llParam.gravity = Gravity.CENTER
            ll.addView(progressBar)
            messageText = SoftReference(TextView(context))
            messageText?.get()?.let { textView ->
                textView.text = message
                textView.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                textView.textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 8f, Resources.getSystem().displayMetrics
                )
                textView.layoutParams = llParam
            ll.addView(textView)
            }
            val builder = AlertDialog.Builder(context)
            builder.setCancelable(true)
            builder.setView(ll)
            dialog = builder.create().apply {
                show()
                val window = window
                if (window != null) {
                    val layoutParams = WindowManager.LayoutParams()
                    layoutParams.copyFrom(window.attributes)
                    layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
                    layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                    window.attributes = layoutParams
                }
            }
        }
        return this
    }

    fun setMessage(message: String?) {
        messageText?.get()?.let { textView -> textView.text = message }
    }

    @Suppress("unused")
    val isShowing: Boolean
        get() = dialog?.isShowing ?: false

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}