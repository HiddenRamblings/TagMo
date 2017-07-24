package com.hiddenramblings.tagmo.amiibo;

import android.support.annotation.NonNull;

public class GameSeries implements Comparable<GameSeries> {
    public static final int MASK = 0xFFC00000;
    public static final int BITSHIFT = 4 * 5;

    public final AmiiboManager manager;
    public final int id;
    public final String name;

    public GameSeries(AmiiboManager manager, int id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public GameSeries(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public static int hexToId(String value) {
        return (Integer.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull GameSeries gameSeries) {
        return this.name.compareTo(gameSeries.name);
    }
}
