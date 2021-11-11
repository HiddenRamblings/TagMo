package com.hiddenramblings.tagmo.nfctech.data;

import java.io.IOException;

public class AppDataSSB extends AppData {
    static final int APPEARANCE_OFFSET = 0x08;
    static final int APPEARANCE_MIN_VALUE = 0;
    static final int APPEARANCE_MAX_VALUE = 7;

    static final int SPECIAL_MIN_VALUE = 0;
    static final int SPECIAL_MAX_VALUE = 2;
    static final int SPECIAL_NEUTRAL_OFFSET = 0x09;
    static final int SPECIAL_SIDE_OFFSET = 0x0A;
    static final int SPECIAL_UP_OFFSET = 0x0B;
    static final int SPECIAL_DOWN_OFFSET = 0x0C;

    static final int STATS_MIN_VALUE = -200;
    static final int STATS_MAX_VALUE = 200;
    static final int STATS_ATTACK_OFFSET = 0x10;
    static final int STATS_DEFENSE_OFFSET = 0x12;
    static final int STATS_SPEED_OFFSET = 0x14;

    static final int BONUS_MIN_VALUE = 0;
    static final int BONUS_MAX_VALUE = 0xFF;
    static final int BONUS_EFFECT1_OFFSET = 0x0D;
    static final int BONUS_EFFECT2_OFFSET = 0x0E;
    static final int BONUS_EFFECT3_OFFSET = 0x0F;

    static final int EXPERIENCE_MIN_VALUE = 0x0000;
    static final int EXPERIENCE_MAX_VALUE = 0x093E;
    static final int EXPERIENCE_OFFSET = 0x7C;

    public AppDataSSB(byte[] appData) throws IOException {
        super(appData);
    }

    public void checkAppearence(int value) throws NumberFormatException {
        if (value < APPEARANCE_MIN_VALUE || value > APPEARANCE_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getAppearence() throws NumberFormatException {
        int value = appData.get(APPEARANCE_OFFSET) & 0xFF;
        checkAppearence(value);
        return value;
    }

    public void setAppearence(int value) throws NumberFormatException {
        checkAppearence(value);
        appData.put(APPEARANCE_OFFSET, (byte) value);
    }

    public void checkSpecial(int value) throws NumberFormatException {
        if (value < SPECIAL_MIN_VALUE || value > SPECIAL_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getSpecialNeutral() throws NumberFormatException {
        int value = appData.get(SPECIAL_NEUTRAL_OFFSET) & 0xFF;
        checkSpecial(value);
        return value;
    }

    public void setSpecialNeutral(int value) throws NumberFormatException {
        checkSpecial(value);
        appData.put(SPECIAL_NEUTRAL_OFFSET, (byte) value);
    }

    public int getSpecialSide() throws NumberFormatException {
        int value = appData.get(SPECIAL_SIDE_OFFSET) & 0xFF;
        checkSpecial(value);
        return value;
    }

    public void setSpecialSide(int value) throws NumberFormatException {
        checkSpecial(value);
        appData.put(SPECIAL_SIDE_OFFSET, (byte) value);
    }

    public int getSpecialUp() throws NumberFormatException {
        int value = appData.get(SPECIAL_UP_OFFSET) & 0xFF;
        checkSpecial(value);
        return value;
    }

    public void setSpecialUp(int value) throws NumberFormatException {
        checkSpecial(value);
        appData.put(SPECIAL_UP_OFFSET, (byte) value);
    }

    public int getSpecialDown() throws NumberFormatException {
        int value = appData.get(SPECIAL_DOWN_OFFSET) & 0xFF;
        checkSpecial(value);
        return value;
    }

    public void setSpecialDown(int value) throws NumberFormatException {
        checkSpecial(value);
        appData.put(SPECIAL_DOWN_OFFSET, (byte) value);
    }

    public void checkStat(int value) throws NumberFormatException {
        if (value < STATS_MIN_VALUE || value > STATS_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getStatAttack() throws NumberFormatException {
        int value = appData.getShort(STATS_ATTACK_OFFSET) & 0xFFFF;
        checkStat(value);
        return value;
    }

    public void setStatAttack(int value) throws NumberFormatException {
        checkStat(value);
        appData.putShort(STATS_ATTACK_OFFSET, (short) value);
    }

    public int getStatDefense() throws NumberFormatException {
        int value = appData.getShort(STATS_DEFENSE_OFFSET) & 0xFFFF;
        checkStat(value);
        return value;
    }

    public void setStatDefense(int value) throws NumberFormatException {
        checkStat(value);
        appData.putShort(STATS_DEFENSE_OFFSET, (short) value);
    }

    public int getStatSpeed() throws NumberFormatException {
        int value = appData.getShort(STATS_SPEED_OFFSET) & 0xFFFF;
        checkStat(value);
        return value;
    }

    public void setStatSpeed(int value) throws NumberFormatException {
        checkStat(value);
        appData.putShort(STATS_SPEED_OFFSET, (short) value);
    }

    public void checkBonus(int value) throws NumberFormatException {
        if (value < BONUS_MIN_VALUE || value > BONUS_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getBonusEffect1() throws NumberFormatException {
        int value = appData.get(BONUS_EFFECT1_OFFSET) & 0xFF;
        checkBonus(value);
        return value;
    }

    public void setBonusEffect1(int value) throws NumberFormatException {
        checkBonus(value);
        appData.put(BONUS_EFFECT1_OFFSET, (byte) value);
    }

    public int getBonusEffect2() throws NumberFormatException {
        int value = appData.get(BONUS_EFFECT2_OFFSET) & 0xFF;
        checkBonus(value);
        return value;
    }

    public void setBonusEffect2(int value) throws NumberFormatException {
        checkBonus(value);
        appData.put(BONUS_EFFECT2_OFFSET, (byte) value);
    }

    public int getBonusEffect3() throws NumberFormatException {
        int value = appData.get(BONUS_EFFECT3_OFFSET) & 0xFF;
        checkBonus(value);
        return value;
    }

    public void setBonusEffect3(int value) throws NumberFormatException {
        checkBonus(value);
        appData.put(BONUS_EFFECT3_OFFSET, (byte) value);
    }

    public void checkExperience(int value) throws NumberFormatException {
        if (value < EXPERIENCE_MIN_VALUE || value > EXPERIENCE_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getExperience() throws NumberFormatException {
        int value = appData.getShort(EXPERIENCE_OFFSET) & 0xFFFF;
        checkExperience(value);
        return value;
    }

    public void setExperience(int value) throws NumberFormatException {
        checkExperience(value);
        appData.putShort(EXPERIENCE_OFFSET, (short) value);
    }

    private static final int[] LEVEL_THRESHOLDS = new int[]{
            0x0000, 0x0008, 0x0010, 0x001D, 0x002D, 0x0048, 0x005B, 0x0075, 0x008D, 0x00AF,
            0x00E1, 0x0103, 0x0126, 0x0149, 0x0172, 0x0196, 0x01BE, 0x01F7, 0x0216, 0x0240,
            0x0278, 0x02A4, 0x02D6, 0x030E, 0x034C, 0x037C, 0x03BB, 0x03F4, 0x042A, 0x0440,
            0x048A, 0x04B6, 0x04E3, 0x053F, 0x056D, 0x059C, 0x0606, 0x0641, 0x0670, 0x069E,
            0x06FC, 0x072E, 0x075D, 0x07B9, 0x07E7, 0x0844, 0x0875, 0x08D3, 0x0902, 0x093E,
    };

    public int getLevel() throws NumberFormatException {
        int value = getExperience();
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (LEVEL_THRESHOLDS[i] <= value)
                return i + 1;
        }
        throw new NumberFormatException();
    }

    public void setLevel(int level) throws NumberFormatException {
        setExperience(LEVEL_THRESHOLDS[level - 1]);
    }
}
