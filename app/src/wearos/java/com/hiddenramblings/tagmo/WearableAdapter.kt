package com.hiddenramblings.tagmo

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.wear.widget.drawer.WearableNavigationDrawerView

class WearableAdapter(private val context: Context) : WearableNavigationDrawerView.WearableNavigationDrawerAdapter() {
    private val prefs = Preferences(context)
    private val isEliteEnabled = prefs.eliteEnabled()

    val items = mutableListOf<Item>().apply {
        add(Item(ACTION_AMIIBO, context.getString(R.string.menu_amiibo), R.drawable.ic_nfc_scanner_menu))
        add(Item(ACTION_GAMES, context.getString(R.string.menu_games), R.drawable.ic_joystick_menu))
        add(Item(ACTION_FOOMIIBO, context.getString(R.string.menu_foomiibo), R.drawable.ic_foomiibo_menu))
        add(Item(ACTION_GUIDES, context.getString(R.string.menu_guides), R.drawable.ic_support_required_menu))
        if (isEliteEnabled) {
            add(Item(ACTION_ELITE, context.getString(R.string.menu_elite), R.drawable.ic_nfc_icon_menu))
        }
        add(Item(ACTION_GATT, context.getString(R.string.menu_gatt), R.drawable.ic_bluup_labs_menu))
        add(Item(ACTION_SETTINGS, context.getString(R.string.menu_settings), R.drawable.ic_outline_settings_menu))
    }

    data class Item(val action: Int, val text: String, val iconRes: Int)

    override fun getItemText(pos: Int): CharSequence = items[pos].text
    override fun getItemDrawable(pos: Int): Drawable? = ContextCompat.getDrawable(context, items[pos].iconRes)
    override fun getCount(): Int = items.size

    fun getItemAction(pos: Int): Int = items.getOrNull(pos)?.action ?: ACTION_AMIIBO

    companion object {
        const val ACTION_AMIIBO = 0
        const val ACTION_GAMES = 1
        const val ACTION_FOOMIIBO = 2
        const val ACTION_GUIDES = 3
        const val ACTION_ELITE = 4
        const val ACTION_GATT = 5
        const val ACTION_SETTINGS = 6
    }
}
