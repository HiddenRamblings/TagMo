package com.hiddenramblings.tagmo.nfc;

import com.hiddenramblings.tagmo.TagMo;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Date;

public class TagData {
    public static final int TAG_FILE_SIZE = 532;

    public static final int UID_OFFSET = 0x1D4;
    public static final int UID_LENGTH = 0x9;
    public static final int AMIIBO_ID_OFFSET = 0x54;
    public static final int SETTING_FLAGS_OFFSET = 0x38 - 0xC;
    public static final int SETTING_FLAGS_LENGTH = 0x1;
    public static final int USER_DATA_INITIALIZED_OFFSET = 0x4;
    public static final int APP_DATA_INITIALIZED_OFFSET = 0x5;
    public static final int COUNTRY_CODE = 0x2D;
    public static final int INIT_DATA_OFFSET = 0x30;
    public static final int MODIFIED_DATA_OFFSET = 0x32;
    public static final int NICKNAME_OFFSET = 0x38;
    public static final int NICKNAME_LENGTH = 0x14;
    public static final int MII_NAME_OFFSET = 0x4C + 0x1A;
    public static final int MII_NAME_LENGTH = 0x14;
    public static final int TITLE_ID_OFFSET = 0xB6 - 0x8A + 0x80;
    public static final int WRITE_COUNT_MIN_VALUE = 0;
    public static final int WRITE_COUNT_MAX_VALUE = Short.MAX_VALUE & 0xFFFF;
    public static final int WRITE_COUNT_OFFSET = 0xB4;
    public static final int APP_ID_OFFSET = 0xB6;
    public static final int APP_DATA_OFFSET = 0xED;
    public static final int APP_DATA_LENGTH = 0xD8;

    private final ByteBuffer tagData;

    public TagData(byte[] tagData) throws Exception {
        if (tagData.length < TAG_FILE_SIZE)
            throw new Exception("Invalid tag data");

        this.tagData = ByteBuffer.wrap(tagData);
    }

    public byte[] array() {
        return tagData.array();
    }

    public byte[] getUID() {
        byte[] bytes = new byte[UID_LENGTH];
        tagData.position(UID_OFFSET);
        tagData.get(bytes, 0x0, UID_LENGTH - 1);
        bytes[8] = tagData.get(0x0);
        return bytes;
    }

    /*
    public void setUID(byte[] value) throws Exception {
        if (value.length != UID_LENGTH)
            throw new Exception();

        tagData.position(UID_OFFSET);
        tagData.put(value, 0x0, UID_LENGTH - 1);
        tagData.put(0x0, value[8]);
    }
    */
    public long getAmiiboID() {
        return tagData.getLong(AMIIBO_ID_OFFSET);
    }

    @SuppressWarnings("unused")
    public void setAmiiboID(long value) {
        tagData.putLong(AMIIBO_ID_OFFSET, value);
    }

    public BitSet getSettingFlags() {
        return TagUtils.getBitSet(tagData, SETTING_FLAGS_OFFSET, SETTING_FLAGS_LENGTH);
    }

    public void setSettingFlags(BitSet value) {
        TagUtils.putBitSet(tagData, SETTING_FLAGS_OFFSET, SETTING_FLAGS_LENGTH, value);
    }

    public boolean isUserDataInitialized() {
        return getSettingFlags().get(USER_DATA_INITIALIZED_OFFSET);
    }

    public void setUserDataInitialized(boolean value) {
        BitSet bitSet = getSettingFlags();
        bitSet.set(USER_DATA_INITIALIZED_OFFSET, value);
        setSettingFlags(bitSet);
    }

    public boolean isAppDataInitialized() {
        return getSettingFlags().get(APP_DATA_INITIALIZED_OFFSET);
    }

    public void setAppDataInitialized(boolean value) {
        BitSet bitSet = getSettingFlags();
        bitSet.set(APP_DATA_INITIALIZED_OFFSET, value);
        setSettingFlags(bitSet);
    }

    public void checkCountryCode(int value) throws Exception {
        if (value < 0 || value > 20)
            throw new Exception();
    }

    public int getCountryCode() throws Exception {
        int value = tagData.get(COUNTRY_CODE) & 0xFF;
        checkCountryCode(value);
        return value;
    }

    public void setCountryCode(int value) throws Exception {
        checkCountryCode(value);
        tagData.put(COUNTRY_CODE, (byte) value);
    }

    public Date getInitializedDate() throws Exception {
        return TagUtils.getDate(tagData, INIT_DATA_OFFSET);
    }

    public void setInitializedDate(Date value) throws Exception {
        TagUtils.putDate(tagData, INIT_DATA_OFFSET, value);
    }

    public Date getModifiedDate() throws Exception {
        return TagUtils.getDate(tagData, MODIFIED_DATA_OFFSET);
    }

    public void setModifiedDate(Date value) throws Exception {
        TagUtils.putDate(tagData, MODIFIED_DATA_OFFSET, value);
    }

    public String getNickname() throws UnsupportedEncodingException {
        return TagUtils.getString(tagData, NICKNAME_OFFSET, NICKNAME_LENGTH, TagMo.UTF_16BE);
    }

    public void setNickname(String value) {
            TagUtils.putString(tagData, NICKNAME_OFFSET, NICKNAME_LENGTH, TagMo.UTF_16BE, value);
    }

    public String getMiiName() throws UnsupportedEncodingException {
        return TagUtils.getString(tagData, MII_NAME_OFFSET, MII_NAME_LENGTH, TagMo.UTF_16LE);
    }

    public void setMiiName(String value) {
        TagUtils.putString(tagData, MII_NAME_OFFSET, MII_NAME_LENGTH, TagMo.UTF_16LE, value);
    }

    @SuppressWarnings("unused")
    public long getTitleID() {
        return tagData.getLong(TITLE_ID_OFFSET);
    }

    @SuppressWarnings("unused")
    public void setTitleID(long value) {
        tagData.putLong(TITLE_ID_OFFSET, value);
    }

    public void checkWriteCount(int value) throws Exception {
        if (value < WRITE_COUNT_MIN_VALUE || value > WRITE_COUNT_MAX_VALUE)
            throw new Exception();
    }

    public int getWriteCount() {
        return tagData.getShort(WRITE_COUNT_OFFSET) & 0xFFFF;
    }

    public void setWriteCount(int value) throws Exception {
        checkWriteCount(value);
        tagData.putShort(WRITE_COUNT_OFFSET, (short) value);
    }

    public int getAppId() {
        return tagData.getInt(APP_ID_OFFSET);
    }

    public void setAppId(int value) {
        tagData.putInt(APP_ID_OFFSET, value);
    }

    public byte[] getAppData() {
        return TagUtils.getBytes(tagData, APP_DATA_OFFSET, APP_DATA_LENGTH);
    }

    public void setAppData(byte[] value) throws Exception {
        if (value.length != APP_DATA_LENGTH)
            throw new Exception();

        TagUtils.putBytes(tagData, APP_DATA_OFFSET, value);
    }
}
