/*
 * ====================================================================
 * amiibo-generator Copyright (C) 2020 hax0kartik
 * Copyright (C) 2021 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.nfctech

import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.text.DecimalFormat
import java.util.*

class Foomiibo {
    private fun getRandomBytes(size: Int): ByteArray {
        val random = Random()
        val randBytes = ByteArray(size)
        random.nextBytes(randBytes)
        return randBytes
    }

    fun generateRandomUID(): ByteArray {
        val uid = getRandomBytes(9)
        uid[0x0] = 0x04
        uid[0x3] = (0x88 xor uid[0].toInt() xor uid[1].toInt() xor uid[2].toInt()).toByte()
        uid[0x8] = (uid[4].toInt() xor uid[5].toInt() xor uid[6].toInt() xor uid[7].toInt()).toByte()
        return uid
    }

    @Suppress("UNUSED")
    private fun randomizeSerial(serial: String): String {
        val random = Random()
        val week = DecimalFormat("00").format(
            (random.nextInt(52 - 1 + 1) + 1).toLong()
        )
        val year = random.nextInt(9 + 1).toString()
        val identifier = serial.substring(3, 7)
        val facility = TagMo.appContext.resources.getStringArray(
            R.array.production_factory
        )[random.nextInt(3 + 1)]
        return week + year + "000" + identifier + facility
    }

    fun generateData(id: String): ByteArray {
        val arr = ByteArray(NfcByte.TAG_DATA_SIZE)

        // Set UID, BCC0
        // 0x04, (byte) 0xC0, 0x0A, 0x46, 0x61, 0x6B, 0x65, 0x0A
        val uid = generateRandomUID()
        System.arraycopy(uid, 0, arr, 0x1D4, uid.size)

        // Set BCC1
        arr[0] = uid[0x8]

        // Set Internal, Static Lock, and CC
        val CC = byteArrayOf(
            0x48,
            0x0F,
            0xE0.toByte(),
            0xF1.toByte(),
            0x10,
            0xFF.toByte(),
            0xEE.toByte()
        )
        System.arraycopy(CC, 0, arr, 0x1, CC.size)

        // Set 0xA5, Write Counter, and Unknown
        val OxA5 = byteArrayOf(0xA5.toByte(), 0x00, 0x00, 0x00)
        System.arraycopy(OxA5, 0, arr, 0x28, OxA5.size)

        // Set Dynamic Lock, and RFUI
        val RFUI = byteArrayOf(0x01, 0x00, 0x0F, 0xBD.toByte())
        System.arraycopy(RFUI, 0, arr, 0x208, RFUI.size)

        // Set CFG0
        val CFG0 = byteArrayOf(0x00, 0x00, 0x00, 0x04)
        System.arraycopy(CFG0, 0, arr, 0x20C, CFG0.size)

        // Set CFG1
        val CFG1 = byteArrayOf(0x5F, 0x00, 0x00, 0x00)
        System.arraycopy(CFG1, 0, arr, 0x210, CFG1.size)

        // Set Keygen Salt
        val salt = getRandomBytes(32)
        System.arraycopy(salt, 0, arr, 0x1E8, salt.size)
        var off1 = 0x54
        var off2 = 0x1DC
        // Write Identification Block
        var i = 0
        while (i < 16) {
            val currByte = id.substring(i, i + 2).toInt(16).toByte()
            arr[off1] = currByte
            arr[off2] = currByte
            i += 2
            off1 += 1
            off2 += 1
        }
        return arr
    }

    fun generateData(id: Long): ByteArray {
        return generateData(id.toString())
    }

    fun getSignedData(tagData: ByteArray): ByteArray {
        val signedData = ByteArray(NfcByte.TAG_FILE_SIZE)
        System.arraycopy(tagData, 0, signedData, 0x0, tagData.size)
        val signature = TagArray.hexToByteArray(hexSingature)
        System.arraycopy(signature, 0, signedData, 0x21C, signature.size)
        return signedData
    }

    fun getSignedData(id: String): ByteArray {
        return getSignedData(generateData(id))
    }

    companion object {
        private const val hexSingature = "5461674d6f20382d426974204e544147"
        fun getDataSignature(tagData: ByteArray): String? {
            if (tagData.size == NfcByte.TAG_FILE_SIZE) {
                val signature = TagArray.bytesToHex(
                    tagData.copyOfRange(540, NfcByte.TAG_FILE_SIZE)
                ).substring(0, 32).lowercase()
                Debug.info(TagMo::class.java, TagArray.hexToString(signature))
                if (hexSingature == signature) return signature
            }
            return null
        }
    }
}