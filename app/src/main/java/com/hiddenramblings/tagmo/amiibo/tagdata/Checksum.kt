/*
 * ====================================================================
 * SSBU_Amiibo Copyright (c) 2021 odwdinc
 * src/ssbu_amiibo/amiibo_class.py
 * smash-amiibo-editor Copyright (c) 2021 jozz024
 * utils/ssbu_amiibo.py
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.amiibo.tagdata

import java.nio.ByteBuffer

class Checksum {
    private val u0: ByteArray

    init {
        val p0 = -0x12477ce0 or -0x80000000
        u0 = ByteArray(0x100)
        var i = 0x1
        while (i and 0xFF != 0) {
            var t0 = i
            for (x in 0..0x7) {
                val b = t0 and 0x1 != 0
                t0 = t0 shr 0x1
                if (b) t0 = t0 xor p0
            }
            u0[i] = t0.toByte()
            i += 0x1
        }
    }

    fun generate(appData: ByteBuffer): Int {
        val checksum = ByteArray(212) // 0xD4
        System.arraycopy(appData.array(), 0xE0, checksum, 0, checksum.size)
        var t = 0x0
        checksum.forEach {
            t = t shr 0x8 xor u0[it.toInt() xor t and 0xFF].toInt()
        }
        return t xor -0x1
    }
}