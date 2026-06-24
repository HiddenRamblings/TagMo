package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChameleonFrameTest {

    // --- Frozen vectors (computed by hand, see docs/03) ---

    @Test
    fun encode_get_app_version_empty() {
        // SOF EF | CMD 03E8 | STATUS 0000 | LEN 0000 | LRC2 15 | (no data) | LRC3 00
        val expected = hex("11 EF 03 E8 00 00 00 00 15 00")
        assertArrayEquals(expected, ChameleonFrame.encode(Command.GET_APP_VERSION))
    }

    @Test
    fun encode_set_active_slot_zero() {
        // CMD 03EB | STATUS 0000 | LEN 0001 | LRC2 11 | DATA 00 | LRC3 00
        val expected = hex("11 EF 03 EB 00 00 00 01 11 00 00")
        val frame = ChameleonFrame.encode(Command.SET_ACTIVE_SLOT, byteArrayOf(0x00))
        assertArrayEquals(expected, frame)
    }

    @Test
    fun decode_get_app_version_vector() {
        val frame = ChameleonFrame.decode(hex("11 EF 03 E8 00 00 00 00 15 00")).getOrThrow()
        assertEquals(1000, frame.cmd)
        assertEquals(0, frame.status)
        assertEquals(0, frame.data.size)
    }

    @Test
    fun round_trip_with_payload() {
        val data = ByteArray(64) { it.toByte() }
        val encoded = ChameleonFrame.encode(0x1234, 0x0000, data)
        val decoded = ChameleonFrame.decode(encoded).getOrThrow()
        assertEquals(0x1234, decoded.cmd)
        assertEquals(0x0000, decoded.status)
        assertArrayEquals(data, decoded.data)
    }

    @Test
    fun round_trip_max_data() {
        val data = ByteArray(ChameleonFrame.MAX_DATA) { (it and 0xFF).toByte() }
        val decoded = ChameleonFrame.decode(ChameleonFrame.encode(0x0001, 0, data)).getOrThrow()
        assertArrayEquals(data, decoded.data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encode_rejects_oversized_data() {
        ChameleonFrame.encode(0x0001, 0, ByteArray(ChameleonFrame.MAX_DATA + 1))
    }

    @Test
    fun decode_rejects_bad_sof() {
        assertTrue(ChameleonFrame.decode(hex("12 EF 03 E8 00 00 00 00 15 00")).isFailure)
    }

    @Test
    fun decode_rejects_bad_lrc3() {
        // dernier octet (LRC3) corrompu
        assertTrue(ChameleonFrame.decode(hex("11 EF 03 E8 00 00 00 00 15 FF")).isFailure)
    }

    @Test
    fun decode_rejects_trailing_bytes() {
        assertTrue(ChameleonFrame.decode(hex("11 EF 03 E8 00 00 00 00 15 00 AA")).isFailure)
    }

    @Test
    fun decode_incomplete_is_failure() {
        assertTrue(ChameleonFrame.decode(hex("11 EF 03 E8 00")).isFailure)
    }
}
