package com.hiddenramblings.tagmo.bluetooth

import android.os.Build
import androidx.annotation.RequiresApi
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

    val omllboNUS: UUID = UUID.fromString("0000ff10-0000-1000-8000-00805f9b34fb")
    val omllboTX: UUID = UUID.fromString("0000ff11-0000-1000-8000-00805f9b34fb")
    val omllboRX: UUID = UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb")

    fun UUID.isUUID(uuid: UUID): Boolean {
       return this.compareTo(uuid) == 0
    }

    val UUID.logTag: String get() {
        return when {
            this.isUUID(TX) -> {
                "NordicTX"
            }
            this.isUUID(RX) -> {
                "NordicRX"
            }
            this.isUUID(NUS) -> {
                "NordicNUS"
            }
            this.isUUID(LegacyTX) -> {
                "LegacyTX"
            }
            this.isUUID(LegacyRX) -> {
                "LegacyRX"
            }
            this.isUUID(LegacyNUS) -> {
                "LegacyNUS"
            }
            else -> {
                "Gatt[${this}]"
            }
        }
    }
}