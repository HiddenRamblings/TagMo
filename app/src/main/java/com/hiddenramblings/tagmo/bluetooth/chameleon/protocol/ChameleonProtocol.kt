package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Protocol constants and small guards. Values are extracted from pinned upstream sources
 * (chameleon_enum.py @ ChameleonUltra 6e2a902d); see constants/chameleon-extracted.md for provenance.
 */
object ChameleonProtocol {

    // --- NTAG215 facts (NXP NTAG21x datasheet) ---
    const val NTAG215_PAGE_SIZE: Int = 4
    const val NTAG215_PAGE_COUNT: Int = 135
    const val NTAG215_DUMP_SIZE: Int = NTAG215_PAGE_SIZE * NTAG215_PAGE_COUNT // 540

    /** NTAG215 U16 value in `tag_specific_type_t`. */
    const val TAG_TYPE_NTAG215: Int = 1101          // enum.py:348  (NTAG213=1100, NTAG216=1102)

    /** HF `sense_type` in `tag_sense_type_t`. */
    const val SENSE_HF: Int = 2                     // enum.py:281

    /** NTAG215 originality signature length (READ_SIG), in bytes. */
    const val NTAG215_SIGNATURE_SIZE: Int = 32

    /** NTAG215 GET_VERSION response (8 bytes), used to make the emulator look like a genuine NTAG215. */
    val NTAG215_VERSION: ByteArray = byteArrayOf(0x00, 0x04, 0x04, 0x02, 0x01, 0x00, 0x11, 0x03)

    /** NTAG215 ISO14443-3 anti-collision values. */
    val NTAG215_ATQA: ByteArray = byteArrayOf(0x44, 0x00)
    const val NTAG215_SAK: Int = 0x00

    /** Success status for general-config commands. */
    const val STATUS_SUCCESS: Int = 0x68            // enum.py:203

    /** The protocol numbers slots 0..7 (the CLI numbers them 1..8). */
    fun requireValidSlot(slot: Int): Int {
        require(slot in 0..7) { "slot out of protocol range 0..7: $slot" }
        return slot
    }
}
