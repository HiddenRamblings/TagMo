package com.hiddenramblings.tagmo.amiibo;

import com.hiddenramblings.tagmo.nfctech.TagUtils;

import java.util.ArrayList;
import java.util.Arrays;

public class Bonuses {
    public ArrayList<String> getBonuses(Amiibo amiibo) {
        ArrayList<String> bonuses = new ArrayList<>();
        if (amiibo.getAmiiboTypeId() == AmiiboType.hexToId("0x09")) {
            String[] excludeIds = {
                    "0105000003580902",
                    "0100000003990902",
                    "01070000035a0902",
                    "0106000003590902",
                    "01000000037c0002",
                    "0101030004140902",
            };
            if (!Arrays.asList(excludeIds).contains(TagUtils.amiiboIdToHex(amiibo.id)))
                bonuses.add("Breath of the Wild");
        }

        return bonuses;
    }
}
