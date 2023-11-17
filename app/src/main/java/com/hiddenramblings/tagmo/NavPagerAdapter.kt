package com.hiddenramblings.tagmo

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.fragment.*

class NavPagerAdapter(fa: FragmentActivity?) : FragmentStateAdapter(fa!!) {
    private var mPrefs = Preferences(TagMo.appContext)
    private var isEliteEnabled = mPrefs.eliteEnabled()
    val browser = BrowserFragment()
    val website = WebsiteFragment()
    val eliteBanks = EliteBankFragment()
    val gattSlots = GattSlotFragment()
    @SuppressLint("NewApi")
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> browser
            1 -> website
            2 -> if (isEliteEnabled) eliteBanks else gattSlots
            3 -> gattSlots
            else -> browser
        }
    }

    override fun getItemCount(): Int {
        var viewCount = 3
        if (isEliteEnabled) viewCount += 1
        return viewCount
    }
}