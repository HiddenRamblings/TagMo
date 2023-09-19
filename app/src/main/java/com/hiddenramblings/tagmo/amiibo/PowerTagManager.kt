package com.hiddenramblings.tagmo.amiibo

import android.util.Base64
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.nfctech.TagArray.toHex
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object PowerTagManager {
    // private static final String POWERTAG_KEYTABLE_FILE = "keytable.json";
    private var keys: HashMap<String, HashMap<String, ByteArray>>? = null

    @Throws(JSONException::class)
    private fun parseKeyTable(json: JSONObject) {
        val keytable = hashMapOf<String, HashMap<String, ByteArray>>()
        val uidIterator = json.keys()
        while (uidIterator.hasNext()) {
            val uid = uidIterator.next()
            val pageKeys = json.getJSONObject(uid)
            val keyvalues = hashMapOf<String, ByteArray>()
            keytable[uid] = keyvalues
            val pageByteIterator = pageKeys.keys()
            while (pageByteIterator.hasNext()) {
                val pageBytes = pageByteIterator.next()
                val keyStr = pageKeys.getString(pageBytes)
                val key = Base64.decode(keyStr, Base64.DEFAULT)
                keyvalues[pageBytes] = key
            }
        }
        keys = keytable
    }

    @JvmStatic
    @Throws(Exception::class)
    fun setPowerTagManager() {
        if (null != keys) return
        TagMo.appContext.resources.openRawResource(R.raw.keytable).use { stream ->
            ByteArray(stream.available()).also {
                stream.read(it)
                parseKeyTable(JSONObject(String(it)))
            }
        }
    }

    @Suppress("unused")
    fun resetPowerTag(): Boolean {
        try {
            TagMo.appContext.resources.openRawResource(R.raw.powertag).use { stream ->
                ByteArray(stream.available()).also {
                    stream.read(it)
                }
                return true
            }
        } catch (e: IOException) {
            return false
        }
    }

    @JvmStatic
    @Throws(NullPointerException::class)
    fun getPowerTagKey(uid: ByteArray, page10bytes: String): ByteArray {
        keys?.let {
            val uidc = byteArrayOf(
                (uid[0].toInt() and 0xFE).toByte(),
                (uid[1].toInt() and 0xFE).toByte(),
                (uid[2].toInt() and 0xFE).toByte(),
                (uid[3].toInt() and 0xFE).toByte(),
                (uid[4].toInt() and 0xFE).toByte(),
                (uid[5].toInt() and 0xFE).toByte(),
                (uid[6].toInt() and 0xFE).toByte()
            )
            val keymap = it[uidc.toHex()] ?: throw NullPointerException(
                TagMo.appContext.getString(R.string.uid_key_missing)
            )
            return keymap[page10bytes] ?: throw NullPointerException(
                TagMo.appContext.getString(R.string.p10_key_missing)
            )
        } ?: throw NullPointerException(
            TagMo.appContext.getString(R.string.error_powertag_key)
        )

    }
}