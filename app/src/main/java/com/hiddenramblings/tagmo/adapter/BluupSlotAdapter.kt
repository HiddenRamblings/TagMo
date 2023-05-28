package com.hiddenramblings.tagmo.adapter

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.hiddenramblings.tagmo.BrowserSettings
import com.hiddenramblings.tagmo.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.BrowserSettings.VIEW
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.adapter.BluupSlotAdapter.BluupViewHolder
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.BluupTag
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget

class BluupSlotAdapter(
    private val settings: BrowserSettings, private val listener: OnAmiiboClickListener
) : RecyclerView.Adapter<BluupViewHolder>(), BrowserSettingsListener {

    private var bluupAmiibo: ArrayList<Amiibo?> = arrayListOf()
    fun setBluupAmiibo(amiibo: ArrayList<Amiibo?>) {
        bluupAmiibo = amiibo
    }

    fun addBluupAmiibo(amiibo: ArrayList<Amiibo?>) {
        bluupAmiibo.addAll(amiibo)
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) { }

    override fun getItemCount(): Int {
        return bluupAmiibo.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun getItem(i: Int): Amiibo? {
        return bluupAmiibo[i]
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    fun getDuplicates(amiibo: Amiibo) : Int {
        return bluupAmiibo.count { it?.id == amiibo.id }
    }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BluupViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(parent, settings, listener)
            VIEW.LARGE -> LargeViewHolder(parent, settings, listener)
            VIEW.IMAGE -> ImageViewHolder(parent, settings, listener)
            VIEW.SIMPLE -> SimpleViewHolder(parent, settings, listener)
        }.apply {
            itemView.setOnClickListener {
                listener?.onAmiiboClicked(amiibo, bindingAdapterPosition)
            }
            imageAmiibo?.setOnClickListener {
                if (settings.amiiboView == VIEW.IMAGE.value)
                    listener?.onAmiiboClicked(amiibo, bindingAdapterPosition)
                else listener?.onAmiiboImageClicked(amiibo)
            }
        }
    }

    override fun onBindViewHolder(holder: BluupViewHolder, position: Int) {
        holder.bind(getItem(holder.bindingAdapterPosition))
    }

    abstract class BluupViewHolder(
        itemView: View, private val settings: BrowserSettings, val listener: OnAmiiboClickListener?
    ) : RecyclerView.ViewHolder(itemView) {
        val txtError: TextView?
        val txtName: TextView?
        val txtTagId: TextView?
        val txtAmiiboSeries: TextView?
        val txtAmiiboType: TextView?
        val txtGameSeries: TextView?
        val txtPath: TextView?
        val txtUsage: TextView?
        var imageAmiibo: AppCompatImageView? = null
        var amiibo: Amiibo? = null

        init {
            txtError = itemView.findViewById(R.id.txtError)
            txtName = itemView.findViewById(R.id.txtName)
            txtTagId = itemView.findViewById(R.id.txtTagId)
            txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries)
            txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType)
            txtGameSeries = itemView.findViewById(R.id.txtGameSeries)
            txtPath = itemView.findViewById(R.id.txtPath)
            txtUsage = itemView.findViewById(R.id.txtUsage)
            imageAmiibo = itemView.findViewById(R.id.imageAmiibo)
        }

        fun bind(item: Amiibo?) {
            amiibo = item
            txtPath?.isGone = true
            txtUsage?.isGone = true
            var amiiboHexId = ""
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            var amiiboImageUrl: String? = null
            when (amiibo) {
                null -> {
                    setAmiiboInfoText(txtName, TagMo.appContext.getString(R.string.empty_tag))
                    txtError?.isGone = true
                    txtTagId?.isGone = true
                    txtAmiiboSeries?.isGone = true
                    txtAmiiboType?.isGone = true
                    txtGameSeries?.isGone = true
                    return
                }
                is BluupTag -> {
                    setAmiiboInfoText(txtName, TagMo.appContext.getString(R.string.blank_tag))
                }
                else -> {
                    setAmiiboInfoText(txtName, amiibo?.name)
                    amiiboImageUrl = amiibo?.imageUrl
                }
            }
            imageAmiibo?.let {
                val imageTarget: CustomTarget<Bitmap?> = ImageTarget.getTarget(it)
                GlideApp.with(it).clear(it)
                if (amiiboImageUrl.isNullOrEmpty()) {
                    it.setImageResource(R.drawable.ic_no_image_60)
                } else {
                    GlideApp.with(it).asBitmap().load(amiiboImageUrl).into(imageTarget)
                }
            }
            if (settings.amiiboView != VIEW.IMAGE.value) {
                txtError?.isGone = true
                if (amiibo is BluupTag) {
                    txtTagId?.isGone = true
                    txtAmiiboSeries?.isGone = true
                    txtAmiiboType?.isGone = true
                    txtGameSeries?.isGone = true
                } else {
                    amiibo?.let {
                        amiiboHexId = Amiibo.idToHex(it.id)
                        it.amiiboSeries?.let { series -> amiiboSeries = series.name }
                        it.amiiboType?.let { type -> amiiboType = type.name }
                        it.gameSeries?.let { series -> gameSeries = series.name }
                    }
                    txtTagId?.isVisible = true
                    txtAmiiboSeries?.isVisible = true
                    txtAmiiboType?.isVisible = true
                    txtGameSeries?.isVisible = true
                    setAmiiboInfoText(txtTagId, amiiboHexId)
                    setAmiiboInfoText(txtAmiiboSeries, amiiboSeries)
                    setAmiiboInfoText(txtAmiiboType, amiiboType)
                    setAmiiboInfoText(txtGameSeries, gameSeries)
                    if (hasSpoofData(amiiboHexId)) txtTagId?.isEnabled = false
                }
            }
        }

        fun setAmiiboInfoText(textView: TextView?, text: CharSequence?) {
            textView?.isVisible = true
             if (!text.isNullOrEmpty()) {
                textView?.text = text
                textView?.isEnabled = true
            } else {
                textView?.setText(R.string.unknown)
                textView?.isEnabled = false
            }
        }
    }

    internal class SimpleViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : BluupViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_simple_card, parent, false
        ),
        settings, listener
    )

    internal class CompactViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : BluupViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_compact_card, parent, false
        ),
        settings, listener
    )

    internal class LargeViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : BluupViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_large_card, parent, false
        ),
        settings, listener
    )

    internal class ImageViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : BluupViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_image_card, parent, false
        ),
        settings, listener
    )

    interface OnAmiiboClickListener {
        fun onAmiiboClicked(amiibo: Amiibo?, position: Int)
        fun onAmiiboImageClicked(amiibo: Amiibo?)
    }
}