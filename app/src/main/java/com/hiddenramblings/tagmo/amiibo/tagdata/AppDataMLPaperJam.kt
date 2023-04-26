/*
 * ====================================================================
 * Unlock all Sparkle cards - Bangaio
 * gbatemp -> mario-luigi-paper-jam-amiibo-save-data-editing.482084
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.amiibo.tagdata

import com.hiddenramblings.tagmo.nfctech.TagArray

class AppDataMLPaperJam(appData: ByteArray?) : AppData(appData!!) {

    private val sparkleCards = TagArray.hexToByteArray("FFFFFFFFFFFFFF")
    fun checkSparkleCards(): Boolean {
        return sparkleCards.contentEquals(
            appData.array().copyOfRange(SPARKLE_CARD_OFFSET, sparkleCards.size)
        )
    }
    fun unlockSparkleCards() {
        appData.put(sparkleCards, SPARKLE_CARD_OFFSET, sparkleCards.size)
    }

    companion object {
        const val SPARKLE_CARD_OFFSET = 0x20
    }
}

