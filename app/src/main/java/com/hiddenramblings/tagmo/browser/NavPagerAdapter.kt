package com.hiddenramblings.tagmo.browser

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.browser.fragment.*

class NavPagerAdapter(fa: FragmentActivity?) : FragmentStateAdapter(fa!!) {
    var mPrefs = Preferences(TagMo.appContext)
    var hasEliteEnabled = mPrefs.eliteEnabled()
    var hasFlaskEnabled = mPrefs.flaskEnabled()
    val browser = BrowserFragment()
    val eliteBanks = EliteBankFragment()
    val flaskSlots = FlaskSlotFragment()
    val settings = SettingsFragment()
    val website = WebsiteFragment()
    @SuppressLint("NewApi")
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            1 -> if (!BuildConfig.WEAR_OS && hasEliteEnabled) eliteBanks
            else if (hasFlaskEnabled) flaskSlots
            else if (BuildConfig.WEAR_OS) settings
            else website
            2 -> if (!BuildConfig.WEAR_OS && hasEliteEnabled && hasFlaskEnabled) flaskSlots
            else if (BuildConfig.WEAR_OS && hasFlaskEnabled) settings
            else website
            3 -> website
            else -> browser
        }
    }

    override fun getItemCount(): Int {
        var viewCount = 2
        if (hasEliteEnabled) viewCount += 1
        if (hasFlaskEnabled) viewCount += 1
        return viewCount
    }
}