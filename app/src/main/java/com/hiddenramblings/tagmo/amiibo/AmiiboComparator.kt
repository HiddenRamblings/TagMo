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
                value = when {
                    null == amiibo1 && null == amiibo2 -> 0
                    null == amiibo1 -> 1
                    null == amiibo2 -> -1
                    sort == SORT.NAME.value -> compareAmiiboName(amiibo1, amiibo2)
                    sort == SORT.AMIIBO_SERIES.value -> compareAmiiboSeries(amiibo1, amiibo2)
                    sort == SORT.AMIIBO_TYPE.value -> compareAmiiboType(amiibo1, amiibo2)
                    sort == SORT.GAME_SERIES.value -> compareGameSeries(amiibo1, amiibo2)
                    sort == SORT.CHARACTER.value -> compareCharacter(amiibo1, amiibo2)
                    else -> 0
                }
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
        return when {
            null == name1 && null == name2 -> 0
            null == name1 -> 1
            null == name2 -> -1
            else -> name1.compareTo(name2)
        }
    }

    private fun compareAmiiboSeries(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val amiiboSeries1 = amiibo1.amiiboSeries
        val amiiboSeries2 = amiibo2.amiiboSeries
        return when {
            null == amiiboSeries1 && null == amiiboSeries2 -> 0
            null == amiiboSeries1 -> 1
            null == amiiboSeries2 -> -1
            else -> amiiboSeries1.compareTo(amiiboSeries2)
        }
    }

    private fun compareAmiiboType(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val amiiboType1 = amiibo1.amiiboType
        val amiiboType2 = amiibo2.amiiboType
        return when {
            null == amiiboType1 && null == amiiboType2 -> 0
            null == amiiboType1 -> 1
            null == amiiboType2 -> -1
            else -> amiiboType1.compareTo(amiiboType2)
        }
    }

    private fun compareGameSeries(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val gameSeries1 = amiibo1.gameSeries
        val gameSeries2 = amiibo2.gameSeries
        return when {
            null == gameSeries1 && null == gameSeries2 -> 0
            null == gameSeries1 -> 1
            null == gameSeries2 -> -1
            else -> gameSeries1.compareTo(gameSeries2)
        }
    }

    private fun compareCharacter(amiibo1: Amiibo, amiibo2: Amiibo): Int {
        val character1 = amiibo1.character
        val character2 = amiibo2.character
        return when {
            null == character1 && null == character2 -> 0
            null == character1 -> 1
            null == character2 -> -1
            else -> character1.compareTo(character2)
        }
    }
}