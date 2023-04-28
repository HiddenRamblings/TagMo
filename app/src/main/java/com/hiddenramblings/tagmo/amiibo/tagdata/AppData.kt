package com.hiddenramblings.tagmo.amiibo.tagdata

import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import java.io.IOException
import java.nio.ByteBuffer

open class AppData(appData: ByteArray) {
    var appData: ByteBuffer
    val array: ByteArray
        get() = appData.array()

    init {
        if (appData.size < APP_FILE_SIZE)
            throw IOException(TagMo.appContext.getString(R.string.invalid_app_data))
        this.appData = ByteBuffer.wrap(appData)
    }

    companion object {
        private const val APP_FILE_SIZE = 0xD8

        var transferId = 0
        var transferData: ByteArray? = null

        fun putInverseShort(appData: ByteBuffer, offset: Int, input: Int) {
            appData.put(offset, (input and 0xff).toByte())
            appData.put(offset + 1, (input shr 8 and 0xff).toByte())
        }

        fun getInverseShort(appData: ByteBuffer, offset: Int): Short {
            return ((appData[offset + 1].toInt() and 0xFF) shl 8
                    or (appData[offset].toInt() and 0xFF) and 0xFF).toShort()
        }
    }
}