package com.hiddenramblings.tagmo.browser.adapter

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.hiddenramblings.tagmo.GlideApp
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.hasSpoofData
import com.hiddenramblings.tagmo.amiibo.FlaskTag
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW
import com.hiddenramblings.tagmo.browser.Preferences
import com.hiddenramblings.tagmo.browser.adapter.FlaskSlotAdapter.FlaskViewHolder

class FlaskSlotAdapter(
    private val settings: BrowserSettings,
    private val listener: OnAmiiboClickListener
) : RecyclerView.Adapter<FlaskViewHolder>(), BrowserSettingsListener {
    var mPrefs = Preferences(TagMo.appContext)
    private var flaskAmiibo: ArrayList<Amiibo?> = ArrayList()
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
        return flaskAmiibo[i]?.flaskTail!!.toLong()
    }

    fun getItem(i: Int): Amiibo? {
        return flaskAmiibo[i]
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlaskViewHolder {
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

    override fun onBindViewHolder(holder: FlaskViewHolder, position: Int) {
        val highlight = holder.itemView.findViewById<View>(R.id.highlight)
        if (mPrefs.flaskActiveSlot() == position) {
            highlight.setBackgroundResource(R.drawable.cardview_outline)
        } else {
            highlight.setBackgroundResource(0)
        }
        holder.itemView.setOnClickListener {
            if (null != holder.listener) {
                holder.listener.onAmiiboClicked(holder.amiibo, holder.bindingAdapterPosition)
            }
        }
        holder.imageAmiibo?.setOnClickListener {
            if (null != holder.listener) {
                if (settings.amiiboView == VIEW.IMAGE.value) holder.listener.onAmiiboClicked(
                    holder.amiibo, holder.bindingAdapterPosition
                ) else holder.listener.onAmiiboImageClicked(holder.amiibo)
            }
        }
        holder.bind(getItem(position))
    }

    abstract class FlaskViewHolder(
        itemView: View, private val settings: BrowserSettings,
        val listener: OnAmiiboClickListener?
    ) : RecyclerView.ViewHolder(itemView) {
        val txtError: TextView?
        val txtName: TextView?
        val txtTagId: TextView?
        val txtAmiiboSeries: TextView?
        val txtAmiiboType: TextView?
        val txtGameSeries: TextView?
        val txtPath: TextView?
        var imageAmiibo: AppCompatImageView? = null
        var amiibo: Amiibo? = null
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
            txtPath = itemView.findViewById(R.id.txtPath)
            imageAmiibo = itemView.findViewById(R.id.imageAmiibo)
        }

        fun bind(item: Amiibo?) {
            amiibo = item
            val amiiboHexId: String
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            var amiiboImageUrl: String? = null
            when (amiibo) {
                null -> {
                    setAmiiboInfoText(txtName, TagMo.appContext.getString(R.string.empty_tag))
                    txtError?.visibility = View.GONE
                    txtPath?.visibility = View.GONE
                    txtTagId?.visibility = View.GONE
                    txtAmiiboSeries?.visibility = View.GONE
                    txtAmiiboType?.visibility = View.GONE
                    txtGameSeries?.visibility = View.GONE
                    return
                }
                is FlaskTag -> {
                    setAmiiboInfoText(txtName, TagMo.appContext.getString(R.string.blank_tag))
                }
                else -> {
                    setAmiiboInfoText(txtName, amiibo!!.name)
                    amiiboImageUrl = amiibo!!.imageUrl
                }
            }
            if (settings.amiiboView != VIEW.IMAGE.value) {
                txtError?.visibility = View.GONE
                txtPath?.visibility = View.GONE
                if (amiibo is FlaskTag) {
                    txtTagId?.visibility = View.GONE
                    txtAmiiboSeries?.visibility = View.GONE
                    txtAmiiboType?.visibility = View.GONE
                    txtGameSeries?.visibility = View.GONE
                } else {
                    amiiboHexId = Amiibo.idToHex(amiibo!!.id)
                    if (null != amiibo!!.amiiboSeries) amiiboSeries = amiibo!!.amiiboSeries!!.name
                    if (null != amiibo!!.amiiboType) amiiboType = amiibo!!.amiiboType!!.name
                    if (null != amiibo!!.gameSeries) gameSeries = amiibo!!.gameSeries!!.name
                    txtTagId?.visibility = View.VISIBLE
                    txtAmiiboSeries?.visibility = View.VISIBLE
                    txtAmiiboType?.visibility = View.VISIBLE
                    txtGameSeries?.visibility = View.VISIBLE
                    setAmiiboInfoText(txtTagId, amiiboHexId)
                    setAmiiboInfoText(txtAmiiboSeries, amiiboSeries)
                    setAmiiboInfoText(txtAmiiboType, amiiboType)
                    setAmiiboInfoText(txtGameSeries, gameSeries)
                    if (hasSpoofData(amiiboHexId)) txtTagId?.isEnabled = false
                }
            }
            if (null != imageAmiibo) {
                GlideApp.with(imageAmiibo!!).clear(imageAmiibo!!)
                if (!amiiboImageUrl.isNullOrEmpty()) {
                    GlideApp.with(imageAmiibo!!).asBitmap().load(amiiboImageUrl).into(target)
                } else {
                    imageAmiibo!!.setImageResource(R.mipmap.ic_launcher_round)
                    imageAmiibo!!.visibility = View.VISIBLE
                }
            }
        }

        fun setAmiiboInfoText(textView: TextView?, text: CharSequence?) {
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