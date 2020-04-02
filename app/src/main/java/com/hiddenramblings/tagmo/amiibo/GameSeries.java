package com.hiddenramblings.tagmo.amiibo;

import androidx.annotation.NonNull;

public class GameSeries implements Comparable<GameSeries> {
    public static final long MASK = 0xFFC0000000000000L;
    public static final int BITSHIFT = 4 * 13;

    public final AmiiboManager manager;
    public final long id;
    public final String name;

    public GameSeries(AmiiboManager manager, long id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public GameSeries(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public static long hexToId(String value) {
        return (Long.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull GameSeries gameSeries) {
        return this.name.compareTo(gameSeries.name);
    }
}
