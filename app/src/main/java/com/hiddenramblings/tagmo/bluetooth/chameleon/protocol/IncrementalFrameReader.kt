package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Reassembles ChameleonUltra frames from a byte stream arriving in chunks (BLE notifications).
 * Handles: frames fragmented across multiple chunks, several frames within a single chunk, and
 * leading garbage bytes (resynchronization on SOF).
 *
 * Not thread-safe: confine to a single thread/scope (callers synchronize external access).
 */
class IncrementalFrameReader {

    private var buffer = ByteArray(0)

    /** Bytes currently pending (partial frame not yet complete). */
    val pending: Int get() = buffer.size

    /**
     * Appends a received chunk and returns the newly available complete frames (in order).
     */
    fun append(chunk: ByteArray): List<ChameleonFrame> {
        if (chunk.isEmpty() && buffer.isEmpty()) return emptyList()
        buffer += chunk

        val frames = ArrayList<ChameleonFrame>()
        var off = 0
        loop@ while (off < buffer.size) {
            when (val o = ChameleonFrame.tryParse(buffer, off, buffer.size - off)) {
                ParseOutcome.NeedMore -> break@loop
                is ParseOutcome.Ok -> {
                    frames += o.frame
                    off += o.consumed
                }
                is ParseOutcome.Corrupt -> off += o.resync
            }
        }

        // Compact: keep only the unconsumed tail.
        buffer = if (off == 0) buffer else buffer.copyOfRange(off, buffer.size)
        return frames
    }

    /** Clears the buffer (e.g. after disconnect/reconnect). */
    fun reset() {
        buffer = ByteArray(0)
    }
}
