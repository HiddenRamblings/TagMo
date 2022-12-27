package com.hiddenramblings.tagmo.browser.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.SectionIndexer
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboComparator
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.*
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter.FoomiiboViewHolder
import com.hiddenramblings.tagmo.widget.BoldSpannable
import java.util.*

class FoomiiboAdapter(
    private val settings: BrowserSettings,
    private val listener: OnFoomiiboClickListener
) : RecyclerView.Adapter<FoomiiboViewHolder>(), Filterable, BrowserSettingsListener,
    SectionIndexer {
    private var data = ArrayList<Amiibo>()
    private var filteredData: ArrayList<Amiibo>?
    private var filter: FoomiiboFilter? = null
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
                BrowserSettings.hasFilterChanged(newBrowserSettings, oldBrowserSettings)
        if (!BrowserSettings.equals(
                newBrowserSettings.amiiboFiles,
                oldBrowserSettings.amiiboFiles
            )
        ) {
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

    private fun getItem(i: Int): Amiibo? {
        return filteredData?.get(i)
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
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
            var i = 0
            val size = filteredData!!.size
            while (i < size) {
                val amiibo = filteredData!![i]
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
                if (null != heading && heading.isNotEmpty()) {
                    section = heading[0].toString().uppercase(Locale.getDefault())
                }
                if (null != section && !sections.contains(section)) {
                    sections.add(section)
                    mSectionPositions!!.add(i)
                }
                i++
            }
        }
        return sections.toTypedArray()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        return mSectionPositions!![sectionIndex]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoomiiboViewHolder {
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

    private fun handleClickEvent(holder: FoomiiboViewHolder) {
        if (null != holder.listener) {
            if (settings.amiiboView != VIEW.IMAGE.value) {
                if (foomiiboId.contains(holder.foomiibo?.id)) {
                    foomiiboId.remove(holder.foomiibo?.id)
                } else {
                    holder.foomiibo?.let { foomiiboId.add(it.id) }
                }
            } else {
                foomiiboId.clear()
            }
            holder.listener.onFoomiiboClicked(holder.itemView, holder.foomiibo)
        }
    }

    override fun onBindViewHolder(holder: FoomiiboViewHolder, position: Int) {
        val clickPosition = if (hasStableIds()) holder.bindingAdapterPosition else position
        holder.itemView.setOnClickListener { handleClickEvent(holder) }
        holder.imageAmiibo?.setOnClickListener {
            if (settings.amiiboView == VIEW.IMAGE.value) handleClickEvent(
                holder
            ) else if (null != holder.listener)
                holder.listener.onFoomiiboImageClicked(holder.foomiibo)
        }
        holder.bind(getItem(clickPosition))
    }

    fun refresh() {
        getFilter().filter(settings.query)
    }

    override fun getFilter(): FoomiiboFilter {
        if (null == filter) {
            filter = FoomiiboFilter()
        }
        return filter!!
    }

    inner class FoomiiboFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString() ?: ""
            val filterResults = FilterResults()
            if (TextUtils.isEmpty(query.trim { it <= ' ' })) {
                filterResults.count = data.size
                filterResults.values = data
            }
            settings.query = query
            if (null != settings.amiiboManager)
                data = ArrayList(settings.amiiboManager?.amiibos!!.values)
            else
                data.clear()
            val tempList = ArrayList<Amiibo>()
            val queryText = query.trim { it <= ' ' }.lowercase(Locale.getDefault())
            data.forEach {
                if (settings.amiiboContainsQuery(it, queryText)) tempList.add(it)
            }
            filterResults.count = tempList.size
            filterResults.values = tempList
            return filterResults
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            if (null != filteredData && filteredData === filterResults.values) return
            filteredData = filterResults.values as ArrayList<Amiibo>
            if (itemCount > 0) {
                Collections.sort(filteredData, AmiiboComparator(settings))
                val missingFiles = ArrayList<Amiibo>()
                val amiiboIds = HashSet<Long>()
                settings.amiiboFiles.forEach {
                    amiiboIds.add(it!!.id)
                }
                val iterator = filteredData!!.iterator()
                while (iterator.hasNext()) {
                    val amiibo = iterator.next()
                    if (!amiiboIds.contains(amiibo.id)) {
                        iterator.remove()
                        missingFiles.add(amiibo)
                    }
                }
                if (missingFiles.isNotEmpty()) {
                    Collections.sort(missingFiles, AmiiboComparator(settings))
                    filteredData!!.addAll(0, missingFiles)
                }
            }
            notifyDataSetChanged()
        }
    }

    abstract class FoomiiboViewHolder(
        itemView: View, private val settings: BrowserSettings,
        val listener: OnFoomiiboClickListener?
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
        var foomiibo: Amiibo? = null
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

        fun bind(item: Amiibo?) {
            foomiibo = item
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
            setFoomiiboInfoText(txtName, amiiboName, false)
            if (settings.amiiboView != VIEW.IMAGE.value) {
                val hasTagInfo = null != tagInfo
                if (hasTagInfo) {
                    setFoomiiboInfoText(txtError, tagInfo, false)
                } else {
                    txtError?.visibility = View.GONE
                }
                setFoomiiboInfoText(
                    txtTagId,
                    boldSpannable.startsWith(amiiboHexId, query),
                    hasTagInfo
                )
                setFoomiiboInfoText(
                    txtAmiiboSeries,
                    boldSpannable.indexOf(amiiboSeries, query), hasTagInfo
                )
                setFoomiiboInfoText(
                    txtAmiiboType,
                    boldSpannable.indexOf(amiiboType, query), hasTagInfo
                )
                setFoomiiboInfoText(
                    txtGameSeries,
                    boldSpannable.indexOf(gameSeries, query), hasTagInfo
                )
                // setAmiiboInfoText(this.txtCharacter,
                // boldText.Matching(character, query), hasTagInfo);
                txtPath?.visibility = View.GONE
                val expanded = foomiiboId.contains(foomiibo?.id)
                itemView.findViewById<View>(R.id.menu_options).visibility =
                    if (expanded) View.VISIBLE else View.GONE
                itemView.findViewById<View>(R.id.txtUsage).visibility =
                    if (expanded) View.VISIBLE else View.GONE
                if (expanded) listener!!.onFoomiiboRebind(itemView, foomiibo)
            }
            if (AmiiboManager.hasSpoofData(amiiboHexId) && null != txtTagId) txtTagId.isEnabled =
                false
            if (null != imageAmiibo) {
                GlideApp.with(imageAmiibo!!).clear(imageAmiibo!!)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(imageAmiibo!!).asBitmap().load(amiiboImageUrl).into(target)
                }
            }
        }

        private fun setFoomiiboInfoText(textView: TextView?, text: CharSequence?, hasTagInfo: Boolean) {
            if (hasTagInfo) {
                textView?.visibility = View.GONE
            } else {
                textView?.visibility = View.VISIBLE
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
        listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_simple_card, parent, false
        ),
        settings, listener
    )

    internal class CompactViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_compact_card, parent, false
        ),
        settings, listener
    )

    internal class LargeViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_large_card, parent, false
        ),
        settings, listener
    )

    internal class ImageViewHolder(
        parent: ViewGroup, settings: BrowserSettings,
        listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_image_card, parent, false
        ),
        settings, listener
    )

    interface OnFoomiiboClickListener {
        fun onFoomiiboClicked(itemView: View?, amiibo: Amiibo?)
        fun onFoomiiboRebind(itemView: View?, amiibo: Amiibo?)
        fun onFoomiiboImageClicked(amiibo: Amiibo?)
    }

    companion object {
        private val foomiiboId = ArrayList<Long>()
        fun resetVisible() {
            foomiiboId.clear()
        }
    }
}