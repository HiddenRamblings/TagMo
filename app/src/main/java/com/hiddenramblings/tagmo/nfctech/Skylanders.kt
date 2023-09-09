/*
 * ====================================================================
 * nfctoys Copyright (C) 2018 Vitorio Miliano
 * ====================================================================
 */
package com.hiddenramblings.tagmo.nfctech

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.nfc.tech.TagTechnology
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toHexByteArray
import java.io.IOException
import java.math.BigInteger

class Skylanders  : TagTechnology {

    private val tagMifare: MifareClassic?
    private val tagNfcA: NfcA?
    private var maxTransceiveLength: Int = 0

    constructor(nfcA: NfcA?) {
        tagNfcA = nfcA?.also {
            maxTransceiveLength = it.maxTransceiveLength / 4 + 1
        }
        tagMifare = null
    }

    constructor(mifare: MifareClassic?) {
        tagNfcA = null
        tagMifare = mifare?.also {
            maxTransceiveLength = it.maxTransceiveLength / 4 + 1
        }
    }

    @Suppress("unused")
    var timeout: Int
        get() = tagMifare?.timeout ?: tagNfcA?.timeout ?: 0
        set(timeout) {
            tagMifare?.timeout = timeout
            tagNfcA?.timeout = timeout
        }

    @Throws(IOException::class)
    override fun connect() {
        tagMifare?.connect() ?: tagNfcA?.connect()
    }

    override fun isConnected(): Boolean {
        return tagMifare?.isConnected ?: tagNfcA?.isConnected ?: false
    }

    @Throws(IOException::class)
    override fun close() {
        tagMifare?.close() ?: tagNfcA?.close()
    }

    override fun getTag(): Tag? {
        return tagMifare?.tag ?: tagNfcA?.tag
    }



    fun transceive(data: ByteArray?): ByteArray? {
        return try {
            tagMifare?.transceive(data) ?: tagNfcA?.transceive(data)
        } catch (e: IOException) {
            Debug.warn(e)
            null
        }
    }

    companion object {

        private infix fun Short.equals(i: Int): Boolean = this == i.toShort()

        private val uidre = Regex("^[0-9a-f]{8}\$", RegexOption.IGNORE_CASE)
        private val magicNumbers = listOf(2, 3, 73, 1103, 2017, 560381651, 12868356821)

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

        private fun keySkylanders(uid: String, sector: Int): ByteArray {
            if (sector == 0)
                return String.format("%012x", magicNumbers[2] * magicNumbers[4] * magicNumbers[5]).toHexByteArray()

            if (!uidre.matches(uid))
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

        private fun getMifareClasssic(tag: Tag?): Skylanders? {
            return MifareClassic.get(tag)?.let {
                for (sector in 0 until 16) {
                    it.authenticateSectorWithKeyA(sector, keySkylanders(it.tag.id.toHex(), sector))
                }
                Skylanders(it)
            }
        }

        private fun getNfcA(tag: Tag?): Skylanders? {
            return NfcA.get(tag)?.let {
                if (it.sak equals 0x09 && it.atqa.equals(byteArrayOf(0x00, 0x44)))
                    Skylanders(it)
                else
                    null
            }
        }

        operator fun get(tag: Tag?): Skylanders? {
            return try {
                getMifareClasssic(tag)?.also { it.connect() }
            } catch (e: IOException) {
                getNfcA(tag)?.also { it.connect() }
            } ?: getNfcA(tag)?.also { it.connect() }
        }
    }
}