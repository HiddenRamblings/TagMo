package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class NavPagerAdapter extends FragmentStateAdapter {
    private final BrowserFragment fragmentBrowser = new BrowserFragment();
    private final FoomiiboFragment fragmentFoomiibo = new FoomiiboFragment();
    private final EliteBankFragment fragmentElite = new EliteBankFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final FlaskSlotFragment fragmentFlask = new FlaskSlotFragment();
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private final JoyConFragment fragmentJoyCon = new JoyConFragment();

    public NavPagerAdapter(FragmentActivity fa) {
        super(fa);
    }

    @SuppressLint("NewApi")
    @NonNull @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return fragmentFoomiibo;
            case 2:
                return fragmentElite;
            case 3:
                return fragmentFlask;
            case 4:
                return fragmentJoyCon;
            default:
                return fragmentBrowser;
        }
    }

    @Override
    public int getItemCount() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 ? 5 : 3;
    }

    public BrowserFragment getBrowser() {
        return fragmentBrowser;
    }

    public FoomiiboFragment getFoomiibo() {
        return fragmentFoomiibo;
    }

    public EliteBankFragment getEliteBanks() {
        return fragmentElite;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public FlaskSlotFragment getFlaskSlots() {
        return fragmentFlask;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public JoyConFragment getJoyCons() {
        return fragmentJoyCon;
    }
}

