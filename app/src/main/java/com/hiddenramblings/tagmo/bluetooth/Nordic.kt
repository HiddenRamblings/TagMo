package com.hiddenramblings.tagmo.bluetooth

import java.util.UUID

object Nordic {
    enum class DEVICE {
        FLASK, SLIDE, BLUUP, LINK, LOOP, PIXL, PUCK, GATT
    }

    val NUS  = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val TX = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val RX = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    val LegacyNUS: UUID = UUID.fromString("78290001-d52e-473f-a9f4-f03da7c67dd1")
    val LegacyTX = UUID.fromString("78290002-d52e-473f-a9f4-f03da7c67dd1")
    val LegacyRX = UUID.fromString("78290003-d52e-473f-a9f4-f03da7c67dd1")

    fun getLogTag(device:String, uuid: UUID): String {
        return when {
            uuid.compareTo(TX) == 0 -> {
                "${device}TX"
            }
            uuid.compareTo(RX) == 0 -> {
                "${device}RX"
            }
            uuid.compareTo(NUS) == 0 -> {
                "${device}NUS"
            }
            uuid.compareTo(LegacyTX) == 0  -> {
                "${device}LegacyTX"
            }
            uuid.compareTo(LegacyRX) == 0 -> {
                "${device}LegacyRX"
            }
            uuid.compareTo(LegacyNUS) == 0 -> {
                "${device}LegacyNUS"
            }
            else -> {
                "${device}[${uuid}]"
            }
        }
    }
}