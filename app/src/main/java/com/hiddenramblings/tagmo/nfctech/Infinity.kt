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
import java.security.MessageDigest

class Infinity(mifare: MifareClassic?) : TagTechnology { // Mifare Mini

    private val magicNumbers: Array<BigInteger> = arrayOf(
            BigInteger("3"), BigInteger("5"),
            BigInteger("7"), BigInteger("23"),
            BigInteger("9985861487287759675192201655940647"),
            BigInteger("38844225342798321268237511320137937")
    )

    private val tagMifare: MifareClassic?
    private var maxTransceiveLength: Int = 0

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

    @Throws(NumberFormatException::class)
    private fun keyInfinity(uid: String) : ByteArray {
        if (!Regex("^04[0-9a-f]{12}\$", RegexOption.IGNORE_CASE).matches(uid))
            throw NumberFormatException(TagMo.appContext.getString(R.string.fail_uid_invalid, 7))

        val sha1 = MessageDigest.getInstance("SHA-1")
        val textBytes: ByteArray = "${String.format(
                "%032X", magicNumbers[0] * magicNumbers[1] * magicNumbers[3] * magicNumbers[5]
        )}$uid${String.format(
                "%030X", magicNumbers[0] * magicNumbers[2] * magicNumbers[4]
        )}".toHexByteArray()
        sha1.update(textBytes, 0, textBytes.size)
        val key = sha1.digest()

        return key.copyOfRange(0, 3).reversed().toByteArray().plus(
                key.copyOfRange(5, 7).reversed().toByteArray()
        )
    }

    @Throws(IOException::class, NumberFormatException::class)
    fun authenticate() {
        tagMifare?.let {
            if (it.size != MifareClassic.SIZE_1K)
                throw IOException(TagMo.appContext.getString(R.string.error_tag_format))
            val keyA = keyInfinity(it.tag.id.toHex())
            it.authenticateSectorWithKeyA(0, keyA)
            Debug.info(Companion::class.java, keyA.toHex())
        }
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

        private infix fun Short.equals(i: Int): Boolean = this == i.toShort()

        operator fun get(tag: Tag?): Infinity? {
            return NfcA.get(tag)?.let {
                if (it.sak equals 0x09 && it.atqa.equals(byteArrayOf(0x00, 0x44)))
                    MifareClassic.get(tag)?.let { mifare -> Infinity(mifare) }
                else
                    null
            }
        }
    }
}