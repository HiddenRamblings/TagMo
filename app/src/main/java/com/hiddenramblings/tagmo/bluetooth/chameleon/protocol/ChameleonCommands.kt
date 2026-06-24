package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/**
 * Command builders: each function returns an encoded frame (ByteArray) ready to send.
 * Built on top of the [ChameleonFrame] codec. No Android/BLE dependency.
 *
 * Convention: STATUS = 0 (client -> device direction).
 */
object ChameleonCommands {

    /** GET_APP_VERSION — no data. */
    fun getAppVersion(): ByteArray = ChameleonFrame.encode(Command.GET_APP_VERSION)

    /** SET_ACTIVE_SLOT — payload: slot(1). */
    fun setActiveSlot(slot: Int): ByteArray {
        ChameleonProtocol.requireValidSlot(slot)
        return ChameleonFrame.encode(Command.SET_ACTIVE_SLOT, byteArrayOf(slot.toByte()))
    }

    /** SET_SLOT_TAG_TYPE — payload: slot(1) | tagType(2, U16 big-endian). */
    fun setSlotTagType(slot: Int, tagType: Int): ByteArray {
        ChameleonProtocol.requireValidSlot(slot)
        require(tagType in 0..0xFFFF) { "tagType out of U16 range: $tagType" }
        val data = byteArrayOf(slot.toByte(), (tagType ushr 8).toByte(), tagType.toByte())
        return ChameleonFrame.encode(Command.SET_SLOT_TAG_TYPE, data)
    }

    /** SET_SLOT_ENABLE — payload: slot(1) | senseType(1) | enable(1). */
    fun setSlotEnable(slot: Int, senseType: Int, enable: Boolean): ByteArray {
        ChameleonProtocol.requireValidSlot(slot)
        require(senseType in 0..0xFF) { "senseType out of byte range: $senseType" }
        val data = byteArrayOf(slot.toByte(), senseType.toByte(), if (enable) 1 else 0)
        return ChameleonFrame.encode(Command.SET_SLOT_ENABLE, data)
    }

    /** Convenience: configure the slot as NTAG215. */
    fun setSlotNtag215(slot: Int): ByteArray =
        setSlotTagType(slot, ChameleonProtocol.TAG_TYPE_NTAG215)

    /** Convenience: enable/disable the slot's HF part. */
    fun setHfSlotEnabled(slot: Int, enable: Boolean = true): ByteArray =
        setSlotEnable(slot, ChameleonProtocol.SENSE_HF, enable)

    /**
     * Writes a block of emulator pages (eload).
     * Layout (chameleon_cmd.py:1279-1290): data = startPage(1) | pageCount(1) | pages[pageCount * 4].
     */
    fun writeEmuPages(startPage: Int, pages: ByteArray): ByteArray {
        require(pages.isNotEmpty() && pages.size % ChameleonProtocol.NTAG215_PAGE_SIZE == 0) {
            "pages must be a multiple of ${ChameleonProtocol.NTAG215_PAGE_SIZE} bytes"
        }
        val count = pages.size / ChameleonProtocol.NTAG215_PAGE_SIZE
        // Firmware constraint: startPage + count <= 256.
        require(startPage in 0..0xFF && startPage + count <= 256) {
            "startPage/pageCount out of range (startPage=$startPage, count=$count)"
        }
        val data = ByteArray(2 + pages.size)
        data[0] = startPage.toByte()
        data[1] = count.toByte()
        pages.copyInto(data, 2)
        return ChameleonFrame.encode(Command.MF0_NTAG_WRITE_EMU_PAGE_DATA, data)
    }

    /** Persists the slots configuration to flash. */
    fun saveSlotConfig(): ByteArray = ChameleonFrame.encode(Command.SLOT_DATA_CONFIG_SAVE)

    /** SET_SLOT_DATA_DEFAULT — reset slot data to the type default. payload: slot(1) | tagType(2 BE). */
    fun setSlotDataDefault(slot: Int, tagType: Int = ChameleonProtocol.TAG_TYPE_NTAG215): ByteArray {
        ChameleonProtocol.requireValidSlot(slot)
        require(tagType in 0..0xFFFF) { "tagType out of U16 range: $tagType" }
        val data = byteArrayOf(slot.toByte(), (tagType ushr 8).toByte(), tagType.toByte())
        return ChameleonFrame.encode(Command.SET_SLOT_DATA_DEFAULT, data)
    }

    /** MF0_NTAG_SET_VERSION_DATA — emulated GET_VERSION response (8 bytes). */
    fun setVersionData(version: ByteArray = ChameleonProtocol.NTAG215_VERSION): ByteArray {
        require(version.size == 8) { "version must be 8 bytes" }
        return ChameleonFrame.encode(Command.MF0_NTAG_SET_VERSION_DATA, version)
    }

    /** MF0_NTAG_SET_SIGNATURE_DATA — emulated READ_SIG originality signature (32 bytes). */
    fun setSignatureData(signature: ByteArray): ByteArray {
        require(signature.size == ChameleonProtocol.NTAG215_SIGNATURE_SIZE) {
            "signature must be ${ChameleonProtocol.NTAG215_SIGNATURE_SIZE} bytes"
        }
        return ChameleonFrame.encode(Command.MF0_NTAG_SET_SIGNATURE_DATA, signature)
    }

    /** MF0_NTAG_SET_WRITE_MODE — payload: mode(1). */
    fun setWriteMode(mode: Int): ByteArray {
        require(mode in 0..0xFF) { "mode out of byte range: $mode" }
        return ChameleonFrame.encode(Command.MF0_NTAG_SET_WRITE_MODE, byteArrayOf(mode.toByte()))
    }

    /** MF0_NTAG_SET_UID_MAGIC_MODE — payload: enabled(1). */
    fun setUidMagicMode(enabled: Boolean): ByteArray =
        ChameleonFrame.encode(Command.MF0_NTAG_SET_UID_MAGIC_MODE, byteArrayOf(if (enabled) 1 else 0))

    /**
     * HF14A_SET_ANTI_COLL_DATA — sets the emulated UID/ATQA/SAK/ATS so the tag broadcasts the right UID.
     * payload: uidLen(1) | uid | atqa(2) | sak(1) | atsLen(1) | ats   (chameleon_cmd.py:1334)
     */
    fun setAntiCollision(
        uid: ByteArray,
        atqa: ByteArray = ChameleonProtocol.NTAG215_ATQA,
        sak: Int = ChameleonProtocol.NTAG215_SAK,
        ats: ByteArray = ByteArray(0),
    ): ByteArray {
        require(uid.isNotEmpty() && uid.size <= 0xFF) { "uid length out of range: ${uid.size}" }
        require(atqa.size == 2) { "atqa must be 2 bytes" }
        require(sak in 0..0xFF && ats.size <= 0xFF) { "sak/ats out of range" }
        val data = ByteArray(1 + uid.size + 2 + 1 + 1 + ats.size)
        var o = 0
        data[o++] = uid.size.toByte()
        uid.copyInto(data, o); o += uid.size
        atqa.copyInto(data, o); o += 2
        data[o++] = sak.toByte()
        data[o++] = ats.size.toByte()
        ats.copyInto(data, o)
        return ChameleonFrame.encode(Command.HF14A_SET_ANTI_COLL_DATA, data)
    }

    /** Extracts the 7-byte NTAG215 UID from a dump: pages 0-1 minus BCC0 (byte index 3). */
    fun ntag215Uid(dump: ByteArray): ByteArray {
        require(dump.size >= 8) { "dump too short for UID: ${dump.size}" }
        return byteArrayOf(dump[0], dump[1], dump[2], dump[4], dump[5], dump[6], dump[7])
    }
}
