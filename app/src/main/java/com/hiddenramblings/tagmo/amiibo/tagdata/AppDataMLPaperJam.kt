/*
 * ====================================================================
 * Unlock all Sparkle cards - Bangaio
 * gbatemp -> mario-luigi-paper-jam-amiibo-save-data-editing.482084
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.amiibo.tagdata

import com.hiddenramblings.tagmo.nfctech.TagArray.toHexByteArray

class AppDataMLPaperJam(appData: ByteArray?) : AppData(appData!!) {

    private val sparkleCardHex = "FF FF FF FF FF FF FF"
    private val sparkleCardBytes = sparkleCardHex.toHexByteArray()

    fun checkSparkleCards(): Boolean {
        val sparkleCardData = appData.array().copyOfRange(SPARKLE_CARD_OFFSET, sparkleCardBytes.size)
        return sparkleCardData.contentEquals(sparkleCardBytes)
    }
    fun unlockSparkleCards() {
        sparkleCardBytes.forEachIndexed { x, byte -> appData.put(SPARKLE_CARD_OFFSET + x, byte) }
    }

    companion object {
        const val SPARKLE_CARD_OFFSET = 0x20 // 0xF0
    }
}

