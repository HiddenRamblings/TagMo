package com.hiddenramblings.tagmo.adapter

import android.annotation.SuppressLint
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
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.Amiibo
import com.hiddenramblings.tagmo.amiibo.EliteTag
import com.hiddenramblings.tagmo.eightbit.request.ImageTarget
import com.hiddenramblings.tagmo.widget.BoldSpannable
import java.util.*

class EliteBankAdapter(
    private val settings: BrowserSettings, private val listener: OnAmiiboClickListener
    ) : RecyclerView.Adapter<EliteBankAdapter.AmiiboViewHolder>(), BrowserSettingsListener {
    var mPrefs = Preferences(TagMo.appContext)
    private var amiibos: ArrayList<EliteTag?> = arrayListOf()
    fun setAmiibos(amiibos: ArrayList<EliteTag?>) {
        this.amiibos = amiibos
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) { }

    override fun getItemCount(): Int {
        return amiibos.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getItem(i: Int): EliteTag? {
        return amiibos[i]
    }

    override fun getItemViewType(position: Int): Int {
        return settings.amiiboView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmiiboViewHolder {
        return when (VIEW.valueOf(viewType)) {
            VIEW.COMPACT -> CompactViewHolder(parent, settings, listener)
            VIEW.LARGE -> LargeViewHolder(parent, settings, listener)
            VIEW.IMAGE -> ImageViewHolder(parent, settings, listener)
            VIEW.SIMPLE -> SimpleViewHolder(parent, settings, listener)
        }.apply {
            val highlight = itemView.findViewById<View>(R.id.highlight)
            if (mPrefs.eliteActiveBank() == bindingAdapterPosition) {
                highlight.setBackgroundResource(R.drawable.cardview_outline)
            } else {
                highlight.setBackgroundResource(0)
            }
            itemView.setOnClickListener {
                listener?.onAmiiboClicked(amiiboItem, bindingAdapterPosition)
            }
            imageAmiibo?.setOnClickListener {
                if (settings.amiiboView == VIEW.IMAGE.value)
                    listener?.onAmiiboClicked(amiiboItem, bindingAdapterPosition)
                else listener?.onAmiiboImageClicked(amiiboItem, bindingAdapterPosition)
            }
            itemView.setOnLongClickListener {
                return@setOnLongClickListener listener?.onAmiiboLongClicked(
                    amiiboItem, bindingAdapterPosition
                ) ?: false
            }
        }
    }

    override fun onBindViewHolder(holder: AmiiboViewHolder, position: Int) {
        holder.bind(getItem(holder.bindingAdapterPosition))
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
        val txtUsage: TextView?
        var imageAmiibo: AppCompatImageView? = null
        var amiiboItem: EliteTag? = null
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
            txtUsage = itemView.findViewById(R.id.txtUsage)
            imageAmiibo = itemView.findViewById(R.id.imageAmiibo)
        }

        @SuppressLint("SetTextI18n")
        fun bind(amiibo: EliteTag?) {
            amiiboItem = amiibo
            txtPath?.isGone = true
            txtUsage?.isGone = true
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
                amiiboItem?.index = absoluteAdapterPosition
                amiiboHexId = Amiibo.idToHex(amiibo!!.id)
                amiiboImageUrl = amiibo.imageUrl
                amiibo.name?.let { name -> amiiboName = name }
                amiibo.amiiboSeries?.let { series -> amiiboSeries = series.name }
                amiibo.amiiboType?.let { type -> amiiboType = type.name }
                amiibo.gameSeries?.let { series -> gameSeries = series.name }
                setAmiiboInfoText(txtName, "$value: $amiiboName")
            } else {
                setAmiiboInfoText(
                    txtName, TagMo.appContext.getString(R.string.blank_bank, value)
                )
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
                if (isAmiibo) {
                    setAmiiboInfoText(txtTagId, boldSpannable.startsWith(amiiboHexId, query))
                    setAmiiboInfoText(txtAmiiboSeries, boldSpannable.indexOf(amiiboSeries, query))
                    setAmiiboInfoText(txtAmiiboType, boldSpannable.indexOf(amiiboType, query))
                    setAmiiboInfoText(txtGameSeries, boldSpannable.indexOf(gameSeries, query))
                } else {
                    txtTagId?.isGone = true
                    txtAmiiboSeries?.isGone = true
                    txtAmiiboType?.isGone = true
                    txtGameSeries?.isGone = true
                }
            }
        }

        private fun setAmiiboInfoText(textView: TextView?, text: CharSequence?) {
            textView?.run {
                isVisible = true
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

    internal class SimpleViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_simple_card, parent, false
        ),
        settings, listener
    )

    internal class CompactViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_compact_card, parent, false
        ),
        settings, listener
    )

    internal class LargeViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
    ) : AmiiboViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.amiibo_large_card, parent, false
        ),
        settings, listener
    )

    internal class ImageViewHolder(
        parent: ViewGroup, settings: BrowserSettings, listener: OnAmiiboClickListener?
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