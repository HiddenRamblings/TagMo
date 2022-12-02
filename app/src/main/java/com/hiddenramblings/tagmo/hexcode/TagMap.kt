package com.hiddenramblings.tagmo.hexcode

import android.graphics.Color

class TagMap internal constructor(var index: Int, var color: Int, var label: String) {
    companion object {
        // positions must be sorted in descending order to facilitate lookup
        var getTagMap = arrayOf(
            TagMap(0x1E4, Color.parseColor("#F0E68C"), "Crypto Seed"),
            TagMap(0x1DC, Color.parseColor("#F0F8FF"), "Char. ID"),
            TagMap(0x1D4, Color.parseColor("#FA8072"), "NTAG UID"),
            TagMap(0x1B4, Color.parseColor("#CD853F"), "Tag HMAC"),
            TagMap(0xDC, Color.parseColor("#40E0D0"), "App Data"),
            TagMap(0xBC, Color.parseColor("#D2B48C"), "Hash"),
            TagMap(0xB6, Color.parseColor("#FF7F50"), "App ID"),
            TagMap(0xB4, Color.parseColor("#FF7F50"), "Write Counter"),
            TagMap(0x4C, Color.parseColor("#98FB98"), "Mii"),
            TagMap(0x38, Color.parseColor("#00BFFF"), "Nickname"),
            TagMap(0x34, Color.parseColor("#DDA0DD"), "Console #"),
            TagMap(0x2E, Color.parseColor("#F08080"), "Hash?"),
            TagMap(0x2C, Color.parseColor("#32CD32"), "Modified Date"),
            TagMap(0x2A, Color.parseColor("#FFA07A"), "Init Date"),
            TagMap(0x28, Color.YELLOW, "Counter"),
            TagMap(0x27, Color.parseColor("#BC8F8F"), "Country Code"),
            TagMap(0x26, Color.parseColor("#FFDEAD"), "Flags"),
            TagMap(0x08, Color.LTGRAY, "Data HMAC"),
            TagMap(0x00, Color.GRAY, "Lock/CC")
        )
    }
}