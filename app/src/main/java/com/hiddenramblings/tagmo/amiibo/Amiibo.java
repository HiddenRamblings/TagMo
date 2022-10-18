package com.hiddenramblings.tagmo.amiibo;


import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData;

import java.io.IOException;
import java.io.Serializable;

public class Amiibo implements Comparable<Amiibo>, Parcelable {

    private static final String AMIIBO_IMAGE = AmiiboManager.RENDER_RAW
            + "images/icon_%08x-%08x.png";

    static long HEAD_MASK = 0xFFFFFFFF00000000L;
    static long TAIL_MASK = 0x00000000FFFFFFFFL;
    static int HEAD_BITSHIFT = 4 * 8;
    static int TAIL_BITSHIFT = 4 * 0;

    static long VARIANT_MASK = 0xFFFFFF0000000000L;
    static long AMIIBO_MODEL_MASK = 0x00000000FFFF0000L;
    static long UNKNOWN_MASK = 0x00000000000000FFL;

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

    public int getHead() {
        return (int) ((this.id & HEAD_MASK) >> HEAD_BITSHIFT);
    }

    public int getTail() {
        return (int) ((this.id & TAIL_MASK) >> TAIL_BITSHIFT);
    }

    public long getCharacterId() {
        return this.id & Character.MASK;
    }

    public Character getCharacter() {
        return this.manager.characters.get(this.getCharacterId());
    }

    public long getGameSeriesId() {
        return this.id & GameSeries.MASK;
    }

    public GameSeries getGameSeries() {
        return this.manager.gameSeries.get(this.getGameSeriesId());
    }

    @SuppressWarnings("unused")
    public long getVariantId() {
        return this.id & VARIANT_MASK;
    }

    public long getAmiiboTypeId() {
        return this.id & AmiiboType.MASK;
    }

    public AmiiboType getAmiiboType() {
        return this.manager.amiiboTypes.get(this.getAmiiboTypeId());
    }

    @SuppressWarnings("unused")
    public long getAmiiboModelId() {
        return this.id & AMIIBO_MODEL_MASK;
    }

    public long getAmiiboSeriesId() {
        return this.id & AmiiboSeries.MASK;
    }

    public AmiiboSeries getAmiiboSeries() {
        return this.manager.amiiboSeries.get(this.getAmiiboSeriesId());
    }

    @SuppressWarnings("unused")
    public long getUnknownId() {
        return this.id & UNKNOWN_MASK;
    }

    static long hexToId(String value) {
        return Long.decode(value);
    }

    public static String idToHex(long amiiboId) {
        return String.format("%016X", amiiboId);
    }

    public static long dataToId(byte[] data) throws NumberFormatException, IOException {
        return new AmiiboData(data).getAmiiboID();
    }

    public String getImageUrl() {
        return String.format(AMIIBO_IMAGE, getHead(), getTail());
    }

    public static String getImageUrl(long amiiboId) {
        int head = (int) ((amiiboId & HEAD_MASK) >> HEAD_BITSHIFT);
        int tail = (int) ((amiiboId & TAIL_MASK) >> TAIL_BITSHIFT);
        return String.format(AMIIBO_IMAGE, head, tail);
    }

    public String getFlaskTail() {
        return Integer.toString(Integer.parseInt(
                idToHex(this.id).substring(8, 16), 16
        ), 36);
    }

    @Override
    public int compareTo(@NonNull Amiibo amiibo) {
        if (this.id == amiibo.id)
            return 0;
        int value;

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

        GameSeries gameSeries1 = this.getGameSeries();
        GameSeries gameSeries2 = amiibo.getGameSeries();
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

        String name1 = this.name;
        String name2 = amiibo.name;
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

    public static boolean matchesCharacterFilter(Character character, String characterFilter) {
        if (null != character) {
            return TextUtils.isEmpty(characterFilter) || character.name.equals(characterFilter);
        }
        return true;
    }

    public static boolean matchesGameSeriesFilter(GameSeries gameSeries, String gameSeriesFilter) {
        if (null != gameSeries) {
            return TextUtils.isEmpty(gameSeriesFilter) || gameSeries.name.equals(gameSeriesFilter);
        }
        return true;
    }

    public static boolean matchesAmiiboSeriesFilter(AmiiboSeries amiiboSeries, String amiiboSeriesFilter) {
        if (null != amiiboSeries) {
            return TextUtils.isEmpty(amiiboSeriesFilter) || amiiboSeries.name.equals(amiiboSeriesFilter);
        }
        return true;
    }

    public static boolean matchesAmiiboTypeFilter(AmiiboType amiiboType, String amiiboTypeFilter) {
        if (null != amiiboType) {
            return TextUtils.isEmpty(amiiboTypeFilter) || amiiboType.name.equals(amiiboTypeFilter);
        }
        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(this.name);
        dest.writeLong(this.id);
        dest.writeSerializable((Serializable) this.releaseDates);
//        dest.writeByteArray(this.data);
    }

    protected Amiibo(Parcel in) {
        this.name = in.readString();
        this.id = in.readLong();
        this.releaseDates = (AmiiboReleaseDates) in.readSerializable();
//        this.data = in.createByteArray();
//        in.readByteArray(this.data);
    }

    public static final Parcelable.Creator<Amiibo> CREATOR = new Parcelable.Creator<>() {
        @Override
        public Amiibo createFromParcel(Parcel source) {
            return new Amiibo(source);
        }

        @Override
        public Amiibo[] newArray(int size) {
            return new Amiibo[size];
        }
    };
}
