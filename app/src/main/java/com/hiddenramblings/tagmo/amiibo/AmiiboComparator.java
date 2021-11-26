package com.hiddenramblings.tagmo.amiibo;

import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.SORT;

import java.io.File;
import java.util.Comparator;

public class AmiiboComparator implements Comparator<AmiiboFile> {

    BrowserSettings settings;

    public AmiiboComparator(BrowserSettings settings) {
        this.settings = settings;
    }

    @Override
    public int compare(AmiiboFile amiiboFile1, AmiiboFile amiiboFile2) {
        int value = 0;
        int sort = settings.getSort();

        File filePath1 = amiiboFile1.getFilePath();
        File filePath2 = amiiboFile2.getFilePath();
        if (sort == SORT.FILE_PATH.getValue() && !(null == filePath1  && null == filePath2 ))
            value = compareFilePath(filePath1, filePath2);

        long amiiboId1 = amiiboFile1.getId();
        long amiiboId2 = amiiboFile2.getId();
        if (sort == SORT.ID.getValue()) {
            value = compareAmiiboId(amiiboId1, amiiboId2);
        } else if (value == 0) {
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (null != amiiboManager ) {
                Amiibo amiibo1 = amiiboManager.amiibos.get(amiiboId1);
                Amiibo amiibo2 = amiiboManager.amiibos.get(amiiboId2);
                if (null == amiibo1  && null == amiibo2 )
                    //noinspection all
                    value = 0;
                else if (null == amiibo1 )
                    value = 1;
                else if (null == amiibo2 )
                    value = -1;
                else if (sort == SORT.NAME.getValue()) {
                    value = compareAmiiboName(amiibo1, amiibo2);
                } else if (sort == SORT.AMIIBO_SERIES.getValue()) {
                    value = compareAmiiboSeries(amiibo1, amiibo2);
                } else if (sort == SORT.AMIIBO_TYPE.getValue()) {
                    value = compareAmiiboType(amiibo1, amiibo2);
                } else if (sort == SORT.GAME_SERIES.getValue()) {
                    value = compareGameSeries(amiibo1, amiibo2);
                } else if (sort == SORT.CHARACTER.getValue()) {
                    value = compareCharacter(amiibo1, amiibo2);
                }
                if (value == 0 && null != amiibo1 )
                    value = amiibo1.compareTo(amiibo2);
            }
            if (value == 0)
                value = compareAmiiboId(amiiboId1, amiiboId2);
        }

        if (value == 0)
            value = compareFilePath(filePath1, filePath2);

        return value;
    }

    int compareFilePath(File filePath1, File filePath2) {
        if (null == filePath1 ) {
            return 1;
        } else if (null == filePath2 ) {
            return -1;
        } else {
            return filePath1.compareTo(filePath2);
        }
    }

    int compareAmiiboId(long amiiboId1, long amiiboId2) {
        return Long.compare(amiiboId1, amiiboId2);
    }

    int compareAmiiboName(Amiibo amiibo1, Amiibo amiibo2) {
        String name1 = amiibo1.name;
        String name2 = amiibo2.name;
        if (null == name1  && null == name2 ) {
            return 0;
        }
        if (null == name1 ) {
            return 1;
        } else if (null == name2 ) {
            return -1;
        }
        return name1.compareTo(name2);
    }

    int compareAmiiboSeries(Amiibo amiibo1, Amiibo amiibo2) {
        AmiiboSeries amiiboSeries1 = amiibo1.getAmiiboSeries();
        AmiiboSeries amiiboSeries2 = amiibo2.getAmiiboSeries();
        if (null == amiiboSeries1  && null == amiiboSeries2  ) {
            return 0;
        }
        if (null == amiiboSeries1 ) {
            return 1;
        } else if (null == amiiboSeries2) {
            return -1;
        }
        return amiiboSeries1.compareTo(amiiboSeries2);
    }

    int compareAmiiboType(Amiibo amiibo1, Amiibo amiibo2) {
        AmiiboType amiiboType1 = amiibo1.getAmiiboType();
        AmiiboType amiiboType2 = amiibo2.getAmiiboType();
        if (null == amiiboType1 && null == amiiboType2 ) {
            return 0;
        }
        if (null == amiiboType1) {
            return 1;
        } else if (null == amiiboType2) {
            return -1;
        }
        return amiiboType1.compareTo(amiiboType2);
    }

    int compareGameSeries(Amiibo amiibo1, Amiibo amiibo2) {
        GameSeries gameSeries1 = amiibo1.getGameSeries();
        GameSeries gameSeries2 = amiibo2.getGameSeries();
        if (null == gameSeries1 && null == gameSeries2) {
            return 0;
        }
        if (null == gameSeries1) {
            return 1;
        } else if (null == gameSeries2) {
            return -1;
        }
        return gameSeries1.compareTo(gameSeries2);
    }

    int compareCharacter(Amiibo amiibo1, Amiibo amiibo2) {
        Character character1 = amiibo1.getCharacter();
        Character character2 = amiibo2.getCharacter();
        if (null == character1 && null == character2) {
            return 0;
        }
        if (null == character1) {
            return 1;
        } else if (null == character2) {
            return -1;
        }
        return character1.compareTo(character2);
    }
}