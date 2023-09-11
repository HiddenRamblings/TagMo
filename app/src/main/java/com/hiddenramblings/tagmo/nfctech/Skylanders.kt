/*
 * ====================================================================
 * nfctoys Copyright (C) 2018 Vitorio Miliano
 * UID.py Copyright (C) 2022 Nitrus#1839, DevZillion @ TheSkyLib
 * ====================================================================
 */
package com.hiddenramblings.tagmo.nfctech

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.nfc.tech.TagTechnology
import com.github.snksoft.crc.CRC
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.TagArray.toByteArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toHexByteArray
import java.io.IOException
import java.math.BigInteger

class Skylanders(mifare: MifareClassic?) : TagTechnology {

    private val magicNumbers = listOf(2, 3, 73, 1103, 2017, 560381651, 12868356821)

    private val tagMifare: MifareClassic?
    private var maxTransceiveLength: Int = 0

    private val sectorKeys: ArrayList<ByteArray> = arrayListOf()

    init {
        tagMifare = mifare?.also {
            maxTransceiveLength = it.maxTransceiveLength / 4 + 1
        }
    }

    @Suppress("unused")
    var timeout: Int
        get() = tagMifare?.timeout ?: 0
        set(timeout) {
            tagMifare?.timeout = timeout
        }

    @Throws(IOException::class)
    override fun connect() {
        tagMifare?.connect()
    }

    override fun isConnected(): Boolean {
        return tagMifare?.isConnected ?: false
    }

    @Throws(IOException::class)
    override fun close() {
        tagMifare?.close()
    }

    override fun getTag(): Tag? {
        return tagMifare?.tag
    }

    private fun pseudoCrc48(crc: Long, data: List<Int>): Long {
        val poly = 0x42f0e1eba9ea3693
        val msb = 0x800000000000
        val trim = 0xffffffffffff
        var currentCrc = crc
        for (x in data) {
            currentCrc = currentCrc xor (x.toLong() shl 40)
            for (k in 0 until 8) {
                currentCrc = if (currentCrc and msb != 0L)
                    (currentCrc shl 1) xor poly
                else
                    currentCrc shl 1
                currentCrc = currentCrc and trim
            }
        }
        return currentCrc
    }

    @Throws(NumberFormatException::class)
    private fun keySkylanders(uid: String, sector: Int): ByteArray {
        if (sector == 0) {
            val keyA = String.format("%012x", magicNumbers[2] * magicNumbers[4] * magicNumbers[5])
            if (staticKeyA == keyA)
                return keyA.toHexByteArray()
            else
                throw NumberFormatException(TagMo.appContext.getString(R.string.fail_primary_key))
        }

        if (!Regex("^[0-9a-f]{8}\$", RegexOption.IGNORE_CASE).matches(uid))
            throw NumberFormatException(TagMo.appContext.getString(R.string.fail_uid_invalid, 4))

        if (sector < 0 || sector > 15)
            throw NumberFormatException(TagMo.appContext.getString(R.string.fail_sector_invalid))

        val key = pseudoCrc48((
                magicNumbers[0] * magicNumbers[0] * magicNumbers[1] * magicNumbers[3] * magicNumbers[6]),
                uid.chunked(2).map { it.toInt(16) } + sector
        )

        return (BigInteger.valueOf(key).toByteArray().take(6)
                .joinToString("") { "%02x".format(it) }).toHexByteArray()
    }

    @Throws(IOException::class, NumberFormatException::class)
    fun authenticate() {
        tagMifare?.let {
            if (it.size != MifareClassic.SIZE_1K)
                throw IOException(TagMo.appContext.getString(R.string.error_tag_format))
            for (sector in 0 until 16) {
                val keyA = keySkylanders(it.tag.id.toHex(), sector)
                sectorKeys.add(keyA)
                it.authenticateSectorWithKeyA(sector, keyA)
                Debug.info(Companion::class.java, keyA.toHex())
            }
        }
    }

    fun convertDump(blankTag: ByteArray, tagData: ByteArray): ByteArray {
        val skyData = tagData.copyInto(ByteArray(NfcByte.C1K_DATA_SIZE))
        val lockedZeroBlock = blankTag.copyOfRange(0, 0x10)
        val skylanderInfo = tagData.copyOfRange(0x10, 0x10 + 0x0E)
        val zeroChecksum = lockedZeroBlock + skylanderInfo
        val binaryCrc16 = CRC.calculateCRC(CRC.Parameters.CCITT, zeroChecksum, 0xFFFF).toByteArray()
        binaryCrc16.copyInto(skyData, 0x0E)
        var index = 0x30
        for (i in 0 until 16) {
            index += 0x30
            sectorKeys[i].copyInto(skyData, index)
            index += 0xA
        }
        sector0.toHexByteArray().copyInto(skyData, 0x36)
        return skyData
    }

    fun transceive(data: ByteArray?): ByteArray? {
        return try {
            tagMifare?.transceive(data)
        } catch (e: IOException) {
            Debug.warn(e)
            null
        }
    }

    companion object {

        const val staticKeyA = "4B0B20107CCB"
        const val sector0 = "FF0780"

        private infix fun Short.equals(i: Int): Boolean = this == i.toShort()

        operator fun get(tag: Tag?): Skylanders? {
            return NfcA.get(tag)?.let {
                if (it.sak equals 0x01 && it.atqa.equals(byteArrayOf(0x0F, 0x01)))
                    MifareClassic.get(tag)?.let { mifare -> Skylanders(mifare) }
                else
                    null
            }
        }
    }
}