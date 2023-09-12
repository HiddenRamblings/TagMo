package com.hiddenramblings.tagmo.amiibo

import android.os.Parcel
import android.os.Parcelable
import androidx.documentfile.provider.DocumentFile
import com.hiddenramblings.tagmo.amiibo.tagdata.AmiiboData
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.nfctech.Foomiibo
import com.hiddenramblings.tagmo.nfctech.TagArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

open class AmiiboFile : Parcelable {
    var filePath: File? = null
    var docUri: DocumentFile? = null
    var id: Long
    var data: ByteArray?

    @JvmOverloads
    constructor(filePath: File?, id: Long, data: ByteArray? = null) {
        this.filePath = filePath
        this.id = id
        this.data = data
    }

    constructor(docPath: DocumentFile?, id: Long, data: ByteArray?) {
        docUri = docPath
        this.id = id
        this.data = data
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(filePath)
        dest.writeLong(id)
        data?.let {
            dest.writeInt(it.size)
            dest.writeByteArray(it)
        }
    }

    protected constructor(parcel: Parcel) {
        filePath = if (Version.isTiramisu)
            parcel.readSerializable(null, File::class.java)
        else
            @Suppress("deprecation") parcel.readSerializable() as File?
        id = parcel.readLong()
        data = ByteArray(parcel.readInt()).also { parcel.readByteArray(it) }
    }

    suspend fun withRandomSerials(keyManager: KeyManager, count: Int) : ArrayList<AmiiboData> {
        val dataList: ArrayList<AmiiboData> = arrayListOf()
        coroutineScope {
            val tagData = TagArray.getValidatedAmiibo(keyManager, this@AmiiboFile)
            (0 until count).map {
                async(Dispatchers.IO) {
                    try {
                        AmiiboData(keyManager.decrypt(tagData)).apply {
                            uID = Foomiibo.generateRandomUID()
                        }.also {
                            dataList.add(it)
                        }
                    } catch (e: Exception) {
                        Debug.warn(e)
                    }
                }
            }.awaitAll()
        }
        return dataList
    }

    suspend fun withRandomSerials(count: Int) : ArrayList<AmiiboData> {
        val dataList: ArrayList<AmiiboData> = arrayListOf()
        this@AmiiboFile.data?.let { tagData ->
            coroutineScope {
                (0 until count).map {
                    async(Dispatchers.IO) {
                        try {
                            AmiiboData(tagData).apply {
                                uID = Foomiibo.generateRandomUID()
                            }.also {
                                dataList.add(it)
                            }
                        } catch (e: Exception) {
                            Debug.warn(e)
                        }
                    }
                }.awaitAll()
            }
        }
        return dataList
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<AmiiboFile?> = object : Parcelable.Creator<AmiiboFile?> {
            override fun createFromParcel(source: Parcel): AmiiboFile {
                return AmiiboFile(source)
            }

            override fun newArray(size: Int): Array<AmiiboFile?> {
                return arrayOfNulls(size)
            }
        }
    }
}