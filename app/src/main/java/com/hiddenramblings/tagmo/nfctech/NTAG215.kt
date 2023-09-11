/*
 * ====================================================================
 * N2 Elite Copyright (C) 2019 MasterCheaterz
 * Copyright (c) 2021 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.nfctech

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.TagTechnology
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.io.IOException

class NTAG215 : TagTechnology {

    private val tagMifare: MifareUltralight?
    private val tagNfcA: NfcA?
    private var maxTransceiveLength: Int = 0

    constructor(nfcA: NfcA?) {
        tagNfcA = nfcA?.also {
            maxTransceiveLength = it.maxTransceiveLength / 4 + 1
        }
        tagMifare = null
    }

    constructor(mifare: MifareUltralight?) {
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

    fun getVersion(isGeneric: Boolean): ByteArray? {
        val command = if (isGeneric)
            byteArrayOf(NfcByte.CMD_GET_VERSION.toByte())
        else byteArrayOf(NfcByte.N2_GET_VERSION.toByte())
        return transceive(command)
    }

    @Throws(IOException::class)
    fun readPages(pageOffset: Int): ByteArray? {
        return tagMifare?.readPages(pageOffset) ?: if (null != tagNfcA) {
            validatePageIndex(pageOffset)
            //checkConnected();
            val cmd = byteArrayOf(
                NfcByte.CMD_READ.toByte(), pageOffset.toByte()
            )
            tagNfcA.transceive(cmd)
        } else {
            null
        }
    }

    @Throws(IOException::class)
    fun writePage(pageOffset: Int, data: ByteArray) {
        tagMifare?.writePage(pageOffset, data) ?:
        if (null != tagNfcA) {
            validatePageIndex(pageOffset)
            //m_nfcA.checkConnected();
            val cmd = ByteArray(data.size + 2)
            cmd[0] = NfcByte.CMD_WRITE.toByte()
            cmd[1] = pageOffset.toByte()
            System.arraycopy(data, 0, cmd, 2, data.size)
            tagNfcA.transceive(cmd)
        } else {
            throw IOException()
        }
    }

    @Suppress("unused")
    val bankCount: ByteArray?
        get() {
            val req = ByteArray(1)
            req[0] = NfcByte.N2_BANK_COUNT.toByte()
            return transceive(req)
        }

    fun readSignature(isGeneric: Boolean): ByteArray? {
        val command = if (isGeneric)
            byteArrayOf(NfcByte.CMD_READ_SIG.toByte(), 0x00.toByte())
        else byteArrayOf(NfcByte.N2_READ_SIG.toByte())
        return transceive(command)
    }

    fun setBankCount(count: Int) {
        transceive(byteArrayOf(NfcByte.N2_SET_BANKCOUNT.toByte(), (count and 0xFF).toByte()))
    }

    fun activateBank(bank: Int) {
        transceive(byteArrayOf(NfcByte.N2_ACTIVATE_BANK.toByte(), (bank and 0xFF).toByte()))
    }

    fun initFirmware() {
        transceive(
            byteArrayOf(
                0xFFF4.toByte(),
                0x49.toByte(),
                0xFF9B.toByte(),
                0xFF99.toByte(),
                0xFFC3.toByte(),
                0xFFDA.toByte(),
                0x57.toByte(),
                0x71.toByte(),
                0x0A.toByte(),
                0x64.toByte(),
                0x4A.toByte(),
                0xFF9E.toByte(),
                0xFFF8.toByte(),
                NfcByte.CMD_WRITE.toByte(),
                NfcByte.CMD_READ.toByte(),
                0xFFD9.toByte()
            )
        )
    }

    private interface IFastRead {
        fun doFastRead(start: Int, end: Int, bank: Int): ByteArray?
    }

    private interface IFastWrite {
        fun doFastWrite(addr: Int, bank: Int, data: ByteArray): Boolean
    }

    fun fastRead(startAddr: Int, endAddr: Int): ByteArray? {
        return internalFastRead(object : IFastRead {
            override fun doFastRead(start: Int, end: Int, bank: Int): ByteArray? {
                return transceive(
                    byteArrayOf(
                        NfcByte.CMD_FAST_READ.toByte(),
                        (start and 0xFF).toByte(),
                        (end and 0xFF).toByte()
                    )
                )
            }
        }, startAddr, endAddr, 0)
    }

    fun amiiboFastRead(startAddr: Int, endAddr: Int, bank: Int): ByteArray? {
        return internalFastRead(object : IFastRead {
            override fun doFastRead(start: Int, end: Int, bank: Int): ByteArray? {
                return transceive(
                    byteArrayOf(
                        NfcByte.N2_FAST_READ.toByte(),
                        (start and 0xFF).toByte(),
                        (end and 0xFF).toByte(),
                        (bank and 0xFF).toByte()
                    )
                )
            }
        }, startAddr, endAddr, bank)
    }

    private fun internalFastRead(
        method: IFastRead, startAddr: Int, endAddr: Int, bank: Int
    ): ByteArray? {
        if (endAddr < startAddr) return null
        val resp = ByteArray((endAddr - startAddr + 1) * 4)
        val maxReadLength = maxTransceiveLength / 4 - 1
        if (maxReadLength < 1) return null
        val snippetByteSize = maxReadLength * 4
        var startSnippet = startAddr
        var i = 0
        while (startSnippet <= endAddr) {
            var endSnippet = startSnippet + maxReadLength - 1
            if (endSnippet > endAddr) endSnippet = endAddr
            val respSnippet = method.doFastRead(startSnippet, endSnippet, bank) ?: return null
            if (respSnippet.size != (endSnippet - startSnippet + 1) * 4) return null
            if (respSnippet.size == resp.size) return respSnippet
            System.arraycopy(respSnippet, 0, resp, i * snippetByteSize, respSnippet.size)
            startSnippet += maxReadLength
            i++
        }
        return resp
    }

    private fun internalWrite(method: IFastWrite, addr: Int, bank: Int, data: ByteArray): Boolean {
        val query = ByteArray(4)
        for (i in 0 until data.size / 4) {
            System.arraycopy(data, i * 4, query, 0, 4)
            if (!method.doFastWrite(addr + i, bank, query)) {
                return false
            }
        }
        return true
    }

    fun amiiboWrite(addr: Int, bank: Int, data: ByteArray?): Boolean {
        return if (null != data && data.size % 4 == 0) {
            internalWrite(object : IFastWrite {
                override fun doFastWrite(addr: Int, bank: Int, data: ByteArray): Boolean {
                    val req = ByteArray(3)
                    req[0] = NfcByte.N2_WRITE.toByte()
                    req[1] = (addr and 0xFF).toByte()
                    req[2] = (bank and 0xFF).toByte()
                    return try {
                        transceive(req.plus(data.copyOfRange(0, 4)))
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }, addr, bank, data)
        } else false
    }

    private fun internalFastWrite(
        method: IFastWrite, startAddr: Int, bank: Int, data: ByteArray
    ): Boolean {
        var snippetByteSize = 16
        val endAddr = startAddr + data.size / 4
        var startSnippet = startAddr
        var i = 0
        while (startSnippet <= endAddr) {
            if (startSnippet + 4 >= endAddr) snippetByteSize = data.size % snippetByteSize
            if (snippetByteSize == 0) return true
            val query = ByteArray(snippetByteSize)
            System.arraycopy(data, i, query, 0, snippetByteSize)
            if (!method.doFastWrite(startSnippet, bank, query)) return false
            startSnippet += 4
            i += snippetByteSize
        }
        return true
    }

    fun amiiboFastWrite(addr: Int, bank: Int, data: ByteArray?): Boolean {
        return if (null == data) {
            false
        } else internalFastWrite(object : IFastWrite {
            override fun doFastWrite(addr: Int, bank: Int, data: ByteArray): Boolean {
                val req = ByteArray(4)
                req[0] = NfcByte.N2_FAST_WRITE.toByte()
                req[1] = (addr and 0xFF).toByte()
                req[2] = (bank and 0xFF).toByte()
                req[3] = (data.size and 0xFF).toByte()
                return try {
                    transceive(req.plus(data))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }, addr, bank, data)
    }

    fun amiiboLock() {
        transceive(byteArrayOf(NfcByte.N2_LOCK.toByte()))
    }

    fun amiiboPrepareUnlock(): ByteArray? {
        return transceive(byteArrayOf(NfcByte.N2_UNLOCK_1.toByte()))
    }

    fun amiiboUnlock() {
        transceive(byteArrayOf(NfcByte.N2_UNLOCK_2.toByte()))
    }

    companion object {

        private infix fun Short.equals(i: Int): Boolean = this == i.toShort()

        private const val NXP_MANUFACTURER_ID = 0x04
        private const val MAX_PAGE_COUNT = 256

        private fun validatePageIndex(pageIndex: Int) {
            // Do not be too strict on upper bounds checking, since some cards
            // may have more addressable memory than they report.
            // Note that issuing a command to an out-of-bounds block is safe - the
            // tag will wrap the read to an addressable area. This validation is a
            // helper to guard against obvious programming mistakes.
            if (pageIndex < 0 || pageIndex >= MAX_PAGE_COUNT)
                throw IndexOutOfBoundsException("page out of bounds: $pageIndex")
        }

        private fun getMifareUltralight(tag: Tag?): NTAG215? {
            return MifareUltralight.get(tag)?.let {
                NTAG215(it)
            }
        }

        private fun getNfcA(tag: Tag?): NTAG215? {
            return NfcA.get(tag)?.let {
                if (it.sak equals 0x00 && it.tag.id[0].toInt() == NXP_MANUFACTURER_ID)
                    NTAG215(it)
                else
                    null
            }
        }

        @Throws(IOException::class)
        fun getBlind(tag: Tag?): NTAG215 {
            return try {
                NTAG215(NfcA.get(tag)).apply { connect() }
            } catch (ex: IOException) {
                Debug.warn(ex)
                null
            } ?: throw IOException(TagMo.appContext.getString(R.string.error_tag_unavailable))
        }

        @Throws(IOException::class)
        operator fun get(tag: Tag?): NTAG215? {
            return try {
                getMifareUltralight(tag)?.apply { connect() }
            } catch (ex: IOException) {
                Debug.warn(ex)
                null
            } ?: getNfcA(tag)?.apply { connect() }
        }
    }
}