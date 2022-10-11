package com.hiddenramblings.tagmo.amiibo.tagdata;

import com.hiddenramblings.tagmo.eightbit.util.TagArray;

import java.io.IOException;

public class AppDataSplatoon extends AppData {
    static final int GAME_DATA_OFFSET = 0x0;

    public AppDataSplatoon(byte[] appData) throws IOException {
        super(appData);
    }


    String gameDataHex =                        "01 01 00 00" +
            "00 00 00 00 42 C0 7B 0A 36 FA 8F C1 48";
    byte[] gameDataBytes = TagArray.hexToByteArray(gameDataHex.replace(" ", ""));

    String appDataHex =                            "F5 09 60" +
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
            "88 9C B6 3D";
    byte[] appDataBytes = TagArray.hexToByteArray(appDataHex.replace(" ", ""));

    public boolean checkGameData(byte[] tagData) {
        byte[] gameData = new byte[0];
        System.arraycopy(tagData, 0xDC, gameData, 0, gameDataBytes.length);
        return gameDataBytes == gameData;
    }

    public byte[] injectAppData(byte[] tagData) {
        System.arraycopy(gameDataBytes, 0, tagData, 0xDC, gameDataBytes.length);
        return tagData;
    }

    public void injectAppData() {
        for (int i = 0; i < appDataBytes.length; i++) {
            appData.put(GAME_DATA_OFFSET + i, appDataBytes[i]);
        }
    }
}