/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.bluetooth

import com.hiddenramblings.tagmo.nfctech.NfcByte
import java.util.Date

object GattArray {

    fun ByteArray.toDataBytes(): ByteArray {
        return this.copyOf(NfcByte.TAG_DATA_SIZE + 8)
    }

    fun ByteArray.toFileBytes(): ByteArray {
        return this.copyOf(NfcByte.TAG_FILE_SIZE)
    }

    @JvmStatic
    fun ByteArray.toPortions(sizePerPortion: Int): ArrayList<ByteArray> {
        val byteArrayPortions: ArrayList<ByteArray> = arrayListOf()
        this.asIterable().chunked(sizePerPortion).forEach {
            byteArrayPortions.add(it.toByteArray())
        }
        return byteArrayPortions
    }

    @JvmStatic
    fun String.toPortions(sizePerPortion: Int): ArrayList<String> {
        val stringPortions: ArrayList<String> = arrayListOf()
        val size = this.length
        if (size <= sizePerPortion) {
            stringPortions.add(this)
        } else {
            var index = 0
            while (index < size) {
                stringPortions.add(this.substring(index,
                    (index + sizePerPortion).coerceAtMost(this.length)
                ))
                index += sizePerPortion
            }
        }
        return stringPortions
    }

    @JvmStatic
    fun String.toUnicode(): String {
        val sb = StringBuilder(this.length * 3)
        for (c in this.toCharArray()) {
            if (c.code < 256) {
                sb.append(c)
            } else {
                val strHex = Integer.toHexString(c.code)
                sb.append("\\u").append(strHex)
            }
        }
        return sb.toString()
    }

    fun String.toMilliseconds(): Long {
        return this.toLong() / 10
    }
}