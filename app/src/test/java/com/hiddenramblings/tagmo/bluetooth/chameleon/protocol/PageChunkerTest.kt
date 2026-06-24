package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageChunkerTest {

    private fun dump(size: Int) = ByteArray(size) { (it and 0xFF).toByte() }

    @Test
    fun chunks_ntag215_540_into_blocks_of_32_pages() {
        val blocks = PageChunker.chunkByPages(dump(540), pagesPerBlock = 32)
        // 135 pages -> 32,32,32,32,7
        assertEquals(5, blocks.size)
        assertEquals(listOf(0, 32, 64, 96, 128), blocks.map { it.startPage })
        assertEquals(listOf(32, 32, 32, 32, 7), blocks.map { it.pageCount })
        assertEquals(7 * 4, blocks.last().data.size)
    }

    @Test
    fun blocks_reconstruct_original_dump() {
        val original = dump(540)
        val rebuilt = PageChunker.chunkByPages(original, pagesPerBlock = 16)
            .fold(ByteArray(0)) { acc, b -> acc + b.data }
        assertArrayEquals(original, rebuilt)
    }

    @Test
    fun single_block_when_block_larger_than_dump() {
        val blocks = PageChunker.chunkByPages(dump(540), pagesPerBlock = 1000)
        assertEquals(1, blocks.size)
        assertEquals(0, blocks[0].startPage)
        assertEquals(135, blocks[0].pageCount)
    }

    @Test
    fun rejects_non_page_multiple() {
        assertThrows(IllegalArgumentException::class.java) {
            PageChunker.chunkByPages(dump(541), pagesPerBlock = 32)
        }
    }

    @Test
    fun require_ntag215_accepts_540_rejects_others() {
        PageChunker.requireNtag215(dump(540)) // does not throw
        assertThrows(IllegalArgumentException::class.java) { PageChunker.requireNtag215(dump(532)) }
        assertThrows(IllegalArgumentException::class.java) { PageChunker.requireNtag215(dump(572)) }
    }

    @Test
    fun normalize_handles_540_532_and_larger() {
        // 540: unchanged
        assertEquals(540, PageChunker.normalizeToNtag215(dump(540)).size)
        // 532: padded to 540, first 532 bytes preserved, last 8 zeroed
        val padded = PageChunker.normalizeToNtag215(dump(532))
        assertEquals(540, padded.size)
        assertArrayEquals(dump(532), padded.copyOfRange(0, 532))
        assertArrayEquals(ByteArray(8), padded.copyOfRange(532, 540))
        // 572: truncated to the first 540
        val truncated = PageChunker.normalizeToNtag215(dump(572))
        assertEquals(540, truncated.size)
        assertArrayEquals(dump(572).copyOfRange(0, 540), truncated)
    }

    @Test
    fun normalize_rejects_aberrant_size() {
        assertThrows(IllegalArgumentException::class.java) { PageChunker.normalizeToNtag215(dump(100)) }
    }
}
