package com.hiddenramblings.tagmo

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.wear.widget.drawer.WearableNavigationDrawerView

class WearableAdapter(private val context: Context) : WearableNavigationDrawerView.WearableNavigationDrawerAdapter() {
    private val prefs = Preferences(context)
    private val isEliteEnabled = prefs.eliteEnabled()

    val items = mutableListOf<Item>().apply {
        add(Item(context.getString(R.string.menu_amiibo), R.drawable.ic_nfc_scanner_menu))
        add(Item(context.getString(R.string.menu_foomiibo), R.drawable.ic_foomiibo_menu))
        add(Item(context.getString(R.string.menu_guides), R.drawable.ic_support_required_menu))
        if (isEliteEnabled) {
            add(Item(context.getString(R.string.menu_elite), R.drawable.ic_nfc_icon_menu))
        }
        add(Item(context.getString(R.string.menu_gatt), R.drawable.ic_bluup_labs_menu))
        add(Item(context.getString(R.string.menu_settings), R.drawable.ic_outline_settings_menu))
    }

    data class Item(val text: String, val iconRes: Int)

    override fun getItemText(pos: Int): CharSequence = items[pos].text
    override fun getItemDrawable(pos: Int): Drawable? = ContextCompat.getDrawable(context, items[pos].iconRes)
    override fun getCount(): Int = items.size
}
