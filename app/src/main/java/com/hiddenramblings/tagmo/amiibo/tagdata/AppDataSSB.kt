package com.hiddenramblings.tagmo.amiibo.tagdata

class AppDataSSB(appData: ByteArray?) : AppData(appData!!) {
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
    fun checkStat(value: Int) {
        if (value < STATS_MIN_VALUE || value > STATS_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var statAttack: Int
        get() {
            val value: Int = appData.getShort(STATS_ATTACK_OFFSET).toInt() and 0xFFFF
            checkStat(value)
            return value
        }
        set(value) {
            checkStat(value)
            appData.putShort(STATS_ATTACK_OFFSET, value.toShort())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var statDefense: Int
        get() {
            val value: Int = appData.getShort(STATS_DEFENSE_OFFSET).toInt() and 0xFFFF
            checkStat(value)
            return value
        }
        set(value) {
            checkStat(value)
            appData.putShort(STATS_DEFENSE_OFFSET, value.toShort())
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
            val value: Int = appData.getShort(EXPERIENCE_OFFSET).toInt() and 0xFFFF
            checkExperience(value)
            return value
        }
        set(value) {
            checkExperience(value)
            appData.putShort(EXPERIENCE_OFFSET, value.toShort())
        }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var level: Int
        get() {
            val value = experience
            for (i in LEVEL_THRESHOLDS.indices.reversed()) {
                if (LEVEL_THRESHOLDS[i] <= value) return i + 1
            }
            throw NumberFormatException()
        }
        set(level) {
            experience = LEVEL_THRESHOLDS[level - 1]
        }

    companion object {
        const val APPEARANCE_OFFSET = 0x19 // 0x08
        const val APPEARANCE_MIN_VALUE = 0
        const val APPEARANCE_MAX_VALUE = 7
        const val SPECIAL_MIN_VALUE = 0
        const val SPECIAL_MAX_VALUE = 2
        const val SPECIAL_NEUTRAL_OFFSET = 0x1A // 0x09
        const val SPECIAL_SIDE_OFFSET = 0x1B // 0x0A
        const val SPECIAL_UP_OFFSET = 0x1C // 0x0B
        const val SPECIAL_DOWN_OFFSET = 0x1D // 0x0C
        const val STATS_MIN_VALUE = -200
        const val STATS_MAX_VALUE = 200
        const val STATS_ATTACK_OFFSET = 0x21 // 0x10
        const val STATS_DEFENSE_OFFSET = 0x23 // 0x12
        const val STATS_SPEED_OFFSET = 0x25 // 0x14
        const val BONUS_MIN_VALUE = 0
        const val BONUS_MAX_VALUE = 0xFF
        const val BONUS_EFFECT1_OFFSET = 0x1E // 0x0D
        const val BONUS_EFFECT2_OFFSET = 0x1F // 0x0E
        const val BONUS_EFFECT3_OFFSET = 0x20 // 0x0F
        const val LEVEL_MAX_VALUE = 50
        const val EXPERIENCE_MIN_VALUE = 0x0000
        const val EXPERIENCE_MAX_VALUE = 0x093E
        const val EXPERIENCE_OFFSET = 0x8D // 0x7C
        private val LEVEL_THRESHOLDS = intArrayOf(
            0x0000, 0x0008, 0x0010, 0x001D, 0x002D, 0x0048, 0x005B, 0x0075, 0x008D, 0x00AF,
            0x00E1, 0x0103, 0x0126, 0x0149, 0x0172, 0x0196, 0x01BE, 0x01F7, 0x0216, 0x0240,
            0x0278, 0x02A4, 0x02D6, 0x030E, 0x034C, 0x037C, 0x03BB, 0x03F4, 0x042A, 0x0440,
            0x048A, 0x04B6, 0x04E3, 0x053F, 0x056D, 0x059C, 0x0606, 0x0641, 0x0670, 0x069E,
            0x06FC, 0x072E, 0x075D, 0x07B9, 0x07E7, 0x0844, 0x0875, 0x08D3, 0x0902, 0x093E
        )
    }
}