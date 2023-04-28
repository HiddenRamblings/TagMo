package com.hiddenramblings.tagmo.wave9

import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.nfctech.NTAG215
import java.io.IOException

@Suppress("unused")
class JSAPI(val activity: DimensionActivity, val web: WebView) {

    @JavascriptInterface
    fun readTag(page: Byte): String? {
        return NTAG215[activity.tag]?.let {
            try {
                // val payload = mifare.readPages(page.toInt())
                val message = byteArrayOf(0x30, (page.toInt() and 0xFF).toByte())
                it.transceive(message)?.let { payload ->
                    Debug.info(javaClass, String.format(
                        "Payload %02X%02X%02X%02X %02X%02X%02X%02X %02X%02X%02X%02X %02X%02X%02X%02X",
                        payload[0], payload[1], payload[2], payload[3],
                        payload[4], payload[5], payload[6], payload[7],
                        payload[8], payload[9], payload[10], payload[11],
                        payload[12], payload[13], payload[14], payload[15]
                    ))
                    closeTagSilently(it)
                    Base64.encodeToString(payload, 0, 16, 0)
                }
            } catch (e2: IOException) {
                closeTagSilently(it)
                null
            } catch (vomit: Throwable) {
                closeTagSilently(it)
                throw vomit
            }
        }
    }

    @JavascriptInterface
    fun writeTag(page: Byte, payload: String?): Boolean {
        val data = Base64.decode(payload, 0)
        return NTAG215[activity.tag]?.let {
            try {
                Debug.info(javaClass, String.format(
                    "Writing %02X%02X%02X%02X", data[0], data[1], data[2], data[3]
                ))
                it.writePage(page.toInt(), data)
                closeTagSilently(it)
            } catch (e2: IOException) {
                closeTagSilently(it)
            } catch (vomit: Throwable) {
                closeTagSilently(it)
            }
        } ?: false
    }

    private fun callJavaScript(methodName: String, vararg params: Any) {
        val stringBuilder = StringBuilder()
        stringBuilder.append("javascript:try{(window.$methodName||")
        stringBuilder.append("console.warn.bind(console,'UNHANDLED','$methodName'))(")
        params.forEach { stringBuilder.append("'${if (it is String) it else it.toString()}',") }
        stringBuilder.append("'')}catch(error){console.error('ANDROID APP ERROR',error);}")
        this.web.loadUrl(stringBuilder.toString())
    }

    private fun closeTagSilently(mifare: NTAG215) : Boolean {
        return try {
            mifare.close()
            true
        } catch (ex: Exception) {
            false
        }
    }
}