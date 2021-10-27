package com.hiddenramblings.tagmo.nfctag.data;

import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.nfctag.NfcByte;
import com.hiddenramblings.tagmo.nfctag.TagUtils;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Date;
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

    public static final HashMap<Integer, String> countryCodes = new HashMap<>();

    private final ByteBuffer tagData;

    public AmiiboData(byte[] tagData) throws Exception {
        if (tagData.length < NfcByte.TAG_FILE_SIZE)
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
}
