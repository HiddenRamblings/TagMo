package com.hiddenramblings.tagmo.wave9

import android.nfc.tech.MifareUltralight
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.io.IOException

@Suppress("UNUSED")
class JSAPI(val activity: DimensionActivity, val web: WebView) {

    @JavascriptInterface
    fun readTag(page: Byte): String? {
        val mifare = MifareUltralight.get(this.activity.tag)
        return try {
            mifare?.connect()
            val payload = mifare.readPages(page.toInt())
            Debug.info(javaClass, String.format(
                "Payload %02X%02X%02X%02X %02X%02X%02X%02X %02X%02X%02X%02X %02X%02X%02X%02X",
                payload[0], payload[1], payload[2], payload[3],
                payload[4], payload[5], payload[6], payload[7],
                payload[8], payload[9], payload[10], payload[11],
                payload[12], payload[13], payload[14], payload[15]
            ))
            val encodeToString = Base64.encodeToString(payload, 0, 16, 0)
            if (mifare == null) return encodeToString
            try {
                mifare.close()
                encodeToString
            } catch (e: IOException) {
                encodeToString
            }
        } catch (e2: IOException) {
            if (mifare != null) {
                try {
                    mifare.close()
                } catch (ignored: IOException) { }
            }
            null
        } catch (vomit: Throwable) {
            if (mifare != null) {
                try {
                    mifare.close()
                } catch (ignored: IOException) { }
            }
            throw vomit
        }
    }

    @JavascriptInterface
    fun writeTag(page: Byte, payload: String?): Boolean {
        val data = Base64.decode(payload, 0)
        val ultralight = MifareUltralight.get(this.activity.tag)
        return try {
            ultralight.connect()
            Debug.info(javaClass, String.format(
                "Writing %02X%02X%02X%02X", data[0], data[1], data[2], data[3]
            ))
            ultralight.writePage(page.toInt(), data)
            try {
                ultralight.close()
                true
            } catch (e: IOException) {
                false
            }
        } catch (e2: IOException) {
            try {
                ultralight.close()
                true
            } catch (e3: IOException) {
                false
            }
        } catch (vomit: Throwable) {
            try {
                ultralight.close()
                true
            } catch (e4: IOException) {
                false
            }
        }
    }

    private fun callJavaScript(methodName: String, vararg params: Any) {
        val stringBuilder = StringBuilder()
        stringBuilder.append("javascript:try{(window.$methodName||")
        stringBuilder.append("console.warn.bind(console,'UNHANDLED','$methodName'))(")
        params.forEach { stringBuilder.append("'${if (it is String) it else it.toString()}',") }
        stringBuilder.append("'')}catch(error){console.error('ANDROID APP ERROR',error);}")
        this.web.loadUrl(stringBuilder.toString())
    }
}