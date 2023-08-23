/*
 * ====================================================================
 * amiibo-generator Copyright (C) 2020 hax0kartik
 * Copyright (C) 2021 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.nfctech

import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import java.io.File
import java.text.DecimalFormat
import kotlin.random.Random

object Foomiibo {
    val directory: File by lazy { File(TagMo.appContext.filesDir, "Foomiibo") }

    private fun getRandomBytes(size: Int): ByteArray {
        val randBytes = ByteArray(size)
        Random.nextBytes(randBytes)
        return randBytes
    }

    fun generateRandomUID(): ByteArray {
        val uid = getRandomBytes(9)
        uid[0x0] = 0x04
        uid[0x3] = (0x88 xor uid[0].toInt() xor uid[1].toInt() xor uid[2].toInt()).toByte()
        uid[0x8] = (uid[4].toInt() xor uid[5].toInt() xor uid[6].toInt() xor uid[7].toInt()).toByte()
        return uid
    }

    @Suppress("unused")
    private fun randomizeSerial(serial: String): String {
        val week = DecimalFormat("00").format((Random.nextInt(52) + 1))
        val year = Random.nextInt(9 + 1).toString()
        val identifier = serial.substring(3, 7)
        val facility = TagMo.appContext.resources
            .getStringArray(R.array.production_factory)[Random.nextInt(3 + 1)]
        return week + year + "000" + identifier + facility
    }

    private fun generateData(id: String): ByteArray {
        val arr = ByteArray(NfcByte.TAG_DATA_SIZE)

        // Set UID, BCC0
        // 0x04, (byte) 0xC0, 0x0A, 0x46, 0x61, 0x6B, 0x65, 0x0A
        val uid = generateRandomUID()
        System.arraycopy(uid, 0, arr, 0x1D4, uid.size)

        // Set BCC1
        arr[0] = uid[0x8]

        // Set Internal, Static Lock, and CC
        System.arraycopy(byteArrayOf(
            0x48, 0x0F, 0xE0.toByte(), 0xF1.toByte(), 0x10, 0xFF.toByte(), 0xEE.toByte()
        ), 0, arr, 0x1, 7)

        // Set 0xA5, Write Counter, and Unknown
        System.arraycopy(byteArrayOf(0xA5.toByte(), 0x00, 0x00, 0x00), 0, arr, 0x28, 4)

        // Set Dynamic Lock, and RFUI
        System.arraycopy(byteArrayOf(0x01, 0x00, 0x0F, 0xBD.toByte()), 0, arr, 0x208, 4)

        // Set CFG0
        System.arraycopy(byteArrayOf(0x00, 0x00, 0x00, 0x04), 0, arr, 0x20C, 4)

        // Set CFG1
        System.arraycopy(byteArrayOf(0x5F, 0x00, 0x00, 0x00), 0, arr, 0x210, 4)

        // Set Keygen Salt
        System.arraycopy(getRandomBytes(32), 0, arr, 0x1E8, 32)
        // Write Identification Block
        var off1 = 0x54
        var off2 = 0x1DC
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

    private const val hexSignature = "5461674d6f20382d426974204e544147"

    fun getSignedData(tagData: ByteArray): ByteArray {
        return ByteArray(NfcByte.TAG_FILE_SIZE).apply {
            System.arraycopy(tagData, 0, this, 0x0, tagData.size)
            val signature = TagArray.hexToByteArray(hexSignature)
            System.arraycopy(signature, 0, this, NfcByte.SIGNATURE, signature.size)
        }
    }

    fun getSignedData(id: String): ByteArray {
        return getSignedData(generateData(id))
    }

    fun getSignedData(id: Long): ByteArray {
        return getSignedData(generateData(Amiibo.idToHex(id)))
    }

    fun getDataSignature(tagData: ByteArray): String? {
        if (tagData.size == NfcByte.TAG_FILE_SIZE) {
            val signature = tagData.copyOfRange(
                    NfcByte.SIGNATURE, NfcByte.TAG_FILE_SIZE
            ).toHex().substring(0, 32).lowercase()
            Debug.verbose(TagMo::class.java, TagArray.hexToString(signature))
            if (hexSignature == signature) return signature
        }
        return null
    }
}