package com.hiddenramblings.tagmo.amiibo

import android.content.Context
import com.hiddenramblings.tagmo.AmiiTool
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.io.Debug.Warn
import com.hiddenramblings.tagmo.nfctech.NfcByte
import com.hiddenramblings.tagmo.nfctech.TagArray
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class KeyManager(var context: Context) {
    var fixedKey: ByteArray? = null
    var unfixedKey: ByteArray? = null

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
            Warn(R.string.key_read_error, e)
        }
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

    val isKeyMissing: Boolean
        get() = !hasFixedKey() || !hasUnFixedKey()

    @Throws(IOException::class)
    fun saveKeyFile(file: String?, key: ByteArray?) {
        context.openFileOutput(file, Context.MODE_PRIVATE).use { fos -> fos.write(key) }
    }

    @Throws(IOException::class)
    fun evaluateKey(strm: InputStream) {
        val length = strm.available()
        if (length <= 0) {
            throw IOException(context.getString(R.string.invalid_key_error))
        } else if (length == NfcByte.KEY_FILE_SIZE * 2) {
            val data = ByteArray(NfcByte.KEY_FILE_SIZE * 2)
            DataInputStream(strm).readFully(data)
            val key2 = ByteArray(NfcByte.KEY_FILE_SIZE)
            System.arraycopy(data, NfcByte.KEY_FILE_SIZE, key2, 0, NfcByte.KEY_FILE_SIZE)
            readKey(key2)
            val key1 = ByteArray(NfcByte.KEY_FILE_SIZE)
            System.arraycopy(data, 0, key1, 0, NfcByte.KEY_FILE_SIZE)
            readKey(key1)
        } else if (length == NfcByte.KEY_FILE_SIZE) {
            val data = ByteArray(NfcByte.KEY_FILE_SIZE)
            DataInputStream(strm).readFully(data)
            readKey(data)
        } else {
            throw IOException(context.getString(R.string.key_size_error))
        }
    }

    @Throws(IOException::class)
    private fun readKey(data: ByteArray) {
        var md5: String? = null
        try {
            val digest = MessageDigest.getInstance("MD5")
            val result = digest.digest(data)
            md5 = TagArray.bytesToHex(result)
        } catch (e: NoSuchAlgorithmException) {
            Warn(e)
        }
        if (FIXED_KEY_MD5 == md5) {
            saveKeyFile(FIXED_KEY_MD5, data)
            fixedKey = loadKeyFromStorage(FIXED_KEY_MD5)
        } else if (UNFIXED_KEY_MD5 == md5) {
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
        if (tool.setKeysFixed(
                fixedKey,
                fixedKey!!.size
            ) == 0
        ) throw Exception(context.getString(R.string.error_amiitool_init))
        if (tool.setKeysUnfixed(
                unfixedKey,
                unfixedKey!!.size
            ) == 0
        ) throw Exception(context.getString(R.string.error_amiitool_init))
        val decrypted = ByteArray(NfcByte.TAG_DATA_SIZE)
        if (tool.unpack(tagData, tagData.size, decrypted, decrypted.size) == 0) throw Exception(
            context.getString(R.string.fail_decrypt)
        )
        return decrypted
    }

    @Throws(RuntimeException::class)
    fun encrypt(tagData: ByteArray): ByteArray {
        if (!hasFixedKey() || !hasUnFixedKey()) throw RuntimeException(context.getString(R.string.key_not_present))
        val tool = AmiiTool()
        if (tool.setKeysFixed(
                fixedKey,
                fixedKey!!.size
            ) == 0
        ) throw RuntimeException(context.getString(R.string.error_amiitool_init))
        if (tool.setKeysUnfixed(unfixedKey, unfixedKey!!.size) == 0) throw RuntimeException(
            context.getString(R.string.error_amiitool_init)
        )
        val encrypted = ByteArray(NfcByte.TAG_DATA_SIZE)
        if (tool.pack(
                tagData,
                tagData.size,
                encrypted,
                encrypted.size
            ) == 0
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