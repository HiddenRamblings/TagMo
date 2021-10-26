package com.hiddenramblings.tagmo.nfctag;

public class NfcByte {

    public static final int NXP_MANUFACTURER_ID = 0x04;
    public static final int MAX_PAGE_COUNT = 256;

    public static final int TAG_FILE_SIZE = 532;
    public static final int PAGE_SIZE = 4;
    public static final int BULK_READ_PAGE_COUNT = 4;

    @SuppressWarnings("unused")
    public static final int CMD_GET_VERSION = 0x60;
    public static final int CMD_READ = 0x30;
    @SuppressWarnings("unused")
    public static final int CMD_FAST_READ = 0x3A;
    public static final int CMD_WRITE = 0xA2;
    @SuppressWarnings("unused")
    public static final int CMD_COMP_WRITE = 0xA0;
    @SuppressWarnings("unused")
    public static final int CMD_READ_CNT = 0x39;
    @SuppressWarnings("unused")
    public static final int CMD_PWD_AUTH = 0x1B;
    @SuppressWarnings("unused")
    public static final int CMD_READ_SIG = 0x3C;

    // N2 Elite
    public static final byte N2_GET_VERSION = (byte) 0x55;
    public static final byte N2_SELECT_BANK = (byte) 0xA7;
    public static final byte N2_FAST_READ = 0x3B;
    public static final byte N2_FAST_WRITE = (byte) 0xAE;
    public static final byte N2_BANK_COUNT = 0x55;
    public static final byte N2_LOCK = 0x46;
    public static final byte N2_READ_SIG = 0x43;// N2_GET_ID
    public static final byte N2_SET_BANK_CNT = (byte) 0xA9;
    public static final byte N2_UNLOCK_1 = 0x44;
    public static final byte N2_UNLOCK_2 = 0x45;
    public static final byte N2_WRITE = (byte) 0xA5;

    public static final byte[] POWERTAG_SIGNATURE = TagUtils.hexStringToByteArray(
            "213C65444901602985E9F6B50CACB9C8CA3C4BCD13142711FF571CF01E66BD6F");
    public static final byte[] POWERTAG_IDPAGES = TagUtils.hexStringToByteArray(
            "04070883091012131800000000000000");
    public static final String POWERTAG_KEY = "FFFFFFFFFFFFFFFF0000000000000000";
    public static final byte[] COMP_WRITE_CMD = TagUtils.hexStringToByteArray("a000");
    public static final byte[] SIG_CMD = TagUtils.hexStringToByteArray("3c00");
}
