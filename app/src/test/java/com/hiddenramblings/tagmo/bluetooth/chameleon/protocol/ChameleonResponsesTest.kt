package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChameleonResponsesTest {

    // --- AppVersion: fully verifiable comparison logic (used by the firmware gate) ---

    @Test
    fun app_version_ordering() {
        assertTrue(AppVersion(2, 1) > AppVersion(2, 0))
        assertTrue(AppVersion(3, 0) > AppVersion(2, 9))
        assertEquals(0, AppVersion(2, 5).compareTo(AppVersion(2, 5)))
        assertTrue(AppVersion(2, 0) < AppVersion(2, 1))
    }

    @Test
    fun app_version_toString() {
        assertEquals("2.7", AppVersion(2, 7).toString())
    }

    // --- parseAppVersion: confirmed layout (!BB = major, minor) ---

    @Test
    fun parse_app_version_confirmed() {
        val frame = ChameleonFrame(Command.GET_APP_VERSION.code, 0, byteArrayOf(2, 3))
        val v = ChameleonResponses.parseAppVersion(frame)
        assertEquals(AppVersion(2, 3), v)
    }

    @Test
    fun parse_app_version_rejects_short_payload() {
        val frame = ChameleonFrame(Command.GET_APP_VERSION.code, 0, byteArrayOf(2))
        assertThrows(IllegalArgumentException::class.java) { ChameleonResponses.parseAppVersion(frame) }
    }

    // --- isSuccess: amiibo-flow commands only succeed with STATUS_SUCCESS (0x68) ---

    @Test
    fun is_success_requires_status_success() {
        // SUCCESS = 0x68 => success
        assertTrue(ChameleonResponses.isSuccess(ChameleonFrame(Command.SET_ACTIVE_SLOT.code, 0x68, ByteArray(0))))
        // 0x00 (HF_TAG_OK) is NOT a success for config commands => rejected (avoids masking errors)
        assertFalse(ChameleonResponses.isSuccess(ChameleonFrame(Command.SET_ACTIVE_SLOT.code, 0x00, ByteArray(0))))
        // PAR_ERR = 0x60 => failure
        assertFalse(ChameleonResponses.isSuccess(ChameleonFrame(Command.SET_ACTIVE_SLOT.code, 0x60, ByteArray(0))))
    }
}
