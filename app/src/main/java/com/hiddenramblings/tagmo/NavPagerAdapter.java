package com.hiddenramblings.tagmo;

import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

class NavPagerAdapter extends FragmentStateAdapter {
    private final BrowserFragment browserFragment = new BrowserFragment();
    private final FoomiiboFragment foomiiboFragment = new FoomiiboFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @Override
    public Fragment createFragment(int position) {
        if (position == 1)
            return foomiiboFragment;
        else
            return browserFragment;
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    public BrowserFragment getBrowser() {
        return browserFragment;
    }

    public FoomiiboFragment getFoomiibo() {
        return foomiiboFragment;
    }
}

