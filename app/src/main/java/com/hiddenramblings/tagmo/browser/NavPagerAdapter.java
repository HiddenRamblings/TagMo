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
        boolean hasFlaskEnabled = Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2);
        switch (position) {
            case 1:
                return hasEliteEnabled ? fragmentElite : hasFlaskEnabled ? fragmentFlask : fragmentWebsite;
            case 2:
                return hasFlaskEnabled ? fragmentFlask : fragmentWebsite;
            case 3:
                return fragmentWebsite;
            default:
                return fragmentBrowser;
        }
    }

    @Override
    public int getItemCount() {
        boolean hasEliteEnabled = TagMo.getPrefs().enable_elite_support().get();
        boolean hasFlaskEnabled = Debug.isNewer(Build.VERSION_CODES.JELLY_BEAN_MR2);
        return hasEliteEnabled && hasFlaskEnabled ? 4 : hasEliteEnabled || hasFlaskEnabled ? 3 : 2;
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

