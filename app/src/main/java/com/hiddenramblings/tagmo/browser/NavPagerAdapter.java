package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.hiddenramblings.tagmo.R;

public class NavPagerAdapter extends FragmentStateAdapter {
    private final BrowserFragment browserFragment = new BrowserFragment();
    private final FoomiiboFragment foomiiboFragment = new FoomiiboFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final FlaskSlotFragment flaskFragment = new FlaskSlotFragment();
    private final EliteBankFragment eliteFragment = new EliteBankFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final JoyConFragment switchFragment = new JoyConFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @SuppressLint("NewApi")
    @NonNull @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return foomiiboFragment;
            case 2:
                return eliteFragment;
            case 3:
                return flaskFragment;
            case 4:
                return switchFragment;
            default:
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

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public JoyConFragment getSwitchPro() {
        return switchFragment;
    }
}

