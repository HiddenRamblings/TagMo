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
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest


class Infinity : TagTechnology {

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
        return tagNfcA?.isConnected == true
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

        private val BEYOND_ATQT = byteArrayOf(0x00, 0x44)

        private val uidre = Regex("^04[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
        private val magic_nums: Array<BigInteger> = arrayOf(
            BigInteger("3"), BigInteger("5"),
            BigInteger("7"), BigInteger("23"),
            BigInteger("9985861487287759675192201655940647"),
            BigInteger("38844225342798321268237511320137937")
        )

        private fun calc_keya (uid: String) : ByteArray {
            if (!uidre.matches(uid)) throw NumberFormatException("invalid UID (seven hex bytes)")

            val sha1 = MessageDigest.getInstance("SHA-1")
            val textBytes: ByteArray = TagArray.hexToByteArray("${String.format("%032X",
                magic_nums[0] * magic_nums[1] * magic_nums[3] * magic_nums[5]
            )}$uid${String.format("%030X", 
                magic_nums[0] * magic_nums[2] * magic_nums[4]
            )}")
            sha1.update(textBytes, 0, textBytes.size)
            val key = sha1.digest()

            return key.copyOfRange(0, 3).reversed().toByteArray().plus(
                key.copyOfRange(5, 7).reversed().toByteArray()
            )
        }

        private fun andBeyond(tag: Tag) : ByteArray {
            return calc_keya(TagArray.bytesToHex(tag.id))
        }

        private fun getMifareClasssic(tag: Tag?): Infinity? {
            return MifareClassic.get(tag)?.let {
                it.authenticateSectorWithKeyA(0, andBeyond(it.tag))
                Infinity(it)
            }
        }

        private fun getNfcA(tag: Tag?): Infinity? {
            return NfcA.get(tag)?.let {
                if (it.sak equals 0x09 && it.atqa.equals(BEYOND_ATQT)) Infinity(it) else null
            }
        }

        operator fun get(tag: Tag?): Infinity? {
            return try {
                getMifareClasssic(tag)?.also { it.connect() }
            } catch (e: IOException) {
                getNfcA(tag)?.also { it.connect() }
            } ?: getNfcA(tag)?.also { it.connect() }
        }
    }
}