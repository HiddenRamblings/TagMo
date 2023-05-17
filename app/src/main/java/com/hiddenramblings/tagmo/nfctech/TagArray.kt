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
import android.text.Editable
import androidx.documentfile.provider.DocumentFile
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.KeyManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.*


object TagArray {
    @JvmStatic
    fun Tag?.technology(): String {
        with (TagMo.appContext) {
            var type = getString(R.string.unknown_type)
            if (null == this@technology) return type
            techList.forEach {
                when {
                    MifareClassic::class.java.name == it -> {
                        type = when (MifareClassic.get(this@technology).type) {
                            MifareClassic.TYPE_CLASSIC -> getString(R.string.mifare_classic)
                            MifareClassic.TYPE_PLUS -> getString(R.string.mifare_plus)
                            MifareClassic.TYPE_PRO -> getString(R.string.mifare_pro)
                            else -> getString(R.string.mifare_classic)
                        }
                        return type
                    }
                    MifareUltralight::class.java.name == it -> {
                        type = when (MifareUltralight.get(this@technology).type) {
                            MifareUltralight.TYPE_ULTRALIGHT -> getString(R.string.mifare_ultralight)
                            MifareUltralight.TYPE_ULTRALIGHT_C -> getString(R.string.mifare_ultralight_c)
                            else -> getString(R.string.mifare_ultralight)
                        }
                        return type
                    }
                    IsoDep::class.java.name == it -> { return getString(R.string.isodep) }
                    Ndef::class.java.name == it -> { return getString(R.string.ndef) }
                    NdefFormatable::class.java.name == it -> { return getString(R.string.ndef_formatable) }
                }
            }
            return type
        }
    }

    private val mPrefs = Preferences(TagMo.appContext)
    @JvmStatic
    fun isPowerTag(mifare: NTAG215?): Boolean {
        if (mPrefs.powerTagEnabled()) {
            return mifare?.transceive(NfcByte.POWERTAG_SIG)?.let {
                compareRange(it, NfcByte.POWERTAG_SIGNATURE, NfcByte.POWERTAG_SIGNATURE.size)
            } ?: false
        }
        return false
    }

    @JvmStatic
    fun isElite(mifare: NTAG215?): Boolean {
        if (mPrefs.eliteEnabled()) {
            val signature = mifare?.readSignature(false)
            val page10 = hexToByteArray("FFFFFFFFFF")
            return signature?.let {
                compareRange(it, page10, 32 - page10.size, it.size)
            } ?: false
        }
        return false
    }

    private fun compareRange(data: ByteArray, data2: ByteArray?, offset: Int, len: Int): Boolean {
        var i = 0
        var j = offset
        while (j < len) {
            if (data2?.get(i) != data[j]) return false
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
        val sb = StringBuilder()
        bytes?.forEach { sb.append(String.format("%02X", it)) }
        return sb.toString()
    }

    @JvmStatic
    fun hexToByteArray(s: String): ByteArray {
        val len: Int = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun longToBytes(x: Long): ByteArray {
        return ByteBuffer.allocate(
            if (Version.isNougat) java.lang.Long.BYTES else 8
        ).putLong(x).array()
    }

    fun bytesToLong(bytes: ByteArray): Long {
        val buffer = ByteBuffer.allocate(
            if (Version.isNougat) java.lang.Long.BYTES else 8
        ).put(bytes).also { it.flip() }
        return try {
            buffer.long
        } catch (bue: BufferUnderflowException) {
            try {
                buffer.int.toLong()
            } catch (bue: BufferUnderflowException) {
                buffer.short.toLong()
            }
        }
    }

    fun hexToLong(hex: String): Long {
        var result: Long = 0
        try {
            result = hex.toLong(16)
        } catch (nf: NumberFormatException) {
            hex.forEach { result = (result shl 4) + Character.digit(it, 16).toLong() }
        }
        return result
    }

    fun hexToString(hex: String): String {
        val output = StringBuilder()
        var i = 0
        while (i < hex.length) {
            try {
                output.append(Integer.parseInt(hex.substring(i, i + 2), 16).toChar())
            } catch (nf: NumberFormatException) {
                Debug.warn(nf)
                output.clear()
                break
            }
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
        with (TagMo.appContext) {
            if (null == data) throw IOException(getString(R.string.invalid_data_null))
            /* TagWriter.splitPages(data) */
            if (data.size == NfcByte.KEY_FILE_SIZE || data.size == NfcByte.KEY_RETAIL_SZ)
                throw IOException(getString(R.string.invalid_tag_key))
            else if (data.size < NfcByte.TAG_DATA_SIZE)
                throw IOException(
                    getString(R.string.invalid_data_size, data.size, NfcByte.TAG_DATA_SIZE)
                )
            val pages = arrayOfNulls<ByteArray>(data.size / NfcByte.PAGE_SIZE)
            var i = 0
            var j = 0
            while (i < data.size) {
                pages[j] = Arrays.copyOfRange(data, i, i + NfcByte.PAGE_SIZE)
                i += NfcByte.PAGE_SIZE
                j++
            }
            when {
                pages[0]?.let {
                    it[0] != 0x04.toByte()
                } ?: true -> throw Exception(getString(R.string.invalid_tag_prefix))

                pages[2]?.let {
                    it[2] != 0x0F.toByte() || it[3] != 0xE0.toByte()
                } ?: true -> throw Exception(getString(R.string.invalid_tag_lock))

                pages[3]?.let {
                    it[0] != 0xF1.toByte() || it[1] != 0x10.toByte()
                            || it[2] != 0xFF.toByte() || it[3] != 0xEE.toByte()
                } ?: true -> throw Exception(getString(R.string.invalid_tag_cc))

                pages[0x82]?.let {
                    it[0] != 0x01.toByte() || it[1] != 0x0.toByte() || it[2] != 0x0F.toByte()
                } ?: true -> throw Exception(getString(R.string.invalid_tag_dynamic))

                pages[0x83]?.let {
                    it[0] != 0x0.toByte() || it[1] != 0x0.toByte()
                            || it[2] != 0x0.toByte() || it[3] != 0x04.toByte()
                } ?: true -> throw Exception(getString(R.string.invalid_tag_cfg_zero))

                pages[0x84]?.let {
                    it[0] != 0x5F.toByte() || it[1] != 0x0.toByte()
                            || it[2] != 0x0.toByte() || it[3] != 0x00.toByte()
                } ?: true -> throw Exception(getString(R.string.invalid_tag_cfg_one))

                else -> {}
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun validateNtag(mifare: NTAG215, tagData: ByteArray?, validateNtag: Boolean) {
        with (TagMo.appContext) {
            if (null == tagData) throw IOException(getString(R.string.no_source_data))
            if (validateNtag) {
                try {
                    mifare.transceive(byteArrayOf(0x60.toByte()))?.let {
                        if (it.size != 8) throw Exception(getString(R.string.error_tag_version))
                        if (it[0x02] != 0x04.toByte() || it[0x06] != 0x11.toByte())
                            throw FormatException(getString(R.string.error_tag_specs))
                    } ?: throw Exception(getString(R.string.error_tag_version))
                } catch (e: Exception) {
                    Debug.warn(R.string.error_version, e)
                    throw e
                }
            }
            val pages = mifare.readPages(0)
            if (null == pages || pages.size != NfcByte.PAGE_SIZE * 4)
                throw Exception(getString(R.string.fail_read_size))
            if (!compareRange(pages, tagData, 9))
                throw Exception(getString(R.string.fail_mismatch_uid))
            Debug.info(TagWriter::class.java, R.string.validation_success)
        }
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
                Debug.getExceptionClass(e)
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
            amiiboManager?.let {
                return it.amiibos[Amiibo.dataToId(tagData)]?.let { amiibo ->
                    decipherFilename(amiibo, tagData, verified)
                } ?: ""
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
        } catch (e: Exception) {
            validated = keyManager.encrypt(validated)
            validateData(validated)
        }
        validated = keyManager.decrypt(validated)
        return keyManager.encrypt(validated)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getValidatedFile(keyManager: KeyManager?, file: File?): ByteArray? {
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
    fun getValidatedData(keyManager: KeyManager?, file: AmiiboFile): ByteArray? {
        return file.data ?: file.docUri?.let { getValidatedDocument(keyManager, it) }
        ?: file.filePath?.let { getValidatedFile(keyManager, it) }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun writeBytesToFile(directory: File?, name: String, tagData: ByteArray?): String {
        val binFile = File(directory, name)
        FileOutputStream(binFile).use { it.write(tagData) }
        try {
            MediaScannerConnection.scanFile(
                TagMo.appContext, arrayOf(binFile.absolutePath), null, null
            )
        } catch (e: Exception) { Debug.info(e) }
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
        newFile?.let { file ->
            context.contentResolver.openOutputStream(file.uri).use { it?.write(tagData) }
        }
        return newFile?.name
    }

    fun writeBytesWithName(context: Context, fileName: String?, tagData: ByteArray?) : String? {
        return with (Preferences(context.applicationContext)) {
            fileName?.let { name ->
                if (isDocumentStorage) {
                    val rootDocument = browserRootDocument()?.let { uri ->
                        DocumentFile.fromTreeUri(context, Uri.parse(uri))
                    } ?: throw NullPointerException()
                    writeBytesToDocument(context, rootDocument, name, tagData)
                } else {
                    writeBytesToFile(
                        Storage.getDownloadDir("TagMo", "Backups"), name, tagData
                    )
                }
            }
        }
    }

    fun writeBytesWithName(context: Context, input: Editable?, tagData: ByteArray?) : String? {
        return writeBytesWithName(context, input?.toString(), tagData)
    }
}