package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

public class NavPagerAdapter extends FragmentStateAdapter {

    private final BrowserFragment fragmentBrowser = new BrowserFragment();
    private final EliteBankFragment fragmentElite = new EliteBankFragment();
    private final FlaskSlotFragment fragmentFlask = new FlaskSlotFragment();
    private final WebsiteFragment fragmentWebsite = new WebsiteFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @SuppressLint("NewApi")
    @NonNull @Override
    public Fragment createFragment(int position) {
        boolean hasEliteEnabled = TagMo.getPrefs().enable_elite_support().get();
        boolean hasFlaskEnabled = TagMo.getPrefs().enable_flask_support().get();
        switch (position) {
            case 1:
                return hasEliteEnabled ? fragmentElite : hasFlaskEnabled ? fragmentFlask : fragmentWebsite;
            case 2:
                return hasEliteEnabled && hasFlaskEnabled ? fragmentFlask : fragmentWebsite;
            case 3:
                return fragmentWebsite;
            default:
                return fragmentBrowser;
        }
    }

    @Override
    public int getItemCount() {
        int viewCount = 1;
        if (!TagMo.isGalaxyWear()) viewCount += 1;
        if (TagMo.getPrefs().enable_elite_support().get()) viewCount += 1;
        if (TagMo.getPrefs().enable_flask_support().get()) viewCount += 1;
        return viewCount;
    }

    public BrowserFragment getBrowser() {
        return fragmentBrowser;
    }

    public EliteBankFragment getEliteBanks() {
        return fragmentElite;
    }

    public FlaskSlotFragment getFlaskSlots() {
        return fragmentFlask;
    }

    public WebsiteFragment getWebsite() {
        return fragmentWebsite;
    }
}

