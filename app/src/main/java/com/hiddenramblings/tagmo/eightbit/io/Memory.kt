/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
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
package com.hiddenramblings.tagmo.eightbit.io

import android.app.ActivityManager
import android.content.Context
import com.hiddenramblings.tagmo.R
import java.util.Locale

class Memory(val context: Context) {

    private val Long.floatForm: String
        get() = String.format(Locale.ROOT, "%.2f", this.toDouble())

    private fun bytesToSizeUnit(size: Long): String {
        return when {
            size < Kb -> "${size.floatForm} ${context.getString(R.string.memory_byte)}"
            size < Mb -> "${(size / Kb).floatForm} ${context.getString(R.string.memory_kilobyte)}"
            size < Gb -> "${(size / Mb).floatForm} ${context.getString(R.string.memory_megabyte)}"
            size < Tb -> "${(size / Gb).floatForm} ${context.getString(R.string.memory_gigabyte)}"
            size < Pb -> "${(size / Tb).floatForm} ${context.getString(R.string.memory_terabyte)}"
            size < Eb -> "${(size / Pb).floatForm} ${context.getString(R.string.memory_petabyte)}"
            else -> "${(size / Eb).floatForm} ${context.getString(R.string.memory_exabyte)}"
        }
    }

    private val totalMemory =
        with(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager) {
            val memInfo = ActivityManager.MemoryInfo()
            getMemoryInfo(memInfo)
            memInfo.totalMem
        }

    @Suppress("unused")
    fun isLessThan(minimum: Int, size: Long): Boolean {
        return when (size) {
            Kb -> totalMemory < Mb && totalMemory < minimum
            Mb -> totalMemory < Gb && (totalMemory / Mb) < minimum
            Gb -> totalMemory < Tb && (totalMemory / Gb) < minimum
            Tb -> totalMemory < Pb && (totalMemory / Tb) < minimum
            Pb -> totalMemory < Eb && (totalMemory / Pb) < minimum
            Eb -> totalMemory / Eb < minimum
            else -> totalMemory < Kb && totalMemory < minimum
        }
    }

    fun getDeviceRAM(): String {
        return bytesToSizeUnit(totalMemory)
    }

    companion object {
        const val Kb: Long = 1024
        const val Mb = Kb * 1024
        const val Gb = Mb * 1024
        const val Tb = Gb * 1024
        const val Pb = Tb * 1024
        const val Eb = Pb * 1024
    }
}
