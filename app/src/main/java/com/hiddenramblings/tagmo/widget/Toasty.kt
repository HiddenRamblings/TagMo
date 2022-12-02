package com.hiddenramblings.tagmo.widget

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hiddenramblings.tagmo.R
import java.lang.ref.WeakReference

class Toasty(activity: Activity) {
    private val mActivity: WeakReference<Activity>

    init {
        mActivity = WeakReference(activity)
    }

    private fun show(msgRes: Int, length: Int) {
        mActivity.get()!!.runOnUiThread {
            Toast.makeText(mActivity.get(), msgRes, length).show()
        }
    }

    fun Long(msgRes: Int) {
        show(msgRes, Toast.LENGTH_LONG)
    }

    fun Short(msgRes: Int) {
        show(msgRes, Toast.LENGTH_SHORT)
    }

    private fun show(msg: String, length: Int) {
        mActivity.get()!!.runOnUiThread {
            Toast.makeText(mActivity.get(), msg, length).show()
        }
    }

    fun Long(msg: String) {
        show(msg, Toast.LENGTH_LONG)
    }

    fun Short(msg: String) {
        show(msg, Toast.LENGTH_SHORT)
    }

    fun Dialog(msg: String?) {
        mActivity.get()!!.runOnUiThread {
            AlertDialog.Builder(mActivity.get()!!)
                .setMessage(msg)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }

    fun Dialog(msgRes: Int) {
        mActivity.get()!!.runOnUiThread {
            AlertDialog.Builder(mActivity.get()!!)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, null)
                .show()
        }
    }
}