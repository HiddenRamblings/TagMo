package com.hiddenramblings.tagmo.charset

import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
import android.os.Build
import com.hiddenramblings.tagmo.charset.CharsetCompat
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object CharsetCompat {
    @JvmField
    val UTF_8: Charset =
        if (isNewer(Build.VERSION_CODES.KITKAT)) StandardCharsets.UTF_8 else Charset.forName("UTF-8")
    @JvmField
    val UTF_16: Charset =
        if (isNewer(Build.VERSION_CODES.KITKAT)) StandardCharsets.UTF_16 else Charset.forName("UTF-16")
    @JvmField
    val ISO_8859_1: Charset =
        if (isNewer(Build.VERSION_CODES.KITKAT)) StandardCharsets.ISO_8859_1 else Charset.forName("ISO-8859-1")
    @JvmField
    val US_ASCII: Charset =
        if (isNewer(Build.VERSION_CODES.KITKAT)) StandardCharsets.US_ASCII else Charset.forName("US_ASCII")
    @JvmField
    val UTF_16BE: Charset =
        if (isNewer(Build.VERSION_CODES.KITKAT)) StandardCharsets.UTF_16BE else Charset.forName("UTF-16BE")
    @JvmField
    val UTF_16LE: Charset =
        if (isNewer(Build.VERSION_CODES.KITKAT)) StandardCharsets.UTF_16LE else Charset.forName("UTF-16LE")
}