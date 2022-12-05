package com.hiddenramblings.tagmo.amiibo

import androidx.documentfile.provider.DocumentFile
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.SORT
import java.io.File

class AmiiboFileComparator(var settings: BrowserSettings) : Comparator<AmiiboFile> {
    override fun compare(amiiboFile1: AmiiboFile, amiiboFile2: AmiiboFile): Int {
        var value = 0
        val sort = settings.sort
        val filePath1 = amiiboFile1.getFilePath()
        val filePath2 = amiiboFile2.getFilePath()
        val docPath1 = amiiboFile1.docUri
        val docPath2 = amiiboFile2.docUri
        if (null != docPath1 && null != docPath2) {
            if (sort == SORT.FILE_PATH.value && !(null == filePath1 && null == filePath2)) value =
                compareDocPath(docPath1, docPath2)
        } else {
            if (sort == SORT.FILE_PATH.value && !(null == filePath1 && null == filePath2)) value =
                compareFilePath(filePath1, filePath2)
        }
        val amiiboId1 = amiiboFile1.getId()
        val amiiboId2 = amiiboFile2.getId()
        if (sort == SORT.ID.value) {
            value = compareAmiiboId(amiiboId1, amiiboId2)
        } else if (value == 0) {
            val amiiboManager = settings.amiiboManager
            if (null != amiiboManager) {
                val amiibo1 = amiiboManager.amiibos[amiiboId1]
                val amiibo2 = amiiboManager.amiibos[amiiboId2]
                if (null == amiibo1 && null == amiibo2) value = 0 else if (null == amiibo1) value =
                    1 else if (null == amiibo2) value = -1 else if (sort == SORT.NAME.value) {
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
                if (value == 0 && null != amiibo1) value = amiibo1.compareTo(amiibo2)
            }
            if (value == 0) value = compareAmiiboId(amiiboId1, amiiboId2)
        }
        if (value == 0) {
            value = if (null != docPath1 && null != docPath2) {
                compareDocPath(docPath1, docPath2)
            } else {
                compareFilePath(filePath1, filePath2)
            }
        }
        return value
    }

    private fun compareFilePath(filePath1: File?, filePath2: File?): Int {
        return if (null == filePath1) {
            1
        } else if (null == filePath2) {
            -1
        } else {
            filePath1.compareTo(filePath2)
        }
    }

    private fun compareDocPath(filePath1: DocumentFile?, filePath2: DocumentFile?): Int {
        return if (null == filePath1) {
            1
        } else if (null == filePath2) {
            -1
        } else {
            filePath1.uri.compareTo(filePath2.uri)
        }
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