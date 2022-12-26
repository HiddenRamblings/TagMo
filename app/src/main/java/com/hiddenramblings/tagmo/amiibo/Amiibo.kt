package com.hiddenramblings.tagmo.amiibo

import android.os.Build
import android.os.Parcelable
import android.os.Parcel
import kotlin.Throws
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData
import android.text.TextUtils
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.io.IOException
import java.lang.NumberFormatException

open class Amiibo : Comparable<Amiibo>, Parcelable {
    var manager: AmiiboManager? = null
    val id: Long
    val name: String?
    val releaseDates: AmiiboReleaseDates?

    constructor(
        manager: AmiiboManager?, id: Long, name: String?, releaseDates: AmiiboReleaseDates?
    ) {
        this.manager = manager
        this.id = id
        this.name = name
        this.releaseDates = releaseDates
    }

    constructor(
        manager: AmiiboManager?, id: String, name: String?, releaseDates: AmiiboReleaseDates?
    ) : this(manager, hexToId(id), name, releaseDates)

    val head: Int
        get() = (id and HEAD_MASK shr HEAD_BITSHIFT).toInt()
    val tail: Int
        get() = (id and TAIL_MASK shr TAIL_BITSHIFT).toInt()
    val characterId: Long
        get() = id and Character.MASK
    val character: Character?
        get() = manager?.characters?.get(characterId)
    val gameSeriesId: Long
        get() = id and GameSeries.MASK
    val gameSeries: GameSeries?
        get() = manager?.gameSeries?.get(gameSeriesId)
    val variantId: Long
        get() = id and VARIANT_MASK
    val amiiboTypeId: Long
        get() = id and AmiiboType.MASK
    val amiiboType: AmiiboType?
        get() = manager?.amiiboTypes?.get(amiiboTypeId)
    val amiiboModelId: Long
        get() = id and AMIIBO_MODEL_MASK
    val amiiboSeriesId: Long
        get() = id and AmiiboSeries.MASK
    val amiiboSeries: AmiiboSeries?
        get() = manager?.amiiboSeries?.get(amiiboSeriesId)
    val unknownId: Long
        get() = id and UNKNOWN_MASK
    val imageUrl: String
        get() = String.format(AMIIBO_IMAGE, head, tail)
    val flaskTail: String
        get() = idToHex(id).substring(8, 16).toInt(16).toString(36)

    override fun compareTo(other: Amiibo): Int {
        if (id == other.id) return 0
        var value: Int
        val character1 = character
        val character2 = other.character
        value = if (character1 == null && character2 == null) {
            0
        } else if (character1 == null) {
            1
        } else if (character2 == null) {
            -1
        } else {
            character1.compareTo(character2)
        }
        if (value != 0) return value
        val gameSeries1 = gameSeries
        val gameSeries2 = other.gameSeries
        value = if (gameSeries1 == null && gameSeries2 == null) {
            0
        } else if (gameSeries1 == null) {
            1
        } else if (gameSeries2 == null) {
            -1
        } else {
            gameSeries1.compareTo(gameSeries2)
        }
        if (value != 0) return value
        val amiiboSeries1 = amiiboSeries
        val amiiboSeries2 = other.amiiboSeries
        value = if (amiiboSeries1 == null && amiiboSeries2 == null) {
            0
        } else if (amiiboSeries1 == null) {
            1
        } else if (amiiboSeries2 == null) {
            -1
        } else {
            amiiboSeries1.compareTo(amiiboSeries2)
        }
        if (value != 0) return value
        val amiiboType1 = amiiboType
        val amiiboType2 = other.amiiboType
        value = if (amiiboType1 == null && amiiboType2 == null) {
            0
        } else if (amiiboType1 == null) {
            1
        } else if (amiiboType2 == null) {
            -1
        } else {
            amiiboType1.compareTo(amiiboType2)
        }
        if (value != 0) return value
        val name1 = name
        val name2 = other.name
        value = if (name1 == null && name2 == null) {
            0
        } else if (name1 == null) {
            1
        } else if (name2 == null) {
            -1
        } else {
            name1.compareTo(name2)
        }
        return value
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(name)
        dest.writeLong(id)
        dest.writeSerializable(releaseDates)
    }

    protected constructor(parcel: Parcel) {
        name = parcel.readString()
        id = parcel.readLong()
        releaseDates = if (Debug.isNewer(Build.VERSION_CODES.TIRAMISU))
            parcel.readSerializable(null, AmiiboReleaseDates::class.java)
        else
            @Suppress("DEPRECATION") parcel.readSerializable() as AmiiboReleaseDates?
    }

    companion object {
        private const val AMIIBO_IMAGE = (AmiiboManager.RENDER_RAW + "images/icon_%08x-%08x.png")
        const val HEAD_MASK = -0x100000000L
        const val TAIL_MASK = 0x00000000FFFFFFFFL
        const val HEAD_BITSHIFT = 4 * 8
        const val TAIL_BITSHIFT = 4 * 0
        const val VARIANT_MASK = -0x10000000000L
        const val AMIIBO_MODEL_MASK = 0x00000000FFFF0000L
        const val UNKNOWN_MASK = 0x00000000000000FFL
        fun hexToId(value: String): Long {
            return java.lang.Long.decode(value)
        }

        fun idToHex(amiiboId: Long): String {
            return String.format("%016X", amiiboId)
        }

        @Throws(NumberFormatException::class, IOException::class)
        fun dataToId(data: ByteArray?): Long {
            return AmiiboData(data!!).amiiboID
        }

        fun getImageUrl(amiiboId: Long): String {
            val head = (amiiboId and HEAD_MASK shr HEAD_BITSHIFT).toInt()
            val tail = (amiiboId and TAIL_MASK shr TAIL_BITSHIFT).toInt()
            return String.format(AMIIBO_IMAGE, head, tail)
        }

        fun matchesCharacterFilter(character: Character?, characterFilter: String): Boolean {
            return if (null != character) {
                TextUtils.isEmpty(characterFilter) || character.name == characterFilter
            } else true
        }

        fun matchesGameSeriesFilter(gameSeries: GameSeries?, gameSeriesFilter: String): Boolean {
            return if (null != gameSeries) {
                TextUtils.isEmpty(gameSeriesFilter) || gameSeries.name == gameSeriesFilter
            } else true
        }

        fun matchesAmiiboSeriesFilter(
            amiiboSeries: AmiiboSeries?,
            amiiboSeriesFilter: String
        ): Boolean {
            return if (null != amiiboSeries) {
                TextUtils.isEmpty(amiiboSeriesFilter) || amiiboSeries.name == amiiboSeriesFilter
            } else true
        }

        fun matchesAmiiboTypeFilter(amiiboType: AmiiboType?, amiiboTypeFilter: String): Boolean {
            return if (null != amiiboType) {
                TextUtils.isEmpty(amiiboTypeFilter) || amiiboType.name == amiiboTypeFilter
            } else true
        }

        @JvmField
        val CREATOR: Parcelable.Creator<Amiibo?> = object : Parcelable.Creator<Amiibo?> {
            override fun createFromParcel(source: Parcel): Amiibo {
                return Amiibo(source)
            }

            override fun newArray(size: Int): Array<Amiibo?> {
                return arrayOfNulls(size)
            }
        }
    }
}