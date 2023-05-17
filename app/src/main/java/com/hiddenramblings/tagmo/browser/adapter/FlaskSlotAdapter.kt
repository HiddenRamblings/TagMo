package com.hiddenramblings.tagmo.browser.adapter

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
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
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.FlaskTag
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW
import com.hiddenramblings.tagmo.browser.adapter.FlaskSlotAdapter.FlaskViewHolder

class FlaskSlotAdapter(
    private val settings: BrowserSettings, private val listener: OnAmiiboClickListener
) : RecyclerView.Adapter<FlaskViewHolder>(), BrowserSettingsListener {
    var mPrefs = Preferences(TagMo.appContext)
    private var flaskAmiibo: ArrayList<Amiibo?> = arrayListOf()
    fun setFlaskAmiibo(amiibo: ArrayList<Amiibo?>) {
        flaskAmiibo = amiibo
    }

    fun addFlaskAmiibo(amiibo: ArrayList<Amiibo?>) {
        flaskAmiibo.addAll(amiibo)
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
    }

    override fun getItemCount(): Int {
        return flaskAmiibo.size
    }

    override fun getItemId(i: Int): Long {
        return (flaskAmiibo[i]?.flaskTail ?: "").toLong()
    }

    fun getItem(i: Int): Amiibo? {
        return flaskAmiibo[i]
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    fun getDuplicates(amiibo: Amiibo) : Int {
        return flaskAmiibo.count { it?.id == amiibo.id }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlaskViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(parent, settings, listener)
            VIEW.LARGE -> LargeViewHolder(parent, settings, listener)
            VIEW.IMAGE -> ImageViewHolder(parent, settings, listener)
            VIEW.SIMPLE -> SimpleViewHolder(parent, settings, listener)
        }
    }

    private fun setIsHighlighted(holder: FlaskViewHolder, position: Int) {
        val highlight = holder.itemView.findViewById<View>(R.id.highlight)
        if (mPrefs.flaskActiveSlot() == position) {
            highlight.setBackgroundResource(R.drawable.cardview_outline)
        } else {
            highlight.setBackgroundResource(0)
        }
    }

    override fun onBindViewHolder(holder: FlaskViewHolder, position: Int) {
//        setIsHighlighted(holder, position)
        holder.itemView.setOnClickListener {
            holder.listener?.onAmiiboClicked(holder.amiibo, holder.bindingAdapterPosition)
        }
        holder.imageAmiibo?.setOnClickListener {
            if (settings.amiiboView == VIEW.IMAGE.value)
                holder.listener?.onAmiiboClicked(holder.amiibo, holder.bindingAdapterPosition)
            else holder.listener?.onAmiiboImageClicked(holder.amiibo)
        }
        holder.bind(getItem(position))
    }

    abstract class FlaskViewHolder(
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

        var target: CustomTarget<Bitmap?> = object : CustomTarget<Bitmap?>() {
            override fun onLoadFailed(errorDrawable: Drawable?) {
                imageAmiibo?.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                imageAmiibo?.setImageResource(R.drawable.ic_no_image_60)
            }

            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                imageAmiibo?.setImageBitmap(resource)
            }
        }

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
                is FlaskTag -> {
                    setAmiiboInfoText(txtName, TagMo.appContext.getString(R.string.blank_tag))
                }
                else -> {
                    setAmiiboInfoText(txtName, amiibo?.name)
                    amiiboImageUrl = amiibo?.imageUrl
                }
            }
            imageAmiibo?.let {
                GlideApp.with(it).clear(it)
                if (amiiboImageUrl.isNullOrEmpty()) {
                    it.setImageResource(R.drawable.ic_no_image_60)
                } else {
                    GlideApp.with(it).asBitmap().load(amiiboImageUrl).into(target)
                }
            }
            if (settings.amiiboView != VIEW.IMAGE.value) {
                txtError?.isGone = true
                if (amiibo is FlaskTag) {
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
    ) : FlaskViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_simple_card, parent, false
        ),
        settings, listener
    )

    internal class CompactViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : FlaskViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_compact_card, parent, false
        ),
        settings, listener
    )

    internal class LargeViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : FlaskViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_large_card, parent, false
        ),
        settings, listener
    )

    internal class ImageViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : FlaskViewHolder(
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