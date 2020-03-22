package com.hiddenramblings.tagmo.amiibo;


import androidx.annotation.NonNull;

public class Amiibo implements Comparable<Amiibo> {
    public static long HEAD_MASK = 0xFFFFFFFF00000000L;
    public static long TAIL_MASK = 0x00000000FFFFFFFFL;
    public static int HEAD_BITSHIFT = 4 * 8;
    public static int TAIL_BITSHIFT = 4 * 0;

    public static long VARIANT_MASK = 0xFFFFFF0000000000L;
    public static long AMIIBO_MODEL_MASK = 0x00000000FFFF0000L;
    public static long UNKNOWN_MASK = 0x00000000000000FFL;

    public static String AMIIBO_API_IMAGE_URL = "https://raw.githubusercontent.com/N3evin/AmiiboAPI/master/images/icon_%08x-%08x.png";

    public AmiiboManager manager;
    public final long id;
    public final String name;
    public final AmiiboReleaseDates releaseDates;

    public Amiibo(AmiiboManager manager, long id, String name, AmiiboReleaseDates releaseDates) {
        this.manager = manager;
        this.id = id;
        this.name = name;
        this.releaseDates = releaseDates;
    }

    public Amiibo(AmiiboManager manager, String id, String name, AmiiboReleaseDates releaseDates) {
        this(manager, hexToId(id), name, releaseDates);
    }

    public String getName() {
        return this.name;
    }

    public int getHead() {
        return (int)((this.id & HEAD_MASK) >> HEAD_BITSHIFT);
    }

    public int getTail() {
        return (int)((this.id & TAIL_MASK) >> TAIL_BITSHIFT);
    }

    public long getGameSeriesId() {
        return this.id & GameSeries.MASK;
    }

    public GameSeries getGameSeries() {
        return this.manager.gameSeries.get(this.getGameSeriesId());
    }

    public long getCharacterId() {
        return this.id & Character.MASK;
    }

    public Character getCharacter() {
        return this.manager.characters.get(this.getCharacterId());
    }

    public long getVariantId() {
        return this.id & VARIANT_MASK;
    }

    public long getAmiiboTypeId() {
        return this.id & AmiiboType.MASK;
    }

    public AmiiboType getAmiiboType() {
        return this.manager.amiiboTypes.get(this.getAmiiboTypeId());
    }

    public long getAmiiboModelId() {
        return this.id & AMIIBO_MODEL_MASK;
    }

    public long getAmiiboSeriesId() {
        return this.id & AmiiboSeries.MASK;
    }

    public AmiiboSeries getAmiiboSeries() {
        return this.manager.amiiboSeries.get(this.getAmiiboSeriesId());
    }

    public long getUnknownId() {
        return this.id & UNKNOWN_MASK;
    }

    public static long hexToId(String value) {
        return Long.decode(value);
    }

    public String getImageUrl() {
        return String.format(AMIIBO_API_IMAGE_URL, getHead(), getTail());
    }

    public static String getImageUrl(long amiiboId) {
        int head = (int)((amiiboId & HEAD_MASK) >> HEAD_BITSHIFT);
        int tail = (int)((amiiboId & TAIL_MASK) >> TAIL_BITSHIFT);;
        return String.format(AMIIBO_API_IMAGE_URL, head, tail);
    }

    @Override
    public int compareTo(@NonNull Amiibo amiibo) {
        if (this.id == amiibo.id)
            return 0;

        GameSeries gameSeries1 = this.getGameSeries();
        GameSeries gameSeries2 = amiibo.getGameSeries();
        int value;
        if (gameSeries1 == null && gameSeries2 == null) {
            value = 0;
        } else if (gameSeries1 == null) {
            value = 1;
        } else if (gameSeries2 == null) {
            value = -1;
        } else {
            value = gameSeries1.compareTo(gameSeries2);
        }
        if (value != 0)
            return value;

        Character character1 = this.getCharacter();
        Character character2 = amiibo.getCharacter();
        if (character1 == null && character2 == null) {
            value = 0;
        } else if (character1 == null) {
            value = 1;
        } else if (character2 == null) {
            value = -1;
        } else {
            value = character1.compareTo(character2);
        }
        if (value != 0)
            return value;

        AmiiboSeries amiiboSeries1 = this.getAmiiboSeries();
        AmiiboSeries amiiboSeries2 = amiibo.getAmiiboSeries();
        if (amiiboSeries1 == null && amiiboSeries2 == null) {
            value = 0;
        } else if (amiiboSeries1 == null) {
            value = 1;
        } else if (amiiboSeries2 == null) {
            value = -1;
        } else {
            value = amiiboSeries1.compareTo(amiiboSeries2);
        }
        if (value != 0)
            return value;

        AmiiboType amiiboType1 = this.getAmiiboType();
        AmiiboType amiiboType2 = amiibo.getAmiiboType();
        if (amiiboType1 == null && amiiboType2 == null) {
            value = 0;
        } else if (amiiboType1 == null) {
            value = 1;
        } else if (amiiboType2 == null) {
            value = -1;
        } else {
            value = amiiboType1.compareTo(amiiboType2);
        }
        if (value != 0)
            return value;

        String name1 = this.getName();
        String name2 = amiibo.getName();
        if (name1 == null && name2 == null) {
            value = 0;
        } else if (name1 == null) {
            value = 1;
        } else if (name2 == null) {
            value = -1;
        } else {
            value = name1.compareTo(name2);
        }
        return value;
    }

    public static boolean matchesGameSeriesFilter(GameSeries gameSeries, String gameSeriesFilter) {
        if (gameSeries != null) {
            if (!gameSeriesFilter.isEmpty() && !gameSeries.name.equals(gameSeriesFilter))
                return false;
        }
        return true;
    }

    public static boolean matchesCharacterFilter(Character character, String characterFilter) {
        if (character != null) {
            if (!characterFilter.isEmpty() && !character.name.equals(characterFilter))
                return false;
        }
        return true;
    }

    public static boolean matchesAmiiboSeriesFilter(AmiiboSeries amiiboSeries, String amiiboSeriesFilter) {
        if (amiiboSeries != null) {
            if (!amiiboSeriesFilter.isEmpty() && !amiiboSeries.name.equals(amiiboSeriesFilter))
                return false;
        }
        return true;
    }

    public static boolean matchesAmiiboTypeFilter(AmiiboType amiiboType, String amiiboTypeFilter) {
        if (amiiboType != null) {
            if (!amiiboTypeFilter.isEmpty() && !amiiboType.name.equals(amiiboTypeFilter))
                return false;
        }
        return true;
    }
}
