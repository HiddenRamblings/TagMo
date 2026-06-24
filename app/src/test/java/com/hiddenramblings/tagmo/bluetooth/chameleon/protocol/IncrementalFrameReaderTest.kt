package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalFrameReaderTest {

    private val frameA = hex("11 EF 03 E8 00 00 00 00 15 00")        // GET_APP_VERSION, LEN=0
    private val frameB = hex("11 EF 03 EB 00 00 00 01 11 00 00")     // SET_ACTIVE_SLOT slot 0, LEN=1

    @Test
    fun single_frame_in_one_append() {
        val r = IncrementalFrameReader()
        val out = r.append(frameA)
        assertEquals(1, out.size)
        assertEquals(1000, out[0].cmd)
        assertEquals(0, r.pending)
    }

    @Test
    fun two_frames_concatenated_in_one_append() {
        val r = IncrementalFrameReader()
        val out = r.append(frameA + frameB)
        assertEquals(2, out.size)
        assertEquals(1000, out[0].cmd)
        assertEquals(1003, out[1].cmd)
        assertEquals(0, r.pending)
    }

    @Test
    fun frame_split_across_two_appends() {
        val r = IncrementalFrameReader()
        val first = r.append(frameA.copyOfRange(0, 5))  // half a frame
        assertEquals(0, first.size)
        assertEquals(5, r.pending)

        val second = r.append(frameA.copyOfRange(5, frameA.size))
        assertEquals(1, second.size)
        assertEquals(1000, second[0].cmd)
        assertEquals(0, r.pending)
    }

    @Test
    fun leading_garbage_is_resynced() {
        val r = IncrementalFrameReader()
        val out = r.append(hex("00 AA FF") + frameA)
        assertEquals(1, out.size)
        assertEquals(1000, out[0].cmd)
        assertEquals(0, r.pending)
    }

    @Test
    fun garbage_between_two_frames() {
        val r = IncrementalFrameReader()
        val out = r.append(frameA + hex("DE AD") + frameB)
        assertEquals(2, out.size)
        assertEquals(1000, out[0].cmd)
        assertEquals(1003, out[1].cmd)
    }

    @Test
    fun byte_by_byte_feed() {
        val r = IncrementalFrameReader()
        var emitted = 0
        for (b in frameB) {
            emitted += r.append(byteArrayOf(b)).size
        }
        assertEquals(1, emitted)
        assertEquals(0, r.pending)
    }

    @Test
    fun reset_clears_pending() {
        val r = IncrementalFrameReader()
        r.append(frameA.copyOfRange(0, 5))
        assertEquals(5, r.pending)
        r.reset()
        assertEquals(0, r.pending)
    }
}
