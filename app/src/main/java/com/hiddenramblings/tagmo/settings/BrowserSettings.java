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
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
    protected BrowserSettings oldBrowserSettings;

    protected ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();
    protected ArrayList<File> folders = new ArrayList<>();
    protected File browserFolder;
    protected Uri browserDocument;
    protected String query;
    protected int sort;
    protected String filterGameSeries;
    protected String filterCharacter;
    protected String filterAmiiboSeries;
    protected String filterAmiiboType;
    protected int browserAmiiboView;
    protected String imageNetworkSettings;
    protected boolean recursiveFolders;
    protected boolean hideDownloads;
    protected String lastUpdatedAPI;
    protected long lastUpdatedGit;

    public BrowserSettings() {
        oldBrowserSettings = new BrowserSettings(false);
    }

    @SuppressWarnings("unused")
    public BrowserSettings(
            ArrayList<AmiiboFile> amiiboFiles, ArrayList<File> folders, File browserFolder,
            String query, int sort, String filterGameSeries, String filterCharacter,
            String filterAmiiboSeries, String filterAmiiboType, int browserAmiiboView,
            String imageNetworkSettings, boolean recursiveFolders,
            boolean hideDownloads, String lastUpdatedAPI, long lastUpdatedGit
    ) {
        super();

        this.amiiboFiles.addAll(amiiboFiles);
        this.folders.addAll(folders);
        this.browserFolder = browserFolder;
        this.query = query;
        this.sort = sort;
        this.filterGameSeries = filterGameSeries;
        this.filterCharacter = filterCharacter;
        this.filterAmiiboSeries = filterAmiiboSeries;
        this.filterAmiiboType = filterAmiiboType;
        this.browserAmiiboView = browserAmiiboView;
        this.imageNetworkSettings = imageNetworkSettings;
        this.recursiveFolders = recursiveFolders;
        this.hideDownloads = hideDownloads;
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
        this.setAmiiboSeriesFilter(prefs.filterAmiiboSeries().get());
        this.setAmiiboTypeFilter(prefs.filterAmiiboType().get());
        this.setCharacterFilter(prefs.filterCharacter().get());
        this.setGameSeriesFilter(prefs.filterGameSeries().get());
        this.setAmiiboView(prefs.browserAmiiboView().get());
        this.setImageNetworkSettings(prefs.image_network_settings().get());
        this.setRecursiveEnabled(prefs.recursiveFolders().get());
        this.setHideDownloads(prefs.hideDownloads().get());
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

    public ArrayList<AmiiboFile> getAmiiboFiles() {
        return amiiboFiles;
    }

    public void setAmiiboFiles(ArrayList<AmiiboFile> amiiboFiles) {
        this.amiiboFiles.clear();
        this.amiiboFiles.addAll(amiiboFiles);
    }

    public ArrayList<File> getFolders() {
        return folders;
    }

    public void setFolders(ArrayList<File> folders) {
        this.folders.clear();
        this.folders.addAll(folders);
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

    public String getGameSeriesFilter() {
        return this.filterGameSeries;
    }

    public void setGameSeriesFilter(String filterGameSeries) {
        this.filterGameSeries = filterGameSeries;
    }

    public String getCharacterFilter() {
        return this.filterCharacter;
    }

    public void setCharacterFilter(String filterCharacter) {
        this.filterCharacter = filterCharacter;
    }

    public String getAmiiboSeriesFilter() {
        return this.filterAmiiboSeries;
    }

    public void setAmiiboSeriesFilter(String filterAmiiboSeries) {
        this.filterAmiiboSeries = filterAmiiboSeries;
    }

    public String getAmiiboTypeFilter() {
        return this.filterAmiiboType;
    }

    public void setAmiiboTypeFilter(String filterAmiiboType) {
        this.filterAmiiboType = filterAmiiboType;
    }

    public boolean hasFilteredData() {
        return getGameSeriesFilter().length() > 0 || getCharacterFilter().length() > 0
                || getAmiiboSeriesFilter().length() > 0 || getAmiiboTypeFilter().length() > 0;
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

    public boolean isHidingDownloads() {
        return hideDownloads;
    }

    public void setHideDownloads(boolean hideDownloads) {
        this.hideDownloads = hideDownloads;
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
        copy.setGameSeriesFilter(this.getGameSeriesFilter());
        copy.setCharacterFilter(this.getCharacterFilter());
        copy.setAmiiboSeriesFilter(this.getAmiiboSeriesFilter());
        copy.setAmiiboTypeFilter(this.getAmiiboTypeFilter());
        copy.setAmiiboView(this.getAmiiboView());
        copy.setBrowserRootFolder(this.getBrowserRootFolder());
        copy.setBrowserRootDocument(this.getBrowserRootDocument());
        copy.setImageNetworkSettings(this.getImageNetworkSettings());
        copy.setRecursiveEnabled(this.isRecursiveEnabled());
        copy.setHideDownloads(this.isHidingDownloads());
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
        dest.writeString(this.filterGameSeries);
        dest.writeString(this.filterCharacter);
        dest.writeString(this.filterAmiiboSeries);
        dest.writeString(this.filterAmiiboType);
        dest.writeInt(this.browserAmiiboView);
        dest.writeString(this.imageNetworkSettings);
        dest.writeByte(this.recursiveFolders ? (byte) 1 : (byte) 0);
        dest.writeByte(this.hideDownloads ? (byte) 1 : (byte) 0);
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
        this.filterGameSeries = in.readString();
        this.filterCharacter = in.readString();
        this.filterAmiiboSeries = in.readString();
        this.filterAmiiboType = in.readString();
        this.browserAmiiboView = in.readInt();
        this.imageNetworkSettings = in.readString();
        this.recursiveFolders = in.readByte() != 0;
        this.hideDownloads = in.readByte() != 0;
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

    public boolean amiiboContainsQuery(Amiibo amiibo, String query) {
        GameSeries gameSeries = amiibo.getGameSeries();
        if (!Amiibo.matchesGameSeriesFilter(gameSeries, getGameSeriesFilter()))
            return false;

        Character character = amiibo.getCharacter();
        if (!Amiibo.matchesCharacterFilter(character, getCharacterFilter()))
            return false;

        AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
        if (!Amiibo.matchesAmiiboSeriesFilter(amiiboSeries, getAmiiboSeriesFilter()))
            return false;

        AmiiboType amiiboType = amiibo.getAmiiboType();
        if (!Amiibo.matchesAmiiboTypeFilter(amiiboType, getAmiiboTypeFilter()))
            return false;

        if (!query.isEmpty()) {
            if (TagUtils.amiiboIdToHex(amiibo.id).toLowerCase().startsWith(query))
                return true;
            else if (null != amiibo.name && amiibo.name.toLowerCase().contains(query))
                return true;
            else if (null != gameSeries && gameSeries.name.toLowerCase().contains(query))
                return true;
            else if (null != character && character.name.toLowerCase().contains(query))
                return true;
            else if (null != amiiboSeries && amiiboSeries.name.toLowerCase().contains(query))
                return true;
            else return null != amiiboType && amiiboType.name.toLowerCase().contains(query);
        }
        return true;
    }
}
