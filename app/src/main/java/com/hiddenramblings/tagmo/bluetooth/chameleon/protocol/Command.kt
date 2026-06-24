package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * ChameleonUltra protocol opcodes (CMD field, U16).
 *
 * Values are taken from chameleon_enum.py @ ChameleonUltra 6e2a902d; see
 * constants/chameleon-extracted.md for full provenance (file:line@SHA).
 */
enum class Command(val code: Int) {
    GET_APP_VERSION(1000),                  // enum.py:6
    SET_ACTIVE_SLOT(1003),                  // enum.py:9
    SET_SLOT_TAG_TYPE(1004),                // enum.py:10
    SET_SLOT_DATA_DEFAULT(1005),            // enum.py:11  (reset slot data to type default)
    SET_SLOT_ENABLE(1006),                  // enum.py:12
    SLOT_DATA_CONFIG_SAVE(1009),            // enum.py:18  (persist slots config to flash)
    HF14A_SET_ANTI_COLL_DATA(4001),         // enum.py:105 (emulated UID/ATQA/SAK/ATS)
    MF0_NTAG_SET_UID_MAGIC_MODE(4020),      // enum.py:127
    MF0_NTAG_WRITE_EMU_PAGE_DATA(4022),     // enum.py:129 (write emulator pages = eload)
    MF0_NTAG_SET_VERSION_DATA(4024),        // enum.py:131 (GET_VERSION, 8 bytes)
    MF0_NTAG_SET_SIGNATURE_DATA(4026),      // enum.py:133 (READ_SIG, 32 bytes)
    MF0_NTAG_SET_WRITE_MODE(4032);          // enum.py:139

    companion object {
        fun fromCode(code: Int): Command? = entries.firstOrNull { it.code == code }
    }
}
