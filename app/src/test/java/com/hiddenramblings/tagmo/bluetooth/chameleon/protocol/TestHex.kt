package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/** Hex helpers for tests (spaces ignored). */
internal fun hex(s: String): ByteArray {
    val clean = s.replace(" ", "").replace("\n", "")
    require(clean.length % 2 == 0) { "longueur hex impaire" }
    return ByteArray(clean.length / 2) {
        ((Character.digit(clean[it * 2], 16) shl 4) + Character.digit(clean[it * 2 + 1], 16)).toByte()
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
