package com.hiddenramblings.tagmo.browser;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class NavPagerAdapter extends FragmentStateAdapter {
    private final BrowserFragment browserFragment = new BrowserFragment();
    private final FoomiiboFragment foomiiboFragment = new FoomiiboFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final FlaskSlotFragment flaskFragment = new FlaskSlotFragment();
    private final EliteBankFragment eliteFragment = new EliteBankFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final SwitchProFragment switchFragment = new SwitchProFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @NonNull @Override
    public Fragment createFragment(int position) {
        if (position == 1) {
            return foomiiboFragment;
        } else if (position == 2) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                    ? flaskFragment : eliteFragment;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            if (position == 3)
                return eliteFragment;
            else if (position == 4)
                return switchFragment;
            else
                return browserFragment;
        } else {
            return browserFragment;
        }
    }

    @Override
    public int getItemCount() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ? 5 : 3;
    }

    public BrowserFragment getBrowser() {
        return browserFragment;
    }

    public FoomiiboFragment getFoomiibo() {
        return foomiiboFragment;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public FlaskSlotFragment getFlaskSlots() {
        return flaskFragment;
    }

    public EliteBankFragment getEliteBanks() {
        return eliteFragment;
    }

    public SwitchProFragment getSwitchPro() {
        return switchFragment;
    }
}

