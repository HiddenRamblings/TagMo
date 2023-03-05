package com.hiddenramblings.tagmo.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hiddenramblings.tagmo.R


class Toasty(context: Context) {
    private val mContext: Context

    init {
        mContext = context
    }

    private fun show(msgRes: Int, length: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(mContext, msgRes, length).show()
        }
    }

    fun Long(msgRes: Int) {
        show(msgRes, Toast.LENGTH_LONG)
    }

    fun Short(msgRes: Int) {
        show(msgRes, Toast.LENGTH_SHORT)
    }

    private fun show(msg: String, length: Int) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(mContext, msg, length).show()
        }
    }

    fun Long(msg: String) {
        show(msg, Toast.LENGTH_LONG)
    }

    fun Short(msg: String) {
        show(msg, Toast.LENGTH_SHORT)
    }

    fun Dialog(msg: String?) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(mContext)
                .setMessage(msg)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }

    fun Dialog(msgRes: Int) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(mContext)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }
}