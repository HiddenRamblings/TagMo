package com.hiddenramblings.tagmo.amiibo.tagdata

import android.content.Context
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo

object AppId {
    const val ChibiRobo = 0x00152600
    const val ZeldaTP = 0x1019C800
    const val MHStories = 0x0016E100
    const val MLPaperJam = 0x00132600
    const val MLSuperstarSaga = 0x00194B00
    const val MSSuperstars = 0x00188B00
    const val MarioTennis = 0x10199000
    const val Pikmin = 0x001A9200
    const val Splatoon = 0x10162B00
    const val Splatoon3 = 0x38600500
    const val SSB = 0x10110E00
    const val SSBU = 0x34F80200
    const val Unspecified = 0x00000000

    val apps = HashMap<Int, String>()

    init {
        with (TagMo.appContext) {
            apps[ChibiRobo] = getString(R.string.chibi_robo)
            apps[ZeldaTP] = getString(R.string.zelda_twilight)
            apps[MHStories] = getString(R.string.mh_stories)
            apps[MLPaperJam] = getString(R.string.ml_paper_jam)
            apps[MLSuperstarSaga] = getString(R.string.ml_superstar_saga)
            apps[MSSuperstars] = getString(R.string.ms_superstars)
            apps[MarioTennis] = getString(R.string.mario_tennis)
            apps[Pikmin] = getString(R.string.pikmin)
            apps[Splatoon] = getString(R.string.splatoon)
            apps[Splatoon3] = getString(R.string.splatoon_three)
            apps[SSB] = getString(R.string.super_smash)
            apps[SSBU] = getString(R.string.smash_ultimate)
            apps[Unspecified] = getString(R.string.unspecified)
        }
    }
}