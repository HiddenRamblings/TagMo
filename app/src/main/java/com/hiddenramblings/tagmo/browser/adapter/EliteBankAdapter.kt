package com.hiddenramblings.tagmo.browser.adapter

import android.annotation.SuppressLint
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
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.EliteTag
import com.hiddenramblings.tagmo.browser.BrowserSettings
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW
import com.hiddenramblings.tagmo.widget.BoldSpannable
import java.util.*

class EliteBankAdapter     // setHasStableIds(true);
    (private val settings: BrowserSettings, private val listener: OnAmiiboClickListener) :
    RecyclerView.Adapter<EliteBankAdapter.AmiiboViewHolder>(), BrowserSettingsListener {
    var mPrefs = Preferences(TagMo.appContext)
    private var amiibos = ArrayList<EliteTag?>()
    fun setAmiibos(amiibos: ArrayList<EliteTag?>) {
        this.amiibos = amiibos
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
    }

    override fun getItemCount(): Int {
        return amiibos.size
    }

    override fun getItemId(i: Int): Long {
        return (amiibos[i]?.id ?: 0).toLong()
    }

    private fun getItem(i: Int): EliteTag? {
        return amiibos[i]
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

    override fun onBindViewHolder(holder: AmiiboViewHolder, position: Int) {
        val clickPosition = if (hasStableIds()) holder.bindingAdapterPosition else position
        val highlight = holder.itemView.findViewById<View>(R.id.highlight)
        if (mPrefs.eliteActiveBank() == position) {
            highlight.setBackgroundResource(R.drawable.cardview_outline)
        } else {
            highlight.setBackgroundResource(0)
        }
        holder.itemView.setOnClickListener {
            if (null != holder.listener) holder.listener.onAmiiboClicked(
                holder.amiiboItem,
                clickPosition
            )
        }
        holder.imageAmiibo?.setOnClickListener {
            if (null != holder.listener) {
                if (settings.amiiboView == VIEW.IMAGE.value) holder.listener.onAmiiboClicked(
                    holder.amiiboItem,
                    clickPosition
                ) else holder.listener.onAmiiboImageClicked(holder.amiiboItem, clickPosition)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (null != holder.listener) return@setOnLongClickListener holder.listener.onAmiiboLongClicked(
                holder.amiiboItem,
                clickPosition
            )
            false
        }
        holder.bind(getItem(clickPosition))
    }

    abstract class AmiiboViewHolder(
        itemView: View, private val settings: BrowserSettings,
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
        var amiiboItem: EliteTag? = null
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

        @SuppressLint("SetTextI18n")
        fun bind(amiibo: EliteTag?) {
            amiiboItem = amiibo
            var amiiboHexId: String? = ""
            var amiiboName = ""
            var amiiboSeries = ""
            var amiiboType = ""
            var gameSeries = ""
            // String character = "";
            var amiiboImageUrl: String? = null
            val isAmiibo = null != amiibo?.manager
            val query = settings.query?.lowercase(Locale.getDefault())
            val value = (absoluteAdapterPosition + 1).toString()
            if (isAmiibo) {
                amiiboItem!!.index = absoluteAdapterPosition
                amiiboHexId = Amiibo.idToHex(amiibo!!.id)
                amiiboImageUrl = amiibo.imageUrl
                if (null != amiibo.name) amiiboName = amiibo.name
                if (null != amiibo.amiiboSeries) amiiboSeries = amiibo.amiiboSeries!!.name
                if (null != amiibo.amiiboType) amiiboType = amiibo.amiiboType!!.name
                if (null != amiibo.gameSeries) gameSeries = amiibo.gameSeries!!.name
                setAmiiboInfoText(txtName, "$value: $amiiboName")
            } else {
                setAmiiboInfoText(
                    txtName, TagMo.appContext.getString(R.string.blank_bank, value)
                )
            }
            if (settings.amiiboView != VIEW.IMAGE.value) {
                txtError?.visibility = View.GONE
                if (isAmiibo) {
                    setAmiiboInfoText(txtTagId, boldSpannable.startsWith(amiiboHexId!!, query))
                    setAmiiboInfoText(
                        txtAmiiboSeries,
                        boldSpannable.indexOf(amiiboSeries, query)
                    )
                    setAmiiboInfoText(
                        txtAmiiboType,
                        boldSpannable.indexOf(amiiboType, query)
                    )
                    setAmiiboInfoText(
                        txtGameSeries,
                        boldSpannable.indexOf(gameSeries, query)
                    )
                } else {
                    txtTagId?.visibility = View.GONE
                    txtAmiiboSeries?.visibility = View.GONE
                    txtAmiiboType?.visibility = View.GONE
                    txtGameSeries?.visibility = View.GONE
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

        private fun setAmiiboInfoText(textView: TextView?, text: CharSequence?) {
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
        fun onAmiiboClicked(amiibo: EliteTag?, position: Int)
        fun onAmiiboImageClicked(amiibo: EliteTag?, position: Int)
        fun onAmiiboLongClicked(amiibo: EliteTag?, position: Int): Boolean
    }
}