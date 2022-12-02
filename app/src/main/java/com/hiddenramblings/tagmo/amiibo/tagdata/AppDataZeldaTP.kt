package com.hiddenramblings.tagmo.amiibo.tagdata

class AppDataZeldaTP(appData: ByteArray?) : AppData(appData!!) {
    @Throws(NumberFormatException::class)
    fun checkLevel(value: Int) {
        if (value < LEVEL_MIN_VALUE || value > LEVEL_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var level: Int
        get() {
            val value: Int = appData[LEVEL_OFFSET].toInt() and 0xFF
            checkLevel(value)
            return value
        }
        set(value) {
            checkLevel(value)
            appData.put(LEVEL_OFFSET, value.toByte())
        }

    @Throws(NumberFormatException::class)
    fun checkHearts(value: Int) {
        if (value < HEARTS_MIN_VALUE || value > HEARTS_MAX_VALUE) throw NumberFormatException()
    }

    @get:Throws(NumberFormatException::class)
    @set:Throws(NumberFormatException::class)
    var hearts: Int
        get() {
            val value: Int = appData[HEARTS_OFFSET].toInt() and 0xFF
            checkHearts(value)
            return value
        }
        set(value) {
            checkHearts(value)
            appData.put(HEARTS_OFFSET, value.toByte())
        }

    companion object {
        const val LEVEL_MIN_VALUE = 0
        const val LEVEL_MAX_VALUE = 40
        const val LEVEL_OFFSET = 0x11 // 0x0
        const val HEARTS_MIN_VALUE = 0
        const val HEARTS_MAX_VALUE = 20 * 4
        const val HEARTS_OFFSET = LEVEL_OFFSET + 0x01
    }
}