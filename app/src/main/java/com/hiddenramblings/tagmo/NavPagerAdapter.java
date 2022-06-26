package com.hiddenramblings.tagmo;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.hiddenramblings.tagmo.hardware.EliteBankFragment;
import com.hiddenramblings.tagmo.hardware.FlaskSlotFragment;

class NavPagerAdapter extends FragmentStateAdapter {
    private final BrowserFragment browserFragment = new BrowserFragment();
    private final FoomiiboFragment foomiiboFragment = new FoomiiboFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final FlaskSlotFragment flaskFragment = new FlaskSlotFragment();
    private final EliteBankFragment eliteFragment = new EliteBankFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @NonNull @Override
    public Fragment createFragment(int position) {
        if (position == 1)
            return foomiiboFragment;
        else if (position == 2)
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                    ? flaskFragment :eliteFragment;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && position == 3)
            return eliteFragment;
        else
            return browserFragment;
    }

    @Override
    public int getItemCount() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ? 4 : 3;
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
}

