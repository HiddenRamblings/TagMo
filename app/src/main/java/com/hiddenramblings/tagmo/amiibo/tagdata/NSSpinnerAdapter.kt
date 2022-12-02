package com.hiddenramblings.tagmo.amiibo.tagdata

import android.database.DataSetObserver
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.SpinnerAdapter

/**
 * Decorator Adapter to allow a Spinner to show a 'Nothing Selected...' initially
 * displayed instead of the first choice in the Adapter.
 */
open class NSSpinnerAdapter
/**
 * Use this constructor to have NO 'Select One...' item, instead use
 * the standard prompt or nothing at all.
 * you want text grayed out like a prompt...
 */ @JvmOverloads constructor(
    protected var adapter: SpinnerAdapter?, private var nothingSelectedLayout: Int,
    private var nothingSelectedDropdownLayout: Int = -1
) : SpinnerAdapter, ListAdapter {
    /**
     * Use this constructor to Define your 'Select One...' layout as the first
     * row in the returned choices.
     * If you do this, you probably don't want a prompt on your spinner or it'll
     * have two 'Select' rows.
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // This provides the View for the Selected Item in the Spinner, not
        // the dropdown (unless dropdownView is not set).
        return if (position == 0) {
            convertView ?: getNothingSelectedView(parent)
        } else adapter!!.getView(
            position - EXTRA, null, parent
        )
        // Could re-use
        // the convertView if possible.
    }

    /**
     * View to show in Spinner with Nothing Selected
     * Override this to do something dynamic... e.g. "37 Options Found"
     */
    private fun getNothingSelectedView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context)
            .inflate(nothingSelectedLayout, parent, false)
    }

    override fun getDropDownView(position: Int, convertView: View, parent: ViewGroup): View {
        // Android BUG! http://code.google.com/p/android/issues/detail?id=17128 -
        // Spinner does not support multiple view types
        return if (position == 0) {
            if (nothingSelectedDropdownLayout == -1) {
                View(parent.context)
            } else {
                getNothingSelectedDropdownView(parent)
            }
        } else adapter!!.getDropDownView(
            position - EXTRA,
            null,
            parent
        )

        // Could re-use the convertView if possible, use setTag...
    }

    /**
     * Override this to do something dynamic... For example, "Pick your favorite
     * of these 37".
     */
    private fun getNothingSelectedDropdownView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context).inflate(
            nothingSelectedDropdownLayout, parent, false
        )
    }

    override fun getCount(): Int {
        val count = adapter!!.count
        return if (count == 0) 0 else count + EXTRA
    }

    override fun getItem(position: Int): Any {
        return (if (position == 0) null else adapter!!.getItem(position - EXTRA))!!
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return if (position >= EXTRA) adapter!!.getItemId(position - EXTRA) else (position - EXTRA).toLong()
    }

    override fun hasStableIds(): Boolean {
        return adapter!!.hasStableIds()
    }

    override fun isEmpty(): Boolean {
        return null == adapter || adapter!!.isEmpty
    }

    override fun registerDataSetObserver(observer: DataSetObserver) {
        adapter!!.registerDataSetObserver(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver) {
        adapter!!.unregisterDataSetObserver(observer)
    }

    override fun areAllItemsEnabled(): Boolean {
        return false
    }

    override fun isEnabled(position: Int): Boolean {
        return position != 0 // Don't allow the 'nothing selected'
        // item to be picked.
    }

    companion object {
        protected const val EXTRA = 1
    }
}