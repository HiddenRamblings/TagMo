/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.amiibo.tagdata

import com.hiddenramblings.tagmo.nfctech.TagArray

class AppDataSplatoon(appData: ByteArray?) : AppData(appData!!) {
    private var saveDataHex = "01 01 00 00" +
            "00 00 00 00 42 C0 7B 0A 36 FA 8F C1 48 F5 09 60" +
            "C3 8E C5 52 EE E2 9A CE 27 71 5C 65 40 5B C3 F1" +
            "45 BF C6 3E 72 68 A8 9E DB 41 F4 25 8D 3A 4B 75" +
            "FC AA 74 19 24 EF 4F 3A 73 EC C1 CF C5 84 82 DB" +
            "73 8A 5F 63 A8 14 54 4C F1 91 B5 05 E4 23 B7 50" +
            "93 F5 85 6F 9A A5 C1 01 EB 51 33 1B 8B 54 37 1B" +
            "A8 7A 7C 44 6C A6 96 85 5C B3 E9 8B E4 DO FE 6E" +
            "AF 60 56 DA 2C 66 20 38 88 EO CF C5 3E A8 6E FA" +
            "2D A0 08 38 F4 DF 62 06 BO 6E BA FA 5C AE 84 68" +
            "BD 91 70 B1 7F 34 9E 8A B9 94 20 21 FF D4 08 B6" +
            "9E F3 63 B2 BO AE DD A2 5D 09 CC 9B 9B D1 D5 3E" +
            "88 BO 6D C2 AF 9D 66 7E 20 24 77 3B 88 7C AF 7F" +
            "88 9C B6 3D"
    var saveDataBytes = TagArray.hexToByteArray(saveDataHex.filter { !it.isWhitespace() })
    private var saveDataHex2 = "01 01 00 00" +
            "00 00 00 00 99 24 8D 4B 4C 84 9D FO A4 98 OC F5" +
            "AE D1 42 9B 40 1D 20 42 7D 85 28 77 62 OD FO C8" +
            "9E B1 B2 AO 8A D4 5A 42 39 95 6E F3 FA C4 36 FB" +
            "EC CB 4E C5 DC 8F 85 6E 88 FC 3E 37 5F 52 39 70" +
            "05 75 23 BC 34 5F BD 2F 9F 54 B6 AD 39 B8 7D 19" +
            "6E 63 68 DO EF 49 00 57 34 A9 82 22 BD 69 8E 16" +
            "A3 77 7C A6 99 5B 83 4B 15 97 C3 12 9B 35 F9 87" +
            "72 D6 2B 29 C2 OD 8A 72 57 FD 2F 78 E5 A1 7A DE" +
            "98 C5 F4 6A OF A2 F7 B1 8F 1B D9 EO 1B 1E DD BB" +
            "AO F3 48 5F OC 64 BE EB 04 7D D2 F7 FF B1 9C 7F" +
            "02 56 C7 3D B6 D4 F8 7A 90 A1 55 7E D4 4A E1 6B" +
            "78 CO 3F 99 89 47 37 37 ED 6A E2 C8 9E C5 32 67" +
            "AA 42 4E 6D"
    private var saveDataBytes2 = TagArray.hexToByteArray(saveDataHex2.filter { !it.isWhitespace() })
    fun checkSaveData(): Boolean {
        val saveData = appData.array().copyOfRange(GAME_DATA_OFFSET, saveDataBytes.size)
        return saveDataBytes.contentEquals(saveData) || saveDataBytes2.contentEquals(saveData)
    }

    fun injectSaveData(source: Boolean) {
        val appDataBytes = if (source) saveDataBytes else saveDataBytes2
        for (i in appDataBytes.indices) {
            appData.put(GAME_DATA_OFFSET + i, appDataBytes[i])
        }
    }

    companion object {
        const val GAME_DATA_OFFSET = 0x0
    }
}