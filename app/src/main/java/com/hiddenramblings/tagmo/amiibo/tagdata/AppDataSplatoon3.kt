/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */
package com.hiddenramblings.tagmo.amiibo.tagdata

class AppDataSplatoon3(appData: ByteArray?) : AppData(appData!!) {

    fun checkSaveData(): Boolean {
        return SaveDataSplatoon(appData.array()).checkSaveData()
    }

    fun injectSaveData() {
        appData = SaveDataSplatoon(appData.array()).injectSaveData()
    }
}