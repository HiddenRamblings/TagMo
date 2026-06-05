package com.hiddenramblings.tagmo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hiddenramblings.tagmo.BrowserSettings
import com.hiddenramblings.tagmo.BrowserSettings.BrowserSettingsListener
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.amiibo.games.GameTitles
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.util.Locale

class GameTitlesAdapter(
    private val settings: BrowserSettings,
    private val listener: OnGameTitleClickListener
) : RecyclerView.Adapter<GameTitlesAdapter.GameTitleViewHolder>(),
    PopupTextProvider, Filterable, BrowserSettingsListener {
    private var data: ArrayList<GameTitles> = arrayListOf()
    private var filteredData: ArrayList<GameTitles> = arrayListOf()
    private var filter: GameTitleFilter? = null
    private var firstRun = true

    init {
        setHasStableIds(true)
        filteredData = data
    }

    override fun onBrowserSettingsChanged(
        newBrowserSettings: BrowserSettings?,
        oldBrowserSettings: BrowserSettings?
    ) {
        if (null == newBrowserSettings || null == oldBrowserSettings) return
        val refresh = firstRun ||
                !BrowserSettings.equals(newBrowserSettings.query, oldBrowserSettings.query) ||
                !BrowserSettings.equals(newBrowserSettings.gamesManager, oldBrowserSettings.gamesManager) ||
                !BrowserSettings.equals(newBrowserSettings.amiiboManager, oldBrowserSettings.amiiboManager)
        if (refresh) refresh()
        firstRun = false
    }

    override fun getItemCount(): Int {
        return filteredData.size
    }

    override fun getItemId(position: Int): Long {
        return filteredData[position].name.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameTitleViewHolder {
        return GameTitleViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.game_title_card, parent, false
            )
        ).apply {
            itemView.setOnClickListener {
                getItem(bindingAdapterPosition)?.let { title ->
                    listener.onGameTitleClicked(title)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: GameTitleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        if (position >= filteredData.size) return "?"
        val name = filteredData[position].name
        return if (name.isEmpty()) "?" else name[0].uppercase()
    }

    fun refresh() {
        getFilter().filter(settings.query)
    }

    override fun getFilter(): GameTitleFilter {
        if (null == filter) filter = GameTitleFilter()
        return filter as GameTitleFilter
    }

    private fun getItem(position: Int): GameTitles? {
        if (position < 0 || position >= filteredData.size) return null
        return filteredData[position]
    }

    inner class GameTitleFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim { it <= ' ' } ?: ""
            data = ArrayList(settings.gamesManager?.gameTitles ?: emptyList())
            data.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            val queryText = query.lowercase(Locale.getDefault())
            val values = if (queryText.isEmpty()) {
                data
            } else {
                ArrayList(data.filter {
                    it.name.lowercase(Locale.getDefault()).contains(queryText)
                })
            }
            return FilterResults().apply {
                count = values.size
                this.values = values
            }
        }

        override fun publishResults(charSequence: CharSequence?, filterResults: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            filteredData = filterResults.values as? ArrayList<GameTitles> ?: arrayListOf()
            notifyDataSetChanged()
        }
    }

    inner class GameTitleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtName: TextView = itemView.findViewById(R.id.txtName)
        private val txtCompatible: TextView = itemView.findViewById(R.id.txtCompatible)

        fun bind(gameTitle: GameTitles?) {
            txtName.text = gameTitle?.name ?: ""
            val count = settings.amiiboManager?.let { manager ->
                settings.gamesManager?.getCompatibleAmiiboCount(manager, gameTitle?.name)
            } ?: 0
            txtCompatible.text = itemView.context.resources.getQuantityString(
                R.plurals.compatible_amiibo_count, count, count
            )
        }
    }

    interface OnGameTitleClickListener {
        fun onGameTitleClicked(gameTitle: GameTitles)
    }
}
