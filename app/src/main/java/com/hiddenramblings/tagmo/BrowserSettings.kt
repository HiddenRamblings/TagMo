package com.hiddenramblings.tagmo

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.hiddenramblings.tagmo.TagMo.Companion.appContext
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.amiibo.games.GamesManager
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import java.io.File
import java.util.*

open class BrowserSettings : Parcelable {
    enum class SORT(val value: Int) {
        ID(0x0),
        NAME(0x1),
        AMIIBO_SERIES(0x2),
        AMIIBO_TYPE(0x3),
        GAME_SERIES(0x4),
        CHARACTER(0x5),
        FILE_PATH(0x6);

        companion object {
            fun valueOf(value: Int): SORT {
                if (Version.isNougat) {
                    val optional = Arrays.stream(values()).filter {
                            SORT: SORT -> SORT.value == value
                    }.findFirst()
                    if (optional.isPresent) return optional.get()
                } else {
                    for (view in values()) {
                        if (view.value == value) return view
                    }
                }
                return NAME
            }
        }
    }

    enum class FILTER {
        CHARACTER, GAME_SERIES, AMIIBO_SERIES, AMIIBO_TYPE, GAME_TITLES
    }

    enum class VIEW(val value: Int) {
        SIMPLE(0), COMPACT(1), LARGE(2), IMAGE(3);

        companion object {
            fun valueOf(value: Int): VIEW {
                if (Version.isNougat) {
                    val optional =
                        Arrays.stream(values()).filter { VIEW: VIEW -> VIEW.value == value }
                            .findFirst()
                    if (optional.isPresent) return optional.get()
                } else {
                    for (view in values()) {
                        if (view.value == value) return view
                    }
                }
                return COMPACT
            }
        }
    }

    protected var listeners: ArrayList<BrowserSettingsListener?> = arrayListOf()
    var amiiboManager: AmiiboManager? = null
    var gamesManager: GamesManager? = null
    private var oldBrowserSettings: BrowserSettings? = null
    var amiiboFiles: ArrayList<AmiiboFile?> = arrayListOf()
    var folders: ArrayList<File?> = arrayListOf()
    var query: String? = null
    var sort = 0
    private var filterCharacter: String? = null
    private var filterGameSeries: String? = null
    private var filterAmiiboSeries: String? = null
    private var filterAmiiboType: String? = null
    private var filterGameTitles: String? = null
    var amiiboView = 0
    var imageNetworkSettings: String? = null
    var browserRootFolder: File? = null
    var browserRootDocument: Uri? = null
    var isRecursiveEnabled = false
    var lastUpdatedAPI: String? = null
    var lastUpdatedGit: Long = 0

    constructor() {
        oldBrowserSettings = BrowserSettings(false)
    }

    constructor(
        amiiboFiles: ArrayList<AmiiboFile>, folders: ArrayList<File>,
        browserFolder: File?, browserDocument: Uri?,
        query: String?, sort: Int, filterCharacter: String?,
        filterGameSeries: String?, filterAmiiboSeries: String?,
        filterAmiiboType: String?, filterGameTitles: String?,
        browserAmiiboView: Int, imageNetworkSettings: String?,
        recursiveFolders: Boolean,
        lastUpdatedAPI: String?, lastUpdatedGit: Long
    ) : super() {
        this.amiiboFiles.addAll(amiiboFiles)
        this.folders.addAll(folders)
        this.browserRootFolder = browserFolder
        this.browserRootDocument = browserDocument
        this.query = query
        this.sort = sort
        this.filterCharacter = filterCharacter
        this.filterGameSeries = filterGameSeries
        this.filterAmiiboSeries = filterAmiiboSeries
        this.filterAmiiboType = filterAmiiboType
        this.filterGameTitles = filterGameTitles
        this.amiiboView = browserAmiiboView
        this.imageNetworkSettings = imageNetworkSettings
        this.isRecursiveEnabled = recursiveFolders
        this.lastUpdatedAPI = lastUpdatedAPI
        this.lastUpdatedGit = lastUpdatedGit
    }

    private constructor(duplicate: Boolean) {
        if (duplicate) {
            oldBrowserSettings = copy()
        }
    }

    fun initialize(): BrowserSettings {
        val prefs = Preferences(appContext)
        query = prefs.query()
        sort = prefs.sort()
        setFilter(FILTER.CHARACTER, prefs.filterCharacter())
        setFilter(FILTER.GAME_SERIES, prefs.filterGameSeries())
        setFilter(FILTER.AMIIBO_SERIES, prefs.filterAmiiboSeries())
        setFilter(FILTER.AMIIBO_TYPE, prefs.filterAmiiboType())
        setFilter(FILTER.GAME_TITLES, prefs.filterGameTitles())
        amiiboView = prefs.browserAmiiboView()
        imageNetworkSettings = prefs.imageNetwork()
        browserRootFolder = prefs.browserRootFolder()?.let {
            File(Storage.getFile(prefs.preferEmulated()), it)
        } ?: Storage.getDownloadDir(null)
        browserRootDocument = prefs.browserRootDocument()?.let { Uri.parse(it) }
        isRecursiveEnabled = prefs.recursiveFolders()
        lastUpdatedAPI = prefs.lastUpdatedAPI()
        lastUpdatedGit = prefs.lastUpdatedGit()
        return this
    }

    fun getFilter(filter: FILTER?): String {
        var filterText: String? = null
        when (filter) {
            FILTER.CHARACTER -> filterText = filterCharacter
            FILTER.GAME_SERIES -> filterText = filterGameSeries
            FILTER.AMIIBO_SERIES -> filterText = filterAmiiboSeries
            FILTER.AMIIBO_TYPE -> filterText = filterAmiiboType
            FILTER.GAME_TITLES -> filterText = filterGameTitles
            else -> {}
        }
        return if (filterText.isNullOrEmpty()) "" else filterText
    }

    fun setFilter(filter: FILTER?, filterText: String?) {
        when (filter) {
            FILTER.CHARACTER -> filterCharacter = filterText
            FILTER.GAME_SERIES -> filterGameSeries = filterText
            FILTER.AMIIBO_SERIES -> filterAmiiboSeries = filterText
            FILTER.AMIIBO_TYPE -> filterAmiiboType = filterText
            FILTER.GAME_TITLES -> filterGameTitles = filterText
            else -> {}
        }
    }

    fun notifyChanges() {
        listeners.forEach {
            it?.onBrowserSettingsChanged(this, oldBrowserSettings)
        }
        oldBrowserSettings = copy()
    }

    fun addChangeListener(listener: BrowserSettingsListener?) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: BrowserSettingsListener?) {
        listeners.remove(listener)
    }

    fun removeAllChangeListeners() {
        listeners.clear()
    }

    interface BrowserSettingsListener {
        fun onBrowserSettingsChanged(
            newBrowserSettings: BrowserSettings?,
            oldBrowserSettings: BrowserSettings?
        )
    }

    private fun copy(): BrowserSettings {
        return BrowserSettings(false).also {
            it.amiiboManager = amiiboManager
            it.amiiboFiles = amiiboFiles
            it.folders = folders
            it.query = query
            it.sort = sort
            it.setFilter(FILTER.CHARACTER, getFilter(FILTER.CHARACTER))
            it.setFilter(FILTER.GAME_SERIES, getFilter(FILTER.GAME_SERIES))
            it.setFilter(FILTER.AMIIBO_SERIES, getFilter(FILTER.AMIIBO_SERIES))
            it.setFilter(FILTER.AMIIBO_TYPE, getFilter(FILTER.AMIIBO_TYPE))
            it.setFilter(FILTER.GAME_TITLES, getFilter(FILTER.GAME_TITLES))
            it.amiiboView = amiiboView
            it.imageNetworkSettings = imageNetworkSettings
            it.browserRootFolder = browserRootFolder
            it.browserRootDocument = browserRootDocument
            it.isRecursiveEnabled = isRecursiveEnabled
            it.lastUpdatedAPI = lastUpdatedAPI
            it.lastUpdatedGit = lastUpdatedGit
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeTypedList(amiiboFiles)
        dest.writeList(folders)
        dest.writeString(query)
        dest.writeInt(sort)
        dest.writeString(filterCharacter)
        dest.writeString(filterGameSeries)
        dest.writeString(filterAmiiboSeries)
        dest.writeString(filterAmiiboType)
        dest.writeString(filterGameTitles)
        dest.writeInt(amiiboView)
        dest.writeString(imageNetworkSettings)
        dest.writeSerializable(browserRootFolder)
        dest.writeString(browserRootDocument?.toString())
        dest.writeByte(if (isRecursiveEnabled) 1.toByte() else 0.toByte())
        dest.writeString(lastUpdatedAPI)
        dest.writeLong(lastUpdatedGit)
    }

    protected constructor(parcel: Parcel) {
        amiiboFiles = parcel.createTypedArrayList(AmiiboFile.CREATOR)!!
        folders = arrayListOf()
        if (Version.isTiramisu)
            parcel.readList(folders, File::class.java.classLoader, File::class.java)
        else
            @Suppress("DEPRECATION") parcel.readList(folders, File::class.java.classLoader)
        query = parcel.readString()
        sort = parcel.readInt()
        filterCharacter = parcel.readString()
        filterGameSeries = parcel.readString()
        filterAmiiboSeries = parcel.readString()
        filterAmiiboType = parcel.readString()
        filterGameTitles = parcel.readString()
        amiiboView = parcel.readInt()
        imageNetworkSettings = parcel.readString()
        browserRootFolder = if (Version.isTiramisu)
            parcel.readSerializable(File::class.java.classLoader, File::class.java)
        else
            @Suppress("DEPRECATION") parcel.readSerializable() as? File
        val docs = parcel.readString()
        browserRootDocument = if (!docs.isNullOrEmpty()) Uri.parse(docs) else null
        isRecursiveEnabled = parcel.readByte().toInt() != 0
        lastUpdatedAPI = parcel.readString()
        lastUpdatedGit = parcel.readLong()
    }

    val isFilterEmpty: Boolean
        get() = (getFilter(FILTER.CHARACTER).isEmpty()
                && getFilter(FILTER.GAME_SERIES).isEmpty()
                && getFilter(FILTER.AMIIBO_SERIES).isEmpty()
                && getFilter(FILTER.AMIIBO_TYPE).isEmpty()
                && getFilter(FILTER.GAME_TITLES).isEmpty())

    fun amiiboContainsQuery(amiibo: Amiibo, query: String?): Boolean {
        val character = amiibo.character
        if (!Amiibo.matchesCharacterFilter(character, getFilter(FILTER.CHARACTER))) return false
        val gameSeries = amiibo.gameSeries
        if (!Amiibo.matchesGameSeriesFilter(gameSeries, getFilter(FILTER.GAME_SERIES))) return false
        val amiiboSeries = amiibo.amiiboSeries
        if (!Amiibo.matchesAmiiboSeriesFilter(
                amiiboSeries,
                getFilter(FILTER.AMIIBO_SERIES)
            )
        ) return false
        val amiiboType = amiibo.amiiboType
        if (!Amiibo.matchesAmiiboTypeFilter(amiiboType, getFilter(FILTER.AMIIBO_TYPE))) return false
        if (getFilter(FILTER.GAME_TITLES).isNotEmpty()
            && !gamesManager!!.isGameSupported(amiibo, getFilter(FILTER.GAME_TITLES))
        ) return false
        return if (!query.isNullOrEmpty()) {
            if (Amiibo.idToHex(amiibo.id).lowercase().startsWith(query))
                true
            else if (null != amiibo.name && amiibo.name.lowercase(Locale.getDefault()).contains(query))
                true
            else if (null != character && character.name.lowercase(Locale.getDefault()).contains(query))
                true
            else if (null != gameSeries && gameSeries.name.lowercase(Locale.getDefault()).contains(query))
                true
            else if (null != amiiboSeries && amiiboSeries.name.lowercase(Locale.getDefault()).contains(query))
                true
            else null != amiiboType && amiiboType.name.lowercase(Locale.getDefault()).contains(query)
        } else true
    }

    companion object {
        @JvmField @Suppress("unused")
        val CREATOR: Parcelable.Creator<BrowserSettings?> =
            object : Parcelable.Creator<BrowserSettings?> {
                override fun createFromParcel(source: Parcel): BrowserSettings {
                    return BrowserSettings(source)
                }

                override fun newArray(size: Int): Array<BrowserSettings?> {
                    return arrayOfNulls(size)
                }
            }

        fun equals(o1: Any?, o2: Any?): Boolean {
            return if (o1 === o2) {
                true
            } else if (null == o1 || null == o2) {
                false
            } else {
                o1 == o2
            }
        }

        fun hasFilterChanged(current: BrowserSettings?, previous: BrowserSettings?): Boolean {
            return (!equals(
                current?.getFilter(FILTER.CHARACTER),
                previous?.getFilter(FILTER.CHARACTER)
            )
                    || !equals(
                current?.getFilter(FILTER.GAME_SERIES),
                previous?.getFilter(FILTER.GAME_SERIES)
            )
                    || !equals(
                current?.getFilter(FILTER.AMIIBO_SERIES),
                previous?.getFilter(FILTER.AMIIBO_SERIES)
            )
                    || !equals(
                current?.getFilter(FILTER.AMIIBO_TYPE),
                previous?.getFilter(FILTER.AMIIBO_TYPE)
            )
                    || !equals(
                current?.getFilter(FILTER.GAME_TITLES),
                previous?.getFilter(FILTER.GAME_TITLES)
            ))
        }
    }
}