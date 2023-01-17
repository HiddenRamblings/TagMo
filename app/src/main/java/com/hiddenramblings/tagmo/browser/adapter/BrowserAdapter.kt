package com.hiddenramblings.tagmo.browser.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboFile
import com.hiddenramblings.tagmo.amiibo.AmiiboFileComparator
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.hasSpoofData
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.*
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.widget.BoldSpannable
import java.util.*

class BrowserAdapter(
    private val settings: BrowserSettings,
    private val listener: OnAmiiboClickListener
) : RecyclerView.Adapter<BrowserAdapter.AmiiboViewHolder>(), Filterable, BrowserSettingsListener,
    SectionIndexer {
    private var data = ArrayList<AmiiboFile?>()
    private var filteredData: ArrayList<AmiiboFile?>?
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
            data = newBrowserSettings.amiiboFiles
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
        if (refresh) {
            refresh()
        }
        firstRun = false
    }

    override fun getItemCount(): Int {
        return filteredData?.size ?: 0
    }

    override fun getItemId(i: Int): Long {
        return (filteredData?.get(i)?.id ?: 0).toLong()
    }

    private fun getItem(i: Int): AmiiboFile? {
        return filteredData?.get(i)
    }

    private var mSectionPositions: ArrayList<Int>? = null

    init {
        filteredData = data
        setHasStableIds(true)
    }

    override fun getSectionForPosition(position: Int): Int {
        return 0
    }

    override fun getSections(): Array<String> {
        val sections: MutableList<String> = ArrayList(36)
        if (itemCount > 0) {
            mSectionPositions = ArrayList(36)
            val amiiboManager = settings.amiiboManager
            if (null != amiiboManager) {
                var i = 0
                val size = filteredData?.size ?: 0
                while (i < size) {
                    val amiiboId = filteredData?.get(i)?.id
                    var amiibo = amiiboManager.amiibos[amiiboId]
                    if (null == amiibo) amiibo = Amiibo(amiiboManager, amiiboId!!, null, null)
                    var heading: String? = null
                    var section: String? = null
                    when (SORT.valueOf(settings.sort)) {
                        SORT.NAME -> heading = amiibo.name
                        SORT.CHARACTER -> if (null != amiibo.character) {
                            heading = amiibo.character!!.name
                        }
                        SORT.GAME_SERIES -> if (null != amiibo.gameSeries) {
                            heading = amiibo.gameSeries!!.name
                        }
                        SORT.AMIIBO_SERIES -> if (null != amiibo.amiiboSeries) {
                            heading = amiibo.amiiboSeries!!.name
                        }
                        SORT.AMIIBO_TYPE -> if (null != amiibo.amiiboType) {
                            heading = amiibo.amiiboType!!.name
                        }
                        else -> {}
                    }
                    if (heading?.isNotEmpty() == true) {
                        section = heading[0].toString().uppercase(Locale.getDefault())
                    }
                    if (null != section && !sections.contains(section)) {
                        sections.add(section)
                        mSectionPositions!!.add(i)
                    }
                    i++
                }
            }
        }
        return sections.toTypedArray()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        return mSectionPositions!![sectionIndex]
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmiiboViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(
                parent,
                settings,
                listener
            )
            VIEW.LARGE -> LargeViewHolder(
                parent,
                settings,
                listener
            )
            VIEW.IMAGE -> ImageViewHolder(
                parent,
                settings,
                listener
            )
            VIEW.SIMPLE -> SimpleViewHolder(
                parent,
                settings,
                listener
            )
        }
    }

    private fun handleClickEvent(holder: AmiiboViewHolder) {
        if (null != holder.listener) {
            val uri = if (null != holder.amiiboFile?.docUri)
                holder.amiiboFile!!.docUri!!.uri.toString() else null
            val path = if (null != holder.amiiboFile?.filePath)
                holder.amiiboFile!!.filePath!!.absolutePath else null
            if (settings.amiiboView != VIEW.IMAGE.value) {
                if (amiiboPath.contains(uri) || amiiboPath.contains(path)) {
                    if (null != uri) amiiboPath.remove(uri)
                    if (null != path) amiiboPath.remove(path)
                } else {
                    if (null != uri) amiiboPath.add(uri)
                    if (null != path) amiiboPath.add(path)
                }
            } else {
                amiiboPath.clear()
            }
            holder.listener.onAmiiboClicked(holder.itemView, holder.amiiboFile)
        }
    }

    override fun onBindViewHolder(holder: AmiiboViewHolder, position: Int) {
        holder.itemView.setOnClickListener { handleClickEvent(holder) }
        holder.imageAmiibo?.setOnClickListener {
            if (settings.amiiboView == VIEW.IMAGE.value) {
                handleClickEvent(holder)
            } else if (null != holder.listener) {
                holder.listener.onAmiiboImageClicked(holder.amiiboFile)
            }
        }
        holder.bind(getItem(holder.bindingAdapterPosition))
    }

    fun refresh() {
        getFilter().filter(settings.query)
    }

    override fun getFilter(): AmiiboFilter {
        if (null == filter) {
            filter = AmiiboFilter()
        }
        return filter!!
    }

    inner class AmiiboFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString() ?: ""
            val filterResults = FilterResults()
            if (TextUtils.isEmpty(query.trim { it <= ' ' })) {
                filterResults.count = data.size
                filterResults.values = data
            }
            settings.query = query
            data = ArrayList(settings.amiiboFiles)
            val tempList = ArrayList<AmiiboFile>()
            val queryText = query.trim { it <= ' ' }.lowercase(Locale.getDefault())
            val amiiboManager = settings.amiiboManager
            val amiiboFiles = settings.amiiboFiles
            amiiboFiles.forEach {
                var add = false
                if (null != amiiboManager) {
                    var amiibo = amiiboManager.amiibos[it!!.id]
                    if (null == amiibo) amiibo = Amiibo(
                        amiiboManager, it.id, null, null
                    )
                    add = settings.amiiboContainsQuery(amiibo, queryText)
                }
                if (!add && null != it?.docUri)
                    add = pathContainsQuery(it.docUri.toString(), queryText)
                if (!add && null != it?.filePath)
                    add = pathContainsQuery(it.filePath!!.absolutePath, queryText)
                if (add) tempList.add(it!!)
            }
            filterResults.count = tempList.size
            filterResults.values = tempList
            return filterResults
        }

        private fun pathContainsQuery(path: String, query: String?): Boolean {
            return (!TextUtils.isEmpty(query) && settings.isFilterEmpty
                    && path.lowercase(Locale.getDefault()).contains(query!!))
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            if (null != filteredData && filteredData === filterResults.values) return
            filteredData = filterResults.values as ArrayList<AmiiboFile?>
            if (itemCount > 0)
                Collections.sort(filteredData, AmiiboFileComparator(settings))
            notifyDataSetChanged()
        }
    }

    abstract class AmiiboViewHolder(
        itemView: View,
        private val settings: BrowserSettings,
        val listener: OnAmiiboClickListener?
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
        var target: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
            override fun onLoadStarted(placeholder: Drawable?) {
                imageAmiibo?.setImageResource(0)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo?.visibility = View.INVISIBLE
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo?.visibility = View.VISIBLE
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo?.setImageBitmap(resource)
                imageAmiibo?.visibility = View.VISIBLE
            }
        }

        private fun setIsHighlighted(isHighlighted: Boolean) {
            val highlight = itemView.findViewById<View>(R.id.highlight)
            if (isHighlighted) {
                highlight.setBackgroundResource(R.drawable.rounded_view)
            } else {
                highlight.setBackgroundResource(0)
            }
        }

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
            val amiiboManager = settings.amiiboManager
            if (null != amiiboManager) {
                amiibo = amiiboManager.amiibos[amiiboId]
                if (null == amiibo && null != amiiboId)
                    amiibo = Amiibo(amiiboManager, amiiboId, null, null)
            }
            if (null != amiibo) {
                amiiboHexId = Amiibo.idToHex(amiibo.id)
                amiiboImageUrl = amiibo.imageUrl
                if (null != amiibo.name) amiiboName = amiibo.name!!
                if (null != amiibo.amiiboSeries) amiiboSeries = amiibo.amiiboSeries!!.name
                if (null != amiibo.amiiboType) amiiboType = amiibo.amiiboType!!.name
                if (null != amiibo.gameSeries) gameSeries = amiibo.gameSeries!!.name
            } else if (null != amiiboId) {
                amiiboHexId = Amiibo.idToHex(amiiboId)
                tagInfo = "ID: $amiiboHexId"
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId)
            }
            val query = settings.query?.lowercase(Locale.getDefault())
            setAmiiboInfoText(txtName, amiiboName, false)
            if (settings.amiiboView != VIEW.IMAGE.value) {
                val hasTagInfo = null != tagInfo
                if (hasTagInfo) {
                    setAmiiboInfoText(txtError, tagInfo, false)
                } else {
                    txtError?.isGone = true
                }
                setAmiiboInfoText(
                    txtTagId,
                    boldSpannable.startsWith(amiiboHexId, query),
                    hasTagInfo
                )
                setAmiiboInfoText(
                    txtAmiiboSeries,
                    boldSpannable.indexOf(amiiboSeries, query), hasTagInfo
                )
                setAmiiboInfoText(
                    txtAmiiboType,
                    boldSpannable.indexOf(amiiboType, query), hasTagInfo
                )
                setAmiiboInfoText(
                    txtGameSeries,
                    boldSpannable.indexOf(gameSeries, query), hasTagInfo
                )
                if (null != txtPath) {
                    if (null != item?.docUri) {
                        val relativeDocument = Storage.getRelativeDocument(item.docUri!!.uri)
                        val expanded = amiiboPath.contains(relativeDocument)
                        itemView.findViewById<View>(R.id.menu_options).visibility =
                            if (expanded) View.VISIBLE else View.GONE
                        itemView.findViewById<View>(R.id.txtUsage).visibility =
                            if (expanded) View.VISIBLE else View.GONE
                        if (expanded) listener?.onAmiiboRebind(itemView, amiiboFile)
                        itemView.isEnabled = true
                        txtPath.text = boldSpannable.indexOf(relativeDocument, query)
                        val a = TypedValue()
                        txtPath.context.theme.resolveAttribute(
                            android.R.attr.textColor, a, true
                        )
                        if (isNewer(Build.VERSION_CODES.Q) && a.isColorType) {
                            txtPath.setTextColor(a.data)
                        } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT
                        ) {
                            txtPath.setTextColor(a.data)
                        }
                        setIsHighlighted(relativeDocument.startsWith("/Foomiibo/"))
                    } else if (null != item?.filePath) {
                        val expanded = amiiboPath.contains(item.filePath!!.absolutePath)
                        itemView.findViewById<View>(R.id.menu_options).visibility =
                            if (expanded) View.VISIBLE else View.GONE
                        itemView.findViewById<View>(R.id.txtUsage).visibility =
                            if (expanded) View.VISIBLE else View.GONE
                        if (expanded) listener?.onAmiiboRebind(itemView, amiiboFile)
                        var relativeFile = Storage.getRelativePath(
                            item.filePath,
                            mPrefs.preferEmulated()
                        )
                        if (null != mPrefs.browserRootFolder()) {
                            relativeFile = relativeFile.replace(
                                mPrefs.browserRootFolder()!!, ""
                            )
                        }
                        itemView.isEnabled = true
                        txtPath.text = boldSpannable.indexOf(relativeFile, query)
                        val a = TypedValue()
                        txtPath.context.theme.resolveAttribute(
                            android.R.attr.textColor, a, true
                        )
                        if (isNewer(Build.VERSION_CODES.Q) && a.isColorType) {
                            txtPath.setTextColor(a.data)
                        } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT
                        ) {
                            txtPath.setTextColor(a.data)
                        }
                        setIsHighlighted(relativeFile.startsWith("/Foomiibo/"))
                    } else {
                        itemView.isEnabled = false
                        txtPath.text = ""
                        txtPath.setTextColor(
                            ContextCompat.getColor(txtPath.context, R.color.tag_text)
                        )
                    }
                    txtPath.isVisible = true
                }
            }
            if (hasSpoofData(amiiboHexId) && null != txtTagId) txtTagId.isEnabled = false
            if (null != imageAmiibo) {
                GlideApp.with(imageAmiibo!!).clear(imageAmiibo!!)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(imageAmiibo!!).asBitmap().load(amiiboImageUrl).into(target)
                }
            }
        }

        fun setAmiiboInfoText(textView: TextView?, text: CharSequence?, hasTagInfo: Boolean) {
            textView?.isGone = hasTagInfo
            if (!hasTagInfo) {
                 if (!text.isNullOrEmpty()) {
                    textView?.text = text
                    textView?.isEnabled = true
                } else {
                    textView?.setText(R.string.unknown)
                    textView?.isEnabled = false
                }
            }
        }
    }

    internal class SimpleViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnAmiiboClickListener?
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
        private val amiiboPath = ArrayList<String?>()
        fun resetVisible() {
            amiiboPath.clear()
        }
    }
}