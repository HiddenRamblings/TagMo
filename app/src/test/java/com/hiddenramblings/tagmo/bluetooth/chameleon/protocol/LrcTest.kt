package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * JUnit4 (org.junit).
 */
class LrcTest {

    @Test
    fun lrc_of_sof_is_0xEF() {
        // Core protocol property: LRC(0x11) == 0xEF.
        assertEquals(0xEF, Lrc.compute(byteArrayOf(0x11)))
    }

    @Test
    fun lrc_of_empty_is_zero() {
        assertEquals(0x00, Lrc.compute(ByteArray(0)))
    }

    @Test
    fun lrc_known_vector_cmd_status_len() {
        // CMD=0x03E8 (GET_APP_VERSION=1000), STATUS=0, LEN=0  ->  expected LRC2 = 0x15
        val bytes = hex("03 E8 00 00 00 00")
        assertEquals(0x15, Lrc.compute(bytes))
    }

    @Test
    fun lrc_is_complement_to_256() {
        // sum = 0x01+0x02+0x03 = 6  ->  (256-6)=250=0xFA
        assertEquals(0xFA, Lrc.compute(byteArrayOf(0x01, 0x02, 0x03)))
    }
}
