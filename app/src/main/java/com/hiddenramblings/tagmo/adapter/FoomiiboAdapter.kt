package com.hiddenramblings.tagmo.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.hiddenramblings.tagmo.BrowserSettings
import com.hiddenramblings.tagmo.BrowserSettings.*
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboComparator
import com.hiddenramblings.tagmo.amiibo.AmiiboManager
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget
import com.hiddenramblings.tagmo.widget.BoldSpannable
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.util.*

class FoomiiboAdapter(
    private val settings: BrowserSettings, private val listener: OnFoomiiboClickListener
) : RecyclerView.Adapter<FoomiiboAdapter.FoomiiboViewHolder>(),
    PopupTextProvider, Filterable, BrowserSettingsListener {
    private var data: ArrayList<Amiibo> = arrayListOf()
    private var filteredData: ArrayList<Amiibo> = arrayListOf()
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

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getItem(i: Int): Amiibo {
        return filteredData[i]
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    init {
        filteredData = data
        setHasStableIds(true)
    }

    private fun handleClickEvent(holder: FoomiiboViewHolder) {
        holder.listener?.run {
            if (settings.amiiboView != VIEW.IMAGE.value) {
                if (foomiiboId.contains(holder.foomiibo?.id)) {
                    foomiiboId.remove(holder.foomiibo?.id)
                } else {
                    holder.foomiibo?.let { foomiiboId.add(it.id) }
                }
            } else {
                foomiiboId.clear()
            }
            onFoomiiboClicked(holder.itemView, holder.foomiibo)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoomiiboViewHolder {
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
                    listener?.onFoomiiboImageClicked(foomiibo)
            }
        }
    }

    override fun onBindViewHolder(holder: FoomiiboViewHolder, position: Int) {
        holder.bind(getItem(holder.bindingAdapterPosition))
    }

    override fun getPopupText(position: Int) : CharSequence {
        if (position >= filteredData.size) return "?"
        val item = filteredData[position]
        return when (SORT.valueOf(settings.sort)) {
            SORT.NAME -> item.name ?: "?"
            SORT.CHARACTER -> item.character?.name ?: "?"
            SORT.GAME_SERIES -> item.gameSeries?.name ?: "?"
            SORT.AMIIBO_SERIES -> item.amiiboSeries?.name ?: "?"
            SORT.AMIIBO_TYPE -> item.amiiboType?.name ?: "?"
            else -> { "?" }
        }[0].uppercase()
    }

    fun refresh() { getFilter().filter(settings.query) }

    override fun getFilter(): FoomiiboFilter {
        if (null == filter) filter = FoomiiboFilter()
        return filter as FoomiiboFilter
    }

    inner class FoomiiboFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString() ?: ""
            val filterResults = FilterResults()
            if (query.trim { it <= ' ' }.isEmpty()) {
                filterResults.count = data.size
                filterResults.values = data
            }
            settings.query = query
            settings.amiiboManager?.let {
                data = ArrayList(it.amiibos.values)
            } ?: data.clear()
            val tempList: ArrayList<Amiibo> = arrayListOf()
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
            if (filteredData === filterResults.values) return
            filterResults.values.let { filteredData = it as ArrayList<Amiibo> }
            if (itemCount > 0) {
                Collections.sort(filteredData, AmiiboComparator(settings))
                val missingFiles: ArrayList<Amiibo> = arrayListOf()
                val amiiboIds: HashSet<Long> = hashSetOf()
                settings.amiiboFiles.forEach { amiiboFile ->
                    amiiboFile?.let { amiiboIds.add(it.id) }
                }
                val iterator = filteredData.iterator()
                while (iterator.hasNext()) {
                    val amiibo = iterator.next()
                    if (!amiiboIds.contains(amiibo.id)) {
                        iterator.remove()
                        missingFiles.add(amiibo)
                    }
                }
                if (missingFiles.isNotEmpty()) {
                    Collections.sort(missingFiles, AmiiboComparator(settings))
                    filteredData.addAll(0, missingFiles)
                }
            }
            notifyDataSetChanged()
        }
    }

    abstract class FoomiiboViewHolder(
        itemView: View, private val settings: BrowserSettings, val listener: OnFoomiiboClickListener?
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

        var foomiibo: Amiibo? = null
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
            setFoomiiboInfoText(txtName, amiiboName, false)
            if (settings.amiiboView != VIEW.IMAGE.value) {
                val hasTagInfo = null != tagInfo
                if (hasTagInfo) {
                    setFoomiiboInfoText(txtError, tagInfo, false)
                } else {
                    txtError?.isGone = true
                }
                setFoomiiboInfoText(
                    txtTagId, boldSpannable.startsWith(amiiboHexId, query), hasTagInfo
                )
                setFoomiiboInfoText(
                    txtAmiiboSeries, boldSpannable.indexOf(amiiboSeries, query), hasTagInfo
                )
                setFoomiiboInfoText(
                    txtAmiiboType, boldSpannable.indexOf(amiiboType, query), hasTagInfo
                )
                setFoomiiboInfoText(
                    txtGameSeries, boldSpannable.indexOf(gameSeries, query), hasTagInfo
                )
                // setAmiiboInfoText(this.txtCharacter,
                // boldText.Matching(character, query), hasTagInfo);
                txtPath?.isGone = true
                val expanded = foomiiboId.contains(foomiibo?.id)
                menuOptions?.isVisible = expanded
                txtUsage?.isVisible = expanded
                if (expanded) listener?.onFoomiiboRebind(itemView, foomiibo)
            }
            if (AmiiboManager.hasSpoofData(amiiboHexId)) txtTagId?.isEnabled = false
        }

        private fun setFoomiiboInfoText(textView: TextView?, text: CharSequence?, hasTagInfo: Boolean) {
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
        parent: ViewGroup, settings: BrowserSettings, listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_simple_card, parent, false
        ),
        settings, listener
    )

    internal class CompactViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_compact_card, parent, false
        ),
        settings, listener
    )

    internal class LargeViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnFoomiiboClickListener?
    ) : FoomiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_large_card, parent, false
        ),
        settings, listener
    )

    internal class ImageViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnFoomiiboClickListener?
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
        private val foomiiboId: ArrayList<Long> = arrayListOf()
        fun resetVisible() {
            foomiiboId.clear()
        }
    }
}