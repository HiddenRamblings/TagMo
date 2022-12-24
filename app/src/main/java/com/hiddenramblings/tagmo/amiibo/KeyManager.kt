package com.hiddenramblings.tagmo.amiibo

import android.content.Context
import com.hiddenramblings.tagmo.AmiiTool
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.TagArray
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class KeyManager(var context: Context) {
    private var fixedKey: ByteArray? = null
    private var unfixedKey: ByteArray? = null

    init {
        isKeyMissing
    }

    private fun hasLocalFile(file: String): Boolean {
        val files = context.fileList()
        for (file1 in files) {
            if (file1 == file) return true
        }
        return false
    }

    private fun loadKeyFromStorage(file: String): ByteArray? {
        try {
            context.openFileInput(file).use { fs ->
                val key = ByteArray(NfcByte.KEY_FILE_SIZE)
                if (fs.read(key) != NfcByte.KEY_FILE_SIZE) throw IOException(context.getString(R.string.key_size_invalid))
                return key
            }
        } catch (e: Exception) {
            Debug.warn(R.string.key_read_error, e)
        }
        return null
    }

    fun hasFixedKey(): Boolean {
        if (hasLocalFile(FIXED_KEY_MD5))
            fixedKey = loadKeyFromStorage(FIXED_KEY_MD5)
        return null != fixedKey
    }

    fun hasUnFixedKey(): Boolean {
        if (hasLocalFile(UNFIXED_KEY_MD5))
            unfixedKey = loadKeyFromStorage(UNFIXED_KEY_MD5)
        return null != unfixedKey
    }

    val isKeyMissing: Boolean
        get() = !hasFixedKey() || !hasUnFixedKey()

    @Throws(IOException::class)
    fun saveKeyFile(file: String?, key: ByteArray?) {
        context.openFileOutput(file, Context.MODE_PRIVATE).use { fos -> fos.write(key) }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun evaluateKey(strm: InputStream) {
        val length = strm.available()
        when {
            length <= 0 -> {
                throw IOException(context.getString(R.string.invalid_key_error))
            }
            length == NfcByte.KEY_RETAIL_SZ -> {
                val data = ByteArray(NfcByte.KEY_RETAIL_SZ)
                DataInputStream(strm).readFully(data)
                readKey(data.copyOfRange(NfcByte.KEY_FILE_SIZE, data.size))
                readKey(data.copyOfRange(0, NfcByte.KEY_FILE_SIZE))
            }
            length == NfcByte.KEY_FILE_SIZE -> {
                val data = ByteArray(NfcByte.KEY_FILE_SIZE)
                DataInputStream(strm).readFully(data)
                readKey(data)
            }
            else -> {
                throw IOException(context.getString(R.string.key_size_error))
            }
        }
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun readKey(data: ByteArray) {
        val md5 = MessageDigest.getInstance("MD5")
        val hex = TagArray.bytesToHex(md5.digest(data)).uppercase()
        if (FIXED_KEY_MD5 == hex) {
            saveKeyFile(FIXED_KEY_MD5, data)
            fixedKey = loadKeyFromStorage(FIXED_KEY_MD5)
        } else if (UNFIXED_KEY_MD5 == hex) {
            saveKeyFile(UNFIXED_KEY_MD5, data)
            unfixedKey = loadKeyFromStorage(UNFIXED_KEY_MD5)
        } else {
            throw IOException(context.getString(R.string.key_signature_error))
        }
    }

    @Throws(Exception::class)
    fun decrypt(tagData: ByteArray?): ByteArray {
        if (null == tagData)
            throw Exception(context.getString(R.string.fail_decrypt))
        if (!hasFixedKey() || !hasUnFixedKey())
            throw Exception(context.getString(R.string.key_not_present))
        val tool = AmiiTool()
        if (tool.setKeysFixed(fixedKey, fixedKey!!.size) == 0)
            throw Exception(context.getString(R.string.error_amiitool_init))
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey!!.size) == 0)
            throw Exception(context.getString(R.string.error_amiitool_init))
        val decrypted = ByteArray(NfcByte.TAG_DATA_SIZE)
        if (tool.unpack(tagData, tagData.size, decrypted, decrypted.size) == 0)
            throw Exception(context.getString(R.string.fail_decrypt))
        return decrypted
    }

    @Throws(RuntimeException::class)
    fun encrypt(tagData: ByteArray): ByteArray {
        if (!hasFixedKey() || !hasUnFixedKey())
            throw RuntimeException(context.getString(R.string.key_not_present))
        val tool = AmiiTool()
        if (tool.setKeysFixed(
                fixedKey, fixedKey!!.size) == 0
        ) throw RuntimeException(context.getString(R.string.error_amiitool_init))
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey!!.size) == 0) throw RuntimeException(
            context.getString(R.string.error_amiitool_init)
        )
        val encrypted = ByteArray(NfcByte.TAG_DATA_SIZE)
        if (tool.pack(
                tagData, tagData.size, encrypted, encrypted.size) == 0
        ) throw RuntimeException(
            context.getString(R.string.fail_encrypt)
        )
        return encrypted
    }

    companion object {
        private const val FIXED_KEY_MD5 = "0AD86557C7BA9E75C79A7B43BB466333"
        private const val UNFIXED_KEY_MD5 = "2551AFC7C8813008819836E9B619F7ED"
    }
}