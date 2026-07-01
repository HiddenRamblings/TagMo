package com.hiddenramblings.tagmo.amiibo

import android.content.Context
import com.hiddenramblings.tagmo.AmiiTool
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import java.io.DataInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class KeyManager(var context: Context) {
    private var fixedKey: ByteArray? = null
    private var unfixedKey: ByteArray? = null

    private fun hasLocalFile(file: String): Boolean {
        context.fileList().forEach {
            if (it == file) return true
        }
        return false
    }

    private fun loadKeyFromStorage(file: String): ByteArray? {
        try {
            context.openFileInput(file).use { fs ->
                val key = ByteArray(NfcByte.KEY_FILE_SIZE)
                if (fs.read(key) != NfcByte.KEY_FILE_SIZE)
                    throw IOException(context.getString(R.string.key_size_invalid))
                return key
            }
        } catch (e: Exception) { Debug.warn(R.string.key_read_error, e) }
        return null
    }

    fun hasFixedKey(): Boolean {
        if (hasLocalFile(FIXED_KEY_MD5)) fixedKey = loadKeyFromStorage(FIXED_KEY_MD5)
        return null != fixedKey
    }

    fun hasUnFixedKey(): Boolean {
        if (hasLocalFile(UNFIXED_KEY_MD5)) unfixedKey = loadKeyFromStorage(UNFIXED_KEY_MD5)
        return null != unfixedKey
    }

    val isKeyMissing: Boolean get() = run { return !hasFixedKey() || !hasUnFixedKey() }

    @Throws(IOException::class)
    fun saveKeyFile(file: String, key: ByteArray?): ByteArray? {
        context.openFileOutput(file, Context.MODE_PRIVATE).use { fos -> fos.write(key) }
        return loadKeyFromStorage(file)
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun getKeyFile(data: ByteArray, offset: Int = 0): String? {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(data, offset, NfcByte.KEY_FILE_SIZE)
        return when (md5.digest().toHex().uppercase()) {
            FIXED_KEY_MD5 -> FIXED_KEY_MD5
            UNFIXED_KEY_MD5 -> UNFIXED_KEY_MD5
            else -> null
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun readKey(data: ByteArray) {
        when (getKeyFile(data)) {
            FIXED_KEY_MD5 -> {
                fixedKey = saveKeyFile(FIXED_KEY_MD5, data)
            }
            UNFIXED_KEY_MD5 -> {
                unfixedKey = saveKeyFile(UNFIXED_KEY_MD5, data)
            }
            else -> throw IOException(context.getString(R.string.key_signature_error))
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun importEmbeddedKeys(data: ByteArray): Boolean {
        hasFixedKey()
        hasUnFixedKey()
        var imported = false
        var offset = 0
        while (offset <= data.size - NfcByte.KEY_FILE_SIZE) {
            getKeyFile(data, offset)?.let { file ->
                val key = data.copyOfRange(offset, offset + NfcByte.KEY_FILE_SIZE)
                if (file == FIXED_KEY_MD5 && fixedKey == null) {
                    fixedKey = saveKeyFile(file, key)
                    imported = true
                } else if (file == UNFIXED_KEY_MD5 && unfixedKey == null) {
                    unfixedKey = saveKeyFile(file, key)
                    imported = true
                }
                if (fixedKey != null && unfixedKey != null) return imported
            }
            offset++
        }
        return imported
    }

    @Throws(NoSuchAlgorithmException::class)
    fun removeEmbeddedKeys(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(data.size)
        var offset = 0
        while (offset < data.size) {
            if (offset <= data.size - NfcByte.KEY_FILE_SIZE && getKeyFile(data, offset) != null) {
                offset += NfcByte.KEY_FILE_SIZE
            } else {
                output.write(data[offset].toInt())
                offset++
            }
        }
        return output.toByteArray()
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun evaluateKey(data: ByteArray) {
        when {
            data.isEmpty() -> {
                throw IOException(context.getString(R.string.invalid_key_error))
            }
            data.size == NfcByte.KEY_RETAIL_SZ -> {
                readKey(data.copyOfRange(NfcByte.KEY_FILE_SIZE, data.size))
                readKey(data.copyOfRange(0, NfcByte.KEY_FILE_SIZE))
            }
            data.size == NfcByte.KEY_FILE_SIZE -> {
                readKey(data)
            }
            importEmbeddedKeys(data) -> {}
            else -> {
                throw IOException(context.getString(R.string.key_size_error))
            }
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun evaluateKey(strm: InputStream) {
        evaluateKey(DataInputStream(strm).readBytes())
    }

    @Throws(Exception::class)
    fun decrypt(tagData: ByteArray?): ByteArray {
        if (null == tagData) throw Exception(context.getString(R.string.fail_decrypt_null))
        if (!hasFixedKey() || !hasUnFixedKey())
            throw Exception(context.getString(R.string.key_not_present))
        val tool = AmiiTool()
        if ((fixedKey?.let { tool.setKeysFixed(it, it.size) } ?: 0) == 0)
                throw Exception(context.getString(R.string.error_amiitool_init))
        if ((unfixedKey?.let { tool.setKeysUnfixed(it, it.size) } ?: 0) == 0)
                throw Exception(context.getString(R.string.error_amiitool_init))
        val decrypted = ByteArray(NfcByte.TAG_DATA_SIZE)
        if (tool.unpack(tagData, NfcByte.TAG_DATA_SIZE, decrypted, decrypted.size) == 0)
            throw Exception(context.getString(R.string.fail_decrypt))
        return if (tagData.size == NfcByte.TAG_FULL_SIZE)
            ByteArray(8).copyInto(decrypted.copyInto(tagData), NfcByte.TAG_DATA_SIZE)
        else
            decrypted
    }

    @Throws(RuntimeException::class)
    fun encrypt(tagData: ByteArray): ByteArray {
        if (!hasFixedKey() || !hasUnFixedKey())
            throw RuntimeException(context.getString(R.string.key_not_present))
        val tool = AmiiTool()
        if ((fixedKey?.let { tool.setKeysFixed(it, it.size) } ?: 0) == 0)
            throw Exception(context.getString(R.string.error_amiitool_init))
        if ((unfixedKey?.let { tool.setKeysUnfixed(it, it.size) } ?: 0) == 0)
            throw Exception(context.getString(R.string.error_amiitool_init))
        val encrypted = ByteArray(NfcByte.TAG_DATA_SIZE)
        if (tool.pack(tagData, NfcByte.TAG_DATA_SIZE, encrypted, encrypted.size) == 0)
            throw RuntimeException(context.getString(R.string.fail_encrypt))
        return if (tagData.size == NfcByte.TAG_FULL_SIZE)
            ByteArray(8).copyInto(encrypted.copyInto(tagData), NfcByte.TAG_DATA_SIZE)
        else
            encrypted
    }

    companion object {
        private const val FIXED_KEY_MD5 = "0AD86557C7BA9E75C79A7B43BB466333"
        private const val UNFIXED_KEY_MD5 = "2551AFC7C8813008819836E9B619F7ED"
    }
}
