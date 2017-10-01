package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.robertlevonyan.views.chip.Chip;
import com.robertlevonyan.views.chip.OnCloseClickListener;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.androidannotations.api.BackgroundExecutor;
import org.apmem.tools.layouts.FlowLayout;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@EActivity(R.layout.browser_layout)
@OptionsMenu({R.menu.browser_menu})
public class BrowserActivity extends AppCompatActivity implements
    SearchView.OnQueryTextListener,
    SwipeRefreshLayout.OnRefreshListener,
    BrowserSettings.BrowserSettingsListener,
    BrowserAmiibosAdapter.OnAmiiboClickListener
{
    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";
    public static final String BACKGROUND_FOLDERS = "folders";
    public static final String BACKGROUND_AMIIBO_FILES = "amiibo_files";


    public static final int SORT_ID = 0x0;
    public static final int SORT_NAME = 0x1;
    public static final int SORT_AMIIBO_SERIES = 0x2;
    public static final int SORT_AMIIBO_TYPE = 0x3;
    public static final int SORT_GAME_SERIES = 0x4;
    public static final int SORT_CHARACTER = 0x5;
    public static final int SORT_FILE_PATH = 0x6;

    public static final int VIEW_TYPE_SIMPLE = 0;
    public static final int VIEW_TYPE_COMPACT = 1;
    public static final int VIEW_TYPE_LARGE = 2;

    @ViewById(R.id.chip_list)
    FlowLayout chipList;
    @ViewById(R.id.amiibos_list)
    RecyclerView amiibosView;
    @ViewById(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @ViewById(R.id.empty_text)
    TextView emptyText;
    @ViewById(R.id.folders_list)
    RecyclerView foldersView;
    @ViewById(R.id.bottom_sheet)
    ViewGroup bottomSheet;
    @ViewById(R.id.current_folder)
    TextView currentFolderView;
    @ViewById(R.id.toggle)
    ImageView toggle;

    @OptionsMenuItem(R.id.search)
    MenuItem menuSearch;
    @OptionsMenuItem(R.id.sort_id)
    MenuItem menuSortId;
    @OptionsMenuItem(R.id.sort_name)
    MenuItem menuSortName;
    @OptionsMenuItem(R.id.sort_game_series)
    MenuItem menuSortGameSeries;
    @OptionsMenuItem(R.id.sort_character)
    MenuItem menuSortCharacter;
    @OptionsMenuItem(R.id.sort_amiibo_series)
    MenuItem menuSortAmiiboSeries;
    @OptionsMenuItem(R.id.sort_amiibo_type)
    MenuItem menuSortAmiiboType;
    @OptionsMenuItem(R.id.sort_file_path)
    MenuItem menuSortFilePath;
    @OptionsMenuItem(R.id.filter_game_series)
    MenuItem menuFilterGameSeries;
    @OptionsMenuItem(R.id.filter_character)
    MenuItem menuFilterCharacter;
    @OptionsMenuItem(R.id.filter_amiibo_series)
    MenuItem menuFilterAmiiboSeries;
    @OptionsMenuItem(R.id.filter_amiibo_type)
    MenuItem menuFilterAmiiboType;
    @OptionsMenuItem(R.id.view_simple)
    MenuItem menuViewSimple;
    @OptionsMenuItem(R.id.view_compact)
    MenuItem menuViewCompact;
    @OptionsMenuItem(R.id.view_large)
    MenuItem menuViewLarge;
    @OptionsMenuItem(R.id.recursive_files)
    MenuItem menuRecursiveFiles;
    @OptionsMenuItem(R.id.refresh)
    MenuItem menuRefresh;

    SearchView searchView;
    BottomSheetBehavior bottomSheetBehavior;

    @Pref
    Preferences_ prefs;
    @InstanceState
    BrowserSettings settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @AfterViews
    void afterViews() {
        this.bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        if (this.settings == null) {
            this.settings = new BrowserSettings();
            this.settings.setBrowserRootFolder(new File(Util.getSDCardDir(), prefs.browserRootFolder().get()));
            this.settings.setQuery(prefs.query().get());
            this.settings.setSort(prefs.sort().get());
            this.settings.setAmiiboSeriesFilter(prefs.filterAmiiboSeries().get());
            this.settings.setAmiiboTypeFilter(prefs.filterAmiiboType().get());
            this.settings.setCharacterFilter(prefs.filterCharacter().get());
            this.settings.setGameSeriesFilter(prefs.filterGameSeries().get());
            this.settings.setAmiiboView(prefs.browserAmiiboView().get());
            this.settings.setImageNetworkSettings(prefs.imageNetworkSetting().get());
            this.settings.setRecursiveFiles(prefs.recursiveFiles().get());
        } else {
            this.currentFolderView.setText(Util.friendlyPath(settings.getBrowserRootFolder()));
            this.onFilterGameSeriesChanged();
            this.onFilterCharacterChanged();
            this.onFilterAmiiboSeriesChanged();
            this.onFilterAmiiboTypeChanged();
            this.onAmiiboFilesChanged();
        }
        this.settings.addChangeListener(this);

        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.amiibosView.setLayoutManager(new LinearLayoutManager(this));
        this.amiibosView.setAdapter(new BrowserAmiibosAdapter(settings, this));

        this.foldersView.setLayoutManager(new LinearLayoutManager(this));
        this.foldersView.setAdapter(new BrowserFoldersAdapter(settings));

        this.loadAmiiboManager();
        this.settings.notifyChanges();
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        this.onSortChanged();
        this.onViewChanged();
        this.onRecursiveFilesChanged();

        // setOnQueryTextListener will clear this, so make a copy
        String query = settings.getQuery();

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menuSearch.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(true);
        searchView.setOnQueryTextListener(this);

        //focus the SearchView
        if (!query.isEmpty()) {
            menuSearch.expandActionView();
            searchView.setQuery(query, true);
            searchView.clearFocus();
        }

        return result;
    }

    @Click(R.id.toggle)
    void onBrowserFolderExpandClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @OptionsItem(R.id.sort_id)
    void onSortIdClick() {
        settings.setSort(SORT_ID);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_name)
    void onSortNameClick() {
        settings.setSort(SORT_NAME);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_game_series)
    void onSortGameSeriesClick() {
        settings.setSort(SORT_GAME_SERIES);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_character)
    void onSortCharacterClick() {
        settings.setSort(SORT_CHARACTER);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_amiibo_series)
    void onSortAmiiboSeriesClick() {
        settings.setSort(SORT_AMIIBO_SERIES);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_amiibo_type)
    void onSortAmiiboTypeClick() {
        settings.setSort(SORT_AMIIBO_TYPE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.sort_file_path)
    void onSortFilePathClick() {
        settings.setSort(SORT_FILE_PATH);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_simple)
    void onViewSimpleClick() {
        settings.setAmiiboView(VIEW_TYPE_SIMPLE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_compact)
    void onViewCompactClick() {
        settings.setAmiiboView(VIEW_TYPE_COMPACT);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.view_large)
    void onViewLargeClick() {
        settings.setAmiiboView(VIEW_TYPE_LARGE);
        settings.notifyChanges();
    }

    @OptionsItem(R.id.recursive_files)
    void onRecursiveFilesClicked() {
        this.settings.setRecursiveFiles(!this.settings.isRecursiveFiles());
        this.settings.notifyChanges();
    }

    @OptionsItem(R.id.refresh)
    void onRefreshClicked() {
        this.refresh();
    }

    @OptionsItem(R.id.filter_game_series)
    boolean onFilterGameSeriesClick() {
        SubMenu subMenu = menuFilterGameSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return false;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            GameSeries gameSeries = amiibo.getGameSeries();
            if (gameSeries != null &&
                Amiibo.matchesCharacterFilter(amiibo.getCharacter(), settings.getCharacterFilter()) &&
                Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(), settings.getAmiiboSeriesFilter()) &&
                Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(), settings.getAmiiboTypeFilter())
                ) {
                items.add(gameSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                .setChecked(item.equals(settings.getGameSeriesFilter()))
                .setOnMenuItemClickListener(onFilterGameSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setGameSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @OptionsItem(R.id.filter_character)
    boolean onFilterCharacterClick() {
        SubMenu subMenu = menuFilterCharacter.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            Character character = amiibo.getCharacter();
            if (character != null &&
                Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(), settings.getGameSeriesFilter()) &&
                Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(), settings.getAmiiboSeriesFilter()) &&
                Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(), settings.getAmiiboTypeFilter())
                ) {
                items.add(character.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                .setChecked(item.equals(settings.getCharacterFilter()))
                .setOnMenuItemClickListener(onFilterCharacterItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterCharacterItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setCharacterFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @OptionsItem(R.id.filter_amiibo_series)
    boolean onFilterAmiiboSeriesClick() {
        SubMenu subMenu = menuFilterAmiiboSeries.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (amiiboSeries != null &&
                Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(), settings.getGameSeriesFilter()) &&
                Amiibo.matchesCharacterFilter(amiibo.getCharacter(), settings.getCharacterFilter()) &&
                Amiibo.matchesAmiiboTypeFilter(amiibo.getAmiiboType(), settings.getAmiiboTypeFilter())
                ) {
                items.add(amiiboSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                .setChecked(item.equals(settings.getAmiiboSeriesFilter()))
                .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboSeriesFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    @OptionsItem(R.id.filter_amiibo_type)
    boolean onFilterAmiiboTypeClick() {
        SubMenu subMenu = menuFilterAmiiboType.getSubMenu();
        subMenu.clear();

        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (amiiboManager == null)
            return true;

        Set<AmiiboType> items = new HashSet<>();
        for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
            if (amiibo == null)
                continue;

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (amiiboType != null &&
                Amiibo.matchesGameSeriesFilter(amiibo.getGameSeries(), settings.getGameSeriesFilter()) &&
                Amiibo.matchesCharacterFilter(amiibo.getCharacter(), settings.getCharacterFilter()) &&
                Amiibo.matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries(), settings.getAmiiboSeriesFilter())
                ) {
                items.add(amiiboType);
            }
        }

        ArrayList<AmiiboType> list = new ArrayList<>(items);
        Collections.sort(list);
        for (AmiiboType item : list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                .setChecked(item.name.equals(settings.getAmiiboTypeFilter()))
                .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            settings.setAmiiboTypeFilter(menuItem.getTitle().toString());
            settings.notifyChanges();
            return false;
        }
    };

    void refresh() {
        this.loadAmiiboManager();
        this.onRootFolderChanged();
    }

    @Override
    public void onRefresh() {
        this.refresh();
    }

    @Override
    public void onAmiiboClicked(AmiiboFile amiiboFile) {
        Intent returnIntent = new Intent();
        returnIntent.setData(Uri.fromFile(amiiboFile.getFilePath()));

        this.setResult(Activity.RESULT_OK, returnIntent);
        this.finish();
    }

    @Override
    public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
        Bundle bundle = new Bundle();
        bundle.putLong(ImageActivity.INTENT_EXTRA_AMIIBO_ID, amiiboFile.getId());

        Intent intent = new Intent(this, ImageActivity_.class);
        intent.putExtras(bundle);

        this.startActivity(intent);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        settings.setQuery(query);
        settings.notifyChanges();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        settings.setQuery(newText);
        settings.notifyChanges();
        return true;
    }

    @UiThread
    void setAmiiboFilesLoadingBarVisibility(boolean visible) {
        this.swipeRefreshLayout.setRefreshing(visible);
    }

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id=BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            amiiboManager = null;
            showToast("Unable to parse amiibo info");
        }
        if (Thread.currentThread().isInterrupted())
            return;

        final AmiiboManager amiiboManager1 = amiiboManager;
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                settings.setAmiiboManager(amiiboManager1);
                settings.notifyChanges();
            }
        });
    }

    void loadFolders(File rootFolder) {
        BackgroundExecutor.cancelAll(BACKGROUND_FOLDERS, true);
        loadFoldersTask(rootFolder);
    }

    @Background(id=BACKGROUND_FOLDERS)
    void loadFoldersTask(File rootFolder) {
        final ArrayList<File> folders = listFolders(rootFolder);
        Collections.sort(folders, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return file1.getPath().compareToIgnoreCase(file2.getPath());
            }
        });
        if (Thread.currentThread().isInterrupted())
            return;

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                settings.setFolders(folders);
                settings.notifyChanges();
            }
        });
    }

    ArrayList<File> listFolders(File rootFolder) {
        ArrayList<File> folders = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return folders;

        for (File file : files) {
            if (file.isDirectory()) {
                folders.add(file);
            }
        }
        return folders;
    }

    void loadAmiiboFiles(File rootFolder, boolean recursiveFiles) {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_FILES, true);
        loadAmiiboFilesTask(rootFolder, recursiveFiles);
    }

    @Background(id=BACKGROUND_AMIIBO_FILES)
    void loadAmiiboFilesTask(File rootFolder, boolean recursiveFiles) {
        this.setAmiiboFilesLoadingBarVisibility(true);
        final ArrayList<AmiiboFile> amiiboFiles = listAmiibos(rootFolder, recursiveFiles);
        if (Thread.currentThread().isInterrupted())
            return;

        this.setAmiiboFilesLoadingBarVisibility(false);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            }
        });
    }

    ArrayList<AmiiboFile> listAmiibos(File rootFolder, boolean recursiveFiles) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return amiiboFiles;

        for (File file : files) {
            if (file.isDirectory() && recursiveFiles) {
                amiiboFiles.addAll(listAmiibos(file, true));
            } else {
                try {
                    byte[] data = TagUtil.readTag(new FileInputStream(file));
                    TagUtil.validateTag(data);
                    amiiboFiles.add(new AmiiboFile(file, TagUtil.amiiboIdFromTag(data)));
                } catch (Exception e) {
                    //
                }
            }
        }
        return amiiboFiles;
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {
        boolean folderChanged = false;
        if (!Util.equals(newBrowserSettings.getBrowserRootFolder(), oldBrowserSettings.getBrowserRootFolder())) {
            folderChanged = true;
        }
        if (newBrowserSettings.isRecursiveFiles() != oldBrowserSettings.isRecursiveFiles()) {
            folderChanged = true;
            onRecursiveFilesChanged();
        }
        if (folderChanged) {
            onRootFolderChanged();
        }

        if (newBrowserSettings.getSort() != oldBrowserSettings.getSort()) {
            onSortChanged();
        }
        if (!Util.equals(newBrowserSettings.getGameSeriesFilter(), oldBrowserSettings.getGameSeriesFilter())) {
            onFilterGameSeriesChanged();
        }
        if (!Util.equals(newBrowserSettings.getCharacterFilter(), oldBrowserSettings.getCharacterFilter())) {
            onFilterCharacterChanged();
        }
        if (!Util.equals(newBrowserSettings.getAmiiboSeriesFilter(), oldBrowserSettings.getAmiiboSeriesFilter())) {
            onFilterAmiiboSeriesChanged();
        }
        if (!Util.equals(newBrowserSettings.getAmiiboTypeFilter(), oldBrowserSettings.getAmiiboTypeFilter())) {
            onFilterAmiiboTypeChanged();
        }
        if (newBrowserSettings.getAmiiboView() != oldBrowserSettings.getAmiiboView()) {
            onViewChanged();
        }
        if (!Util.equals(newBrowserSettings.getAmiiboFiles(), oldBrowserSettings.getAmiiboFiles())) {
            onAmiiboFilesChanged();
        }

        this.prefs.edit()
            .browserRootFolder().put(Util.friendlyPath(newBrowserSettings.getBrowserRootFolder()))
            .query().put(newBrowserSettings.getQuery())
            .sort().put(newBrowserSettings.getSort())
            .filterGameSeries().put(newBrowserSettings.getGameSeriesFilter())
            .filterCharacter().put(newBrowserSettings.getCharacterFilter())
            .filterAmiiboSeries().put(newBrowserSettings.getAmiiboSeriesFilter())
            .filterAmiiboType().put(newBrowserSettings.getAmiiboTypeFilter())
            .browserAmiiboView().put(newBrowserSettings.getAmiiboView())
            .imageNetworkSetting().put(newBrowserSettings.getImageNetworkSettings())
            .recursiveFiles().put(newBrowserSettings.isRecursiveFiles())
            .apply();
    }

    private void onAmiiboFilesChanged() {
        if (settings.getAmiiboFiles() == null || settings.getAmiiboFiles().size() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    void onSortChanged() {
        if (menuSortId == null)
            return;

        int sort = settings.getSort();
        if (sort == SORT_ID) {
            menuSortId.setChecked(true);
        } else if (sort == SORT_NAME) {
            menuSortName.setChecked(true);
        } else if (sort == SORT_GAME_SERIES) {
            menuSortGameSeries.setChecked(true);
        } else if (sort == SORT_CHARACTER) {
            menuSortCharacter.setChecked(true);
        } else if (sort == SORT_AMIIBO_SERIES) {
            menuSortAmiiboSeries.setChecked(true);
        } else if (sort == SORT_AMIIBO_TYPE) {
            menuSortAmiiboType.setChecked(true);
        } else if (sort == SORT_FILE_PATH) {
            menuSortFilePath.setChecked(true);
        }
    }

    void onViewChanged() {
        if (menuViewSimple == null)
            return;

        int view = settings.getAmiiboView();
        if (view == VIEW_TYPE_SIMPLE) {
            menuViewSimple.setChecked(true);
        } else if (view == VIEW_TYPE_COMPACT) {
            menuViewCompact.setChecked(true);
        } else if (view == VIEW_TYPE_LARGE) {
            menuViewLarge.setChecked(true);
        }
    }

    void onRootFolderChanged() {
        File rootFolder = settings.getBrowserRootFolder();
        this.currentFolderView.setText(Util.friendlyPath(rootFolder));
        this.loadAmiiboFiles(rootFolder, settings.isRecursiveFiles());
        this.loadFolders(rootFolder);
    }

    void onFilterGameSeriesChanged() {
        addFilterItemView(settings.getGameSeriesFilter(), "filter_game_series", onFilterGameSeriesChipCloseClick);
    }

    OnCloseClickListener onFilterGameSeriesChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            settings.setGameSeriesFilter("");
            settings.notifyChanges();
        }
    };

    void onFilterCharacterChanged() {
        addFilterItemView(settings.getCharacterFilter(), "filter_character", onFilterCharacterChipCloseClick);
    }

    OnCloseClickListener onFilterCharacterChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            settings.setCharacterFilter("");
            settings.notifyChanges();
        }
    };

    void onFilterAmiiboSeriesChanged() {
        addFilterItemView(settings.getAmiiboSeriesFilter(), "filter_amiibo_series", onFilterAmiiboSeriesChipCloseClick);
    }

    OnCloseClickListener onFilterAmiiboSeriesChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            settings.setAmiiboSeriesFilter("");
            settings.notifyChanges();
        }
    };

    void onFilterAmiiboTypeChanged() {
        addFilterItemView(settings.getAmiiboTypeFilter(), "filter_amiibo_type", onAmiiboTypeChipCloseClick);
    }

    void onRecursiveFilesChanged() {
        if (menuRecursiveFiles == null)
            return;

        menuRecursiveFiles.setChecked(settings.isRecursiveFiles());
    }

    OnCloseClickListener onAmiiboTypeChipCloseClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            settings.setAmiiboTypeFilter("");
            settings.notifyChanges();
        }
    };

    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        FrameLayout chipContainer = chipList.findViewWithTag(tag);
        chipList.removeView(chipContainer);
        if (!text.isEmpty()) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = chipContainer.findViewById(R.id.chip);
            chip.setChipText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

}
