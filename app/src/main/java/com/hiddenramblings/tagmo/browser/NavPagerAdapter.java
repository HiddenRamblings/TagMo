package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

public class NavPagerAdapter extends FragmentStateAdapter {

    private final BrowserFragment fragmentBrowser = new BrowserFragment();
    private final SettingsFragment fragmentSettings = new SettingsFragment();
    private final EliteBankFragment fragmentElite = new EliteBankFragment();
    private final FlaskSlotFragment fragmentFlask = new FlaskSlotFragment();
    private final WebsiteFragment fragmentWebsite = new WebsiteFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @SuppressLint("NewApi")
    @NonNull @Override
    public Fragment createFragment(int position) {
        boolean hasEliteEnabled = TagMo.getPrefs().elite_support();
        boolean hasFlaskEnabled = TagMo.getPrefs().flask_support();
        switch (position) {
            case 1:
                return BuildConfig.WEAR_OS ? fragmentSettings : hasEliteEnabled
                        ? fragmentElite : hasFlaskEnabled
                        ? fragmentFlask : fragmentWebsite;
            case 2:
                return (BuildConfig.WEAR_OS || hasEliteEnabled) && hasFlaskEnabled
                        ? fragmentFlask : fragmentWebsite;
            case 3:
                return fragmentWebsite;
            default:
                return fragmentBrowser;
        }
    }

    @Override
    public int getItemCount() {
        int viewCount = 2;
        if (TagMo.getPrefs().elite_support()) viewCount += 1;
        if (TagMo.getPrefs().flask_support()) viewCount += 1;
        return viewCount;
    }

    public BrowserFragment getBrowser() {
        return fragmentBrowser;
    }

    public SettingsFragment getSettings() {
        return fragmentSettings;
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

