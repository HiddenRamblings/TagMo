package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/*
 * JUnit4. Requires TestHex.kt (same package & module) for `hex()`.
 */
class ChameleonCommandsTest {

    // --- Verified layouts: frozen vectors ---

    @Test
    fun get_app_version() {
        assertArrayEquals(hex("11 EF 03 E8 00 00 00 00 15 00"), ChameleonCommands.getAppVersion())
    }

    @Test
    fun set_active_slot() {
        assertArrayEquals(hex("11 EF 03 EB 00 00 00 01 11 03 FD"), ChameleonCommands.setActiveSlot(3))
    }

    @Test
    fun set_slot_tag_type_generic() {
        // explicit tagType 0x04E8 (example value, NOT the real NTAG215 value)
        assertArrayEquals(
            hex("11 EF 03 EC 00 00 00 03 0E 02 04 E8 12"),
            ChameleonCommands.setSlotTagType(slot = 2, tagType = 0x04E8),
        )
    }

    @Test
    fun set_slot_enable_generic() {
        // explicit senseType = 2 (example value, NOT the real SENSE_HF value)
        assertArrayEquals(
            hex("11 EF 03 EE 00 00 00 03 0C 01 02 01 FC"),
            ChameleonCommands.setSlotEnable(slot = 1, senseType = 2, enable = true),
        )
    }

    // --- Bounds ---

    @Test
    fun slot_out_of_range_throws() {
        assertThrows(IllegalArgumentException::class.java) { ChameleonCommands.setActiveSlot(8) }
        assertThrows(IllegalArgumentException::class.java) { ChameleonCommands.setActiveSlot(-1) }
    }

    // --- Frozen vectors using the resolved constants (chameleon_enum.py @ 6e2a902d) ---

    @Test
    fun set_slot_ntag215() {
        // TAG_TYPE_NTAG215 = 1101 = 0x044D -> setSlotTagType(0, 0x044D)
        assertArrayEquals(
            hex("11 EF 03 EC 00 00 00 03 0E 00 04 4D AF"),
            ChameleonCommands.setSlotNtag215(0),
        )
    }

    @Test
    fun set_hf_slot_enabled() {
        // SENSE_HF = 2 -> setSlotEnable(0, 2, true)
        assertArrayEquals(
            hex("11 EF 03 EE 00 00 00 03 0C 00 02 01 FD"),
            ChameleonCommands.setHfSlotEnabled(0),
        )
    }

    @Test
    fun write_emu_pages_vector() {
        // OPCODE = MF0_NTAG_WRITE_EMU_PAGE_DATA = 4022 = 0x0FB6 ; layout page_start|count|data (verified)
        assertArrayEquals(
            hex("11 EF 0F B6 00 00 00 06 35 00 01 AA BB CC DD F1"),
            ChameleonCommands.writeEmuPages(0, hex("AA BB CC DD")),
        )
    }

    @Test
    fun save_slot_config_vector() {
        // OPCODE = SLOT_DATA_CONFIG_SAVE = 1009 = 0x03F1 ; no data
        assertArrayEquals(
            hex("11 EF 03 F1 00 00 00 00 0C 00"),
            ChameleonCommands.saveSlotConfig(),
        )
    }

    @Test
    fun write_emu_pages_rejects_non_page_multiple() {
        assertThrows(IllegalArgumentException::class.java) {
            ChameleonCommands.writeEmuPages(0, ByteArray(3)) // not a multiple of 4
        }
    }
}
