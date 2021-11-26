package com.hiddenramblings.tagmo.adapter;

import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.SpinnerAdapter;

/**
 * Decorator Adapter to allow a Spinner to show a 'Nothing Selected...' initially
 * displayed instead of the first choice in the Adapter.
 */
public class NoSelectionSpinnerAdapter implements SpinnerAdapter, ListAdapter {
    protected static final int EXTRA = 1;
    protected SpinnerAdapter adapter;
    protected int nothingSelectedLayout;
    protected int nothingSelectedDropdownLayout;

    /**
     * Use this constructor to have NO 'Select One...' item, instead use
     * the standard prompt or nothing at all.
     *
     * @param spinnerAdapter        wrapped Adapter.
     * @param nothingSelectedLayout layout for nothing selected, perhaps
     *                              you want text grayed out like a prompt...
     */
    public NoSelectionSpinnerAdapter(
            SpinnerAdapter spinnerAdapter,
            int nothingSelectedLayout) {

        this(spinnerAdapter, nothingSelectedLayout, -1);
    }

    /**
     * Use this constructor to Define your 'Select One...' layout as the first
     * row in the returned choices.
     * If you do this, you probably don't want a prompt on your spinner or it'll
     * have two 'Select' rows.
     *
     * @param spinnerAdapter                wrapped Adapter. Should probably return false for isEnabled(0)
     * @param nothingSelectedLayout         layout for nothing selected, perhaps you want
     *                                      text grayed out like a prompt...
     * @param nothingSelectedDropdownLayout layout for your 'Select an Item...' in
     *                                      the dropdown.
     */
    public NoSelectionSpinnerAdapter(
            SpinnerAdapter spinnerAdapter, int nothingSelectedLayout,
            int nothingSelectedDropdownLayout
    ) {
        this.adapter = spinnerAdapter;
        this.nothingSelectedLayout = nothingSelectedLayout;
        this.nothingSelectedDropdownLayout = nothingSelectedDropdownLayout;
    }

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        // This provides the View for the Selected Item in the Spinner, not
        // the dropdown (unless dropdownView is not set).
        if (position == 0) {
            if (null != convertView) {
                return convertView;
            } else {
                return getNothingSelectedView(parent);
            }
        }
        return adapter.getView(position - EXTRA, null, parent); // Could re-use
        // the convertView if possible.
    }

    /**
     * View to show in Spinner with Nothing Selected
     * Override this to do something dynamic... e.g. "37 Options Found"
     */
    protected View getNothingSelectedView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext())
                .inflate(nothingSelectedLayout, parent, false);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        // Android BUG! http://code.google.com/p/android/issues/detail?id=17128 -
        // Spinner does not support multiple view types
        if (position == 0) {
            if (nothingSelectedDropdownLayout == -1) {
                return new View(parent.getContext());
            } else {
                return getNothingSelectedDropdownView(parent);
            }
        }

        // Could re-use the convertView if possible, use setTag...
        return adapter.getDropDownView(position - EXTRA, null, parent);
    }

    /**
     * Override this to do something dynamic... For example, "Pick your favorite
     * of these 37".
     */
    protected View getNothingSelectedDropdownView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(
                nothingSelectedDropdownLayout, parent, false);
    }

    @Override
    public int getCount() {
        int count = adapter.getCount();
        return count == 0 ? 0 : count + EXTRA;
    }

    @Override
    public Object getItem(int position) {
        return position == 0 ? null : adapter.getItem(position - EXTRA);
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position >= EXTRA ? adapter.getItemId(position - EXTRA) : position - EXTRA;
    }

    @Override
    public boolean hasStableIds() {
        return adapter.hasStableIds();
    }

    @Override
    public boolean isEmpty() {
        return adapter.isEmpty();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        adapter.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        adapter.unregisterDataSetObserver(observer);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return position != 0; // Don't allow the 'nothing selected'
        // item to be picked.
    }
}