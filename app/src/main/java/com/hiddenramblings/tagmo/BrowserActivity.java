package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ListView;
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
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.annotations.sharedpreferences.Pref;
import org.apmem.tools.layouts.FlowLayout;
import org.json.JSONException;
import org.parceler.Parcel;
import org.parceler.ParcelConstructor;
import org.parceler.Parcels;

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
@OptionsMenu({R.menu.search_manu})
public class BrowserActivity extends AppCompatActivity implements SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    public static int SORT_ID = 0x0;
    public static int SORT_NAME = 0x1;
    public static int SORT_AMIIBO_SERIES = 0x2;
    public static int SORT_AMIIBO_TYPE = 0x3;
    public static int SORT_GAME_SERIES = 0x4;
    public static int SORT_CHARACTER = 0x5;
    public static int SORT_FILE_PATH = 0x6;

    @ViewById(R.id.chip_list)
    FlowLayout chipList;
    @ViewById(R.id.list)
    ListView listView;
    @ViewById(R.id.swiperefresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @ViewById(R.id.internalEmpty)
    TextView emptyText;


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
    @OptionsMenuItem(R.id.refresh)
    MenuItem menuRefresh;

    SearchView searchView;

    ArrayList<AmiiboFile> amiiboFiles = null;

    AmiiboManager amiiboManager = null;

    @Pref
    Preferences_ prefs;

    @Parcel
    public static class AmiiboFile {
        String filePath;
        long id;

        public AmiiboFile() {}

        @ParcelConstructor
        public AmiiboFile(String filePath, long id) {
            this.filePath = filePath;
            this.id = id;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            amiiboFiles = Parcels.unwrap(savedInstanceState.getParcelable("amiiboFiles"));
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("amiiboFiles", Parcels.wrap(amiiboFiles));
    }

    @AfterViews
    protected void afterViews() {
        setGameSeriesFilter(getGameSeriesFilter());
        setCharacterFilter(getCharacterFilter());
        setAmiiboSeriesFilter(getAmiiboSeriesFilter());
        setAmiiboTypeFilter(getAmiiboTypeFilter());

        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.listView.setAdapter(new AmiiboFilesAdapter(this));
        if (this.amiiboFiles == null) {
            this.onRefresh();
        } else {
            this.loadAmiiboManager();
            this.setAmiiboFiles(this.amiiboFiles);
        }
    }

    @Override
    public void onRefresh() {
        this.loadAmiiboManager();
        this.runListAmiibos();
    }

    protected AmiiboFilesAdapter getListAdapter() {
        return (AmiiboFilesAdapter) this.listView.getAdapter();
    }

    @ItemClick(R.id.list)
    public void onListItemClicked(AmiiboFile item) {
        Intent returnIntent = new Intent();
        returnIntent.setData(Uri.fromFile(new File(item.filePath)));
        this.setResult(Activity.RESULT_OK, returnIntent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        setSort(getSort());

        // setOnQueryTextListener will clear this, so make a copy
        String query = getQuery();

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) MenuItemCompat.getActionView(menuSearch);
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

    @Background
    void loadAmiiboManager() {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = Util.loadAmiiboManager(this);
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
            showToast("Unable to parse amiibo database");
        }
        this.setAmiiboManager(amiiboManager);
    }

    @UiThread
    void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        this.getListAdapter().refresh();
    }

    @Background
    void runListAmiibos() {
        setLoadingBarVisibility(true);
        ArrayList<AmiiboFile> amiiboFiles = listAmiibos(Util.getDataDir());
        setLoadingBarVisibility(false);
        setAmiiboFiles(amiiboFiles);
    }

    ArrayList<AmiiboFile> listAmiibos(File rootFolder) {
        ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();

        File[] files = rootFolder.listFiles();
        if (files == null)
            return amiiboFiles;

        for (File file : files) {
            if (file.isDirectory()) {
                amiiboFiles.addAll(listAmiibos(file));
            } else {
                try {
                    byte[] data = TagUtil.readTag(new FileInputStream(file));
                    TagUtil.validateTag(data);
                    amiiboFiles.add(new AmiiboFile(file.getAbsolutePath(), TagUtil.amiiboIdFromTag(data)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return amiiboFiles;
    }

    @UiThread
    void setAmiiboFiles(ArrayList<AmiiboFile> amiiboFiles) {
        this.amiiboFiles = amiiboFiles;
        this.getListAdapter().setData(amiiboFiles);
        if (amiiboFiles != null && amiiboFiles.size() == 0) {
            String dirPath = Util.friendlyPath(Util.getDataDir().getAbsolutePath());
            emptyText.setText("No Amiibo files found in\n" +  dirPath);
        } else {
            emptyText.setText("");
        }
    }

    @UiThread
    void setLoadingBarVisibility(boolean visible) {
        this.swipeRefreshLayout.setRefreshing(visible);
    }

    public String getQuery() {
        return this.prefs.query().get();
    }

    public void setQuery(String query) {
        this.prefs.query().put(query);
    }

    public int getSort() {
        return this.prefs.sort().get();
    }

    public void setSort(int sort) {
        this.prefs.sort().put(sort);
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

    public String getGameSeriesFilter() {
        return this.prefs.filterGameSeries().get();
    }

    public void setGameSeriesFilter(String gameSeriesFilter) {
        this.prefs.filterGameSeries().put(gameSeriesFilter);

        addFilterItemView(gameSeriesFilter, "game_series", onFilterGameSeriesChipClick);
    }

    public String getCharacterFilter() {
        return this.prefs.filterCharacter().get();
    }

    public void setCharacterFilter(String characterFilter) {
        this.prefs.filterCharacter().put(characterFilter);

        addFilterItemView(characterFilter, "character", onFilterCharacterChipClick);
    }

    public String getAmiiboSeriesFilter() {
        return this.prefs.filterAmiiboSeries().get();
    }

    public void setAmiiboSeriesFilter(String amiiboSeriesFilter) {
        this.prefs.filterAmiiboSeries().put(amiiboSeriesFilter);

        addFilterItemView(amiiboSeriesFilter, "amiibo_series", onFilterAmiiboSeriesChipClick);
    }

    public String getAmiiboTypeFilter() {
        return this.prefs.filterAmiiboType().get();
    }

    public void setAmiiboTypeFilter(String amiiboTypeFilter) {
        this.prefs.filterAmiiboType().put(amiiboTypeFilter);

        addFilterItemView(amiiboTypeFilter, "amiibo_type", onAmiiboTypeChipClick);
    }

    public boolean matchesGameSeriesFilter(GameSeries gameSeries) {
        if (gameSeries != null) {
            String filterGameSeries = getGameSeriesFilter();
            if (!filterGameSeries.isEmpty() && !gameSeries.name.equals(filterGameSeries))
                return false;
        }
        return true;
    }

    public boolean matchesCharacterFilter(Character character) {
        if (character != null) {
            String filterCharacter = getCharacterFilter();
            if (!filterCharacter.isEmpty() && !character.name.equals(filterCharacter))
                return false;
        }
        return true;
    }

    public boolean matchesAmiiboSeriesFilter(AmiiboSeries amiiboSeries) {
        if (amiiboSeries != null) {
            String filterAmiiboSeries = getAmiiboSeriesFilter();
            if (!filterAmiiboSeries.isEmpty() && !amiiboSeries.name.equals(filterAmiiboSeries))
                return false;
        }
        return true;
    }

    public boolean matchesAmiiboTypeFilter(AmiiboType amiiboType) {
        if (amiiboType != null) {
            String filterAmiiboType = getAmiiboTypeFilter();
            if (!filterAmiiboType.isEmpty() && !amiiboType.name.equals(filterAmiiboType))
                return false;
        }
        return true;
    }

    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        FrameLayout chipContainer = (FrameLayout) chipList.findViewWithTag(tag);
        chipList.removeView(chipContainer);
        if (!text.isEmpty()) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = (Chip) chipContainer.findViewById(R.id.chip);
            chip.setChipText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    @OptionsItem(R.id.sort_id)
    void onSortIdClick() {
        setSort(SORT_ID);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.sort_name)
    void onSortNameClick() {
        setSort(SORT_NAME);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.sort_game_series)
    void onSortGameSeriesClick() {
        setSort(SORT_GAME_SERIES);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.sort_character)
    void onSortCharacterClick() {
        setSort(SORT_CHARACTER);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.sort_amiibo_series)
    void onSortAmiiboSeriesClick() {
        setSort(SORT_AMIIBO_SERIES);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.sort_amiibo_type)
    void onSortAmiiboTypeClick() {
        setSort(SORT_AMIIBO_TYPE);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.sort_file_path)
    void onSortFilePathClick() {
        setSort(SORT_FILE_PATH);
        this.getListAdapter().refresh();
    }

    @OptionsItem(R.id.refresh)
    void onRefreshClicked() {
        this.setAmiiboFiles(null);
        this.onRefresh();
    }

    @OptionsItem(R.id.filter_game_series)
    boolean onFilterGameSeriesClick() {
        SubMenu subMenu = menuFilterGameSeries.getSubMenu();
        subMenu.clear();

        AmiiboFilesAdapter adapter = getListAdapter();
        if (amiiboManager == null)
            return false;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            GameSeries gameSeries = amiibo.getGameSeries();
            if (gameSeries != null &&
                matchesCharacterFilter(amiibo.getCharacter()) &&
                matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries()) &&
                matchesAmiiboTypeFilter(amiibo.getAmiiboType())
                ) {
                items.add(gameSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_game_series_group, Menu.NONE, 0, item)
                .setChecked(item.equals(getGameSeriesFilter()))
                .setOnMenuItemClickListener(onFilterGameSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_game_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterGameSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setGameSeriesFilter(menuItem.getTitle().toString());
            getListAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onFilterGameSeriesChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setGameSeriesFilter("");
            getListAdapter().refresh();
        }
    };

    @OptionsItem(R.id.filter_character)
    boolean onFilterCharacterClick() {
        SubMenu subMenu = menuFilterCharacter.getSubMenu();
        subMenu.clear();

        AmiiboFilesAdapter adapter = getListAdapter();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            Character character = amiibo.getCharacter();
            if (character != null &&
                matchesGameSeriesFilter(amiibo.getGameSeries()) &&
                matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries()) &&
                matchesAmiiboTypeFilter(amiibo.getAmiiboType())
                ) {
                items.add(character.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_character_group, Menu.NONE, 0, item)
                .setChecked(item.equals(getCharacterFilter()))
                .setOnMenuItemClickListener(onFilterCharacterItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_character_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterCharacterItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setCharacterFilter(menuItem.getTitle().toString());
            getListAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onFilterCharacterChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setCharacterFilter("");

            getListAdapter().refresh();
        }
    };

    @OptionsItem(R.id.filter_amiibo_series)
    boolean onFilterAmiiboSeriesClick() {
        SubMenu subMenu = menuFilterAmiiboSeries.getSubMenu();
        subMenu.clear();

        AmiiboFilesAdapter adapter = getListAdapter();
        if (amiiboManager == null)
            return true;

        Set<String> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (amiiboSeries != null &&
                matchesGameSeriesFilter(amiibo.getGameSeries()) &&
                matchesCharacterFilter(amiibo.getCharacter()) &&
                matchesAmiiboTypeFilter(amiibo.getAmiiboType())
                ) {
                items.add(amiiboSeries.name);
            }
        }

        ArrayList<String> list = new ArrayList<>(items);
        Collections.sort(list);
        for (String item : list) {
            subMenu.add(R.id.filter_amiibo_series_group, Menu.NONE, 0, item)
                .setChecked(item.equals(getAmiiboSeriesFilter()))
                .setOnMenuItemClickListener(onFilterAmiiboSeriesItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_series_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboSeriesItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setAmiiboSeriesFilter(menuItem.getTitle().toString());
            getListAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onFilterAmiiboSeriesChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setAmiiboSeriesFilter("");
            getListAdapter().refresh();
        }
    };

    @OptionsItem(R.id.filter_amiibo_type)
    boolean onFilterAmiiboTypeClick() {
        SubMenu subMenu = menuFilterAmiiboType.getSubMenu();
        subMenu.clear();

        AmiiboFilesAdapter adapter = getListAdapter();
        if (amiiboManager == null)
            return true;

        Set<AmiiboType> items = new HashSet<>();
        for (AmiiboFile amiiboFile : adapter.data) {
            Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
            if (amiibo == null)
                continue;

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (amiiboType != null &&
                matchesGameSeriesFilter(amiibo.getGameSeries()) &&
                matchesCharacterFilter(amiibo.getCharacter()) &&
                matchesAmiiboSeriesFilter(amiibo.getAmiiboSeries())
                ) {
                items.add(amiiboType);
            }
        }

        ArrayList<AmiiboType> list = new ArrayList<>(items);
        Collections.sort(list);
        for (AmiiboType item : list) {
            subMenu.add(R.id.filter_amiibo_type_group, Menu.NONE, 0, item.name)
                .setChecked(item.name.equals(getAmiiboTypeFilter()))
                .setOnMenuItemClickListener(onFilterAmiiboTypeItemClick);
        }
        subMenu.setGroupCheckable(R.id.filter_amiibo_type_group, true, true);

        return true;
    }

    MenuItem.OnMenuItemClickListener onFilterAmiiboTypeItemClick = new MenuItem.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            setAmiiboTypeFilter(menuItem.getTitle().toString());
            getListAdapter().refresh();
            return false;
        }
    };

    OnCloseClickListener onAmiiboTypeChipClick = new OnCloseClickListener() {
        @Override
        public void onCloseClick(View v) {
            setAmiiboTypeFilter("");
            getListAdapter().refresh();
        }
    };

    @Override
    public boolean onQueryTextChange(String query) {
        this.getListAdapter().getFilter().filter(query);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        this.getListAdapter().getFilter().filter(query);
        searchView.clearFocus();
        return true;
    }

    static class AmiiboFilesAdapter extends BaseAdapter implements Filterable {
        private final BrowserActivity activity;
        private ArrayList<AmiiboFile> data;
        private ArrayList<AmiiboFile> filteredData;
        private AmiiboFilter filter;

        AmiiboFilesAdapter(BrowserActivity activity) {
            this.activity = activity;
            this.data = new ArrayList<>();
            this.filteredData = this.data;
        }

        void setData(ArrayList<AmiiboFile> data) {
            this.data.clear();
            if (data != null)
                this.data.addAll(data);
            this.refresh();
        }

        @Override
        public int getCount() {
            return filteredData.size();
        }

        @Override
        public AmiiboFile getItem(int i) {
            return filteredData.get(i);
        }

        @Override
        public long getItemId(int i) {
            return filteredData.get(i).id;
        }

        class CustomComparator implements Comparator<AmiiboFile> {
            @Override
            public int compare(AmiiboFile amiiboFile1, AmiiboFile amiiboFile2) {
                String filePath1 = amiiboFile1.filePath;
                String filePath2 = amiiboFile2.filePath;

                int sort = activity.getSort();
                if (sort == SORT_FILE_PATH)
                    return filePath1.compareTo(filePath2);

                int value = 0;
                long amiiboId1 = amiiboFile1.id;
                long amiiboId2 = amiiboFile2.id;
                if (sort == SORT_ID) {
                    value = compareAmiiboId(amiiboId1, amiiboId2);
                } else {
                    AmiiboManager amiiboManager = activity.amiiboManager;
                    if (amiiboManager != null) {
                        Amiibo amiibo1 = amiiboManager.amiibos.get(amiiboId1);
                        Amiibo amiibo2 = amiiboManager.amiibos.get(amiiboId2);
                        if (amiibo1 == null)
                            value = 1;
                        else if (amiibo2 == null)
                            value = -1;
                        else if (sort == SORT_NAME) {
                            value = compareAmiiboName(amiibo1, amiibo2);
                        } else if (sort == SORT_AMIIBO_SERIES) {
                            value = compareAmiiboSeries(amiibo1, amiibo2);
                        } else if (sort == SORT_AMIIBO_TYPE) {
                            value = compareAmiiboType(amiibo1, amiibo2);
                        } else if (sort == SORT_GAME_SERIES) {
                            value = compareGameSeries(amiibo1, amiibo2);
                        } else if (sort == SORT_CHARACTER) {
                            value = compareCharacter(amiibo1, amiibo2);
                        }
                        if (value == 0)
                            value = amiibo1.compareTo(amiibo2);
                    }
                    if (value == 0)
                        value = compareAmiiboId(amiiboId1, amiiboId2);
                }

                if (value == 0)
                    value = filePath1.compareTo(filePath2);

                return value;
            }

            int compareAmiiboId(long amiiboId1, long amiiboId2) {
                if (amiiboId1 == amiiboId2)
                    return 0;
                return amiiboId1 < amiiboId2 ? -1 : 1;
            }

            int compareAmiiboName(Amiibo amiibo1, Amiibo amiibo2) {
                String name1 = amiibo1.name;
                String name2 = amiibo2.name;
                if (name1 == null) {
                    return 1;
                } else if (name2 == null) {
                    return -1;
                }
                return name1.compareTo(name2);
            }

            int compareAmiiboSeries(Amiibo amiibo1, Amiibo amiibo2) {
                AmiiboSeries amiiboSeries1 = amiibo1.getAmiiboSeries();
                AmiiboSeries amiiboSeries2 = amiibo2.getAmiiboSeries();
                if (amiiboSeries1 == null) {
                    return 1;
                } else if (amiiboSeries2 == null) {
                    return -1;
                }
                return amiiboSeries1.compareTo(amiiboSeries2);
            }

            int compareAmiiboType(Amiibo amiibo1, Amiibo amiibo2) {
                AmiiboType amiiboType1 = amiibo1.getAmiiboType();
                AmiiboType amiiboType2 = amiibo2.getAmiiboType();
                if (amiiboType1 == null) {
                    return 1;
                } else if (amiiboType2 == null) {
                    return -1;
                }
                return amiiboType1.compareTo(amiiboType2);
            }

            int compareGameSeries(Amiibo amiibo1, Amiibo amiibo2) {
                GameSeries gameSeries1 = amiibo1.getGameSeries();
                GameSeries gameSeries2 = amiibo2.getGameSeries();
                if (gameSeries1 == null) {
                    return 1;
                } else if (gameSeries2 == null) {
                    return -1;
                }
                return gameSeries1.compareTo(gameSeries2);
            }

            int compareCharacter(Amiibo amiibo1, Amiibo amiibo2) {
                Character character1 = amiibo1.getCharacter();
                Character character2 = amiibo2.getCharacter();
                if (character1 == null) {
                    return 1;
                } else if (character2 == null) {
                    return -1;
                }
                return character1.compareTo(character2);
            }
        }

        SpannableStringBuilder boldMatchingText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (query.isEmpty())
                return str;

            text = text.toLowerCase();
            int j = 0;
            while (j < text.length()) {
                int i = text.indexOf(query, j);
                if (i == -1)
                    break;

                j = i + query.length();
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        SpannableStringBuilder boldStartText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (!query.isEmpty() && text.toLowerCase().startsWith(query)) {
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.amiibo_item_view, parent, false);
                holder = new ViewHolder(convertView);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AmiiboFile item = getItem(position);
            String tagInfo = "";
            String amiiboHexId = "";
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            String character = "";

            long amiiboId = item.id;
            Amiibo amiibo = null;
            AmiiboManager amiiboManager = activity.amiiboManager;
            if (amiiboManager != null) {
                amiibo = amiiboManager.amiibos.get(amiiboId);
                if (amiibo == null)
                    amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
            }
            if (amiibo != null) {
                amiiboHexId = TagUtil.amiiboIdToHex(amiibo.id);
                if (amiibo.name != null)
                    amiiboName = amiibo.name;
                if (amiibo.getAmiiboSeries() != null)
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (amiibo.getAmiiboType() != null)
                    amiiboType = amiibo.getAmiiboType().name;
                if (amiibo.getGameSeries() != null)
                    gameSeries = amiibo.getGameSeries().name;
                if (amiibo.getCharacter() != null)
                    character = amiibo.getCharacter().name;
            } else {
                tagInfo = "<Unknown amiibo id: " + TagUtil.amiiboIdToHex(amiiboId) + ">";
            }

            String query = activity.getQuery().toLowerCase();
            holder.txtTagInfo.setText(tagInfo);
            setAmiiboInfoText(holder.txtName, boldMatchingText(amiiboName, query), !tagInfo.isEmpty());
            setAmiiboInfoText(holder.txtTagId, boldStartText(amiiboHexId, query), !tagInfo.isEmpty());
            setAmiiboInfoText(holder.txtAmiiboSeries, boldMatchingText(amiiboSeries, query), !tagInfo.isEmpty());
            setAmiiboInfoText(holder.txtAmiiboType, boldMatchingText(amiiboType, query), !tagInfo.isEmpty());
            setAmiiboInfoText(holder.txtGameSeries, boldMatchingText(gameSeries, query), !tagInfo.isEmpty());
            setAmiiboInfoText(holder.txtCharacter, boldMatchingText(character, query), !tagInfo.isEmpty());

            holder.txtPath.setText(boldMatchingText(Util.friendlyPath(item.filePath), query));
            holder.txtPath.setVisibility(View.VISIBLE);

            return convertView;
        }

        void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
            if (hasTagInfo) {
                textView.setText("");
            } else if (text.length() == 0) {
                textView.setText("Unknown");
                textView.setTextColor(Color.RED);
            } else {
                textView.setText(text);
                textView.setTextColor(textView.getTextColors().getDefaultColor());
            }
        }

        static class ViewHolder {
            TextView txtTagInfo;
            TextView txtName;
            TextView txtTagId;
            TextView txtAmiiboSeries;
            TextView txtAmiiboType;
            TextView txtGameSeries;
            TextView txtCharacter;
            TextView txtPath;

            public ViewHolder(View view) {
                this.txtTagInfo = ((TextView) view.findViewById(R.id.txtTagInfo));
                this.txtName = ((TextView) view.findViewById(R.id.txtName));
                this.txtTagId = ((TextView) view.findViewById(R.id.txtTagId));
                this.txtAmiiboSeries = ((TextView) view.findViewById(R.id.txtAmiiboSeries));
                this.txtAmiiboType = ((TextView) view.findViewById(R.id.txtAmiiboType));
                this.txtGameSeries = ((TextView) view.findViewById(R.id.txtGameSeries));
                this.txtCharacter = ((TextView) view.findViewById(R.id.txtCharacter));
                this.txtPath = ((TextView) view.findViewById(R.id.txtPath));

                view.setTag(this);
            }
        }

        public void refresh() {
            this.getFilter().filter(activity.getQuery());
        }

        @Override
        public AmiiboFilter getFilter() {
            if (this.filter == null) {
                this.filter = new AmiiboFilter();
            }

            return this.filter;
        }

        public class AmiiboFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint != null ? constraint.toString() : "";
                activity.setQuery(query);

                FilterResults filterResults = new FilterResults();
                ArrayList<AmiiboFile> tempList = new ArrayList<>();
                String queryText = query.trim().toLowerCase();
                for (AmiiboFile amiiboFile : data) {
                    boolean add;

                    AmiiboManager amiiboManager = activity.amiiboManager;
                    if (amiiboManager != null) {
                        Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.id);
                        if (amiibo == null)
                            amiibo = new Amiibo(amiiboManager, amiiboFile.id, null, null);
                        add = amiiboContainsQuery(amiibo, queryText);
                    } else {
                        add = queryText.isEmpty();
                    }
                    if (!add)
                        add = pathContainsQuery(amiiboFile.filePath, queryText);
                    if (add)
                        tempList.add(amiiboFile);
                }
                filterResults.count = tempList.size();
                filterResults.values = tempList;

                return filterResults;
            }

            public boolean pathContainsQuery(String path, String query) {
                return !query.isEmpty() &&
                    activity.getGameSeriesFilter().isEmpty() &&
                    activity.getCharacterFilter().isEmpty() &&
                    activity.getAmiiboSeriesFilter().isEmpty() &&
                    activity.getAmiiboTypeFilter().isEmpty() &&
                    path.toLowerCase().contains(query);
            }

            public boolean amiiboContainsQuery(Amiibo amiibo, String query) {
                GameSeries gameSeries = amiibo.getGameSeries();
                if (!activity.matchesGameSeriesFilter(gameSeries))
                    return false;

                Character character = amiibo.getCharacter();
                if (!activity.matchesCharacterFilter(character))
                    return false;

                AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
                if (!activity.matchesAmiiboSeriesFilter(amiiboSeries))
                    return false;

                AmiiboType amiiboType = amiibo.getAmiiboType();
                if (!activity.matchesAmiiboTypeFilter(amiiboType))
                    return false;

                if (!query.isEmpty()) {
                    if (TagUtil.amiiboIdToHex(amiibo.id).toLowerCase().startsWith(query))
                        return true;
                    else if (amiibo.name != null && amiibo.name.toLowerCase().contains(query))
                        return true;
                    else if (gameSeries != null && gameSeries.name.toLowerCase().contains(query))
                        return true;
                    else if (character != null && character.name.toLowerCase().contains(query))
                        return true;
                    else if (amiiboSeries != null && amiiboSeries.name.toLowerCase().contains(query))
                        return true;
                    else if (amiiboType != null && amiiboType.name.toLowerCase().contains(query))
                        return true;

                    return false;
                }
                return true;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                Collections.sort((ArrayList<AmiiboFile>) filterResults.values, new CustomComparator());
                filteredData = (ArrayList<AmiiboFile>) filterResults.values;
                notifyDataSetChanged();
            }
        }
    }

    @UiThread
    public void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
