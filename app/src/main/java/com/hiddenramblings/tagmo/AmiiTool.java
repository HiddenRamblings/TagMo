package com.hiddenramblings.tagmo;

public class AmiiTool {
    static {
        System.loadLibrary("amiitool");
    }

    public native int setKeysFixed(byte[] data, int length);

    public native int setKeysUnfixed(byte[] data, int length);

    public native int unpack(byte[] tag, int tagLength, byte[] unpackedTag, int unpackedTagLength);

    public native int pack(byte[] tag, int tagLength, byte[] unpackedTag, int unpackedTagLength);
}
