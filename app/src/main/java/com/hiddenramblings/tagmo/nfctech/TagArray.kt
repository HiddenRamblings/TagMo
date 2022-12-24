/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.nfctech

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.nfc.FormatException
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.*

object TagArray {
    @JvmStatic
    fun getTagTechnology(tag: Tag?): String {
        val context = TagMo.appContext
        var type = context.getString(R.string.unknown_type)
        if (null == tag) return type
        tag.techList.forEach {
            when {
                MifareClassic::class.java.name == it -> {
                    type = when (MifareClassic.get(tag).type) {
                        MifareClassic.TYPE_CLASSIC -> context.getString(R.string.mifare_classic)
                        MifareClassic.TYPE_PLUS -> context.getString(R.string.mifare_plus)
                        MifareClassic.TYPE_PRO -> context.getString(R.string.mifare_pro)
                        else -> context.getString(R.string.mifare_classic)
                    }
                    return type
                }
                MifareUltralight::class.java.name == it -> {
                    type = when (MifareUltralight.get(tag).type) {
                        MifareUltralight.TYPE_ULTRALIGHT -> context.getString(R.string.mifare_ultralight)
                        MifareUltralight.TYPE_ULTRALIGHT_C -> context.getString(R.string.mifare_ultralight_c)
                        else -> context.getString(R.string.mifare_ultralight)
                    }
                    return type
                }
                IsoDep::class.java.name == it -> {
                    return context.getString(R.string.isodep)
                }
                Ndef::class.java.name == it -> {
                    return context.getString(R.string.ndef)
                }
                NdefFormatable::class.java.name == it -> {
                    return context.getString(R.string.ndef_formatable)
                }
            }
        }
        return type
    }

    private val mPrefs = Preferences(TagMo.appContext)
    @JvmStatic
    fun isPowerTag(mifare: NTAG215?): Boolean {
        if (mPrefs.powerTagEnabled()) {
            val signature = mifare?.transceive(NfcByte.POWERTAG_SIG)
            return null != signature && compareRange(
                signature, NfcByte.POWERTAG_SIGNATURE, NfcByte.POWERTAG_SIGNATURE.size
            )
        }
        return false
    }

    @JvmStatic
    fun isElite(mifare: NTAG215?): Boolean {
        if (mPrefs.eliteEnabled()) {
            val signature = mifare?.readSignature(false)
            val page10 = hexToByteArray("FFFFFFFFFF")
            return null != signature && compareRange(
                signature, page10,
                32 - page10.size, signature.size
            )
        }
        return false
    }

    private fun compareRange(data: ByteArray, data2: ByteArray?, offset: Int, len: Int): Boolean {
        var i = 0
        var j = offset
        while (j < len) {
            if (data[j] != data2!![i]) return false
            i++
            j++
        }
        return true
    }

    @JvmStatic
    fun compareRange(data: ByteArray, data2: ByteArray?, len: Int): Boolean {
        return compareRange(data, data2, 0, len)
    }

    @JvmStatic
    fun bytesToHex(bytes: ByteArray?): String {
        val sb = java.lang.StringBuilder()
        bytes?.forEach {
            sb.append(String.format("%02X", it))
        }
        return sb.toString()
    }

    @JvmStatic
    fun hexToByteArray(s: String): ByteArray {
        return try {
            ByteArray(s.length / 2) {
                Integer.parseInt(s, it * 2, (it + 1) * 2, 16).toByte()
            }
        } catch (e: NumberFormatException) {
            val byterator = s.chunkedSequence(2).map { it.toInt(16).toByte() }.iterator()
            ByteArray(s.length / 2) { byterator.next() }
        }
    }

    fun longToBytes(x: Long): ByteArray {
        return ByteBuffer.allocate(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) java.lang.Long.BYTES else 8
        ).putLong(x).array()
    }

    fun bytesToLong(bytes: ByteArray?): Long {
        val buffer = ByteBuffer.allocate(
            if (Debug.isNewer(Build.VERSION_CODES.N)) java.lang.Long.BYTES else 8
        )
        if (bytes != null) {
            buffer.put(bytes)
            buffer.flip() // need flip
        }
        return try {
            buffer.long
        } catch (bue: BufferUnderflowException) {
            buffer.int.toLong()
        }
    }

    fun hexToLong(hex: String): Long {
        var result: Long = 0
        try {
            result = hex.toLong(16)
        } catch (nf: NumberFormatException) {
            for (i in hex.indices) {
                result = (result shl 4) + Character.digit(hex[i], 16).toLong()
            }
        }
        return result
    }

    fun hexToString(hex: String): String {
        val output = StringBuilder()
        var i = 0
        while (i < hex.length) {
            output.append(hex.substring(i, i + 2).toInt(16).toChar())
            i += 2
        }
        return output.toString()
    }

    fun bytesToString(bytes: ByteArray?): String {
        return hexToString(bytesToHex(bytes))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validateData(data: ByteArray?) {
        val context = TagMo.appContext
        if (null == data) throw IOException(context.getString(R.string.invalid_data_null))
        /* TagWriter.splitPages(data) */
        if (data.size == NfcByte.KEY_FILE_SIZE || data.size == NfcByte.KEY_RETAIL_SZ)
            throw IOException(context.getString(R.string.invalid_tag_key))
        else if (data.size < NfcByte.TAG_DATA_SIZE)
            throw IOException(context.getString(
                R.string.invalid_data_size, data.size, NfcByte.TAG_DATA_SIZE
            ))
        val pages = arrayOfNulls<ByteArray>(data.size / NfcByte.PAGE_SIZE)
        var i = 0
        var j = 0
        while (i < data.size) {
            pages[j] = Arrays.copyOfRange(data, i, i + NfcByte.PAGE_SIZE)
            i += NfcByte.PAGE_SIZE
            j++
        }
        if (pages[0]!![0] != 0x04.toByte()) throw Exception(context.getString(R.string.invalid_tag_prefix))
        if (pages[2]!![2] != 0x0F.toByte() || pages[2]!![3] != 0xE0.toByte())
            throw Exception(context.getString(R.string.invalid_tag_lock))
        if (pages[3]!![0] != 0xF1.toByte() || pages[3]!![1] != 0x10.toByte()
            || pages[3]!![2] != 0xFF.toByte() || pages[3]!![3] != 0xEE.toByte())
            throw Exception(context.getString(R.string.invalid_tag_cc))
        if (pages[0x82]!![0] != 0x01.toByte() || pages[0x82]!![1] != 0x0.toByte()
            || pages[0x82]!![2] != 0x0F.toByte())
            throw Exception(context.getString(R.string.invalid_tag_dynamic))
        if (pages[0x83]!![0] != 0x0.toByte() || pages[0x83]!![1] != 0x0.toByte()
            || pages[0x83]!![2] != 0x0.toByte() || pages[0x83]!![3] != 0x04.toByte())
            throw Exception(context.getString(R.string.invalid_tag_cfg_zero))
        if (pages[0x84]!![0] != 0x5F.toByte() || pages[0x84]!![1] != 0x0.toByte()
            || pages[0x84]!![2] != 0x0.toByte() || pages[0x84]!![3] != 0x00.toByte())
            throw Exception(context.getString(R.string.invalid_tag_cfg_one))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validateNtag(mifare: NTAG215, tagData: ByteArray?, validateNtag: Boolean) {
        val context = TagMo.appContext
        if (null == tagData) throw IOException(context.getString(R.string.no_source_data))
        if (validateNtag) {
            try {
                val versionInfo = mifare.transceive(byteArrayOf(0x60.toByte()))
                if (null == versionInfo || versionInfo.size != 8) throw Exception(
                    context.getString(
                        R.string.error_tag_version
                    )
                )
                if (versionInfo[0x02] != 0x04.toByte() || versionInfo[0x06] != 0x11.toByte()) throw FormatException(
                    context.getString(R.string.error_tag_specs)
                )
            } catch (e: Exception) {
                Debug.warn(R.string.error_version, e)
                throw e
            }
        }
        val pages = mifare.readPages(0)
        if (null == pages || pages.size != NfcByte.PAGE_SIZE * 4)
            throw Exception(context.getString(
                R.string.fail_read_size
            ))
        if (!compareRange(pages, tagData, 9))
            throw Exception(context.getString(R.string.fail_mismatch_uid))
        Debug.info(TagWriter::class.java, R.string.validation_success)
    }

    @JvmStatic
    fun decipherFilename(amiibo: Amiibo, tagData: ByteArray?, verified: Boolean): String {
        var status = ""
        if (verified) {
            status = try {
                validateData(tagData)
                "Validated"
            } catch (e: Exception) {
                Debug.warn(e)
                Debug.getExceptionSummary(e)
            }
        }
        try {
            val name = amiibo.name?.replace(File.separatorChar, '-')
            val uidHex = bytesToHex(tagData?.copyOfRange(0, 9))
            return if (verified) String.format(
                Locale.ROOT, "%1\$s[%2\$s]-%3\$s.bin", name, uidHex, status
            ) else String.format(
                Locale.ROOT, "%1\$s[%2\$s].bin", name, uidHex
            )
        } catch (ex: Exception) {
            Debug.warn(ex)
        }
        return ""
    }

    @JvmStatic
    fun decipherFilename(
        amiiboManager: AmiiboManager?, tagData: ByteArray?, verified: Boolean
    ): String {
        try {
            val amiiboId = Amiibo.dataToId(tagData)
            if (null != amiiboManager) {
                return decipherFilename(amiiboManager.amiibos[amiiboId]!!, tagData, verified)
            }
        } catch (ex: Exception) {
            Debug.warn(ex)
        }
        return ""
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getValidatedData(keyManager: KeyManager?, data: ByteArray?): ByteArray? {
        if (null == keyManager || null == data) return null
        var validated = data
        try {
            validateData(validated)
            validated = keyManager.decrypt(validated)
        } catch (e: Exception) {
            validated = keyManager.encrypt(validated)
            validateData(validated)
            validated = keyManager.decrypt(validated)
        }
        return keyManager.encrypt(validated!!)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getValidatedFile(keyManager: KeyManager?, file: File): ByteArray? {
        return getValidatedData(keyManager, TagReader.readTagFile(file))
    }

    @Throws(Exception::class)
    fun getValidatedDocument(keyManager: KeyManager?, fileUri: Uri): ByteArray? {
        return getValidatedData(keyManager, TagReader.readTagDocument(fileUri))
    }

    @Throws(Exception::class)
    fun getValidatedDocument(
        keyManager: KeyManager?, file: DocumentFile
    ): ByteArray? {
        return getValidatedData(keyManager, TagReader.readTagDocument(file.uri))
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getValidatedData(keyManager: KeyManager?, file: AmiiboFile): ByteArray {
        return if (null != file.data) file.data!! else (
                if (null != file.docUri) getValidatedDocument(keyManager, file.docUri!!
        ) else getValidatedFile(keyManager, file.filePath!!))!!
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeBytesToFile(directory: File?, name: String, tagData: ByteArray?): String {
        val binFile = File(directory, name)
        FileOutputStream(binFile).write(tagData)
        try {
            MediaScannerConnection.scanFile(
                TagMo.appContext, arrayOf(binFile.absolutePath), null, null
            )
        } catch (e: Exception) {
            Debug.info(e)
        }
        return binFile.absolutePath
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeBytesToDocument(
        context: Context, directory: DocumentFile, name: String, tagData: ByteArray?
    ): String? {
        val newFile = directory.createFile(
            context.resources.getStringArray(R.array.mimetype_bin)[0], name
        )
        if (null != newFile) {
            val docWriter = context.contentResolver.openOutputStream(newFile.uri)
            docWriter!!.write(tagData)
            docWriter.close()
        }
        return newFile?.name
    }
}