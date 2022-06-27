package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.flexbox.FlexboxLayout;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.robertlevonyan.views.chip.Chip;
import com.robertlevonyan.views.chip.OnCloseClickListener;

public class BrowserFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener {

    private FlexboxLayout chipList;
    private RecyclerView amiibosView;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BrowserActivity activity = (BrowserActivity) requireActivity();
        BrowserSettings settings = activity.getSettings();

        chipList = view.findViewById(R.id.chip_list);
        amiibosView = view.findViewById(R.id.amiibos_list);
        amiibosView.setHasFixedSize(true);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);

        chipList.setVisibility(View.GONE);

        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.swipeRefreshLayout.setProgressViewOffset(false, 0, (int) getResources().getDimension(R.dimen.swipe_progress_end));

        amiibosView.setLayoutManager(settings.getAmiiboView()
                == BrowserSettings.VIEW.IMAGE.getValue()
                ? new GridLayoutManager(activity, activity.getColumnCount())
                : new LinearLayoutManager(activity));
        this.amiibosView.setAdapter(new BrowserAdapter(settings, activity));
        settings.addChangeListener((BrowserSettings.BrowserSettingsListener)
                this.amiibosView.getAdapter());
    }

    public RecyclerView getAmiibosView() {
        return amiibosView;
    }

    @SuppressLint("InflateParams")
    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        if (null == chipList) return;
        FrameLayout chipContainer = chipList.findViewWithTag(tag);
        chipList.removeView(chipContainer);
        if (!text.isEmpty()) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = chipContainer.findViewById(R.id.chip);
            chip.setText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BrowserActivity) requireActivity()).onRootFolderChanged(false);
    }

    @Override
    public void onRefresh() {
        ((BrowserActivity) requireActivity()).onRefresh(true);
        swipeRefreshLayout.setRefreshing(false);
        ((BrowserActivity) requireActivity()).checkForUpdates();
    }
}

