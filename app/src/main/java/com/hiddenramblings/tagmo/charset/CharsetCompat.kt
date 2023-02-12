package com.hiddenramblings.tagmo.charset

import com.hiddenramblings.tagmo.eightbit.os.Version
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object CharsetCompat {
    @JvmField
    val UTF_8: Charset =
        if (Version.isKitKat) StandardCharsets.UTF_8 else Charset.forName("UTF-8")
    @JvmField
    val UTF_16: Charset =
        if (Version.isKitKat) StandardCharsets.UTF_16 else Charset.forName("UTF-16")
    @JvmField
    val ISO_8859_1: Charset =
        if (Version.isKitKat) StandardCharsets.ISO_8859_1 else Charset.forName("ISO-8859-1")
    @JvmField
    val US_ASCII: Charset =
        if (Version.isKitKat) StandardCharsets.US_ASCII else Charset.defaultCharset() // Unknown
    @JvmField
    val UTF_16BE: Charset =
        if (Version.isKitKat) StandardCharsets.UTF_16BE else Charset.forName("UTF-16BE")
    @JvmField
    val UTF_16LE: Charset =
        if (Version.isKitKat) StandardCharsets.UTF_16LE else Charset.forName("UTF-16LE")
}