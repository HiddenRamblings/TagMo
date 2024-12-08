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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.hiddenramblings.tagmo.R
import java.lang.ref.SoftReference

object ProgressAlert {
    private var dialog: AlertDialog? = null
    private var messageText: SoftReference<TextView>? = null

    fun show(context: Context, message: String, imageUrk: String? = null) : ProgressAlert {
        if (dialog == null) {
            val builder = AlertDialog.Builder(context)
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_progressalert, null).apply {
                keepScreenOn = true
            }
            messageText = SoftReference(view.findViewById<TextView>(R.id.process_text).apply {
                text = message
            })
            view.findViewById<ImageView>(R.id.process_image)?.apply {
                isVisible = imageUrk != null
                imageUrk?.let {
                    Glide.with(this).clear(this)
                    Glide.with(this).load(it).into(this)
                }

            }
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
        get() = dialog?.isShowing == true

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}