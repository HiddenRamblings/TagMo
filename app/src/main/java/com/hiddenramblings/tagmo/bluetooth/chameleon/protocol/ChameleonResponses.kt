package com.hiddenramblings.tagmo.bluetooth.chameleon.protocol

/** Firmware application version, comparable (used for the firmware compatibility gate). */
data class AppVersion(val major: Int, val minor: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        if (major != other.major) major.compareTo(other.major) else minor.compareTo(other.minor)

    override fun toString(): String = "$major.$minor"
}

/** Response parsers + status validation. */
object ChameleonResponses {

    /**
     * Parses the GET_APP_VERSION response.
     * Layout: `struct.unpack('!BB', data)` => data = [major, minor]
     * (chameleon_cmd.py:29-35 @ ChameleonUltra 6e2a902d).
     */
    fun parseAppVersion(frame: ChameleonFrame): AppVersion {
        require(frame.data.size >= 2) { "version response too short (${frame.data.size} bytes)" }
        return AppVersion(frame.data[0].toInt() and 0xFF, frame.data[1].toInt() and 0xFF)
    }

    /**
     * True if the response carries a success status.
     *
     * The amiibo flow only uses general-config commands (SET_ACTIVE_SLOT, SET_SLOT_TAG_TYPE,
     * SET_SLOT_ENABLE, WRITE_EMU_PAGE_DATA, SLOT_DATA_CONFIG_SAVE, GET_APP_VERSION) which all reply
     * with STATUS_SUCCESS (0x68). We deliberately do NOT accept HF_TAG_OK (0x00) here: 0x00 is the
     * HF-operation success code, not returned by these commands, and accepting it would mask errors.
     */
    fun isSuccess(frame: ChameleonFrame): Boolean =
        frame.status == ChameleonProtocol.STATUS_SUCCESS

    /** Throws if the response is not a success. */
    fun requireSuccess(frame: ChameleonFrame): ChameleonFrame {
        require(isSuccess(frame)) { "non-success status: 0x%04X".format(frame.status) }
        return frame
    }
}
