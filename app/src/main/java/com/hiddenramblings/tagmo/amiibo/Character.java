package com.hiddenramblings.tagmo.amiibo;

import android.support.annotation.NonNull;

public class Character implements Comparable<Character> {
    public static final int MASK = 0xFFFF0000;
    public static final int BITSHIFT = 4 * 4;

    public final AmiiboManager manager;
    public final int id;
    public final String name;

    public Character(AmiiboManager manager, int id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public Character(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public int getGameSeriesId() {
        return this.id & GameSeries.MASK;
    }

    public GameSeries getGameSeries() {
        return this.manager.gameSeries.get(this.getGameSeriesId());
    }

    public static int hexToId(String value) {
        return (Integer.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull Character character) {
        return this.name.compareTo(character.name);
    }
}
