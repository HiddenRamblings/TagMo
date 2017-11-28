package com.hiddenramblings.tagmo;

import java.util.HashMap;

public class AppIds {
    public static final HashMap<Integer, String> appIds = new HashMap<>();

    static {
        appIds.put(AppDataTPFragment.APP_ID, "The Legend of Zelda: Twilight Princess HD");
        appIds.put(AppDataSSBFragment.APP_ID, "Super Smash Bros. for Nintendo 3DS and Wii U");
    }
}
