package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.hiddenramblings.tagmo.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;

public class FoomiiboFragment extends Fragment implements
        FoomiiboAdapter.OnFoomiiboClickListener,
        SwipeRefreshLayout.OnRefreshListener {

    private final Foomiibo foomiibo = new Foomiibo();
    private final File directory = Storage.getDownloadDir("TagMo", "Foomiibo");
    private RecyclerView amiibosView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressDialog dialog;

    private BrowserSettings settings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_foomiibo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        this.settings = new BrowserSettings().initialize();

        amiibosView = view.findViewById(R.id.amiibos_list);
        ArrayList<Long> missingIds = ((BrowserActivity) requireActivity()).getMissingIds();
        if (this.settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiibosView.setLayoutManager(new GridLayoutManager(requireActivity(), getColumnCount()));
        else
            amiibosView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        amiibosView.setAdapter(new FoomiiboAdapter(settings, missingIds, this));
        this.settings.addChangeListener((BrowserSettings.BrowserSettingsListener) amiibosView.getAdapter());

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.swipeRefreshLayout.setProgressViewOffset(false, 0,
                (int) getResources().getDimension(R.dimen.swipe_progress_end));

        SearchView searchView = view.findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) requireContext().getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        searchView.setIconifiedByDefault(false);
        LinearLayout searchBar = searchView.findViewById(R.id.search_bar);
        searchBar.getLayoutParams().height = (int)
                getResources().getDimension(R.dimen.button_height_min);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return true;
            }
        });

        view.findViewById(R.id.clear_foomiibo_set).setOnClickListener(clickView -> {
            deleteDir(directory);
            ((BrowserActivity) requireActivity()).onRootFolderChanged(true);
        });

        view.findViewById(R.id.build_foomiibo_set).setOnClickListener(clickView -> buildFoomiiboSet());
    }

    private String decipherFilename(AmiiboManager amiiboManager, byte[] tagData) {
        try {
            long amiiboId = TagUtils.amiiboIdFromTag(tagData);
            String name = TagUtils.amiiboIdToHex(amiiboId);
            if (null != amiiboManager) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null != amiibo && null != amiibo.name) {
                    name = amiibo.name.replace(File.separatorChar, '-');
                }
            }

            byte[] uid = Arrays.copyOfRange(tagData, 0, 9);
            String uidHex = TagUtils.bytesToHex(uid);
            return String.format(Locale.ROOT, "%1$s[%2$s]-Foomiibo.bin", name, uidHex);
        } catch (Exception e) {
            Debug.Log(TagUtils.class, e.getMessage());
        }
        return "";
    }

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) requireContext().getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshMissingIds() {

    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteDir(File dir) {
        if (!directory.exists()) return;
        File[] files = dir.listFiles();
        if (null != files && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory())
                    deleteDir(file);
                else
                    file.delete();
            }
        }
        dir.delete();
    }

    private void buildFoomiiboFile(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, decipherFilename(
                    settings.getAmiiboManager(), tagData), tagData);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    private void buildFoomiiboSet() {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;
        Handler handler = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {
            handler.post(() -> dialog = ProgressDialog.show(requireActivity(),
                    "", "", true));

            deleteDir(directory);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();

            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                buildFoomiiboFile(amiibo);
                handler.post(() -> dialog.setMessage(getString(
                        R.string.foomiibo_progress, amiibo.getCharacter().name)));
            }
            handler.post(() -> dialog.dismiss());

            ((BrowserActivity) requireActivity()).onRootFolderChanged(true);
        });
    }

    private void onBuildFoomiibo(Amiibo amiibo) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;

        buildFoomiiboFile(amiibo);
        ((BrowserActivity) requireActivity()).onRootFolderChanged(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        this.onRefresh();
    }

    @Override
    public void onFoomiiboClicked(Amiibo foomiibo) {
        onBuildFoomiibo(foomiibo);
    }

    @Override
    public void onFoomiiboImageClicked(Amiibo foomiibo) {
        onBuildFoomiibo(foomiibo);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onRefresh() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(
                        requireContext().getApplicationContext()
                );
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error);
            }

            if (Thread.currentThread().isInterrupted()) return;

            final AmiiboManager uiAmiiboManager = amiiboManager;
            requireActivity().runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.notifyChanges();
            });
        });
        ArrayList<Long> missingIds = ((BrowserActivity) requireActivity()).getMissingIds();
        if (null != amiibosView.getAdapter()) {
            ((FoomiiboAdapter) amiibosView.getAdapter()).setMissingIds(missingIds);
            amiibosView.getAdapter().notifyDataSetChanged();
        }
        swipeRefreshLayout.setRefreshing(false);
    }
}

