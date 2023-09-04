/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.bluetooth

import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.NfcByte

object GattArray {

    fun generateBlank(): ByteArray {
        val blankData = ByteArray(NfcByte.TAG_FILE_SIZE)
        Foomiibo.generateRandomUID().plus(byteArrayOf(
                0x48, 0x00, 0x00, 0xE1.toByte(), 0x10, 0x3E, 0x00, 0x03, 0x00, 0xFE.toByte()
        )).copyInto(blankData, 0x00)
        byteArrayOf(
                0xBD.toByte(), 0x04, 0x00, 0x00, 0xFF.toByte(), 0x00, 0x05
        ).copyInto(blankData, 0x20B)
        return blankData
    }

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