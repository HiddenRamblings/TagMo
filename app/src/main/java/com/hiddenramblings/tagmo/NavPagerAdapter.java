package com.hiddenramblings.tagmo;

import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

class NavPagerAdapter extends FragmentStateAdapter {
    private BrowserFragment browserFragment = new BrowserFragment();
    private FoomiiboFragment foomiiboFragment = new FoomiiboFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private FlaskFragment flaskFragment = new FlaskFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @Override
    public Fragment createFragment(int position) {
        if (position == 1)
            return foomiiboFragment;
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && position == 2)
            return flaskFragment;
        else
            return browserFragment;
    }

    @Override
    public int getItemCount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) return 3;
        return 2;
    }

    public BrowserFragment getBrowser() {
        return browserFragment;
    }

    public FoomiiboFragment getFoomiibo() {
        return foomiiboFragment;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public FlaskFragment getFlask() {
        return flaskFragment;
    }
}

