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

import com.hiddenramblings.tagmo.nfctech.TagArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AppDataSSBU(appData: ByteArray?) : AppData(appData!!) { // 0xE2 - 0D // 0xE3 - 01

    private val checksum: ByteArray

    init {
        var p0 = 0xEDB88320 or 0x80000000
        p0 = p0 shr 0
        checksum = ByteArray(0x100)
        var i = 0x1
        while (i and 0xFF != 0) {
            var t0 = i
            for (x in 0..0x8) {
                val b = (t0 and 0x1) shr 0
                t0 = (t0 shr 0x1) shr 0
                if (b == 0x1) t0 = (t0 xor p0.toInt()) shr 0
            }
            checksum[i] = (t0 shr 0).toByte()
            i += 0x1
        }
    }

    @Throws(NumberFormatException::class)
    fun checkAppearence(value: Int) {
        if (value < APPEARANCE_MIN_VALUE || value > APPEARANCE_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var appearence: Int
        get() {
            val value: Int = appData[APPEARANCE_OFFSET].toInt() and 0xFF
            checkAppearence(value)
            return value
        }
        set(value) {
            checkAppearence(value)
            appData.put(APPEARANCE_OFFSET, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    val giftCount: Int
        get() = getInverseShort(appData, GIFT_COUNT_OFFSET).toInt()

    @Throws(NumberFormatException::class)
    fun checkSpecial(value: Int) {
        if (value < SPECIAL_MIN_VALUE || value > SPECIAL_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var specialNeutral: Int
        get() {
            val value: Int = appData[SPECIAL_NEUTRAL_OFFSET].toInt() and 0xFF
            checkSpecial(value)
            return value
        }
        set(value) {
            checkSpecial(value)
            appData.put(SPECIAL_NEUTRAL_OFFSET, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var specialSide: Int
        get() {
            val value: Int = appData[SPECIAL_SIDE_OFFSET].toInt() and 0xFF
            checkSpecial(value)
            return value
        }
        set(value) {
            checkSpecial(value)
            appData.put(SPECIAL_SIDE_OFFSET, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var specialUp: Int
        get() {
            val value: Int = appData[SPECIAL_UP_OFFSET].toInt() and 0xFF
            checkSpecial(value)
            return value
        }
        set(value) {
            checkSpecial(value)
            appData.put(SPECIAL_UP_OFFSET, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var specialDown: Int
        get() {
            val value: Int = appData[SPECIAL_DOWN_OFFSET].toInt() and 0xFF
            checkSpecial(value)
            return value
        }
        set(value) {
            checkSpecial(value)
            appData.put(SPECIAL_DOWN_OFFSET, value.toByte())
        }

    @Throws(NumberFormatException::class)
    fun checkPhysicalStats(value: Int) {
        if (value < PHYSICAL_MIN_VALUE || value > PHYSICAL_MAX_VALUE) throw NumberFormatException()
    }

    @Throws(NumberFormatException::class)
    fun checkStat(value: Int) {
        if (value < STATS_MIN_VALUE || value > STATS_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var statAttack: Int
        get() {
            val value = getInverseShort(appData, STATS_ATTACK_OFFSET)
            checkPhysicalStats(value.toInt())
            return value.toInt()
        }
        set(value) {
            checkPhysicalStats(value)
            putInverseShort(appData, STATS_ATTACK_OFFSET, value)
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var statDefense: Int
        get() {
            val value = getInverseShort(appData, STATS_DEFENSE_OFFSET)
            checkPhysicalStats(value.toInt())
            return value.toInt()
        }
        set(value) {
            checkPhysicalStats(value)
            putInverseShort(appData, STATS_DEFENSE_OFFSET, value)
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var statSpeed: Int
        get() {
            val value: Int = appData.getShort(STATS_SPEED_OFFSET).toInt() and 0xFFFF
            checkStat(value)
            return value
        }
        set(value) {
            checkStat(value)
            appData.putShort(STATS_SPEED_OFFSET, value.toShort())
        }

    @Throws(NumberFormatException::class)
    fun checkBonus(value: Int) {
        if (value < BONUS_MIN_VALUE || value > BONUS_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var bonusEffect1: Int
        get() {
            val value: Int = appData[BONUS_EFFECT1_OFFSET].toInt() and 0xFF
            checkBonus(value)
            return value
        }
        set(value) {
            checkBonus(value)
            appData.put(BONUS_EFFECT1_OFFSET, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var bonusEffect2: Int
        get() {
            val value: Int = appData[BONUS_EFFECT2_OFFSET].toInt() and 0xFF
            checkBonus(value)
            return value
        }
        set(value) {
            checkBonus(value)
            appData.put(BONUS_EFFECT2_OFFSET, value.toByte())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var bonusEffect3: Int
        get() {
            val value: Int = appData[BONUS_EFFECT3_OFFSET].toInt() and 0xFF
            checkBonus(value)
            return value
        }
        set(value) {
            checkBonus(value)
            appData.put(BONUS_EFFECT3_OFFSET, value.toByte())
        }

    @Throws(NumberFormatException::class)
    fun checkExperience(value: Int) {
        if (value < EXPERIENCE_MIN_VALUE || value > EXPERIENCE_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var experience: Int
        get() {
            val value = getInverseShort(appData, EXPERIENCE_OFFSET)
            checkExperience(value.toInt())
            return value.toInt()
        }
        set(value) {
            checkExperience(value)
            putInverseShort(appData, EXPERIENCE_OFFSET, value)
        }

    @get:Throws(NumberFormatException::class)
    val experienceCPU: Int
        get() = getInverseShort(appData, EXPERIENCE_OFFSET_CPU).toInt()

    @Throws(NumberFormatException::class)
    fun experienceToLevel(experience: Int, threshholds: IntArray): Int {
        for (i in threshholds.indices.reversed()) {
            if (threshholds[i] <= experience) return i + 1
        }
        throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var level: Int
        get() = experienceToLevel(experience, LEVEL_THRESHOLDS)
        set(level) {
            experience = LEVEL_THRESHOLDS[level - 1]
        }

    @get:Throws(NumberFormatException::class)
    val levelCPU: Int
        get() = experienceToLevel(experienceCPU, LEVEL_THRESHOLDS_CPU)

    fun withChecksum(amiiboData: ByteArray): ByteBuffer {
        var t = 0xFFFFFFFF.toInt()
        amiiboData.copyOfRange(0xE0, 0xE0 + 0xD4).forEach {// 0xD4
            t = (t shr 0x8) xor checksum[(it.toInt() xor t) and 0xFF].toInt()
        }
        val crc32 = ByteBuffer.allocate(4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt((t xor 0xFFFFFFFF.toInt()) shr 0)
        }.array()
        appData.put(crc32, GAME_CRC32_OFFSET , crc32.size)

        var crc16 = 0
        amiiboData.copyOfRange(0xA0, 0xFE).forEach {
            crc16 = crc16 xor (Integer.reverseBytes(it.toInt()) shl 8)
            for (x in 0..0x8) {
                crc16 = crc16 shl 1
                if ((crc16 and 0x10000) > 0) crc16 = crc16 xor 0x1021
            }
        }
        val crcHex = TagArray.hexToByteArray(String.format("%04X", crc16 and 0xFFFF))
        appData.put(crcHex, GAME_CRC16_OFFSET, crcHex.size)
        return appData
    }

    companion object {
        const val GAME_CRC32_OFFSET = 0x0
        const val GAME_CRC16_OFFSET = 0x22
        const val APPEARANCE_OFFSET = 0xC7
        const val APPEARANCE_MIN_VALUE = 0
        const val APPEARANCE_MAX_VALUE = 7
        const val SPECIAL_MIN_VALUE = 0
        const val SPECIAL_MAX_VALUE = 2
        const val SPECIAL_NEUTRAL_OFFSET = 0x09
        const val SPECIAL_SIDE_OFFSET = 0x0A
        const val SPECIAL_UP_OFFSET = 0x0B
        const val SPECIAL_DOWN_OFFSET = 0x0C
        const val STATS_MIN_VALUE = -200
        const val STATS_MAX_VALUE = 200
        const val PHYSICAL_MIN_VALUE = -2500
        const val PHYSICAL_MAX_VALUE = 2500
        const val STATS_ATTACK_OFFSET = 0x74
        const val STATS_DEFENSE_OFFSET = 0x76
        const val STATS_SPEED_OFFSET = 0x14
        const val BONUS_MIN_VALUE = 0
        const val BONUS_MAX_VALUE = 0xFF
        const val BONUS_EFFECT1_OFFSET = 0x0D
        const val BONUS_EFFECT2_OFFSET = 0x0E
        const val BONUS_EFFECT3_OFFSET = 0x0F
        const val LEVEL_MIN_VALUE = 1
        const val EXPERIENCE_MIN_VALUE = 0x0000
        const val EXPERIENCE_MAX_VALUE = 0x0F48
        const val EXPERIENCE_OFFSET = 0x70
        const val EXPERIENCE_OFFSET_CPU = 0x72
        const val GIFT_COUNT_OFFSET = 0x7A
        private val LEVEL_THRESHOLDS = intArrayOf(
            0x0000, 0x0008, 0x0016, 0x0029, 0x003F, 0x005A, 0x0078, 0x009B, 0x00C3, 0x00EE,
            0x011C, 0x014A, 0x0178, 0x01AA, 0x01DC, 0x0210, 0x0244, 0x0278, 0x02AC, 0x02E1,
            0x0316, 0x034B, 0x0380, 0x03B6, 0x03EC, 0x0422, 0x0458, 0x048F, 0x04C6, 0x04FD,
            0x053B, 0x057E, 0x05C6, 0x0613, 0x0665, 0x06BC, 0x0718, 0x0776, 0x07DC, 0x0843,
            0x08AC, 0x0919, 0x099B, 0x0A3B, 0x0AEF, 0x0BB7, 0x0C89, 0x0D65, 0x0E55, 0x0F48
        )
        private val LEVEL_THRESHOLDS_CPU = intArrayOf(
            0x0000, 0x003F, 0x00D2, 0x01B2, 0x02ED, 0x0475, 0x0643, 0x0811, 0x0ACD
        )
    }
}