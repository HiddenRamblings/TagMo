package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Result of incrementally parsing a frame from a possibly partial/noisy buffer.
 */
sealed interface ParseOutcome {
    /** Not enough bytes to decide: wait for more. */
    object NeedMore : ParseOutcome

    /** Complete and valid frame extracted; [consumed] bytes to remove from the buffer. */
    data class Ok(val frame: ChameleonFrame, val consumed: Int) : ParseOutcome

    /** Invalid leading bytes; resynchronize by skipping [resync] bytes. */
    data class Corrupt(val reason: String, val resync: Int) : ParseOutcome
}

/**
 * A decoded ChameleonUltra frame. CMD/STATUS are U16 (0..65535).
 *
 * Format (verified, see docs/03):
 *   [0]   SOF   = 0x11
 *   [1]   LRC1  = LRC(SOF) = 0xEF
 *   [2..3]CMD    (big-endian)
 *   [4..5]STATUS (big-endian; client->device = 0x0000)
 *   [6..7]LEN    (big-endian; DATA length, max 512)
 *   [8]   LRC2  = LRC(CMD|STATUS|LEN)
 *   [9..] DATA  (LEN bytes)
 *   [..]  LRC3  = LRC(DATA)
 * Total size = LEN + 10 (between 10 and 522).
 */
class ChameleonFrame(
    val cmd: Int,
    val status: Int,
    val data: ByteArray,
) {
    init {
        require(cmd in 0..0xFFFF) { "cmd out of U16 range" }
        require(status in 0..0xFFFF) { "status out of U16 range" }
        require(data.size <= MAX_DATA) { "DATA > $MAX_DATA" }
    }

    /** Serializes this frame. (STATUS kept as-is — for a client->device send, use 0.) */
    fun encode(): ByteArray = encode(cmd, status, data)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChameleonFrame) return false
        return cmd == other.cmd && status == other.status && data.contentEquals(other.data)
    }

    override fun hashCode(): Int =
        (cmd * 31 + status) * 31 + data.contentHashCode()

    override fun toString(): String =
        "ChameleonFrame(cmd=$cmd, status=$status, data=${data.joinToString("") { "%02x".format(it) }})"

    companion object {
        const val SOF: Byte = 0x11
        const val LRC1: Int = 0xEF          // = LRC(SOF)
        const val HEADER_SIZE: Int = 9      // SOF,LRC1,CMD2,STATUS2,LEN2,LRC2 -> DATA starts at index 9
        const val OVERHEAD: Int = 10        // header + LRC3
        const val MAX_DATA: Int = 512
        const val MAX_FRAME: Int = MAX_DATA + OVERHEAD

        /** Builds a frame from a typed command (STATUS=0, client->device direction). */
        fun encode(command: Command, data: ByteArray = ByteArray(0)): ByteArray =
            encode(command.code, 0, data)

        /** Builds a raw frame. */
        fun encode(cmd: Int, status: Int = 0, data: ByteArray = ByteArray(0)): ByteArray {
            require(cmd in 0..0xFFFF) { "cmd out of U16 range" }
            require(status in 0..0xFFFF) { "status out of U16 range" }
            require(data.size <= MAX_DATA) { "DATA > $MAX_DATA" }
            val len = data.size
            val out = ByteArray(len + OVERHEAD)
            out[0] = SOF
            out[1] = Lrc.compute(out, 0, 1).toByte()        // over SOF -> 0xEF
            out[2] = (cmd ushr 8).toByte(); out[3] = cmd.toByte()
            out[4] = (status ushr 8).toByte(); out[5] = status.toByte()
            out[6] = (len ushr 8).toByte(); out[7] = len.toByte()
            out[8] = Lrc.compute(out, 2, 8).toByte()        // over CMD|STATUS|LEN
            data.copyInto(out, 9)
            out[9 + len] = Lrc.compute(out, 9, 9 + len).toByte() // over DATA
            return out
        }

        /**
         * Tries to parse ONE frame from [buf] at offset [off] over [len] available bytes.
         * Does not throw: returns NeedMore / Ok / Corrupt. Used by IncrementalFrameReader.
         */
        fun tryParse(buf: ByteArray, off: Int, len: Int): ParseOutcome {
            if (len < 1) return ParseOutcome.NeedMore
            if (buf[off] != SOF) return ParseOutcome.Corrupt("expected SOF 0x11", 1)
            if (len < HEADER_SIZE) return ParseOutcome.NeedMore
            if ((buf[off + 1].toInt() and 0xFF) != LRC1) return ParseOutcome.Corrupt("invalid LRC1", 1)

            val cmd = u16(buf, off + 2)
            val status = u16(buf, off + 4)
            val dlen = u16(buf, off + 6)
            if (dlen > MAX_DATA) return ParseOutcome.Corrupt("LEN > $MAX_DATA", 1)

            val lrc2 = Lrc.compute(buf, off + 2, off + 8)
            if ((buf[off + 8].toInt() and 0xFF) != lrc2) return ParseOutcome.Corrupt("invalid LRC2", 1)

            val total = dlen + OVERHEAD
            if (len < total) return ParseOutcome.NeedMore

            val dataStart = off + 9
            val lrc3 = Lrc.compute(buf, dataStart, dataStart + dlen)
            if ((buf[dataStart + dlen].toInt() and 0xFF) != lrc3) return ParseOutcome.Corrupt("invalid LRC3", 1)

            val data = buf.copyOfRange(dataStart, dataStart + dlen)
            return ParseOutcome.Ok(ChameleonFrame(cmd, status, data), total)
        }

        /**
         * Decodes a frame assumed to be complete and standalone. Fails if incomplete, corrupt, or with extra bytes.
         */
        fun decode(bytes: ByteArray): Result<ChameleonFrame> =
            when (val o = tryParse(bytes, 0, bytes.size)) {
                is ParseOutcome.Ok ->
                    if (o.consumed == bytes.size) Result.success(o.frame)
                    else Result.failure(IllegalArgumentException("extra bytes after frame"))
                ParseOutcome.NeedMore -> Result.failure(IllegalArgumentException("incomplete frame"))
                is ParseOutcome.Corrupt -> Result.failure(IllegalArgumentException(o.reason))
            }

        private fun u16(buf: ByteArray, i: Int): Int =
            ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
    }
}
