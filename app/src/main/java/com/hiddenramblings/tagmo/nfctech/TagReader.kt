package com.hiddenramblings.tagmo.nfctech

import android.net.Uri
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo.Companion.appContext
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.Foomiibo.Companion.getDataSignature
import com.hiddenramblings.tagmo.nfctech.TagArray.bytesToHex
import java.io.*
import java.util.*

object TagReader {
    private const val BULK_READ_PAGE_COUNT = 4
    @Throws(IOException::class)
    fun validateBlankTag(mifare: NTAG215) {
        val lockPage = mifare.readPages(0x02)
        Debug.info(TagWriter::class.java, bytesToHex(lockPage!!))
        if (lockPage[2] == 0x0F.toByte() && lockPage[3] == 0xE0.toByte()) {
            throw IOException(appContext.getString(R.string.error_tag_rewrite))
        }
        Debug.info(TagWriter::class.java, R.string.validation_success)
    }

    @Throws(Exception::class)
    private fun getTagData(path: String?, inputStream: InputStream): ByteArray {
        return when (val length = inputStream.available()) {
            NfcByte.KEY_FILE_SIZE, NfcByte.KEY_RETAIL_SZ -> throw IOException(
                appContext.getString(
                    R.string.invalid_tag_key
                )
            )
            NfcByte.TAG_FILE_SIZE -> {
                val signed = ByteArray(NfcByte.TAG_FILE_SIZE)
                DataInputStream(inputStream).readFully(signed)
                getDataSignature(signed)
                signed.copyOfRange(0, NfcByte.TAG_DATA_SIZE)
            }
            NfcByte.TAG_DATA_SIZE, NfcByte.TAG_DATA_SIZE + 8 -> {
                val tagData = ByteArray(NfcByte.TAG_DATA_SIZE)
                DataInputStream(inputStream).readFully(tagData)
                tagData
            }
            else -> throw IOException(appContext.getString(
                    R.string.invalid_file_size, path, length, NfcByte.TAG_DATA_SIZE
            ))
        }
    }

    @Throws(Exception::class)
    fun readTagFile(file: File): ByteArray {
        FileInputStream(file).use { inputStream -> return getTagData(file.path, inputStream) }
    }

    @Throws(Exception::class)
    fun readTagDocument(uri: Uri): ByteArray {
        appContext.contentResolver.openInputStream(uri)
            .use { inputStream -> return getTagData(uri.path, inputStream!!) }
    }

    @Throws(Exception::class)
    fun readFromTag(tag: NTAG215?): ByteArray {
        val tagData = ByteArray(NfcByte.TAG_DATA_SIZE)
        val pageCount = NfcByte.TAG_DATA_SIZE / NfcByte.PAGE_SIZE
        var i = 0
        while (i < pageCount) {
            val pages = tag?.readPages(i)
            if (null == pages || pages.size != NfcByte.PAGE_SIZE * BULK_READ_PAGE_COUNT) throw IOException(
                appContext
                    .getString(R.string.fail_invalid_size)
            )
            val dstIndex = i * NfcByte.PAGE_SIZE
            val dstCount = (BULK_READ_PAGE_COUNT * NfcByte.PAGE_SIZE).coerceAtMost(tagData.size - dstIndex)
            System.arraycopy(pages, 0, tagData, dstIndex, dstCount)
            i += BULK_READ_PAGE_COUNT
        }
        Debug.info(TagReader::class.java, bytesToHex(tagData))
        return tagData
    }

    private fun readBankTitle(tag: NTAG215, bank: Int): ByteArray? {
        return tag.amiiboFastRead(0x15, 0x16, bank)
    }

    @Throws(NullPointerException::class)
    fun readTagTitles(tag: NTAG215, numBanks: Int): ArrayList<String> {
        val tags = ArrayList<String>()
        var i = 0
        while (i < numBanks and 0xFF) {
            try {
                val tagData = readBankTitle(tag, i)
                if (tagData?.size != 8) {
                    throw NullPointerException()
                }
                tags.add(bytesToHex(tagData))
                i++
            } catch (e: Exception) {
                Debug.warn(TagReader::class.java, appContext.getString(R.string.fail_parse_banks))
            }
        }
        return tags
    }

    fun getBankParams(tag: NTAG215?): ByteArray? {
        return tag?.getVersion(false)
    }

    fun getBankSignature(tag: NTAG215): String? {
        val signature = tag.readSignature(false)
        return if (null != signature) bytesToHex(signature).substring(0, 22) else null
    }

    @Throws(IllegalStateException::class, NullPointerException::class)
    fun scanTagToBytes(tag: NTAG215, bank: Int): ByteArray {
        val tagData = ByteArray(NfcByte.TAG_DATA_SIZE)
        return try {
            val data = (if (bank == -1) tag.fastRead(0x00, 0x86)
            else tag.amiiboFastRead(0x00, 0x86, bank))
                ?: throw NullPointerException(appContext.getString(R.string.fail_read_amiibo))
            System.arraycopy(data, 0, tagData, 0, NfcByte.TAG_DATA_SIZE)
            tagData
        } catch (e: IllegalStateException) {
            throw IllegalStateException(appContext.getString(R.string.fail_early_remove))
        } catch (npe: NullPointerException) {
            throw NullPointerException(appContext.getString(R.string.fail_amiibo_null))
        }
    }

    @Throws(IllegalStateException::class, NullPointerException::class)
    fun scanBankToBytes(tag: NTAG215, bank: Int): ByteArray {
        val context = appContext
        val tagData = ByteArray(NfcByte.TAG_DATA_SIZE)
        return try {
            val data = tag.amiiboFastRead(0x00, 0x86, bank)
                ?: throw NullPointerException(context.getString(R.string.fail_read_amiibo))
            System.arraycopy(data, 0, tagData, 0, NfcByte.TAG_DATA_SIZE)
            Debug.info(TagReader::class.java, bytesToHex(tagData))
            tagData
        } catch (e: IllegalStateException) {
            throw IllegalStateException(context.getString(R.string.fail_early_remove))
        } catch (npe: NullPointerException) {
            throw NullPointerException(context.getString(R.string.fail_amiibo_null))
        }
    }

    fun needsFirmware(tag: NTAG215?): Boolean {
        val version = getBankParams(tag)
        return !((version?.size != 4 || version[3] == 0x03.toByte())
                && !(version?.size == 2 && version[0].toInt() == 100 && version[1].toInt() == 0))
    }
}