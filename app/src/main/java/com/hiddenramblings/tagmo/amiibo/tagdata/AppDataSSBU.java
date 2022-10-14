/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo.amiibo.tagdata;

import com.hiddenramblings.tagmo.nfctech.TagArray;

import java.io.IOException;

public class AppDataSSBU extends AppData {
    static final int GAME_CRC32_OFFSET = 0x0;

    static final int APPEARANCE_OFFSET = 0xC7;
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
    static final int PHYSICAL_MIN_VALUE = -2500;
    static final int PHYSICAL_MAX_VALUE = 2500;
    static final int STATS_ATTACK_OFFSET = 0x74;
    static final int STATS_DEFENSE_OFFSET = 0x76;
    static final int STATS_SPEED_OFFSET = 0x14;

    static final int BONUS_MIN_VALUE = 0;
    static final int BONUS_MAX_VALUE = 0xFF;
    static final int BONUS_EFFECT1_OFFSET = 0x0D;
    static final int BONUS_EFFECT2_OFFSET = 0x0E;
    static final int BONUS_EFFECT3_OFFSET = 0x0F;

    static final int LEVEL_MIN_VALUE = 1;
    static final int EXPERIENCE_MIN_VALUE = 0x0000;
    static final int EXPERIENCE_MAX_VALUE = 0x0F48;
    static final int EXPERIENCE_OFFSET = 0x70;
    static final int EXPERIENCE_OFFSET_CPU = 0x72;

    static final int GIFT_COUNT_OFFSET = 0x7A;

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

    public int getGiftCount() throws NumberFormatException {
        return AppData.getInverseShort(appData, GIFT_COUNT_OFFSET);
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

    public void checkPhysicalStats(int value) throws NumberFormatException {
        if (value < PHYSICAL_MIN_VALUE || value > PHYSICAL_MAX_VALUE)
            throw new NumberFormatException();
    }

    public void checkStat(int value) throws NumberFormatException {
        if (value < STATS_MIN_VALUE || value > STATS_MAX_VALUE)
            throw new NumberFormatException();
    }

    public int getStatAttack() throws NumberFormatException {
        short value = AppData.getInverseShort(appData, STATS_ATTACK_OFFSET);
        checkPhysicalStats(value);
        return value;
    }

    public void setStatAttack(int value) throws NumberFormatException {
        checkPhysicalStats(value);
        AppData.putInverseShort(appData, STATS_ATTACK_OFFSET, value);
    }

    public int getStatDefense() throws NumberFormatException {
        short value = AppData.getInverseShort(appData, STATS_DEFENSE_OFFSET);
        checkPhysicalStats(value);
        return value;
    }

    public void setStatDefense(int value) throws NumberFormatException {
        checkPhysicalStats(value);
        AppData.putInverseShort(appData, STATS_DEFENSE_OFFSET, value);
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
        short value = AppData.getInverseShort(appData, EXPERIENCE_OFFSET);
        checkExperience(value);
        return value;
    }

    public void setExperience(int value) throws NumberFormatException {
        checkExperience(value);
        AppData.putInverseShort(appData, EXPERIENCE_OFFSET, value);
    }

    public int getExperienceCPU() throws NumberFormatException {
        return AppData.getInverseShort(appData, EXPERIENCE_OFFSET_CPU);
    }

    private static final int[] LEVEL_THRESHOLDS = new int[] {
            0x0000, 0x0008, 0x0016, 0x0029, 0x003F, 0x005A, 0x0078, 0x009B, 0x00C3, 0x00EE,
            0x011C, 0x014A, 0x0178, 0x01AA, 0x01DC, 0x0210, 0x0244, 0x0278, 0x02AC, 0x02E1,
            0x0316, 0x034B, 0x0380, 0x03B6, 0x03EC, 0x0422, 0x0458, 0x048F, 0x04C6, 0x04FD,
            0x053B, 0x057E, 0x05C6, 0x0613, 0x0665, 0x06BC, 0x0718, 0x0776, 0x07DC, 0x0843,
            0x08AC, 0x0919, 0x099B, 0x0A3B, 0x0AEF, 0x0BB7, 0x0C89, 0x0D65, 0x0E55, 0x0F48
    };
    private static final int[] LEVEL_THRESHOLDS_CPU = new int[] {
            0x0000, 0x003F, 0x00D2, 0x01B2, 0x02ED, 0x0475, 0x0643, 0x0811, 0x0ACD
    };

    public int experienceToLevel(int experience, int[] threshholds) throws NumberFormatException {
        for (int i = threshholds.length - 1; i >= 0; i--) {
            if (threshholds[i] <= experience)
                return i + 1;
        }
        throw new NumberFormatException();
    }

    public int getLevel() throws NumberFormatException {
        return experienceToLevel(getExperience(), LEVEL_THRESHOLDS);
    }

    public void setLevel(int level) throws NumberFormatException {
        setExperience(LEVEL_THRESHOLDS[level - 1]);
    }

    public int getLevelCPU() throws NumberFormatException {
        return experienceToLevel(getExperienceCPU(), LEVEL_THRESHOLDS_CPU);
    }

    public void writeChecksum() {
        byte[] crc32 = TagArray.intToLittleEndian(new Checksum().generate(appData.array()));
        for (int i = 0; i < crc32.length; i++) {
            appData.put(GAME_CRC32_OFFSET + i, crc32[i]);
        }
    }
}
