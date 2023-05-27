package com.hiddenramblings.tagmo.amiibo

import com.hiddenramblings.tagmo.BrowserSettings
import com.hiddenramblings.tagmo.BrowserSettings.SORT

class AmiiboComparator(var settings: BrowserSettings) : Comparator<Amiibo> {
    override fun compare(amiiboFile1: Amiibo, amiiboFile2: Amiibo): Int {
        var value = 0
        val sort = settings.sort
        val amiiboId1 = amiiboFile1.id
        val amiiboId2 = amiiboFile2.id
        if (sort == SORT.FILE_PATH.value || sort == SORT.ID.value) {
            value = compareAmiiboId(amiiboId1, amiiboId2)
        } else {
            settings.amiiboManager?.let {
                val amiibo1 = it.amiibos[amiiboId1]
                val amiibo2 = it.amiibos[amiiboId2]
                if (null == amiibo1 && null == amiibo2)
                    value = 0
                else if (null == amiibo1)
                    value = 1
                else if (null == amiibo2)
                    value = -1
                else if (sort == SORT.NAME.value) {
                    value = compareAmiiboName(amiibo1, amiibo2)
                } else if (sort == SORT.AMIIBO_SERIES.value) {
                    value = compareAmiiboSeries(amiibo1, amiibo2)
                } else if (sort == SORT.AMIIBO_TYPE.value) {
                    value = compareAmiiboType(amiibo1, amiibo2)
                } else if (sort == SORT.GAME_SERIES.value) {
                    value = compareGameSeries(amiibo1, amiibo2)
                } else if (sort == SORT.CHARACTER.value) {
                    value = compareCharacter(amiibo1, amiibo2)
                }
                if (value == 0 && null != amiibo1) value = amiibo1.compareTo(amiibo2!!)
            }
            if (value == 0) value = compareAmiiboId(amiiboId1, amiiboId2)
        }
        return value
    }

    private fun compareAmiiboId(amiiboId1: Long, amiiboId2: Long): Int {
        return amiiboId1.compareTo(amiiboId2)
    }

    private fun compareAmiiboName(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val name1 = amiibo1.name
        val name2 = amiibo2.name
        if (null == name1 && null == name2) {
            return 0
        }
        if (null == name1) {
            return 1
        } else if (null == name2) {
            return -1
        }
        return name1.compareTo(name2)
    }

    private fun compareAmiiboSeries(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val amiiboSeries1 = amiibo1.amiiboSeries
        val amiiboSeries2 = amiibo2.amiiboSeries
        if (null == amiiboSeries1 && null == amiiboSeries2) {
            return 0
        }
        if (null == amiiboSeries1) {
            return 1
        } else if (null == amiiboSeries2) {
            return -1
        }
        return amiiboSeries1.compareTo(amiiboSeries2)
    }

    private fun compareAmiiboType(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val amiiboType1 = amiibo1.amiiboType
        val amiiboType2 = amiibo2.amiiboType
        if (null == amiiboType1 && null == amiiboType2) {
            return 0
        }
        if (null == amiiboType1) {
            return 1
        } else if (null == amiiboType2) {
            return -1
        }
        return amiiboType1.compareTo(amiiboType2)
    }

    private fun compareGameSeries(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val gameSeries1 = amiibo1.gameSeries
        val gameSeries2 = amiibo2.gameSeries
        if (null == gameSeries1 && null == gameSeries2) {
            return 0
        }
        if (null == gameSeries1) {
            return 1
        } else if (null == gameSeries2) {
            return -1
        }
        return gameSeries1.compareTo(gameSeries2)
    }

    private fun compareCharacter(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val character1 = amiibo1.character
        val character2 = amiibo2.character
        if (null == character1 && null == character2) {
            return 0
        }
        if (null == character1) {
            return 1
        } else if (null == character2) {
            return -1
        }
        return character1.compareTo(character2)
    }
}