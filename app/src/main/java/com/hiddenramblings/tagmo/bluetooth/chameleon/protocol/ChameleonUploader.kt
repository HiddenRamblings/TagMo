package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Orchestrates the amiibo upload flow to a ChameleonUltra, on top of a [ChameleonTransport].
 * Pure code (no Android/BLE dependency): JVM-testable with a fake.
 *
 * Replicated sequence (matches the chameleon-ultra-amiibo reference app, so a strict reader such as a
 * Switch accepts the emulated tag):
 *   1. SET_ACTIVE_SLOT(slot)
 *   2. SET_SLOT_TAG_TYPE(slot, NTAG215) + SET_SLOT_DATA_DEFAULT(slot, NTAG215)
 *   3. SET_VERSION_DATA (NTAG215 GET_VERSION) ; SET_WRITE_MODE / SET_UID_MAGIC_MODE (best-effort)
 *   4. SET_SIGNATURE_DATA (READ_SIG: from a 572-byte dump's trailing 32 bytes, else zeros)
 *   5. WRITE_EMU_PAGE_DATA x blocks (the 135 pages)
 *   6. SET_ANTI_COLL_DATA (the original UID from pages 0..1) — so the broadcast UID matches the
 *      amiibo's UID-bound signature; omitting this makes a Switch report "not an amiibo"
 *   7. SET_SLOT_ENABLE(slot, HF, true)
 *   8. SLOT_DATA_CONFIG_SAVE() — otherwise lost on reboot
 *
 * Guardrails: NTAG215 dump (540 bytes) validated, UID preserved, no amiibo crypto (signature/version
 * are the tag's own NTAG215 data, not amiibo keys).
 *
 * Not atomic: a mid-sequence failure (timeout, link loss) throws and leaves the slot partially written
 * (the firmware offers no rollback); the caller surfaces the error and the user can retry the upload.
 */
class ChameleonUploader(private val transport: ChameleonTransport) {

    /** Fetches and parses the application version (for the firmware compatibility gate). */
    suspend fun getAppVersion(): AppVersion {
        val resp = exec(ChameleonCommands.getAppVersion(), Command.GET_APP_VERSION)
        return ChameleonResponses.parseAppVersion(resp)
    }

    /**
     * Pushes [dump] (NTAG215, 540 bytes) into [slot] (0..7).
     * @param pagesPerBlock number of pages per emulator write command.
     * @param onProgress callback (written blocks, total) — emitted at start (0,total) then after each block.
     */
    suspend fun uploadAmiibo(
        dump: ByteArray,
        slot: Int,
        pagesPerBlock: Int = DEFAULT_PAGES_PER_BLOCK,
        onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val signature = extractSignature(dump)
        val data = PageChunker.normalizeToNtag215(dump)
        ChameleonProtocol.requireValidSlot(slot)

        exec(ChameleonCommands.setActiveSlot(slot), Command.SET_ACTIVE_SLOT)
        exec(ChameleonCommands.setSlotNtag215(slot), Command.SET_SLOT_TAG_TYPE)
        exec(ChameleonCommands.setSlotDataDefault(slot), Command.SET_SLOT_DATA_DEFAULT)
        exec(ChameleonCommands.setVersionData(), Command.MF0_NTAG_SET_VERSION_DATA)
        // Best-effort: some firmware revisions may not support these write-mode toggles.
        execBestEffort(ChameleonCommands.setWriteMode(0), Command.MF0_NTAG_SET_WRITE_MODE)
        execBestEffort(ChameleonCommands.setUidMagicMode(false), Command.MF0_NTAG_SET_UID_MAGIC_MODE)
        exec(ChameleonCommands.setSignatureData(signature), Command.MF0_NTAG_SET_SIGNATURE_DATA)

        val blocks = PageChunker.chunkByPages(data, pagesPerBlock)
        onProgress(0, blocks.size)
        blocks.forEachIndexed { index, block ->
            exec(
                ChameleonCommands.writeEmuPages(block.startPage, block.data),
                Command.MF0_NTAG_WRITE_EMU_PAGE_DATA,
            )
            onProgress(index + 1, blocks.size)
        }

        // Set the emulated UID so the broadcast UID matches the amiibo's UID-bound signature.
        exec(
            ChameleonCommands.setAntiCollision(ChameleonCommands.ntag215Uid(data)),
            Command.HF14A_SET_ANTI_COLL_DATA,
        )
        exec(ChameleonCommands.setHfSlotEnabled(slot, enable = true), Command.SET_SLOT_ENABLE)
        exec(ChameleonCommands.saveSlotConfig(), Command.SLOT_DATA_CONFIG_SAVE)
    }

    /**
     * READ_SIG signature for extended dumps: 32 bytes at offset 544 (540 pages + 4 counter bytes),
     * matching the reference app; zeros otherwise (the Switch accepts a zero signature).
     */
    private fun extractSignature(dump: ByteArray): ByteArray {
        val sigStart = ChameleonProtocol.NTAG215_DUMP_SIZE + 4
        val sigEnd = sigStart + ChameleonProtocol.NTAG215_SIGNATURE_SIZE
        return if (dump.size >= sigEnd) dump.copyOfRange(sigStart, sigEnd)
        else ByteArray(ChameleonProtocol.NTAG215_SIGNATURE_SIZE)
    }

    /** Like [exec] but tolerates an unsupported/non-success command (firmware variance). */
    private suspend fun execBestEffort(request: ByteArray, expected: Command) {
        try {
            exec(request, expected)
        } catch (_: ChameleonProtocolException) {
            // Command not supported or returned non-success on this firmware; safe to skip.
        }
    }

    /** Sends a request, verifies the CMD echo then the success status; throws otherwise. */
    private suspend fun exec(request: ByteArray, expected: Command): ChameleonFrame {
        val resp = transport.send(request)
        if (resp.cmd != expected.code) {
            throw ChameleonProtocolException(
                "unexpected response CMD: expected ${expected.code} (${expected.name}), got ${resp.cmd}",
            )
        }
        if (!ChameleonResponses.isSuccess(resp)) {
            throw ChameleonProtocolException(
                "non-success status for ${expected.name}: 0x%04X".format(resp.status),
            )
        }
        return resp
    }

    companion object {
        /**
         * Pages per WRITE_EMU command. Conservative value (128 data bytes + header, well under the
         * 512 DATA limit and the firmware constraint page_start+count<=256).
         */
        const val DEFAULT_PAGES_PER_BLOCK = 32
    }
}
