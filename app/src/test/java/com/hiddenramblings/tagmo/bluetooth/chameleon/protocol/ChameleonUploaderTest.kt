package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChameleonUploaderTest {

    private fun dump(size: Int) = ByteArray(size) { (it and 0xFF).toByte() }

    @Test
    fun upload_happy_path_sends_full_sequence() = runBlocking {
        val transport = FakeChameleonTransport.allSuccess()
        val uploader = ChameleonUploader(transport)
        val data = dump(540)

        val progress = mutableListOf<Pair<Int, Int>>()
        uploader.uploadAmiibo(data, slot = 3, pagesPerBlock = 32) { w, t -> progress += w to t }

        // 135 pages / 32 -> 5 write blocks ; full reference sequence
        val cmds = transport.sent.map { it.cmd }
        val expected = listOf(
            Command.SET_ACTIVE_SLOT.code,
            Command.SET_SLOT_TAG_TYPE.code,
            Command.SET_SLOT_DATA_DEFAULT.code,
            Command.MF0_NTAG_SET_VERSION_DATA.code,
            Command.MF0_NTAG_SET_WRITE_MODE.code,
            Command.MF0_NTAG_SET_UID_MAGIC_MODE.code,
            Command.MF0_NTAG_SET_SIGNATURE_DATA.code,
            Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code,
            Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code,
            Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code,
            Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code,
            Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code,
            Command.HF14A_SET_ANTI_COLL_DATA.code,
            Command.SET_SLOT_ENABLE.code,
            Command.SLOT_DATA_CONFIG_SAVE.code,
        )
        assertEquals(expected, cmds)
        assertEquals(0 to 5, progress.first())
        assertEquals(5 to 5, progress.last())
    }

    @Test
    fun upload_preserves_original_uid_in_first_write_block() = runBlocking {
        val transport = FakeChameleonTransport.allSuccess()
        val data = dump(540)
        // UID = first 3 bytes of the dump (pages 0..2); must be sent as-is in the first block.
        ChameleonUploader(transport).uploadAmiibo(data, slot = 0, pagesPerBlock = 32)

        val firstWrite = transport.sent.first { it.cmd == Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code }
        // payload = page_start(1) | count(1) | pages...
        assertEquals(0, firstWrite.data[0].toInt())            // startPage 0
        assertEquals(32, firstWrite.data[1].toInt())           // 32 pages
        val pages = firstWrite.data.copyOfRange(2, firstWrite.data.size)
        assertArrayEquals(data.copyOfRange(0, 32 * 4), pages)  // bytes identical to the dump (UID included)
    }

    @Test
    fun upload_sets_anticollision_with_original_uid() = runBlocking {
        val transport = FakeChameleonTransport.allSuccess()
        val data = dump(540)
        ChameleonUploader(transport).uploadAmiibo(data, slot = 0, pagesPerBlock = 32)
        val antiColl = transport.sent.first { it.cmd == Command.HF14A_SET_ANTI_COLL_DATA.code }
        // payload: uidLen(1) | uid(7) | atqa(2) | sak(1) | atsLen(1)
        assertEquals(7, antiColl.data[0].toInt())
        val uid = antiColl.data.copyOfRange(1, 8)
        // UID = pages 0-1 minus BCC0 (byte index 3)
        assertArrayEquals(
            byteArrayOf(data[0], data[1], data[2], data[4], data[5], data[6], data[7]), uid,
        )
    }

    @Test
    fun upload_rejects_wrong_dump_size() {
        val transport = FakeChameleonTransport.allSuccess()
        assertThrows(IllegalArgumentException::class.java) {
            // 100 bytes: neither 540, 532, nor > 540 -> rejected (aberrant size).
            runBlocking { ChameleonUploader(transport).uploadAmiibo(ByteArray(100), slot = 0) }
        }
        // Nothing was sent: validation precedes any I/O.
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun upload_accepts_532_byte_amiibo_variant() = runBlocking {
        // 532-byte variant (without PWD/PACK): must be normalized to 540 and uploaded.
        val transport = FakeChameleonTransport.allSuccess()
        ChameleonUploader(transport).uploadAmiibo(dump(532), slot = 0, pagesPerBlock = 32)
        val writes = transport.sent.filter { it.cmd == Command.MF0_NTAG_WRITE_EMU_PAGE_DATA.code }
        // 540 normalized bytes -> 135 pages -> 5 blocks of 32.
        assertEquals(5, writes.size)
    }

    @Test
    fun upload_aborts_on_non_success_status() {
        // Failure at SET_SLOT_TAG_TYPE: the following commands must not be sent.
        val transport = FakeChameleonTransport { req ->
            if (req.cmd == Command.SET_SLOT_TAG_TYPE.code) {
                ChameleonFrame(req.cmd, 0x01, ByteArray(0)) // HF_TAG_NO
            } else {
                FakeChameleonTransport.ok(req)
            }
        }
        assertThrows(ChameleonProtocolException::class.java) {
            runBlocking { ChameleonUploader(transport).uploadAmiibo(dump(540), slot = 1) }
        }
        val cmds = transport.sent.map { it.cmd }
        assertEquals(
            listOf(Command.SET_ACTIVE_SLOT.code, Command.SET_SLOT_TAG_TYPE.code),
            cmds,
        )
    }

    @Test
    fun upload_aborts_on_cmd_mismatch() {
        val transport = FakeChameleonTransport { _ ->
            ChameleonFrame(Command.GET_APP_VERSION.code, ChameleonProtocol.STATUS_SUCCESS, ByteArray(0))
        }
        assertThrows(ChameleonProtocolException::class.java) {
            runBlocking { ChameleonUploader(transport).uploadAmiibo(dump(540), slot = 0) }
        }
    }

    @Test
    fun get_app_version_parses_response() = runBlocking {
        val transport = FakeChameleonTransport.allSuccess(versionMajor = 2, versionMinor = 7)
        val v = ChameleonUploader(transport).getAppVersion()
        assertEquals(AppVersion(2, 7), v)
        assertEquals(Command.GET_APP_VERSION.code, transport.sent.single().cmd)
    }

    @Test
    fun upload_invalid_slot_rejected() {
        val transport = FakeChameleonTransport.allSuccess()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ChameleonUploader(transport).uploadAmiibo(dump(540), slot = 8) }
        }
    }
}
