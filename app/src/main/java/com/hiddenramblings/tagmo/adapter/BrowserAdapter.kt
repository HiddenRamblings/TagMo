package com.hiddenramblings.tagmo.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboFileComparator
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.hasSpoofData
import com.hiddenramblings.tagmo.BrowserSettings
import com.hiddenramblings.tagmo.BrowserSettings.*
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget
import com.hiddenramblings.tagmo.eightbit.widget.BoldSpannable
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.util.*

class BrowserAdapter(
    private val settings: BrowserSettings, private val listener: OnAmiiboClickListener
) : RecyclerView.Adapter<BrowserAdapter.AmiiboViewHolder>(),
    PopupTextProvider, Filterable, BrowserSettingsListener {
    private var data: ArrayList<AmiiboFile?> = arrayListOf()
    private var filteredData: ArrayList<AmiiboFile?> = arrayListOf()
    private var filter: AmiiboFilter? = null
    private var firstRun = true
    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
        if (null == newBrowserSettings || null == oldBrowserSettings) return
        var refresh = firstRun ||
                !BrowserSettings.equals(
                    newBrowserSettings.query,
                    oldBrowserSettings.query
                ) ||
                !BrowserSettings.equals(
                    newBrowserSettings.sort,
                    oldBrowserSettings.sort
                ) ||
                BrowserSettings.hasFilterChanged(oldBrowserSettings, newBrowserSettings)
        if (firstRun || !BrowserSettings.equals(
                newBrowserSettings.amiiboFiles,
                oldBrowserSettings.amiiboFiles
            )
        ) {
            data = arrayListOf()
            data.addAll(newBrowserSettings.amiiboFiles)
            refresh = true
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.amiiboManager,
                oldBrowserSettings.amiiboManager
            )
        ) {
            refresh = true
        }
        if (!BrowserSettings.equals(
                newBrowserSettings.amiiboView,
                oldBrowserSettings.amiiboView
            )
        ) {
            refresh = true
        }
        if (refresh) refresh()
        firstRun = false
    }

    override fun getItemCount(): Int {
        return filteredData.size
    }

    override fun getItemId(i: Int): Long {
        return (filteredData[i]?.id ?: 0).toLong()
    }

    private fun getItem(i: Int): AmiiboFile? {
        return filteredData[i]
    }

    fun hasItem(id: Long) : Boolean {
        return filteredData.any { id == it?.id }
    }

    init {
        filteredData = data
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    private fun handleClickEvent(holder: AmiiboViewHolder) {
        if (null != holder.listener) {
            val uri = holder.amiiboFile?.docUri?.uri.toString()
            val path = holder.amiiboFile?.filePath?.absolutePath
            if (settings.amiiboView != VIEW.IMAGE.value) {
                if (amiiboPath.contains(uri) || amiiboPath.contains(path)) {
                    uri.let { amiiboPath.remove(it) }
                    path?.let { amiiboPath.remove(it) }
                } else {
                    amiiboPath.add(uri)
                    path?.let { amiiboPath.add(it) }
                }
            } else {
                amiiboPath.clear()
            }
            holder.listener.onAmiiboClicked(holder.itemView, holder.amiiboFile)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmiiboViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(parent, settings, listener)
            VIEW.LARGE -> LargeViewHolder(parent, settings, listener)
            VIEW.IMAGE -> ImageViewHolder(parent, settings, listener)
            VIEW.SIMPLE -> SimpleViewHolder(parent, settings, listener)
        }.apply {
            itemView.setOnClickListener { handleClickEvent(this) }
            imageAmiibo?.setOnClickListener {
                if (settings.amiiboView == VIEW.IMAGE.value)
                    handleClickEvent(this)
                else
                    listener?.onAmiiboImageClicked(amiiboFile)
            }
        }
    }

    override fun onBindViewHolder(holder: AmiiboViewHolder, position: Int) {
        holder.bind(getItem(holder.bindingAdapterPosition))
    }

    override fun getPopupText(position: Int) : CharSequence {
        if (position >= filteredData.size) return "?"
        val amiibo: Amiibo? = filteredData[position]?.let { file ->
            settings.amiiboManager?.let {
                it.amiibos[file.id] ?: Amiibo(it, file.id, null, null)
            }
        }
        return amiibo?.let {
            when (SORT.valueOf(settings.sort)) {
                SORT.NAME -> it.name ?: "?"
                SORT.CHARACTER -> it.character?.name ?: "?"
                SORT.GAME_SERIES -> it.gameSeries?.name ?: "?"
                SORT.AMIIBO_SERIES -> it.amiiboSeries?.name ?: "?"
                SORT.AMIIBO_TYPE -> it.amiiboType?.name ?: "?"
                else -> "?"
            }[0].uppercase()
        } ?: "?"
    }

    fun refresh() { getFilter().filter(settings.query) }

    override fun getFilter(): AmiiboFilter {
        if (null == filter) filter = AmiiboFilter()
        return filter as AmiiboFilter
    }

    inner class AmiiboFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString() ?: ""
            val filterResults = FilterResults()
            if (query.trim { it <= ' ' }.isEmpty()) {
                filterResults.count = data.size
                filterResults.values = data
            }
            settings.query = query
            data = ArrayList(settings.amiiboFiles)
            val tempList:ArrayList<AmiiboFile> = arrayListOf()
            val queryText = query.trim { it <= ' ' }.lowercase(Locale.getDefault())
            val amiiboManager = settings.amiiboManager
            val amiiboFiles = settings.amiiboFiles
            amiiboFiles.forEach { amiiboFile ->
                amiiboFile?.let { file ->
                    var add = false
                    amiiboManager?.let {
                        var amiibo = it.amiibos[file.id]
                        if (null == amiibo)
                            amiibo = Amiibo(it, file.id, null, null)
                        add = settings.amiiboContainsQuery(amiibo, queryText)
                    }
                    if (!add) {
                        file.docUri?.let { uri ->
                            add = pathContainsQuery(uri.toString(), queryText)
                        }
                        file.filePath?.let { path ->
                            add = pathContainsQuery(path.absolutePath, queryText)
                        }
                    }
                    if (add) tempList.add(file)
                }
            }
            filterResults.count = tempList.size
            filterResults.values = tempList
            return filterResults
        }

        private fun pathContainsQuery(path: String, query: String?): Boolean {
            return (!query.isNullOrEmpty() && settings.isFilterEmpty
                    && path.lowercase(Locale.getDefault()).contains(query))
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            if (filteredData === filterResults.values) return
            filterResults.values.let { filteredData = it as ArrayList<AmiiboFile?> }
            if (itemCount > 0) Collections.sort(filteredData, AmiiboFileComparator(settings))
            notifyDataSetChanged()
        }
    }

    abstract class AmiiboViewHolder(
        itemView: View, private val settings: BrowserSettings, val listener: OnAmiiboClickListener?
    ) : RecyclerView.ViewHolder(itemView) {
        val txtError: TextView?
        val txtName: TextView?
        val txtTagId: TextView?
        val txtAmiiboSeries: TextView?
        val txtAmiiboType: TextView?
        val txtGameSeries: TextView?
        // public final TextView txtCharacter;
        val txtPath: TextView?
        var imageAmiibo: AppCompatImageView? = null

        private val menuOptions: LinearLayout?
        val txtUsage: TextView?

        var amiiboFile: AmiiboFile? = null
        private val boldSpannable = BoldSpannable()

        init {
            txtError = itemView.findViewById(R.id.txtError)
            txtName = itemView.findViewById(R.id.txtName)
            txtTagId = itemView.findViewById(R.id.txtTagId)
            txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries)
            txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType)
            txtGameSeries = itemView.findViewById(R.id.txtGameSeries)
            // this.txtCharacter = itemView.findViewById(R.id.txtCharacter);
            txtPath = itemView.findViewById(R.id.txtPath)
            imageAmiibo = itemView.findViewById(R.id.imageAmiibo)

            menuOptions = itemView.findViewById(R.id.menu_options)
            txtUsage = itemView.findViewById(R.id.txtUsage)
        }

        fun bind(item: AmiiboFile?) {
            amiiboFile = item
            var tagInfo: String? = null
            var amiiboHexId: String? = ""
            var amiiboName = ""
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            // String character = "";
            var amiiboImageUrl: String? = null
            val amiiboId = item?.id
            var amiibo: Amiibo? = null
            val amiiboManager = settings.amiiboManager
            amiiboManager?.let {
                amiibo = it.amiibos[amiiboId]
                if (null == amiibo && null != amiiboId)
                    amiibo = Amiibo(it, amiiboId, null, null)
            }
            amiibo?.let {
                amiiboHexId = Amiibo.idToHex(it.id)
                amiiboImageUrl = it.imageUrl
                it.name?.let { name -> amiiboName = name }
                it.amiiboSeries?.let { series -> amiiboSeries = series.name }
                it.amiiboType?.let { type -> amiiboType = type.name }
                it.gameSeries?.let { series -> gameSeries = series.name }
            } ?: amiiboId?.let {
                amiiboHexId = Amiibo.idToHex(it)
                tagInfo = "ID: $amiiboHexId"
                amiiboImageUrl = Amiibo.getImageUrl(it)
            }
            imageAmiibo?.let {
                val imageTarget: CustomTarget<Bitmap?> = ImageTarget.getTarget(it)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(it).clear(it)
                    GlideApp.with(it).asBitmap().load(amiiboImageUrl).into(imageTarget)
                }
            }
            val query = settings.query?.lowercase(Locale.getDefault())
            setAmiiboInfoText(txtName, amiiboName, false)
            if (settings.amiiboView != VIEW.IMAGE.value) {
                val isTagInfo = null != tagInfo
                if (isTagInfo) {
                    setAmiiboInfoText(txtError, tagInfo, false)
                } else {
                    txtError?.isGone = true
                }
                setAmiiboInfoText(
                    txtTagId, boldSpannable.startsWith(amiiboHexId, query), isTagInfo
                )
                setAmiiboInfoText(
                    txtAmiiboSeries, boldSpannable.indexOf(amiiboSeries, query), isTagInfo
                )
                setAmiiboInfoText(
                    txtAmiiboType, boldSpannable.indexOf(amiiboType, query), isTagInfo
                )
                setAmiiboInfoText(
                    txtGameSeries, boldSpannable.indexOf(gameSeries, query), isTagInfo
                )
                txtPath?.run {
                    item?.docUri?.let {
                        val relativeDocument = Storage.getRelativeDocument(it.uri)
                        val expanded = amiiboPath.contains(relativeDocument)
                        menuOptions?.isVisible = expanded
                        txtUsage?.isVisible = expanded
                        if (expanded) listener?.onAmiiboRebind(itemView, amiiboFile)
                        itemView.isEnabled = true
                        text = boldSpannable.indexOf(relativeDocument, query)
                        val a = TypedValue()
                        context.theme.resolveAttribute(
                            android.R.attr.textColor, a, true
                        )
                        if (Version.isQuinceTart && a.isColorType) {
                            setTextColor(a.data)
                        } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT
                        ) {
                            setTextColor(a.data)
                        }
                        setIsHighlighted(relativeDocument.startsWith("/Foomiibo/"))
                    } ?: item?.filePath?.let {
                        val expanded = amiiboPath.contains(it.absolutePath)
                        menuOptions?.isVisible = expanded
                        txtUsage?.isVisible = expanded
                        if (expanded) listener?.onAmiiboRebind(itemView, amiiboFile)
                        var relativeFile = Storage.getRelativePath(it, mPrefs.preferEmulated())
                        mPrefs.browserRootFolder()?.let { path ->
                            relativeFile = relativeFile.replace(path, "")
                        }
                        itemView.isEnabled = true
                        text = boldSpannable.indexOf(relativeFile, query)
                        val a = TypedValue()
                        context.theme.resolveAttribute(
                            android.R.attr.textColor, a, true
                        )
                        if (Version.isQuinceTart && a.isColorType) {
                            setTextColor(a.data)
                        } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT
                        ) {
                            setTextColor(a.data)
                        }
                        setIsHighlighted(relativeFile.startsWith("/Foomiibo/"))
                    } ?: {
                        itemView.isEnabled = false
                        text = ""
                        setTextColor(ContextCompat.getColor(context, R.color.tag_text))
                    }
                    isGone = false
                }
            }
            if (hasSpoofData(amiiboHexId)) txtTagId?.isEnabled = false
        }

        private fun setIsHighlighted(isHighlighted: Boolean) {
            val highlight = itemView.findViewById<View>(R.id.highlight)
            if (isHighlighted) {
                highlight.setBackgroundResource(R.drawable.rounded_view)
            } else {
                highlight.setBackgroundResource(0)
            }
        }

        fun setAmiiboInfoText(textView: TextView?, text: CharSequence?, isTagInfo: Boolean) {
            textView?.run {
                isGone = isTagInfo
                if (!isTagInfo) {
                    if (text.isNullOrEmpty()) {
                        setText(R.string.unknown)
                        isEnabled = false
                    } else {
                        this.text = text
                        isEnabled = true
                    }
                }
            }
        }
    }

    internal class SimpleViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_simple_card, parent, false
        ),
        settings, listener
    )

    internal class CompactViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_compact_card, parent, false
        ),
        settings, listener
    )

    internal class LargeViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_large_card, parent, false
        ),
        settings, listener
    )

    internal class ImageViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_image_card, parent, false
        ),
        settings, listener
    )

    interface OnAmiiboClickListener {
        fun onAmiiboClicked(itemView: View, amiiboFile: AmiiboFile?)
        fun onAmiiboRebind(itemView: View, amiiboFile: AmiiboFile?)
        fun onAmiiboImageClicked(amiiboFile: AmiiboFile?)
    }

    companion object {
        var mPrefs = Preferences(TagMo.appContext)
        private val amiiboPath: ArrayList<String?> = arrayListOf()
        fun resetVisible() {
            amiiboPath.clear()
        }
    }
}