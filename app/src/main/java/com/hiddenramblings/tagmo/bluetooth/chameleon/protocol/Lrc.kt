package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/*
 * Pure Kotlin, no Android/BLE dependency: JVM-testable.
 * Frame format reference: docs/03-protocol-reference.md (verified against the official ChameleonUltra wiki).
 */

/**
 * Longitudinal Redundancy Check as defined by the ChameleonUltra protocol:
 * 8-bit two's complement of the sum of the covered bytes, modulo 2^8.
 *
 * Property: LRC(SOF=0x11) == 0xEF.
 */
internal object Lrc {

    /**
     * Computes the LRC over the range [fromIndex, toIndex) of [data].
     * @return value 0..255.
     */
    fun compute(data: ByteArray, fromIndex: Int = 0, toIndex: Int = data.size): Int {
        require(fromIndex in 0..data.size) { "fromIndex out of bounds" }
        require(toIndex in fromIndex..data.size) { "toIndex out of bounds" }
        var sum = 0
        for (i in fromIndex until toIndex) {
            sum = (sum + (data[i].toInt() and 0xFF)) and 0xFF
        }
        return (0x100 - sum) and 0xFF
    }
}
