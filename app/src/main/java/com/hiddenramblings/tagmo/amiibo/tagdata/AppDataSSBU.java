package com.hiddenramblings.tagmo.amiibo.tagdata;

import java.io.IOException;

public class AppDataSSBU extends AppData {
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
    static final int EXPERIENCE_MAX_VALUE = 0x0F48;
    static final int EXPERIENCE_OFFSET = 0x7C;  // 0140 0D

    public AppDataSSBU(byte[] appData) throws IOException {
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

    // github.com/odwdinc/SSBU_Amiibo/blob/master/src/ssbu_amiibo/amiibo_class.py#L195-L245
    private static final int[] LEVEL_THRESHOLDS = new int[] {
            0x0000, 0x0008, 0x0016, 0x0029, 0x003F, 0x005A, 0x0078, 0x009B, 0x00C3, 0x00EE,
            0x011C, 0x014A, 0x0178, 0x01AA, 0x01DC, 0x0210, 0x0244, 0x0278, 0x02AC, 0x02E1,
            0x0316, 0x034B, 0x0380, 0x03B6, 0x03EC, 0x0422, 0x0458, 0x048F, 0x04C6, 0x04FD,
            0x053B, 0x057E, 0x05C6, 0x0613, 0x0665, 0x06BC, 0x0718, 0x0776, 0x07DC, 0x0843,
            0x08AC, 0x0919, 0x099B, 0x0A3B, 0x0AEF, 0x0BB7, 0x0C89, 0x0D65, 0x0E55, 0x0F48
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
