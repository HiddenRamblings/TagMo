package com.hiddenramblings.tagmo.amiibo.tagdata

import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import java.io.IOException
import java.nio.ByteBuffer

open class AppData(appData: ByteArray) {
    var appData: ByteBuffer
    fun array(): ByteArray {
        return appData.array()
    }

    init {
        if (appData.size < APP_FILE_SIZE) throw IOException(
            TagMo.getContext().getString(R.string.invalid_app_data)
        )
        this.appData = ByteBuffer.wrap(appData)
    }

    companion object {
        private const val APP_FILE_SIZE = 0xD8
        fun putInverseShort(appData: ByteBuffer, offset: Int, input: Int) {
            appData.put(offset, (input and 0xff).toByte())
            appData.put(offset + 1, (input shr 8 and 0xff).toByte())
        }

        fun getInverseShort(appData: ByteBuffer, offset: Int): Short {
            return ((appData[offset + 1].toInt() and 0xFF) shl 8
                    or (appData[offset].toInt() and 0xFF) and 0xFF).toShort()
        }

        val appIds = HashMap<Int, String>()

        init {
            appIds[TagDataEditor.AppId_ChibiRobo] =
                TagMo.getContext().getString(R.string.chibi_robo)
            appIds[TagDataEditor.AppId_ZeldaTP] =
                TagMo.getContext().getString(R.string.zelda_twilight)
            appIds[TagDataEditor.AppId_MHStories] =
                TagMo.getContext().getString(R.string.mh_stories)
            appIds[TagDataEditor.AppId_MLPaperJam] =
                TagMo.getContext().getString(R.string.ml_paper_jam)
            appIds[TagDataEditor.AppId_MLSuperstarSaga] =
                TagMo.getContext().getString(R.string.ml_superstar_saga)
            appIds[TagDataEditor.AppId_MSSuperstars] =
                TagMo.getContext().getString(R.string.ms_superstars)
            appIds[TagDataEditor.AppId_MarioTennis] =
                TagMo.getContext().getString(R.string.mario_tennis)
            appIds[TagDataEditor.AppId_Pikmin] =
                TagMo.getContext().getString(R.string.pikmin)
            appIds[TagDataEditor.AppId_Splatoon] =
                TagMo.getContext().getString(R.string.splatoon)
            appIds[TagDataEditor.AppId_Splatoon3] =
                TagMo.getContext().getString(R.string.splatoon_three)
            appIds[TagDataEditor.AppId_SSB] = TagMo.getContext().getString(R.string.super_smash)
            appIds[TagDataEditor.AppId_SSBU] = TagMo.getContext().getString(R.string.smash_ultimate)
            appIds[-1] = TagMo.getContext().getString(R.string.unspecified)
        }
    }
}