package com.hiddenramblings.tagmo.amiibo;

import android.support.annotation.NonNull;

public class AmiiboSeries implements Comparable<AmiiboSeries> {
    public static final int MASK = 0x0000FF00;
    public static final int BITSHIFT = 4 * 2;

    public final AmiiboManager manager;
    public final int id;
    public final String name;

    public AmiiboSeries(AmiiboManager manager, int id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public AmiiboSeries(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public static int hexToId(String value) {
        return (Integer.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull AmiiboSeries amiiboSeries) {
        return this.name.compareTo(amiiboSeries.name);
    }
}
