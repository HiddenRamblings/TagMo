package com.hiddenramblings.tagmo.amiibo;

import android.support.annotation.NonNull;

public class AmiiboType implements Comparable<AmiiboType> {
    public static final int MASK = 0x000000FF;
    public static final int BITSHIFT = 4 * 0;

    public final AmiiboManager manager;
    public final int id;
    public final String name;

    public AmiiboType(AmiiboManager manager, int id, String name) {
        this.manager = manager;
        this.id = id;
        this.name = name;
    }

    public AmiiboType(AmiiboManager manager, String id, String name) {
        this(manager, hexToId(id), name);
    }

    public static int hexToId(String value) {
        return (Integer.decode(value) << BITSHIFT) & MASK;
    }

    @Override
    public int compareTo(@NonNull AmiiboType amiiboType) {
        if (this.id == amiiboType.id) {
            return 0;
        } else if (this.id < amiiboType.id) {
            return -1;
        } else {
            return 1;
        }
    }
}
