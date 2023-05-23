package com.hiddenramblings.tagmo.amiibo

import android.util.Base64
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.nfctech.TagArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object PowerTagManager {
    // private static final String POWERTAG_KEYTABLE_FILE = "keytable.json";
    private var keys: HashMap<String, HashMap<String, ByteArray>>? = null

    @Throws(JSONException::class)
    private fun parseKeyTable(json: JSONObject) {
        val keytable = HashMap<String, HashMap<String, ByteArray>>()
        val uidIterator = json.keys()
        while (uidIterator.hasNext()) {
            val uid = uidIterator.next()
            val pageKeys = json.getJSONObject(uid)
            val keyvalues = HashMap<String, ByteArray>()
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
    fun getPowerTagManager() {
        if (null != keys) return
        TagMo.appContext.resources.openRawResource(R.raw.keytable).use { stream ->
            val data = ByteArray(stream.available()).also {
                stream.read(it)
            }
            parseKeyTable(JSONObject(String(data)))
        }
    }

    @Suppress("unused")
    fun resetPowerTag(): Boolean {
        try {
            TagMo.appContext.resources.openRawResource(R.raw.powertag).use { stream ->
                val data = ByteArray(stream.available())
                stream.read(data)
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
            val uidc = ByteArray(7)
            uidc[0] = (uid[0].toInt() and 0xFE).toByte()
            uidc[1] = (uid[1].toInt() and 0xFE).toByte()
            uidc[2] = (uid[2].toInt() and 0xFE).toByte()
            uidc[3] = (uid[3].toInt() and 0xFE).toByte()
            uidc[4] = (uid[4].toInt() and 0xFE).toByte()
            uidc[5] = (uid[5].toInt() and 0xFE).toByte()
            uidc[6] = (uid[6].toInt() and 0xFE).toByte()
            val keymap = it[TagArray.bytesToHex(uidc)] ?: throw NullPointerException(
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