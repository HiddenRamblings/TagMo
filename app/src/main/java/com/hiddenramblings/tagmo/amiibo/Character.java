package com.hiddenramblings.tagmo.amiibo;

import androidx.annotation.NonNull;

public class Character implements Comparable<Character> {
    public static final long MASK = 0xFFFF000000000000L;
    public static final int BITSHIFT = 4 * 12;

    public final AmiiboManager manager;
    public final long id;
    public final String name;

    public Character(AmiiboManager manager, long id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public Character(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public long getGameSeriesId() {
        return this.id & GameSeries.MASK;
    }

    public GameSeries getGameSeries() {
        return this.manager.gameSeries.get(this.getGameSeriesId());
    }

    public static long hexToId(String value) {
        return (Long.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull Character character) {
        return this.name.compareTo(character.name);
    }
}
