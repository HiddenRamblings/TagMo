package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/** A block of consecutive pages to write in one emulator command. */
class PageBlock(val startPage: Int, val data: ByteArray) {
    val pageCount: Int get() = data.size / ChameleonProtocol.NTAG215_PAGE_SIZE

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PageBlock) return false
        return startPage == other.startPage && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = startPage * 31 + data.contentHashCode()
    override fun toString(): String = "PageBlock(startPage=$startPage, pageCount=$pageCount)"
}

/**
 * LOGICAL splitting of a dump into page blocks (protocol level).
 *
 * Independent of BLE/MTU chunking (handled by the transport). [pagesPerBlock] is dictated by the
 * write command (protocol payload limit and/or firmware constraint).
 */
object PageChunker {

    fun chunkByPages(
        dump: ByteArray,
        pagesPerBlock: Int,
        pageSize: Int = ChameleonProtocol.NTAG215_PAGE_SIZE,
    ): List<PageBlock> {
        require(pageSize > 0) { "pageSize > 0" }
        require(pagesPerBlock > 0) { "pagesPerBlock > 0" }
        require(dump.size % pageSize == 0) { "dump size (${dump.size}) is not a multiple of pageSize ($pageSize)" }

        val totalPages = dump.size / pageSize
        val blocks = ArrayList<PageBlock>()
        var page = 0
        while (page < totalPages) {
            val n = minOf(pagesPerBlock, totalPages - page)
            val from = page * pageSize
            blocks += PageBlock(page, dump.copyOfRange(from, from + n * pageSize))
            page += n
        }
        return blocks
    }

    /** Checks that a dump is exactly NTAG215-sized (540 bytes). */
    fun requireNtag215(dump: ByteArray): ByteArray {
        require(dump.size == ChameleonProtocol.NTAG215_DUMP_SIZE) {
            "expected NTAG215 dump = ${ChameleonProtocol.NTAG215_DUMP_SIZE} bytes, got ${dump.size}"
        }
        return dump
    }

    /**
     * Normalizes an amiibo dump to 540 bytes (NTAG215), handling the common variants:
     * - 540: complete, as-is;
     * - 532: missing the last 2 pages (PWD 0x85 + PACK/RFUI 0x86) -> padded with 8 zero bytes
     *   (no impact on emulated reads: amiibo do not use password auth);
     * - > 540 (e.g. 572, with trailing extra data) -> truncated to the first 540 bytes
     *   (the actual tag pages, UID included).
     */
    fun normalizeToNtag215(dump: ByteArray): ByteArray {
        val full = ChameleonProtocol.NTAG215_DUMP_SIZE
        return when {
            dump.size == full -> dump
            dump.size == full - 8 -> dump.copyOf(full)          // 532 -> 540 (PWD/PACK zeroed)
            dump.size > full -> dump.copyOfRange(0, full)        // 568/572... -> 540
            else -> throw IllegalArgumentException(
                "unexpected NTAG215 dump size: ${dump.size} (expected 532, 540 or more)",
            )
        }
    }
}
