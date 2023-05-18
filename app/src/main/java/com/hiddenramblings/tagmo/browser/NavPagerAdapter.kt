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
    val website = WebsiteFragment()
    val eliteBanks = EliteBankFragment()
    val flaskSlots = FlaskSlotFragment()
    @SuppressLint("NewApi")
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> browser
            1 -> website
            2 -> if (hasEliteEnabled) eliteBanks else if (hasFlaskEnabled) flaskSlots else website
            3 -> if (hasEliteEnabled && hasFlaskEnabled) flaskSlots else website
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