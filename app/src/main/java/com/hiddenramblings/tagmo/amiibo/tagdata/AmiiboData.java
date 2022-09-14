package com.hiddenramblings.tagmo.amiibo.tagdata;

import android.content.Context;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.nfctech.NfcByte;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

public class AmiiboData {

    private static final int UID_OFFSET = 0x1D4;
    private static final int UID_LENGTH = 0x9;
    private static final int AMIIBO_ID_OFFSET = 0x54;
    private static final int SETTING_FLAGS_OFFSET = 0x38 - 0xC;
    private static final int SETTING_FLAGS_LENGTH = 0x1;
    private static final int USER_DATA_INITIALIZED_OFFSET = 0x4;
    private static final int APP_DATA_INITIALIZED_OFFSET = 0x5;
    private static final int COUNTRY_CODE = 0x2D;
    private static final int INIT_DATA_OFFSET = 0x30;
    private static final int MODIFIED_DATA_OFFSET = 0x32;
    private static final int NICKNAME_OFFSET = 0x38;
    private static final int NICKNAME_LENGTH = 0x14;
    private static final int MII_NAME_OFFSET = 0x4C + 0x1A;
    private static final int MII_NAME_LENGTH = 0x14;
    private static final int TITLE_ID_OFFSET = 0xB6 - 0x8A + 0x80;
    public static final int WRITE_COUNT_MIN_VALUE = 0;
    public static final int WRITE_COUNT_MAX_VALUE = Short.MAX_VALUE & 0xFFFF;
    private static final int WRITE_COUNT_OFFSET = 0xB4;
    private static final int APP_ID_OFFSET = 0xB6;
    private static final int APP_DATA_OFFSET = 0xED;
    private static final int APP_DATA_LENGTH = 0xD8;

    private final Context context = TagMo.getContext();

    public static final HashMap<Integer, String> countryCodes = new HashMap<>();

    private final ByteBuffer tagData;

    public AmiiboData(byte[] tagData) throws IOException {
        if (tagData.length < NfcByte.TAG_DATA_SIZE)
            throw new IOException(context.getString(R.string.invalid_data_size,
                    tagData.length, NfcByte.TAG_DATA_SIZE));

        this.tagData = ByteBuffer.wrap(tagData);
    }

    public byte[] array() {
        return tagData.array();
    }

    public byte[] getUID() {
        byte[] bytes = new byte[UID_LENGTH];
        tagData.position(UID_OFFSET);
        tagData.get(bytes, 0x0, UID_LENGTH - 1);
        bytes[0x8] = tagData.get(0x0);
        return bytes;
    }


    public void setUID(byte[] value) throws NumberFormatException {
        if (value.length != UID_LENGTH)
            throw new NumberFormatException(context.getString(R.string.invalid_uid_length));

        tagData.put(0x0, value[0x8]);
        tagData.position(UID_OFFSET);
        tagData.put(value, 0x0, UID_LENGTH - 1);
    }

    public long getAmiiboID() {
        return tagData.getLong(AMIIBO_ID_OFFSET);
    }

    @SuppressWarnings("unused")
    public void setAmiiboID(long value) {
        tagData.putLong(AMIIBO_ID_OFFSET, value);
    }

    public BitSet getSettingFlags() {
        return getBitSet(tagData, SETTING_FLAGS_OFFSET, SETTING_FLAGS_LENGTH);
    }

    public void setSettingFlags(BitSet value) {
        putBitSet(tagData, SETTING_FLAGS_OFFSET, SETTING_FLAGS_LENGTH, value);
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

    public void checkCountryCode(int value) throws NumberFormatException {
        if (value < 0 || value > 255)
            throw new NumberFormatException();
    }

    public int getCountryCode() throws NumberFormatException {
        int value = tagData.get(COUNTRY_CODE) & 0xFF;
        checkCountryCode(value);
        return value;
    }

    public void setCountryCode(int value) throws NumberFormatException {
        checkCountryCode(value);
        tagData.put(COUNTRY_CODE, (byte) value);
    }

    public Date getInitializedDate() throws NumberFormatException {
        return getDate(tagData, INIT_DATA_OFFSET);
    }

    public void setInitializedDate(Date value) throws NumberFormatException {
        putDate(tagData, INIT_DATA_OFFSET, value);
    }

    public Date getModifiedDate() throws NumberFormatException {
        return getDate(tagData, MODIFIED_DATA_OFFSET);
    }

    public void setModifiedDate(Date value) throws NumberFormatException {
        putDate(tagData, MODIFIED_DATA_OFFSET, value);
    }

    public String getNickname() throws UnsupportedEncodingException {
        return getString(tagData, NICKNAME_OFFSET, NICKNAME_LENGTH, CharsetCompat.UTF_16BE);
    }

    public void setNickname(String value) {
            putString(tagData, NICKNAME_OFFSET, NICKNAME_LENGTH, CharsetCompat.UTF_16BE, value);
    }

    public String getMiiName() throws UnsupportedEncodingException {
        return getString(tagData, MII_NAME_OFFSET, MII_NAME_LENGTH, CharsetCompat.UTF_16LE);
    }

    public void setMiiName(String value) {
        putString(tagData, MII_NAME_OFFSET, MII_NAME_LENGTH, CharsetCompat.UTF_16LE, value);
    }

    @SuppressWarnings("unused")
    public long getTitleID() {
        return tagData.getLong(TITLE_ID_OFFSET);
    }

    @SuppressWarnings("unused")
    public void setTitleID(long value) {
        tagData.putLong(TITLE_ID_OFFSET, value);
    }

    public void checkWriteCount(int value) throws NumberFormatException {
        if (value < WRITE_COUNT_MIN_VALUE || value > WRITE_COUNT_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getWriteCount() {
        return tagData.getShort(WRITE_COUNT_OFFSET) & 0xFFFF;
    }

    public void setWriteCount(int value) throws NumberFormatException {
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
        return getBytes(tagData, APP_DATA_OFFSET, APP_DATA_LENGTH);
    }

    public void setAppData(byte[] value) throws Exception {
        if (value.length != APP_DATA_LENGTH)
            throw new IOException(context.getString(R.string.invalid_app_data));

        putBytes(tagData, APP_DATA_OFFSET, value);
    }

    static {
        countryCodes.put(0, "Unset");

        //Japan
        countryCodes.put(1, "Japan");

        //Americas
        countryCodes.put(8, "Anguilla");
        countryCodes.put(9, "Antigua and Barbuda");
        countryCodes.put(10, "Argentina");
        countryCodes.put(11, "Aruba");
        countryCodes.put(12, "Bahamas");
        countryCodes.put(13, "Barbados");
        countryCodes.put(14, "Belize");
        countryCodes.put(15, "Bolivia");
        countryCodes.put(16, "Brazil");
        countryCodes.put(17, "British Virgin Islands");
        countryCodes.put(18, "Canada");
        countryCodes.put(19, "Cayman Islands");
        countryCodes.put(20, "Chile");
        countryCodes.put(21, "Colombia");
        countryCodes.put(22, "Costa Rica");
        countryCodes.put(23, "Dominica");
        countryCodes.put(24, "Dominican Republic");
        countryCodes.put(25, "Ecuador");
        countryCodes.put(26, "El Salvador");
        countryCodes.put(27, "French Guiana");
        countryCodes.put(28, "Grenada");
        countryCodes.put(29, "Guadeloupe");
        countryCodes.put(30, "Guatemala");
        countryCodes.put(31, "Guyana");
        countryCodes.put(32, "Haiti");
        countryCodes.put(33, "Honduras");
        countryCodes.put(34, "Jamaica");
        countryCodes.put(35, "Martinique");
        countryCodes.put(36, "Mexico");
        countryCodes.put(37, "Monsterrat");
        countryCodes.put(38, "Netherlands Antilles");
        countryCodes.put(39, "Nicaragua");
        countryCodes.put(40, "Panama");
        countryCodes.put(41, "Paraguay");
        countryCodes.put(42, "Peru");
        countryCodes.put(43, "St. Kitts and Nevis");
        countryCodes.put(44, "St. Lucia");
        countryCodes.put(45, "St. Vincent and the Grenadines");
        countryCodes.put(46, "Suriname");
        countryCodes.put(47, "Trinidad and Tobago");
        countryCodes.put(48, "Turks and Caicos Islands");
        countryCodes.put(49, "United States");
        countryCodes.put(50, "Uruguay");
        countryCodes.put(51, "US Virgin Islands");
        countryCodes.put(52, "Venezuela");

        //Europe & Africa
        countryCodes.put(64, "Albania");
        countryCodes.put(65, "Australia");
        countryCodes.put(66, "Austria");
        countryCodes.put(67, "Belgium");
        countryCodes.put(68, "Bosnia and Herzegovina");
        countryCodes.put(69, "Botswana");
        countryCodes.put(70, "Bulgaria");
        countryCodes.put(71, "Croatia");
        countryCodes.put(72, "Cyprus");
        countryCodes.put(73, "Czech Republic");
        countryCodes.put(74, "Denmark");
        countryCodes.put(75, "Estonia");
        countryCodes.put(76, "Finland");
        countryCodes.put(77, "France");
        countryCodes.put(78, "Germany");
        countryCodes.put(79, "Greece");
        countryCodes.put(80, "Hungary");
        countryCodes.put(81, "Iceland");
        countryCodes.put(82, "Ireland");
        countryCodes.put(83, "Italy");
        countryCodes.put(84, "Latvia");
        countryCodes.put(85, "Lesotho");
        countryCodes.put(86, "Lichtenstein");
        countryCodes.put(87, "Lithuania");
        countryCodes.put(88, "Luxembourg");
        countryCodes.put(89, "F.Y.R of Macedonia");
        countryCodes.put(90, "Malta");
        countryCodes.put(91, "Montenegro");
        countryCodes.put(92, "Mozambique");
        countryCodes.put(93, "Namibia");
        countryCodes.put(94, "Netherlands");
        countryCodes.put(95, "New Zealand");
        countryCodes.put(96, "Norway");
        countryCodes.put(97, "Poland");
        countryCodes.put(98, "Portugal");
        countryCodes.put(99, "Romania");
        countryCodes.put(100, "Russia");
        countryCodes.put(101, "Serbia");
        countryCodes.put(102, "Slovakia");
        countryCodes.put(103, "Slovenia");
        countryCodes.put(104, "South Africa");
        countryCodes.put(105, "Spain");
        countryCodes.put(106, "Swaziland");
        countryCodes.put(107, "Sweden");
        countryCodes.put(108, "Switzerland");
        countryCodes.put(109, "Turkey");
        countryCodes.put(110, "United Kingdom");
        countryCodes.put(111, "Zambia");
        countryCodes.put(112, "Zimbabwe");
        countryCodes.put(113, "Azerbaijan");
        countryCodes.put(114, "Mauritania (Islamic Republic of Mauritania)");
        countryCodes.put(115, "Mali (Republic of Mali)");
        countryCodes.put(116, "Niger (Republic of Niger)");
        countryCodes.put(117, "Chad (Republic of Chad)");
        countryCodes.put(118, "Sudan (Republic of the Sudan)");
        countryCodes.put(119, "Eritrea (State of Eritrea)");
        countryCodes.put(120, "Djibouti (Republic of Djibouti)");
        countryCodes.put(121, "Somalia (Somali Republic)");

        //Southeast Asia
        countryCodes.put(128, "Taiwan");
        countryCodes.put(136, "South Korea");
        countryCodes.put(144, "Hong Kong");
        countryCodes.put(145, "Macao");
        countryCodes.put(152, "Indonesia");
        countryCodes.put(153, "Singapore");
        countryCodes.put(154, "Thailand");
        countryCodes.put(155, "Philippines");
        countryCodes.put(156, "Malaysia");
        countryCodes.put(160, "China");

        //Middle East
        countryCodes.put(168, "U.A.E.");
        countryCodes.put(169, "India");
        countryCodes.put(170, "Egypt");
        countryCodes.put(171, "Oman");
        countryCodes.put(172, "Qatar");
        countryCodes.put(173, "Kuwait");
        countryCodes.put(174, "Saudi Arabia");
        countryCodes.put(175, "Syria");
        countryCodes.put(176, "Bahrain");
        countryCodes.put(177, "Jordan");
    }

    private static byte[] getBytes(ByteBuffer bb, int offset, int length) {
        byte[] bytes = new byte[length];
        bb.position(offset);
        bb.get(bytes);
        return bytes;
    }

    private static void putBytes(ByteBuffer bb, int offset, byte[] bytes) {
        bb.position(offset);
        bb.put(bytes);
    }

    private static BitSet getBitSet(ByteBuffer bb, int offset, int length) {
        BitSet bitSet = new BitSet(length * 8);
        byte[] bytes = getBytes(bb, offset, length);
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                bitSet.set((i * 8) + j, ((bytes[i] >> j) & 1) == 1);
            }
        }
        return bitSet;
    }

    private static void putBitSet(ByteBuffer bb, int offset, int length, BitSet bitSet) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                boolean set = bitSet.get((i * 8) + j);
                bytes[i] = (byte) (set ? bytes[i] | (1 << j) : bytes[i] & ~(1 << j));
            }
        }
        putBytes(bb, offset, bytes);
    }

    private static Date getDate(ByteBuffer bbuf, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(getBytes(bbuf, offset, 0x2));
        int date = bb.getShort();

        int year = ((date & 0xFE00) >> 9) + 2000;
        int month = ((date & 0x01E0) >> 5) - 1;
        int day = date & 0x1F;

        Calendar calendar = Calendar.getInstance();
        calendar.setLenient(false);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.getTime();
    }

    private static void putDate(ByteBuffer bbuf, int offset, Date date) {
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(date);

        int year = calendar.get(Calendar.YEAR) - 2000;
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.putShort((short) ((year << 9) | (month << 5) | day));

        putBytes(bbuf, offset, bb.array());
    }

    private static String getString(ByteBuffer bb, int offset, int length, Charset charset)
            throws UnsupportedEncodingException {
        bb.position(offset);
        
        // Find the position of the first null terminator
        int i;
        if (charset == CharsetCompat.UTF_16BE || charset == CharsetCompat.UTF_16LE) {
            for (i = 0; i < length / 2 && bb.getShort() != 0; i++);
            i *= 2;
        } else {
            for (i = 0; i < length && bb.get() != 0; i++);
        }

        return charset.decode(ByteBuffer.wrap(getBytes(bb, offset, i))).toString();
    }

    private static void putString(ByteBuffer bb, int offset, int length, Charset charset, String text) {
        byte[] bytes = new byte[length];
        byte[] bytes2 = charset.encode(text).array();
        System.arraycopy(bytes2, 0, bytes, 0, bytes2.length);

        putBytes(bb, offset, bytes);
    }
}
