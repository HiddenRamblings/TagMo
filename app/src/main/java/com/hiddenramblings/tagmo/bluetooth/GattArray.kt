/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.bluetooth

object GattArray {
    @JvmStatic
    fun byteToPortions(largeByteArray: ByteArray, sizePerPortion: Int): List<ByteArray> {
        val byteArrayPortions: MutableList<ByteArray> = ArrayList()
        var offset = 0
        while (offset < largeByteArray.size) {
            byteArrayPortions.add(
                if (largeByteArray.size - offset > sizePerPortion)
                    largeByteArray.copyOfRange(offset, offset + sizePerPortion)
                else largeByteArray.copyOfRange(offset, largeByteArray.size)
            )
            offset += sizePerPortion
        }
        return byteArrayPortions
    }

    @JvmStatic
    fun stringToPortions(largeString: String, sizePerPortion: Int): List<String> {
        val stringPortions: MutableList<String> = ArrayList()
        val size = largeString.length
        if (size <= sizePerPortion) {
            stringPortions.add(largeString)
        } else {
            var index = 0
            while (index < size) {
                stringPortions.add(largeString.substring(
                        index, (index + sizePerPortion).coerceAtMost(largeString.length)
                ))
                index += sizePerPortion
            }
        }
        return stringPortions
    }

    @JvmStatic
    fun stringToUnicode(s: String): String {
        val sb = StringBuilder(s.length * 3)
        for (c in s.toCharArray()) {
            if (c.code < 256) {
                sb.append(c)
            } else {
                val strHex = Integer.toHexString(c.code)
                sb.append("\\u").append(strHex)
            }
        }
        return sb.toString()
    }
}