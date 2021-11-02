package com.hiddenramblings.tagmo.nfctech.code;

import android.graphics.Color;

public class TagMap {
    int index;
    int color;
    String label;

    TagMap(int index, int color, String label) {
        this.index = index;
        this.color = color;
        this.label = label;
    }

    public int getIndex() {
        return index;
    }

    public int getColor() {
        return color;
    }

    // positions must be sorted in descending order to facilitate lookup
    public static TagMap[] getTagMap = new TagMap[] {
            new TagMap(0x1E4, Color.parseColor("#F0E68C"), "Crypto Seed"),
            new TagMap(0x1DC, Color.parseColor("#F0F8FF"), "Char. ID"),
            new TagMap(0x1D4, Color.parseColor("#FA8072"), "NTAG UID"),
            new TagMap(0x1B4, Color.parseColor("#CD853F"), "Tag HMAC"),
            new TagMap(0xDC, Color.parseColor("#40E0D0"), "App Data"),
            new TagMap(0xBC, Color.parseColor("#D2B48C"), "Hash"),
            new TagMap(0xB6, Color.parseColor("#FF7F50"), "App ID"),
            new TagMap(0xB4, Color.parseColor("#FF7F50"), "Write Counter"),
            new TagMap(0x4C, Color.parseColor("#98FB98"), "Mii"),
            new TagMap(0x38, Color.parseColor("#00BFFF"), "Nickname"),
            new TagMap(0x34, Color.parseColor("#DDA0DD"), "Console #"),
            new TagMap(0x2E, Color.parseColor("#F08080"), "Hash?"),
            new TagMap(0x2C, Color.parseColor("#32CD32"), "Modified Date"),
            new TagMap(0x2A, Color.parseColor("#FFA07A"), "Init Date"),
            new TagMap(0x28, Color.YELLOW, "Counter"),
            new TagMap(0x27, Color.parseColor("#BC8F8F"), "Country Code"),
            new TagMap(0x26, Color.parseColor("#FFDEAD"), "Flags"),
            new TagMap(0x08, Color.LTGRAY, "Data HMAC"),
            new TagMap(0x00, Color.GRAY, "Lock/CC"),
    };
}
