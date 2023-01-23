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
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.widget.BoldSpannable
import java.util.*

class WriteTagAdapter(private val settings: BrowserSettings?) :
    RecyclerView.Adapter<WriteTagAdapter.AmiiboViewHolder>(), Filterable, BrowserSettingsListener {
    private var listener: OnAmiiboClickListener? = null
    private var listSize = 1
    private var amiiboFiles = ArrayList<AmiiboFile?>()
    private var filteredData: ArrayList<AmiiboFile?>?
    private var filter: AmiiboFilter? = null
    private var firstRun = true
    private val amiiboList = ArrayList<AmiiboFile?>()

    init {
        filteredData = amiiboFiles
        setHasStableIds(true)
    }

    fun setListener(listener: OnAmiiboClickListener?, listSize: Int) {
        amiiboList.clear()
        this.listener = listener
        this.listSize = listSize
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
            amiiboFiles = ArrayList()
            if (null != newBrowserSettings?.amiiboFiles)
                amiiboFiles.addAll(newBrowserSettings.amiiboFiles)
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
        return filteredData?.size ?: 0
    }

    override fun getItemId(i: Int): Long {
        return (filteredData?.get(i)?.id ?: 0).toLong()
    }

    private fun getItem(i: Int): AmiiboFile? {
        return filteredData?.get(i)
    }

    override fun getItemViewType(position: Int): Int {
        return settings?.amiiboView ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmiiboViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(parent, settings)
            VIEW.LARGE -> LargeViewHolder(parent, settings)
            VIEW.IMAGE -> ImageViewHolder(parent, settings)
            VIEW.SIMPLE -> SimpleViewHolder(parent, settings)
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

    private fun handleClickEvent(holder: AmiiboViewHolder, position: Int) {
        if (listSize > 1) {
            if (amiiboList.contains(holder.amiiboFile)) {
                amiiboList.remove(filteredData?.get(position))
                setIsHighlighted(holder, false)
            } else {
                amiiboList.add(filteredData?.get(position))
                setIsHighlighted(holder, true)
            }
            if (amiiboList.size == listSize) listener?.onAmiiboListClicked(amiiboList)
        } else {
            listener?.onAmiiboClicked(holder.amiiboFile)
        }
    }

    override fun onBindViewHolder(holder: AmiiboViewHolder, position: Int) {
        val clickPosition = if (hasStableIds()) holder.bindingAdapterPosition else position
        holder.itemView.setOnClickListener {
            handleClickEvent(holder, clickPosition)
        }
        holder.imageAmiibo?.setOnClickListener {
            if (settings!!.amiiboView == VIEW.IMAGE.value)
                handleClickEvent(holder, clickPosition)
            else if (null != listener)
                listener!!.onAmiiboImageClicked(holder.amiiboFile)
        }
        holder.bind(getItem(clickPosition)!!)
        setIsHighlighted(holder, amiiboList.contains(holder.amiiboFile))
    }

    fun refresh() {
        getFilter().filter(settings?.query)
    }

    override fun getFilter(): AmiiboFilter {
        if (null == filter) {
            filter = AmiiboFilter()
        }
        return filter!!
    }

    inner class AmiiboFilter : Filter() {
        override fun performFiltering(constraint: CharSequence): FilterResults {
            val filterResults = FilterResults()
            val tempList = ArrayList<AmiiboFile>()
            val queryText = settings?.query!!.trim { it <= ' ' }.lowercase(Locale.getDefault())
            val amiiboManager = settings.amiiboManager
            amiiboFiles.forEach {
                var add = false
                if (null != amiiboManager) {
                    var amiibo = amiiboManager.amiibos[it?.id]
                    if (null == amiibo) amiibo = Amiibo(amiiboManager, it!!.id, null, null)
                    add = settings.amiiboContainsQuery(amiibo, queryText)
                }
                if (!add && null != it?.docUri) add =
                    pathContainsQuery(it.docUri.toString(), queryText)
                if (!add && null != it?.filePath) add =
                    pathContainsQuery(it.filePath!!.absolutePath, queryText)
                if (add) tempList.add(it!!)
            }
            filterResults.count = tempList.size
            filterResults.values = tempList
            return filterResults
        }

        private fun pathContainsQuery(path: String, query: String?): Boolean {
            return (!TextUtils.isEmpty(query) && settings!!.isFilterEmpty
                    && path.lowercase(Locale.getDefault()).contains(query!!))
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            if (null != filteredData && filteredData === filterResults.values) return
            filteredData = filterResults.values as ArrayList<AmiiboFile?>
            if (null != filteredData && null != settings)
                Collections.sort(filteredData, AmiiboFileComparator(settings))
            notifyDataSetChanged()
        }
    }

    abstract class AmiiboViewHolder(
        itemView: View, private val settings: BrowserSettings?
    ) : RecyclerView.ViewHolder(itemView) {
        val txtError: TextView
        val txtName: TextView
        val txtTagId: TextView
        val txtAmiiboSeries: TextView
        val txtAmiiboType: TextView
        val txtGameSeries: TextView

        // public final TextView txtCharacter;
        val txtPath: TextView
        var imageAmiibo: AppCompatImageView? = null
        var amiiboFile: AmiiboFile? = null
        private val boldSpannable = BoldSpannable()
        private val target: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
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
            val amiiboManager = settings!!.amiiboManager
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
                tagInfo = "ID: " + Amiibo.idToHex(amiiboId)
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId)
            }
            val query = settings.query?.lowercase(Locale.getDefault())
            setAmiiboInfoText(txtName, amiiboName, false)
            if (settings.amiiboView != VIEW.IMAGE.value) {
                val hasTagInfo = null != tagInfo
                if (hasTagInfo) {
                    setAmiiboInfoText(txtError, tagInfo, false)
                } else {
                    txtError.isGone = true
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
                if (null != item?.docUri) {
                    itemView.isEnabled = true
                    val relativeDocument = Storage.getRelativeDocument(
                        item.docUri!!.uri
                    )
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
                } else if (null != item?.filePath) {
                    itemView.isEnabled = true
                    var relativeFile = Storage.getRelativePath(
                        item.filePath, mPrefs.preferEmulated()
                    )
                    if (null != mPrefs.browserRootFolder())
                        relativeFile = relativeFile.replace(mPrefs.browserRootFolder()!!, "")
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
                } else {
                    itemView.isEnabled = false
                    txtPath.text = ""
                    txtPath.setTextColor(ContextCompat.getColor(txtPath.context, R.color.tag_text))
                }
                txtPath.isVisible = true
            }
            if (null != imageAmiibo) {
                GlideApp.with(imageAmiibo!!).clear(imageAmiibo!!)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(imageAmiibo!!).asBitmap().load(amiiboImageUrl).into(target)
                }
            }
        }

        fun setAmiiboInfoText(textView: TextView, text: CharSequence?, hasTagInfo: Boolean) {
            textView.isGone = hasTagInfo
            if (!hasTagInfo) {
                 if (!text.isNullOrEmpty()) {
                    textView.text = text
                    textView.isEnabled = true
                } else {
                    textView.setText(R.string.unknown)
                    textView.isEnabled = false
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
    }

    companion object {
        var mPrefs = Preferences(TagMo.appContext)
    }
}