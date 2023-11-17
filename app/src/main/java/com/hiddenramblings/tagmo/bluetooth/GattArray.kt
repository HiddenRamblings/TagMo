/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.NfcByte

object GattArray {
    fun BluetoothGattCharacteristic.hasProperty(property: Int): Boolean {
        return this.properties and property != 0
    }

    fun BluetoothGatt.getCharacteristicByProperty(property: Int): BluetoothGattCharacteristic? {
       return services.find { service ->
           service.characteristics.any { it.hasProperty(property) }
       }?.characteristics?.find { it.hasProperty(property) }
    }

    fun generateBlank(): ByteArray {
        val blankData = ByteArray(NfcByte.TAG_FULL_SIZE)
        Foomiibo.generateRandomUID().plus(byteArrayOf(
                0x48, 0x00, 0x00, 0xE1.toByte(), 0x10, 0x3E, 0x00, 0x03, 0x00, 0xFE.toByte()
        )).copyInto(blankData, 0x00)
        byteArrayOf(
                0xBD.toByte(), 0x04, 0x00, 0x00, 0xFF.toByte(), 0x00, 0x05
        ).copyInto(blankData, 0x20B)
        return blankData
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