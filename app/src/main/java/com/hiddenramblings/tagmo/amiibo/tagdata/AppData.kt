package com.hiddenramblings.tagmo.amiibo.tagdata

import android.content.Context
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import java.io.IOException
import java.nio.ByteBuffer

open class AppData(appData: ByteArray) {
    var appData: ByteBuffer
    val array: ByteArray
        get() = appData.array()

    init {
        if (appData.size < APP_FILE_SIZE)
            throw IOException(TagMo.appContext.getString(R.string.invalid_app_data))
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

        private val context: Context
            get() = TagMo.appContext
        val appIds = HashMap<Int, String>()

        var transferId = 0
        var transferData: ByteArray? = null

        init {
            appIds[AppId.ChibiRobo] = context.getString(R.string.chibi_robo)
            appIds[AppId.ZeldaTP] = context.getString(R.string.zelda_twilight)
            appIds[AppId.MHStories] = context.getString(R.string.mh_stories)
            appIds[AppId.MLPaperJam] = context.getString(R.string.ml_paper_jam)
            appIds[AppId.MLSuperstarSaga] = context.getString(R.string.ml_superstar_saga)
            appIds[AppId.MSSuperstars] = context.getString(R.string.ms_superstars)
            appIds[AppId.MarioTennis] = context.getString(R.string.mario_tennis)
            appIds[AppId.Pikmin] = context.getString(R.string.pikmin)
            appIds[AppId.Splatoon] = context.getString(R.string.splatoon)
            appIds[AppId.Splatoon3] = context.getString(R.string.splatoon_three)
            appIds[AppId.SSB] = context.getString(R.string.super_smash)
            appIds[AppId.SSBU] = context.getString(R.string.smash_ultimate)
            appIds[AppId.Unspecified] = context.getString(R.string.unspecified)
        }
    }
}