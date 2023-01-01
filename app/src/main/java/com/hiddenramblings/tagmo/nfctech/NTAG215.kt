package com.hiddenramblings.tagmo.nfctech

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.TagTechnology
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.io.IOException

class NTAG215 : TagTechnology {
    private val tagMifare: MifareUltralight?
    private val tagNfcA: NfcA?
    private val maxTransceiveLength: Int

    constructor(nfcA: NfcA?) {
        tagNfcA = nfcA
        tagMifare = null
        maxTransceiveLength = tagNfcA!!.maxTransceiveLength / 4 + 1
    }

    constructor(mifare: MifareUltralight?) {
        tagNfcA = null
        tagMifare = mifare
        maxTransceiveLength = tagMifare!!.maxTransceiveLength / 4 + 1
    }

    @Suppress("UNUSED")
    var timeout: Int
        get() = tagMifare?.timeout ?: (tagNfcA?.timeout ?: 0)
        set(timeout) {
            if (null != tagMifare) tagMifare.timeout = timeout
            if (null != tagNfcA) {
                tagNfcA.timeout = timeout
            }
        }

    fun transceive(data: ByteArray?): ByteArray? {
        try {
            if (null != tagMifare) {
                return tagMifare.transceive(data)
            } else if (null != tagNfcA) {
                return tagNfcA.transceive(data)
            }
        } catch (e: IOException) {
            Debug.warn(e)
        }
        return null
    }

    @Throws(IOException::class)
    fun readPages(pageOffset: Int): ByteArray? {
        if (null != tagMifare) return tagMifare.readPages(pageOffset) else if (null != tagNfcA) {
            validatePageIndex(pageOffset)
            //checkConnected();
            val cmd = byteArrayOf(
                NfcByte.CMD_READ.toByte(), pageOffset.toByte()
            )
            return tagNfcA.transceive(cmd)
        }
        return null
    }

    @Throws(IOException::class)
    fun writePage(pageOffset: Int, data: ByteArray) {
        if (null != tagMifare) {
            tagMifare.writePage(pageOffset, data)
        } else if (null != tagNfcA) {
            validatePageIndex(pageOffset)
            //m_nfcA.checkConnected();
            val cmd = ByteArray(data.size + 2)
            cmd[0] = NfcByte.CMD_WRITE.toByte()
            cmd[1] = pageOffset.toByte()
            System.arraycopy(data, 0, cmd, 2, data.size)
            tagNfcA.transceive(cmd)
        }
    }

    @Throws(IOException::class)
    override fun connect() {
        tagMifare?.connect() ?: tagNfcA?.connect()
    }

    @Throws(IOException::class)
    override fun close() {
        tagMifare?.close() ?: tagNfcA?.close()
    }

    override fun getTag(): Tag? {
        if (null != tagMifare) {
            return tagMifare.tag
        } else if (null != tagNfcA) {
            return tagNfcA.tag
        }
        return null
    }

    /*
     * byte 1: currently active slot
     * byte 2: number of active banks
     * byte 3: button pressed?
     * byte 4: FW version?
     * see: http://wiki.yobi.be/wiki/N2_Elite#0x55:_N2_GET_INFO
     */
    fun getVersion(isGeneric: Boolean): ByteArray? {
        val command =
            if (isGeneric) byteArrayOf(NfcByte.CMD_GET_VERSION.toByte()) else byteArrayOf(NfcByte.N2_GET_VERSION.toByte())
        return transceive(command)
    }

    @Suppress("UNUSED")
    val bankCount: ByteArray?
        get() {
            val req = ByteArray(1)
            req[0] = NfcByte.N2_BANK_COUNT.toByte()
            return transceive(req)
        }

    fun readSignature(isGeneric: Boolean): ByteArray? {
        val command = if (isGeneric) byteArrayOf(
            NfcByte.CMD_READ_SIG.toByte(),
            0x00.toByte()
        ) else byteArrayOf(NfcByte.N2_READ_SIG.toByte())
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
        method: IFastRead,
        startAddr: Int,
        endAddr: Int,
        bank: Int
    ): ByteArray? {
        if (endAddr < startAddr) {
            return null
        }
        val resp = ByteArray((endAddr - startAddr + 1) * 4)
        val maxReadLength = maxTransceiveLength / 4 - 1
        if (maxReadLength < 1) {
            return null
        }
        val snippetByteSize = maxReadLength * 4
        var startSnippet = startAddr
        var i = 0
        while (startSnippet <= endAddr) {
            var endSnippet = startSnippet + maxReadLength - 1
            if (endSnippet > endAddr) {
                endSnippet = endAddr
            }
            val respSnippet = method.doFastRead(startSnippet, endSnippet, bank) ?: return null
            if (respSnippet.size != (endSnippet - startSnippet + 1) * 4) {
                return null
            }
            if (respSnippet.size == resp.size) {
                return respSnippet
            }
            System.arraycopy(respSnippet, 0, resp, i * snippetByteSize, respSnippet.size)
            startSnippet += maxReadLength
            i++
        }
        return resp
    }

    private fun internalWrite(method: IFastWrite, addr: Int, bank: Int, data: ByteArray): Boolean {
        val query = ByteArray(4)
        val pages = data.size / 4
        for (i in 0 until pages) {
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
        method: IFastWrite,
        startAddr: Int,
        bank: Int,
        data: ByteArray
    ): Boolean {
        var snippetByteSize = 16
        val endAddr = startAddr + data.size / 4
        var startSnippet = startAddr
        var i = 0
        while (startSnippet <= endAddr) {
            if (startSnippet + 4 >= endAddr) {
                snippetByteSize = data.size % snippetByteSize
            }
            if (snippetByteSize == 0) {
                return true
            }
            val query = ByteArray(snippetByteSize)
            System.arraycopy(data, i, query, 0, snippetByteSize)
            if (!method.doFastWrite(startSnippet, bank, query)) {
                return false
            }
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
        transceive(
            byteArrayOf(
                NfcByte.N2_LOCK
                    .toByte()
            )
        )
    }

    fun amiiboPrepareUnlock(): ByteArray? {
        return transceive(
            byteArrayOf(
                NfcByte.N2_UNLOCK_1
                    .toByte()
            )
        )
    }

    fun amiiboUnlock() {
        transceive(
            byteArrayOf(
                NfcByte.N2_UNLOCK_2
                    .toByte()
            )
        )
    }

    override fun isConnected(): Boolean {
        return tagNfcA!!.isConnected
    }

    companion object {
        val CONNECT = NTAG215::class.java.name + ".connect()"
        private const val NXP_MANUFACTURER_ID = 0x04
        private const val MAX_PAGE_COUNT = 256
        operator fun get(tag: Tag?): NTAG215? {
            val mifare = MifareUltralight.get(tag)
            if (null != mifare) return NTAG215(mifare)
            val nfcA = NfcA.get(tag)
            return if (null != nfcA)
                if (nfcA.sak.toInt() == 0x00 && tag?.id?.get(0)?.toInt()  == NXP_MANUFACTURER_ID)
                    NTAG215(nfcA)
                else null
            else null
        }

        fun getBlind(tag: Tag?): NTAG215? {
            val nfcA = NfcA.get(tag)
            return nfcA?.let { NTAG215(it) }
        }

        private fun validatePageIndex(pageIndex: Int) {
            // Do not be too strict on upper bounds checking, since some cards
            // may have more addressable memory than they report.
            // Note that issuing a command to an out-of-bounds block is safe - the
            // tag will wrap the read to an addressable area. This validation is a
            // helper to guard against obvious programming mistakes.
            if (pageIndex < 0 || pageIndex >= MAX_PAGE_COUNT) {
                throw IndexOutOfBoundsException("page out of bounds: $pageIndex")
            }
        }
    }
}