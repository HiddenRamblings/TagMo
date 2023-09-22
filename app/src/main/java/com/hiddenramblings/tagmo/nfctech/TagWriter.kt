package com.hiddenramblings.tagmo.nfctech

import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.amiibo.PowerTagManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.TagArray.isPowerTag
import com.hiddenramblings.tagmo.nfctech.TagArray.toDecryptedTag
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import com.hiddenramblings.tagmo.nfctech.TagArray.toHexByteArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toTagArray
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

object TagWriter {
    @Throws(Exception::class)
    private fun splitPages(data: ByteArray): Array<ByteArray?> {
        if (data.size < NfcByte.TAG_DATA_SIZE)
            throw IOException(TagMo.appContext.getString(
                R.string.invalid_data_size, data.size, NfcByte.TAG_DATA_SIZE
            ))
        val pages = arrayOfNulls<ByteArray>(data.size / NfcByte.PAGE_SIZE)
        var i = 0
        var j = 0
        while (i < data.size) {
            pages[j] = data.copyOfRange(i, i + NfcByte.PAGE_SIZE)
            i += NfcByte.PAGE_SIZE
            j++
        }
        return pages
    }

    @Throws(Exception::class)
    fun writeToTagRaw(
        mifare: NTAG215, tagData: ByteArray, validateNtag: Boolean
    ) {
        val context = TagMo.appContext
        TagArray.validateNtag(mifare, tagData, validateNtag)
        TagReader.validateBlankTag(mifare)
        try {
            val pages = splitPages(tagData)
            writePages(mifare, 3, 129, pages)
            Debug.verbose(TagWriter::class.java, R.string.data_write)
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.error_data_write), e)
        }
        try {
            writePassword(mifare)
            Debug.verbose(TagWriter::class.java, R.string.password_write)
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.error_password_write), e)
        }
        try {
            writeLockInfo(mifare)
            Debug.verbose(TagWriter::class.java, R.string.lock_write)
        } catch (e: Exception) {
            throw Exception(context.getString(R.string.error_lock_write), e)
        }
    }

    @Throws(IOException::class)
    private fun writePages(
        tag: NTAG215, pagestart: Int, pageend: Int, data: Array<ByteArray?>
    ) {
        for (i in pagestart..pageend) {
            tag.writePage(i, data[i]!!)
            Debug.verbose(TagWriter::class.java, R.string.write_page, i.toString())
        }
    }

    @Throws(Exception::class)
    private fun patchUid(uid: ByteArray, tagData: ByteArray): ByteArray {
        if (uid.size < 9) throw IOException(TagMo.appContext.getString(R.string.invalid_uid_length))
        val patched = tagData.copyOf(tagData.size)
        System.arraycopy(uid, 0, patched, 0x1d4, 8)
        patched[0] = uid[8]
        return patched
    }

    @Throws(Exception::class)
    private fun writePasswordLockInfo(mifare: NTAG215) {
        try {
            writePassword(mifare)
            Debug.verbose(TagWriter::class.java, R.string.password_write)
        } catch (e: Exception) {
            throw Exception(TagMo.appContext.getString(R.string.error_password_write), e)
        }
        try {
            writeLockInfo(mifare)
            Debug.verbose(TagWriter::class.java, R.string.lock_write)
        } catch (e: Exception) {
            throw Exception(TagMo.appContext.getString(R.string.error_lock_write), e)
        }
    }

    @Throws(Exception::class)
    fun writeToTagAuto(
        mifare: NTAG215, tagData: ByteArray, keyManager: KeyManager, validateNtag: Boolean
    ) {
        val idPages = mifare.readPages(0)
        if (null == idPages || idPages.size != NfcByte.PAGE_SIZE * 4)
            throw IOException(TagMo.appContext.getString(R.string.fail_read_size))
        val isPowerTag = mifare.isPowerTag
        Debug.verbose(TagWriter::class.java, R.string.power_tag_verify, isPowerTag.toString())
        var writeData = tagData.toTagArray().toDecryptedTag(keyManager)
        writeData = if (isPowerTag) {
            // use a pre-determined static id for Power Tag
            patchUid(NfcByte.POWERTAG_IDPAGES, writeData)
        } else {
            patchUid(idPages, writeData)
        }
        writeData = keyManager.encrypt(writeData)
        Debug.verbose(TagWriter::class.java, writeData.toHex())
        if (!isPowerTag) {
            TagArray.validateNtag(mifare, writeData, validateNtag)
            try {
                TagReader.validateBlankTag(mifare)
            } catch (e: IOException) {
                throw IOException(e)
            }
        }
        if (isPowerTag) {
            val oldid = mifare.tag!!.id
            if (null == oldid || oldid.size != 7)
                throw Exception(TagMo.appContext.getString(R.string.fail_read_uid))
            Debug.verbose(TagWriter::class.java, R.string.old_uid, oldid.toHex())
            val page10 = mifare.readPages(0x10)
            page10?.let { Debug.verbose(TagWriter::class.java, R.string.page_ten, it.toHex()) }
            val page10bytes = byteArrayOf(page10?.get(0) ?: 0, page10?.get(3) ?: 0).toHex()
            val ptagKeySuffix = PowerTagManager.getPowerTagKey(oldid, page10bytes)
            val ptagKey = NfcByte.POWERTAG_KEY.toHexByteArray()
            System.arraycopy(ptagKeySuffix, 0, ptagKey, 8, 8)
            Debug.verbose(TagWriter::class.java, R.string.ptag_key, ptagKey.toHex())
            mifare.transceive(NfcByte.POWERTAG_WRITE)
            mifare.transceive(ptagKey)
            if (!(idPages[0] == 0xFF.toByte() && idPages[1] == 0xFF.toByte())) doAuth(mifare)
        }
        val pages = splitPages(writeData)
        if (isPowerTag) {
            val zeropage = TagArray.hexToByteArray("00000000")
            mifare.writePage(0x86, zeropage) //PACK
            writePages(mifare, 0x01, 0x84, pages)
            mifare.writePage(0x85, zeropage) //PWD
            mifare.writePage(0x00, pages[0]!!) //UID
            mifare.writePage(0x00, pages[0]!!) //UID
        } else {
            try {
                writePages(mifare, 3, 129, pages)
                Debug.verbose(TagWriter::class.java, R.string.data_write)
            } catch (e: Exception) {
                throw Exception(TagMo.appContext.getString(R.string.error_data_write), e)
            }
            writePasswordLockInfo(mifare)
        }
    }

    @Throws(Exception::class)
    fun writeEliteAuto(
        mifare: NTAG215, tagData: ByteArray?, bankNumber: Int
    ) {
        if (doEliteAuth(mifare, mifare.fastRead(0, 0))) {
            var write = mifare.amiiboFastWrite(0, bankNumber, tagData)
            if (!write) write = mifare.amiiboWrite(0, bankNumber, tagData)
            if (!write) throw IOException(TagMo.appContext.getString(R.string.error_elite_write))
        } else {
            throw Exception(TagMo.appContext.getString(R.string.error_elite_auth))
        }
    }

    @Throws(Exception::class)
    fun restoreTag(
        mifare: NTAG215, tagData: ByteArray, ignoreUid: Boolean,
        keyManager: KeyManager, validateNtag: Boolean
    ) {
        var restoreData = tagData.toTagArray()
        if (!ignoreUid) TagArray.validateNtag(mifare, restoreData, validateNtag) else {
            var liveData = TagReader.readFromTag(mifare)
            if (!TagArray.compareRange(liveData, restoreData, 9)) {
                // restoring to different tag: transplant mii and appdata to livedata and re-encrypt
                liveData = keyManager.decrypt(liveData)
                restoreData = keyManager.decrypt(restoreData)
                System.arraycopy(restoreData, 0x08, liveData, 0x08, 0x1B4 - 0x08)
                /* TODO: Verify that 0x1B4 should not be 0x1D4 */
                restoreData = keyManager.encrypt(liveData)
            }
        }
        doAuth(mifare)
        val pages = splitPages(restoreData)
        writePages(mifare, 4, 12, pages)
        writePages(mifare, 32, 129, pages)
    }

    /**
     * Remove the checksum bytes from the first two pages to get the actual uid
     */
    private fun uidFromPages(pages0_1: ByteArray): ByteArray? {
        if (pages0_1.size < 8) return null
        return byteArrayOf(
            pages0_1[0],
            pages0_1[1],
            pages0_1[2],
            pages0_1[4],
            pages0_1[5],
            pages0_1[6],
            pages0_1[7]
        )
    }

    private fun keygen(uuid: ByteArray?): ByteArray? {
        // from AmiiManage (GPL)
        if (null == uuid) return null
        val uuidIntArray = IntArray(uuid.size)
        uuid.forEachIndexed { i, byte ->
            uuidIntArray[i] = 0xFF and byte.toInt()
        }
        if (uuid.size == 7) {
            return byteArrayOf(
                (0xFF and (0xAA xor (uuidIntArray[1] xor uuidIntArray[3]))).toByte(),
                (0xFF and (0x55 xor (uuidIntArray[2] xor uuidIntArray[4]))).toByte(),
                (0xFF and (0xAA xor (uuidIntArray[3] xor uuidIntArray[5]))).toByte(),
                (0xFF and (0x55 xor (uuidIntArray[4] xor uuidIntArray[6]))).toByte()
            )
        }
        return null
    }

    @Throws(Exception::class)
    private fun doAuth(tag: NTAG215) {
        val pages01 = tag.readPages(0)
        if (null == pages01 || pages01.size != NfcByte.PAGE_SIZE * 4)
            throw IOException(TagMo.appContext.getString(R.string.fail_read))
        val uid = uidFromPages(pages01)
        val password = keygen(uid)
        Debug.verbose(TagWriter::class.java, R.string.password, password!!.toHex())
        val auth = byteArrayOf(
            0x1B.toByte(),
            password[0],
            password[1],
            password[2],
            password[3]
        )
        val response = tag.transceive(auth)
            ?: throw Exception(TagMo.appContext.getString(R.string.error_auth_null))
        val respStr = response.toHex()
        Debug.verbose(TagWriter::class.java, R.string.auth_response, respStr)
        if ("8080" != respStr) {
            throw Exception(TagMo.appContext.getString(R.string.fail_auth))
        }
    }

    private fun doEliteAuth(tag: NTAG215, password: ByteArray?): Boolean {
        var passBytes: ByteArray? = password
        if (null == passBytes || passBytes.size != 4) return false
        val req = ByteArray(5)
        req[0] = NfcByte.CMD_PWD_AUTH.toByte()
        passBytes = try {
            System.arraycopy(passBytes, 0, req, 1, 4)
            tag.transceive(req)
        } catch (e: Exception) {
            return false
        }
        return if (null == passBytes || passBytes.size != 2) {
            false
        } else {
            passBytes[0] == Byte.MIN_VALUE && passBytes[1] == Byte.MIN_VALUE
        }
    }

    @Throws(IOException::class)
    private fun writePassword(tag: NTAG215) {
        val pages01 = tag.readPages(0)
        if (null == pages01 || pages01.size != NfcByte.PAGE_SIZE * 4)
            throw IOException(TagMo.appContext.getString(R.string.fail_read))
        val uid = uidFromPages(pages01)
        val password = keygen(uid)
        Debug.verbose(TagWriter::class.java, R.string.password, password!!.toHex())
        Debug.verbose(TagWriter::class.java, R.string.write_pack)
        tag.writePage(0x86, byteArrayOf(0x80.toByte(), 0x80.toByte(), 0.toByte(), 0.toByte()))
        Debug.verbose(TagWriter::class.java, R.string.write_pwd)
        tag.writePage(0x85, password)
    }

    @Throws(IOException::class)
    private fun writeLockInfo(tag: NTAG215) {
        val pages = tag.readPages(0)
        if (null == pages || pages.size != NfcByte.PAGE_SIZE * 4)
            throw IOException(TagMo.appContext.getString(R.string.fail_read))
        tag.writePage(
            2, byteArrayOf(
                pages[2 * NfcByte.PAGE_SIZE],
                pages[2 * NfcByte.PAGE_SIZE + 1], 0x0F.toByte(), 0xE0.toByte()
            )
        ) // lock bits
        tag.writePage(130, byteArrayOf(0x01.toByte(), 0x00.toByte(), 0x0F.toByte(), 0x00.toByte()))
        // dynamic lock bits. should the last bit be 0xBD according to the nfc docs though:
        // Remark: Set all bits marked with RFUI to 0, when writing to the dynamic lock bytes.
        tag.writePage(
            131, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x04.toByte())
        ) // config
        tag.writePage(
            132, byteArrayOf(0x5F.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        ) // config
    }

    @Throws(Exception::class)
    fun wipeBankData(mifare: NTAG215, bankNumber: Int) {
        if (doEliteAuth(mifare, mifare.fastRead(0, 0))) {
            val tagData = String(CharArray(1080))
                    .replace("\u0000", "F").toHexByteArray()
            var write = mifare.amiiboFastWrite(0, bankNumber, tagData)
            if (!write) write = mifare.amiiboWrite(0, bankNumber, tagData)
            if (write) {
                val result = ByteArray(8)
                System.arraycopy(tagData, 84, result, 0, result.size)
                Debug.verbose(TagWriter::class.java, result.toHex())
            } else {
                throw Exception(TagMo.appContext.getString(R.string.error_elite_write))
            }
        } else {
            throw Exception(TagMo.appContext.getString(R.string.error_elite_write))
        }
    }

    private fun hexToByte(hex: String): Byte {
        var ret = 0.toByte()
        val hi = hex[0].code.toByte()
        val lo = hex[1].code.toByte()
        when {
            hi >= NfcByte.CMD_READ && hi <= NfcByte.CMD_READ_CNT ->
                ret = (hi - 0x30 shl 4).toByte()
            hi >= 0x41.toByte() && hi <= NfcByte.N2_LOCK ->
                ret = (hi - 0x41 + 0x0A shl 4).toByte()
            hi >= 0x61.toByte() && hi <= 0x66.toByte() ->
                ret = (hi - 0x61 + 0x0A shl 4).toByte()
        }
        return when {
            lo >= NfcByte.CMD_READ && lo <= NfcByte.CMD_READ_CNT ->
                (lo - 0x30 or ret.toInt()).toByte()
            lo >= 0x41.toByte() && lo <= NfcByte.N2_LOCK ->
                (lo - 0x41 + 0x0A or ret.toInt()).toByte()
            lo < 0x61.toByte() || lo > 0x66.toByte() -> ret
            else -> (lo - 0x61 + 0x0A or ret.toInt()).toByte()
        }
    }

    @Throws(Exception::class)
    fun updateFirmware(tag: NTAG215?): Boolean {
        if (null == tag) return false
        val context = TagMo.appContext
        var response: ByteArray? = ByteArray(1)
        response?.set(0, 0xFFFF.toByte())
        tag.initFirmware()
        tag.getVersion(true)
        return try {
            BufferedReader(InputStreamReader(
                context.resources.openRawResource(R.raw.firmware)
            )).use { br ->
                while (true) {
                    val strLine = br.readLine() ?: break
                    val parts = strLine.replace(
                        "\\s+".toRegex(), " "
                    ).split(" ".toRegex()).toTypedArray()
                    var i: Int
                    if (parts.isEmpty()) {
                        break
                    } else if (parts[0] == "C-APDU") {
                        val apduBuf = ByteArray(parts.size - 1)
                        i = 1
                        while (i < parts.size) {
                            apduBuf[i - 1] = hexToByte(parts[i])
                            i++
                        }
                        val sz: Int = apduBuf[4].toInt() and 0xFF
                        val isoCmd = ByteArray(sz)
                        if (apduBuf[4] + 5 <= apduBuf.size && apduBuf[4] <= isoCmd.size) {
                            i = 0
                            while (i < sz) {
                                isoCmd[i] = apduBuf[i + 5]
                                i++
                            }
                            var done = false
                            i = 0
                            while (i < 10) {
                                response = tag.transceive(isoCmd)
                                if (null != response) {
                                    done = true
                                    break
                                }
                                i++
                            }
                            if (!done)
                                throw Exception(context.getString(R.string.firmware_failed, 1))
                        }
                        return false
                    } else if (parts[0] == "C-RPDU") {
                        val rpduBuf = ByteArray(parts.size - 1)
                        if (response?.size != parts.size - 3)
                            throw Exception(context.getString(R.string.firmware_failed, 2))
                        i = 1
                        while (i < parts.size) {
                            rpduBuf[i - 1] = hexToByte(parts[i])
                            i++
                        }
                        i = 0
                        while (i < rpduBuf.size - 2) {
                            if (response?.get(i) != rpduBuf[i])
                                throw Exception(context.getString(R.string.firmware_failed, 3))
                            i++
                        }
                    } /* else if (!parts[0].equals("RESET") && parts[0].equals("LOGIN")) { } */
                }
                true
            }
        } catch (e: IOException) {
            throw Exception(context.getString(R.string.firmware_failed, 4))
        }
    }
}