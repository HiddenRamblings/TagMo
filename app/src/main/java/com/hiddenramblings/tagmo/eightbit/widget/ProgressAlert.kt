/*
 * ====================================================================
 * https://stackoverflow.com/a/49272722/461982
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

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.hiddenramblings.tagmo.R
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
            val builder = AlertDialog.Builder(context)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_progressalert, null).apply {
                keepScreenOn = true
            }
            messageText = SoftReference(view.findViewById<TextView>(R.id.process_text).apply {
                text = message
            })
            builder.setView(view)
            dialog = builder.create().also {
                it.show()
                it.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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