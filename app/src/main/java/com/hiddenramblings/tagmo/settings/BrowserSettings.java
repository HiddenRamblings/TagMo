package com.hiddenramblings.tagmo.settings;

import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.amiibo.games.GamesManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

public class BrowserSettings implements Parcelable {

    public enum SORT {
        ID(0x0),
        NAME(0x1),
        AMIIBO_SERIES(0x2),
        AMIIBO_TYPE(0x3),
        GAME_SERIES(0x4),
        CHARACTER(0x5),
        FILE_PATH(0x6);

        private final int value;
        SORT(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static SORT valueOf(int value) {
            if (Debug.isNewer(Build.VERSION_CODES.N)) {
                Optional<SORT> optional = Arrays.stream(values()).filter(
                        SORT -> SORT.value == value).findFirst();
                if (optional.isPresent()) return optional.get();
            } else {
                for (SORT view : SORT.values()) {
                    if (view.getValue() == value) return view;
                }
            }
            return SORT.NAME;
        }
    }

    public enum FILTER {
        CHARACTER,
        GAME_SERIES,
        AMIIBO_SERIES,
        AMIIBO_TYPE,
        GAME_TITLES
    }

    public enum VIEW {
        SIMPLE(0),
        COMPACT(1),
        LARGE(2),
        IMAGE(3);

        private final int value;
        VIEW(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static VIEW valueOf(int value) {
            if (Debug.isNewer(Build.VERSION_CODES.N)) {
                Optional<VIEW> optional = Arrays.stream(values()).filter(
                        VIEW -> VIEW.value == value).findFirst();
                if (optional.isPresent()) return optional.get();
            } else {
                for (VIEW view : VIEW.values()) {
                    if (view.getValue() == value) return view;
                }
            }
            return VIEW.COMPACT;
        }
    }

    protected ArrayList<BrowserSettingsListener> listeners = new ArrayList<>();
    protected AmiiboManager amiiboManager;
    protected GamesManager gamesManager;
    protected BrowserSettings oldBrowserSettings;

    protected ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();
    protected ArrayList<File> folders = new ArrayList<>();
    protected File browserFolder;
    protected Uri browserDocument;
    protected String query;
    protected int sort;
    protected String filterCharacter;
    protected String filterGameSeries;
    protected String filterAmiiboSeries;
    protected String filterAmiiboType;
    protected String filterGameTitles;
    protected int browserAmiiboView;
    protected String imageNetworkSettings;
    protected boolean recursiveFolders;
    protected String lastUpdatedAPI;
    protected long lastUpdatedGit;

    public BrowserSettings() {
        oldBrowserSettings = new BrowserSettings(false);
    }

    @SuppressWarnings("unused")
    public BrowserSettings(
            ArrayList<AmiiboFile> amiiboFiles, ArrayList<File> folders,
            File browserFolder, String query, int sort, String filterCharacter,
            String filterGameSeries, String filterAmiiboSeries, String filterAmiiboType,
            String filterGameTitles, int browserAmiiboView, String imageNetworkSettings,
            boolean recursiveFolders, String lastUpdatedAPI, long lastUpdatedGit
    ) {
        super();

        this.amiiboFiles.addAll(amiiboFiles);
        this.folders.addAll(folders);
        this.browserFolder = browserFolder;
        this.query = query;
        this.sort = sort;
        this.filterCharacter = filterCharacter;
        this.filterGameSeries = filterGameSeries;
        this.filterAmiiboSeries = filterAmiiboSeries;
        this.filterAmiiboType = filterAmiiboType;
        this.filterGameTitles = filterGameTitles;
        this.browserAmiiboView = browserAmiiboView;
        this.imageNetworkSettings = imageNetworkSettings;
        this.recursiveFolders = recursiveFolders;
        this.lastUpdatedAPI = lastUpdatedAPI;
        this.lastUpdatedGit = lastUpdatedGit;
    }

    private BrowserSettings(boolean duplicate) {
        if (duplicate) {
            this.oldBrowserSettings = this.copy();
        }
    }

    public BrowserSettings initialize() {
        final Preferences_ prefs = TagMo.getPrefs();
        this.setBrowserRootFolder(null != prefs.browserRootFolder()
                ? new File(Storage.getFile(prefs.preferEmulated().get()),
                prefs.browserRootFolder().get())
                : Storage.getDownloadDir(null));
        this.setBrowserRootDocument(null != prefs.browserRootDocument()
                ? Uri.parse(prefs.browserRootDocument().get()) : null);
        this.setQuery(prefs.query().get());
        this.setSort(prefs.sort().get());
        this.setFilter(FILTER.CHARACTER, prefs.filterCharacter().get());
        this.setFilter(FILTER.GAME_SERIES, prefs.filterGameSeries().get());
        this.setFilter(FILTER.AMIIBO_SERIES, prefs.filterAmiiboSeries().get());
        this.setFilter(FILTER.AMIIBO_TYPE, prefs.filterAmiiboType().get());
        this.setFilter(FILTER.GAME_TITLES, prefs.filterGameTitles().get());
        this.setAmiiboView(prefs.browserAmiiboView().get());
        this.setImageNetworkSettings(prefs.image_network_settings().get());
        this.setRecursiveEnabled(prefs.recursiveFolders().get());
        this.setLastUpdatedAPI(prefs.lastUpdatedAPI().get());
        this.setLastUpdatedGit(prefs.lastUpdatedGit().get());
        return this;
    }

    public AmiiboManager getAmiiboManager() {
        return this.amiiboManager;
    }

    public void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
    }

    public GamesManager getGamesManager() {
        return this.gamesManager;
    }

    public void setGamesManager(GamesManager gamesManager) {
        this.gamesManager = gamesManager;
    }

    public ArrayList<AmiiboFile> getAmiiboFiles() {
        return amiiboFiles;
    }

    public void setAmiiboFiles(ArrayList<AmiiboFile> amiiboFiles) {
        this.amiiboFiles = new ArrayList<>(amiiboFiles);
    }

    public ArrayList<File> getFolders() {
        return folders;
    }

    public void setFolders(ArrayList<File> folders) {
        this.folders = new ArrayList<>(folders);
    }

    public String getQuery() {
        return this.query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getSort() {
        return this.sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public String getFilter(FILTER filter) {
        String filterText = "";
        switch (filter) {
            case CHARACTER:
                filterText = this.filterCharacter;
                break;
            case GAME_SERIES:
                filterText = this.filterGameSeries;
                break;
            case AMIIBO_SERIES:
                filterText = this.filterAmiiboSeries;
                break;
            case AMIIBO_TYPE:
                filterText = this.filterAmiiboType;
                break;
            case GAME_TITLES:
                filterText = this.filterGameTitles;
                break;
        }
        return filterText;
    }

    public void setFilter(FILTER filter, String filterText) {
        switch (filter) {
            case CHARACTER:
                this.filterCharacter = filterText;
                break;
            case GAME_SERIES:
                this.filterGameSeries = filterText;
                break;
            case AMIIBO_SERIES:
                this.filterAmiiboSeries = filterText;
                break;
            case AMIIBO_TYPE:
                this.filterAmiiboType = filterText;
                break;
            case GAME_TITLES:
                this.filterGameTitles = filterText;
                break;
        }
    }

    public int getAmiiboView() {
        return this.browserAmiiboView;
    }

    public void setAmiiboView(int amiiboView) {
        this.browserAmiiboView = amiiboView;
    }

    public File getBrowserRootFolder() {
        return this.browserFolder;
    }

    public void setBrowserRootFolder(File browserRootFolder) {
        this.browserFolder = browserRootFolder;
    }

    public Uri getBrowserRootDocument() {
        return this.browserDocument;
    }

    public void setBrowserRootDocument(Uri browserRootDocument) {
        this.browserDocument = browserRootDocument;
    }

    public String getImageNetworkSettings() {
        return imageNetworkSettings;
    }

    public void setImageNetworkSettings(String imageNetworkSettings) {
        this.imageNetworkSettings = imageNetworkSettings;
    }

    public boolean isRecursiveEnabled() {
        return recursiveFolders;
    }

    public void setRecursiveEnabled(boolean recursiveFolders) {
        this.recursiveFolders = recursiveFolders;
    }

    public String getLastUpdatedAPI() {
        return lastUpdatedAPI;
    }

    public void setLastUpdatedAPI(String lastUpdatedAPI) {
        this.lastUpdatedAPI = lastUpdatedAPI;
    }

    public long getLastUpdatedGit() {
        return lastUpdatedGit;
    }

    public void setLastUpdatedGit(long lastUpdatedGit) {
        this.lastUpdatedGit = lastUpdatedGit;
    }

    public void notifyChanges() {
        for (BrowserSettingsListener listener : this.listeners) {
            listener.onBrowserSettingsChanged(this, this.oldBrowserSettings);
        }
        this.oldBrowserSettings = this.copy();
    }

    public void addChangeListener(BrowserSettingsListener listener) {
        this.listeners.add(listener);
    }

    @SuppressWarnings("unused")
    public void removeChangeListener(BrowserSettingsListener listener) {
        this.listeners.remove(listener);
    }

    @SuppressWarnings("unused")
    public void removeAllChangeListeners() {
        this.listeners.clear();
    }

    public interface BrowserSettingsListener {
        void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings);
    }

    private BrowserSettings copy() {
        BrowserSettings copy = new BrowserSettings(false);
        copy.setAmiiboManager(this.getAmiiboManager());
        copy.setAmiiboFiles(this.getAmiiboFiles());
        copy.setFolders(this.getFolders());
        copy.setQuery(this.getQuery());
        copy.setSort(this.getSort());
        copy.setFilter(FILTER.CHARACTER, this.getFilter(FILTER.CHARACTER));
        copy.setFilter(FILTER.GAME_SERIES, this.getFilter(FILTER.GAME_SERIES));
        copy.setFilter(FILTER.AMIIBO_SERIES, this.getFilter(FILTER.AMIIBO_SERIES));
        copy.setFilter(FILTER.AMIIBO_TYPE, this.getFilter(FILTER.AMIIBO_TYPE));
        copy.setFilter(FILTER.GAME_TITLES, this.getFilter(FILTER.GAME_TITLES));
        copy.setAmiiboView(this.getAmiiboView());
        copy.setBrowserRootFolder(this.getBrowserRootFolder());
        copy.setBrowserRootDocument(this.getBrowserRootDocument());
        copy.setImageNetworkSettings(this.getImageNetworkSettings());
        copy.setRecursiveEnabled(this.isRecursiveEnabled());
        copy.setLastUpdatedAPI(this.getLastUpdatedAPI());
        copy.setLastUpdatedGit(this.getLastUpdatedGit());

        return copy;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(this.amiiboFiles);
        dest.writeList(this.folders);
        dest.writeSerializable(this.browserFolder);
        dest.writeString(null != this.browserDocument ? this.browserDocument.toString() : null);
        dest.writeString(this.query);
        dest.writeInt(this.sort);
        dest.writeString(this.filterCharacter);
        dest.writeString(this.filterGameSeries);
        dest.writeString(this.filterAmiiboSeries);
        dest.writeString(this.filterAmiiboType);
        dest.writeString(this.filterGameTitles);
        dest.writeInt(this.browserAmiiboView);
        dest.writeString(this.imageNetworkSettings);
        dest.writeByte(this.recursiveFolders ? (byte) 1 : (byte) 0);
        dest.writeString(this.lastUpdatedAPI);
        dest.writeLong(this.lastUpdatedGit);
    }

    protected BrowserSettings(Parcel in) {
        this.amiiboFiles = in.createTypedArrayList(AmiiboFile.CREATOR);
        this.folders = new ArrayList<>();
        in.readList(this.folders, File.class.getClassLoader());
        this.browserFolder = (File) in.readSerializable();
        String docs = in.readString();
        this.browserDocument = null != docs && docs.length() > 0 ? Uri.parse(docs) : null;
        this.query = in.readString();
        this.sort = in.readInt();
        this.filterCharacter = in.readString();
        this.filterGameSeries = in.readString();
        this.filterAmiiboSeries = in.readString();
        this.filterAmiiboType = in.readString();
        this.filterGameTitles = in.readString();
        this.browserAmiiboView = in.readInt();
        this.imageNetworkSettings = in.readString();
        this.recursiveFolders = in.readByte() != 0;
        this.lastUpdatedAPI = in.readString();
        this.lastUpdatedGit = in.readLong();
    }

    static final Parcelable.Creator<BrowserSettings> CREATOR = new Parcelable.Creator<>() {
        @Override
        public BrowserSettings createFromParcel(Parcel source) {
            return new BrowserSettings(source);
        }

        @Override
        public BrowserSettings[] newArray(int size) {
            return new BrowserSettings[size];
        }
    };

    public static boolean equals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        } else if (null == o1 || null == o2) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }

    public boolean isFilterEmpty() {
        return getFilter(FILTER.CHARACTER).isEmpty()
                && getFilter(FILTER.GAME_SERIES).isEmpty()
                && getFilter(FILTER.AMIIBO_SERIES).isEmpty()
                && getFilter(FILTER.AMIIBO_TYPE).isEmpty()
                && getFilter(FILTER.GAME_TITLES).isEmpty();
    }

    public static boolean hasFilterChanged(BrowserSettings current, BrowserSettings previous) {
        return !equals(current.getFilter(FILTER.CHARACTER),
                previous.getFilter(FILTER.CHARACTER))
                || !equals(current.getFilter(FILTER.GAME_SERIES),
                previous.getFilter(FILTER.GAME_SERIES))
                || !equals(current.getFilter(FILTER.AMIIBO_SERIES),
                previous.getFilter(FILTER.AMIIBO_SERIES))
                || !equals(current.getFilter(FILTER.AMIIBO_TYPE),
                previous.getFilter(FILTER.AMIIBO_TYPE))
                || !equals(current.getFilter(FILTER.GAME_TITLES),
                previous.getFilter(FILTER.GAME_TITLES));
    }

    public boolean amiiboContainsQuery(Amiibo amiibo, String query) {
        Character character = amiibo.getCharacter();
        if (!Amiibo.matchesCharacterFilter(character, getFilter(FILTER.CHARACTER)))
            return false;

        GameSeries gameSeries = amiibo.getGameSeries();
        if (!Amiibo.matchesGameSeriesFilter(gameSeries, getFilter(FILTER.GAME_SERIES)))
            return false;

        AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
        if (!Amiibo.matchesAmiiboSeriesFilter(amiiboSeries, getFilter(FILTER.AMIIBO_SERIES)))
            return false;

        AmiiboType amiiboType = amiibo.getAmiiboType();
        if (!Amiibo.matchesAmiiboTypeFilter(amiiboType, getFilter(FILTER.AMIIBO_TYPE)))
            return false;

        if (getFilter(FILTER.GAME_TITLES).length() > 0
                && !gamesManager.isGameSupported(amiibo, getFilter(FILTER.GAME_TITLES)))
            return false;

        if (!query.isEmpty()) {
            if (Amiibo.idToHex(amiibo.id).toLowerCase().startsWith(query))
                return true;
            else if (null != amiibo.name && amiibo.name.toLowerCase().contains(query))
                return true;
            else if (null != character && character.name.toLowerCase().contains(query))
                return true;
            else if (null != gameSeries && gameSeries.name.toLowerCase().contains(query))
                return true;
            else if (null != amiiboSeries && amiiboSeries.name.toLowerCase().contains(query))
                return true;
            else return null != amiiboType && amiiboType.name.toLowerCase().contains(query);
        }
        return true;
    }
}
