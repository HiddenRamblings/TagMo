package com.hiddenramblings.tagmo.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.hiddenramblings.tagmo.*
import com.hiddenramblings.tagmo.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.BrowserSettings.VIEW
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboFileComparator
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget
import com.hiddenramblings.tagmo.widget.BoldSpannable
import java.util.*

class WriteTagAdapter(private val settings: BrowserSettings?) :
    RecyclerView.Adapter<WriteTagAdapter.AmiiboViewHolder>(), Filterable, BrowserSettingsListener {
    private var listener: OnAmiiboClickListener? = null
    private var listSize = 1
    private var isFillEnabled = false
    private var amiiboFiles: ArrayList<AmiiboFile?> = arrayListOf()
    private var filteredData: ArrayList<AmiiboFile?> = arrayListOf()
    private var filter: AmiiboFilter? = null
    private var firstRun = true
    private val amiiboList: ArrayList<AmiiboFile?> = arrayListOf()

    init {
        filteredData = amiiboFiles
        setHasStableIds(true)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setListener(listener: OnAmiiboClickListener?, listSize: Int, isFillEnabled: Boolean) {
        amiiboList.clear()
        notifyDataSetChanged()
        this.listener = listener
        this.listSize = listSize
        this.isFillEnabled = isFillEnabled
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setListener(listener: OnAmiiboClickListener?) {
        setListener(listener, 1, false)
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
        var refresh = firstRun ||
                !BrowserSettings.equals(
                    newBrowserSettings?.query,
                    oldBrowserSettings?.query
                ) ||
                !BrowserSettings.equals(
                    newBrowserSettings?.sort,
                    oldBrowserSettings?.sort
                ) ||
                BrowserSettings.hasFilterChanged(oldBrowserSettings, newBrowserSettings)
        if (firstRun || !BrowserSettings.equals(
                newBrowserSettings?.amiiboFiles,
                oldBrowserSettings?.amiiboFiles
            )
        ) {
            amiiboFiles = arrayListOf()
            newBrowserSettings?.amiiboFiles?.let { amiiboFiles.addAll(it) }
            refresh = true
        }
        if (!BrowserSettings.equals(
                newBrowserSettings?.amiiboManager,
                oldBrowserSettings?.amiiboManager
            )
        ) {
            refresh = true
        }
        if (!BrowserSettings.equals(
                newBrowserSettings?.amiiboView,
                oldBrowserSettings?.amiiboView
            )
        ) {
            refresh = true
        }
        if (refresh) {
            refresh()
        }
    }

    override fun getItemCount(): Int {
        return filteredData.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getItem(i: Int): AmiiboFile? {
        return filteredData[i]
    }

    override fun getItemViewType(position: Int): Int {
        return settings?.amiiboView ?: 0
    }

    private fun handleClickEvent(holder: AmiiboViewHolder, position: Int) {
        if (listSize > 1) {
            if (isFillEnabled) {
                listener?.onAmiiboDataClicked(filteredData[position], listSize)
            } else {
                if (amiiboList.contains(holder.amiiboFile)) {
                    amiiboList.remove(filteredData[position])
                    setIsHighlighted(holder, false)
                } else {
                    amiiboList.add(filteredData[position])
                    setIsHighlighted(holder, true)
                }
                if (amiiboList.size == listSize) listener?.onAmiiboListClicked(amiiboList)
            }
        } else {
            listener?.onAmiiboClicked(holder.amiiboFile)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmiiboViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(parent, settings)
            VIEW.LARGE -> LargeViewHolder(parent, settings)
            VIEW.IMAGE -> ImageViewHolder(parent, settings)
            VIEW.SIMPLE -> SimpleViewHolder(parent, settings)
        }.apply {
            itemView.setOnClickListener {
                handleClickEvent(this, bindingAdapterPosition)
            }
            imageAmiibo?.setOnClickListener {
                if (settings?.amiiboView == VIEW.IMAGE.value)
                    handleClickEvent(this, bindingAdapterPosition)
                else
                    listener?.onAmiiboImageClicked(amiiboFile)
            }
        }
    }

    private fun setIsHighlighted(holder: AmiiboViewHolder, isHighlighted: Boolean) {
        val highlight = holder.itemView.findViewById<View>(R.id.highlight)
        if (isHighlighted) {
            highlight.setBackgroundResource(R.drawable.rounded_neon)
        } else {
            highlight.setBackgroundResource(0)
        }
    }

    override fun onBindViewHolder(holder: AmiiboViewHolder, position: Int) {
        holder.bind(getItem(holder.bindingAdapterPosition))
        setIsHighlighted(holder, amiiboList.contains(holder.amiiboFile))
    }

    fun refresh() { getFilter().filter(settings?.query) }

    override fun getFilter(): AmiiboFilter {
        if (null == filter) {
            filter = AmiiboFilter()
        }
        return filter!!
    }

    inner class AmiiboFilter : Filter() {
        override fun performFiltering(constraint: CharSequence): FilterResults {
            val filterResults = FilterResults()
            val tempList: ArrayList<AmiiboFile> = arrayListOf()
            val queryText = settings?.query!!.trim { it <= ' ' }.lowercase(Locale.getDefault())
            val amiiboManager = settings.amiiboManager
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
            return (!query.isNullOrEmpty() && settings!!.isFilterEmpty
                    && path.lowercase(Locale.getDefault()).contains(query))
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            if (filteredData === filterResults.values) return
            filteredData = filterResults.values as ArrayList<AmiiboFile?>
            settings?.let { Collections.sort(filteredData, AmiiboFileComparator(it)) }
            notifyDataSetChanged()
        }
    }

    abstract class AmiiboViewHolder(
        itemView: View, private val settings: BrowserSettings?
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
            settings?.amiiboManager?.let {
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
                tagInfo = "ID: " + Amiibo.idToHex(it)
                amiiboImageUrl = Amiibo.getImageUrl(it)
            }
            imageAmiibo?.let {
                val imageTarget: CustomTarget<Bitmap?> = ImageTarget.getTarget(it)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(it).clear(it)
                    GlideApp.with(it).asBitmap().load(amiiboImageUrl).into(imageTarget)
                }
            }
            val query = settings?.query?.lowercase(Locale.getDefault())
            setAmiiboInfoText(txtName, amiiboName, false)
            if (settings?.amiiboView != VIEW.IMAGE.value) {
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
                        itemView.isEnabled = true
                        val relativeDocument = Storage.getRelativeDocument(it.uri)
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
                    } ?: item?.filePath?.let {
                        itemView.isEnabled = true
                        var relativeFile = Storage.getRelativePath(it, mPrefs.preferEmulated())
                        mPrefs.browserRootFolder()?.let { path ->
                            relativeFile = relativeFile.replace(path, "")
                        }

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
                    } ?: {
                        itemView.isEnabled = false
                        text = ""
                        setTextColor(ContextCompat.getColor(context, R.color.tag_text))
                    }
                    isGone = false
                }
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

    internal class SimpleViewHolder(parent: ViewGroup, settings: BrowserSettings?) :
        AmiiboViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.amiibo_simple_card, parent, false
            ),
            settings
        )

    internal class CompactViewHolder(parent: ViewGroup, settings: BrowserSettings?) :
        AmiiboViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.amiibo_compact_card, parent, false
            ),
            settings
        )

    internal class LargeViewHolder(parent: ViewGroup, settings: BrowserSettings?) :
        AmiiboViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.amiibo_large_card, parent, false
            ),
            settings
        )

    internal class ImageViewHolder(parent: ViewGroup, settings: BrowserSettings?) :
        AmiiboViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.amiibo_image_card, parent, false
            ),
            settings
        )

    interface OnAmiiboClickListener {
        fun onAmiiboClicked(amiiboFile: AmiiboFile?)
        fun onAmiiboImageClicked(amiiboFile: AmiiboFile?)
        fun onAmiiboListClicked(amiiboList: ArrayList<AmiiboFile?>?)
        fun onAmiiboDataClicked(amiiboFile: AmiiboFile?, count: Int)
    }

    companion object {
        var mPrefs = Preferences(TagMo.appContext)
    }
}