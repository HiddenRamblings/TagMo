package com.hiddenramblings.tagmo.amiibo;

import androidx.annotation.NonNull;

public class AmiiboSeries implements Comparable<AmiiboSeries> {
    public static final long MASK = 0x000000000000FF00L;
    public static final int BITSHIFT = 4 * 2;

    public final AmiiboManager manager;
    public final long id;
    public final String name;

    public AmiiboSeries(AmiiboManager manager, long id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public AmiiboSeries(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public static long hexToId(String value) {
        return (Long.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull AmiiboSeries amiiboSeries) {
        return this.name.compareTo(amiiboSeries.name);
    }
}
