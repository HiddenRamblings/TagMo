package com.hiddenramblings.tagmo.nfctech;

import com.hiddenramblings.tagmo.eightbit.nfc.TagUtils;

@SuppressWarnings("unused")
public class NfcByte {

    public static final int KEY_FILE_SIZE = 80;
    public static final int TAG_FILE_SIZE = 532; // 572 with signature
    public static final int PAGE_SIZE = 4;

    public static final int CMD_GET_VERSION = 0x60;
    public static final int CMD_READ = 0x30;
    public static final int CMD_FAST_READ = 0x3A;
    public static final int CMD_WRITE = 0xA2;
    public static final int CMD_COMP_WRITE = 0xA0;
    public static final int CMD_READ_CNT = 0x39;
    public static final int CMD_PWD_AUTH = 0x1B;
    public static final int CMD_READ_SIG = 0x3C;

    // N2 Elite
    public static final int N2_GET_VERSION = 0x55;
    public static final int N2_ACTIVATE_BANK = 0xA7;
    public static final int N2_FAST_READ = 0x3B;
    public static final int N2_FAST_WRITE = 0xAE;
    public static final int N2_BANK_COUNT = 0x55;
    public static final int N2_LOCK = 0x46;
    public static final int N2_READ_SIG = 0x43;
    public static final int N2_SET_BANKCOUNT = 0xA9;
    public static final int N2_UNLOCK_1 = 0x44;
    public static final int N2_UNLOCK_2 = 0x45;
    public static final int N2_WRITE =  0xA5;
    public static final int SECTOR_SELECT = 0xC2;

    public static final byte[] POWERTAG_SIGNATURE = TagUtils.hexToByteArray(
            "213C65444901602985E9F6B50CACB9C8CA3C4BCD13142711FF571CF01E66BD6F");
    static final byte[] POWERTAG_IDPAGES = TagUtils.hexToByteArray(
            "04070883091012131800000000000000");
    static final String POWERTAG_KEY = "FFFFFFFFFFFFFFFF0000000000000000";
    static final byte[] POWERTAG_WRITE = TagUtils.hexToByteArray("a000");
    public static final byte[] POWERTAG_SIG = TagUtils.hexToByteArray("3c00");
}
