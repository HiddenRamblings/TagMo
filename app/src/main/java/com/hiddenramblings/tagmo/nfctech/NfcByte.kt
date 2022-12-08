package com.hiddenramblings.tagmo.nfctech

object NfcByte {
    const val KEY_FILE_SIZE = 80 // Each key read separately
    const val KEY_RETAIL_SZ = 160 // Each key read separately
    const val TAG_DATA_SIZE = 532 // 540, 572 with signature
    const val TAG_FILE_SIZE = 572 // 540 + 32 byte signature
    const val PAGE_SIZE = 4
    const val CMD_GET_VERSION = 0x60
    const val CMD_READ = 0x30
    const val CMD_FAST_READ = 0x3A
    const val CMD_WRITE = 0xA2
    const val CMD_COMP_WRITE = 0xA0
    const val CMD_READ_CNT = 0x39
    const val CMD_PWD_AUTH = 0x1B
    const val CMD_READ_SIG = 0x3C

    // N2 Elite
    const val N2_GET_VERSION = 0x55
    const val N2_ACTIVATE_BANK = 0xA7
    const val N2_FAST_READ = 0x3B
    const val N2_FAST_WRITE = 0xAE
    const val N2_BANK_COUNT = 0x55
    const val N2_LOCK = 0x46
    const val N2_READ_SIG = 0x43
    const val N2_SET_BANKCOUNT = 0xA9
    const val N2_UNLOCK_1 = 0x44
    const val N2_UNLOCK_2 = 0x45
    const val N2_WRITE = 0xA5
    const val SECTOR_SELECT = 0xC2
    @JvmField
    val POWERTAG_SIGNATURE = TagArray.hexToByteArray(
        "213C65444901602985E9F6B50CACB9C8CA3C4BCD13142711FF571CF01E66BD6F"
    )
    @JvmField
    val POWERTAG_IDPAGES = TagArray.hexToByteArray(
        "04070883091012131800000000000000"
    )
    const val POWERTAG_KEY = "FFFFFFFFFFFFFFFF0000000000000000"
    @JvmField
    val POWERTAG_WRITE = TagArray.hexToByteArray("a000")
    @JvmField
    val POWERTAG_SIG = TagArray.hexToByteArray("3c00")
}