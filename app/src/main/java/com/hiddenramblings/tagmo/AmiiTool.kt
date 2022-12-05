package com.hiddenramblings.tagmo

class AmiiTool {
    external fun setKeysFixed(data: ByteArray?, length: Int): Int
    external fun setKeysUnfixed(data: ByteArray?, length: Int): Int
    external fun unpack(
        tag: ByteArray?,
        tagLength: Int,
        unpackedTag: ByteArray?,
        unpackedTagLength: Int
    ): Int

    external fun pack(
        tag: ByteArray?,
        tagLength: Int,
        unpackedTag: ByteArray?,
        unpackedTagLength: Int
    ): Int

    companion object {
        init {
            System.loadLibrary("amiitool")
        }
    }
}