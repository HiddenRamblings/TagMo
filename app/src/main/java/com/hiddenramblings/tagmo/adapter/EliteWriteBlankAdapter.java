package com.hiddenramblings.tagmo.adapter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.BrowserActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.nfctag.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class EliteWriteBlankAdapter extends RecyclerView.Adapter<EliteWriteBlankAdapter.AmiiboVewHolder>
        implements Filterable, BrowserSettings.BrowserSettingsListener {
    private final BrowserSettings settings;
    private OnAmiiboClickListener listener = null;
    private OnHighlightListener collector = null;
    private final ArrayList<AmiiboFile> amiiboFiles;
    private ArrayList<AmiiboFile> filteredData;
    private AmiiboFilter filter;
    private boolean isCollection;
    private final ArrayList<AmiiboFile> amiiboList = new ArrayList<>();

    public EliteWriteBlankAdapter(BrowserSettings settings, OnAmiiboClickListener listener, ArrayList<AmiiboFile> amiiboFiles) {
        this.settings = settings;
        this.listener = listener;

        this.filteredData = this.amiiboFiles = amiiboFiles;
        Collections.sort(this.amiiboFiles, new AmiiboComparator());
    }

    public EliteWriteBlankAdapter(BrowserSettings settings, OnHighlightListener collector, ArrayList<AmiiboFile> amiiboFiles) {
        this.settings = settings;
        this.collector = collector;

        this.filteredData = this.amiiboFiles = amiiboFiles;
        Collections.sort(this.amiiboFiles, new AmiiboComparator());
    }

    public EliteWriteBlankAdapter withHighlight(boolean isCollection) {
        this.isCollection = isCollection;
        return this;
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {
        if (!BrowserSettings.equals(newBrowserSettings.getQuery(), oldBrowserSettings.getQuery())) {
            this.refresh();
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboManager(), oldBrowserSettings.getAmiiboManager())) {
            this.refresh();
        }
    }

    @Override
    public int getItemCount() {
        return filteredData.size();
    }

    @Override
    public long getItemId(int i) {
        return filteredData.get(i).getId();
    }

    public AmiiboFile getItem(int i) {
        return filteredData.get(i);
    }

    @Override
    public int getItemViewType(int position) {
        return settings.getAmiiboView();
    }

    @NonNull
    @Override
    public AmiiboVewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case BrowserActivity.VIEW_TYPE_COMPACT:
                if (isCollection)
                    return new CompactViewHolder(parent, settings, collector);
                else
                    return new CompactViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_LARGE:
                if (isCollection)
                    return new LargeViewHolder(parent, settings, collector);
                else
                    return new LargeViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_SIMPLE:
            default:
                if (isCollection)
                    return new SimpleViewHolder(parent, settings, collector);
                else
                    return new SimpleViewHolder(parent, settings, listener);
        }
    }

    @Override
    public void onBindViewHolder(final AmiiboVewHolder holder, int position) {
        holder.itemView.setOnClickListener(view -> {
            if (isCollection) {
                amiiboList.add(filteredData.get(holder.getAbsoluteAdapterPosition()));
                holder.itemView.setBackgroundColor(ContextCompat.getColor(TagMo.getContext(),
                        android.R.color.holo_green_light));
                if (holder.collector != null) {
                    holder.collector.onAmiiboClicked(amiiboList);
                }
            } else {
                if (holder.listener != null) {
                    holder.listener.onAmiiboClicked(holder.amiiboFile);
                }
            }
        });
        if (holder.imageAmiibo != null) {
            if (isCollection) {
                holder.imageAmiibo.setOnClickListener(view -> {
                    if (holder.collector != null) {
                        holder.collector.onAmiiboImageClicked(amiiboList);
                    }
                });
            } else {
                holder.imageAmiibo.setOnClickListener(view -> {
                    if (holder.listener != null) {
                        holder.listener.onAmiiboImageClicked(holder.amiiboFile);
                    }
                });
            }
        }
        holder.bind(getItem(position));
        if (amiiboList.contains(holder.amiiboFile)) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(TagMo.getContext(),
                    android.R.color.holo_green_light));
        } else {
            TypedValue a = new TypedValue();
            TagMo.getContext().getTheme().resolveAttribute(
                    android.R.attr.windowBackground, a, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && a.isColorType()) {
                holder.itemView.setBackgroundColor(a.data);
            } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                holder.itemView.setBackgroundColor(a.data);
            } else {
                holder.itemView.setBackgroundResource(a.resourceId);
            }
        }
    }

    class AmiiboComparator implements Comparator<AmiiboFile> {
        @Override
        public int compare(AmiiboFile amiiboFile1, AmiiboFile amiiboFile2) {
            int value = 0;

            long amiiboId1 = amiiboFile1.getId();
            long amiiboId2 = amiiboFile2.getId();

            Amiibo amiibo1 = settings.getAmiiboManager().amiibos.get(amiiboId1);
            Amiibo amiibo2 = settings.getAmiiboManager().amiibos.get(amiiboId2);
            if (amiibo1 != null || amiibo2 != null) {
                if (amiibo1 == null)
                    value = 1;
                else if (amiibo2 == null)
                    value = -1;
                else
                    value = compareAmiiboName(amiibo1, amiibo2);
            }
            if (value == 0 && amiibo1 != null)
                value = amiibo1.compareTo(amiibo2);

            return value;
        }
        int compareAmiiboName(Amiibo amiibo1, Amiibo amiibo2) {
            String name1 = amiibo1.name;
            String name2 = amiibo2.name;
            if (name1 == null && name2 == null) {
                return 0;
            }
            if (name1 == null) {
                return 1;
            } else if (name2 == null) {
                return -1;
            }
            return name1.compareTo(name2);
        }
    }

    public void refresh() {
        this.getFilter().filter(settings.getQuery());
    }

    @Override
    public AmiiboFilter getFilter() {
        if (this.filter == null) {
            this.filter = new AmiiboFilter();
        }

        return this.filter;
    }

    class AmiiboFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();
            ArrayList<AmiiboFile> tempList = new ArrayList<>();
            String queryText = settings.getQuery().trim().toLowerCase();
            for (AmiiboFile amiiboFile : amiiboFiles) {
                boolean add;

                if (settings.getAmiiboManager() != null) {
                    Amiibo amiibo = settings.getAmiiboManager().amiibos.get(amiiboFile.getId());
                    if (amiibo == null)
                        amiibo = new Amiibo(settings.getAmiiboManager(),
                                amiiboFile.getId(), null, null);
                    add = amiiboContainsQuery(amiibo, queryText);
                } else {
                    add = queryText.isEmpty();
                }
                if (!add && amiiboFile.getFilePath() != null)
                    add = pathContainsQuery(amiiboFile.getFilePath().getAbsolutePath(), queryText);
                if (add)
                    tempList.add(amiiboFile);
            }
            filterResults.count = tempList.size();
            filterResults.values = tempList;

            return filterResults;
        }

        public boolean pathContainsQuery(String path, String query) {
            return !query.isEmpty() &&
                    settings.getGameSeriesFilter().isEmpty() &&
                    settings.getCharacterFilter().isEmpty() &&
                    settings.getAmiiboSeriesFilter().isEmpty() &&
                    settings.getAmiiboTypeFilter().isEmpty() &&
                    path.toLowerCase().contains(query);
        }

        public boolean amiiboContainsQuery(Amiibo amiibo, String query) {
            GameSeries gameSeries = amiibo.getGameSeries();
            if (!Amiibo.matchesGameSeriesFilter(gameSeries, settings.getGameSeriesFilter()))
                return false;

            Character character = amiibo.getCharacter();
            if (!Amiibo.matchesCharacterFilter(character, settings.getCharacterFilter()))
                return false;

            AmiiboSeries amiiboSeries = amiibo.getAmiiboSeries();
            if (!Amiibo.matchesAmiiboSeriesFilter(amiiboSeries, settings.getAmiiboSeriesFilter()))
                return false;

            AmiiboType amiiboType = amiibo.getAmiiboType();
            if (!Amiibo.matchesAmiiboTypeFilter(amiiboType, settings.getAmiiboTypeFilter()))
                return false;

            if (!query.isEmpty()) {
                if (TagUtils.amiiboIdToHex(amiibo.id).toLowerCase().startsWith(query))
                    return true;
                else if (amiibo.name != null && amiibo.name.toLowerCase().contains(query))
                    return true;
                else if (gameSeries != null && gameSeries.name.toLowerCase().contains(query))
                    return true;
                else if (character != null && character.name.toLowerCase().contains(query))
                    return true;
                else if (amiiboSeries != null && amiiboSeries.name.toLowerCase().contains(query))
                    return true;
                else return amiiboType != null && amiiboType.name.toLowerCase().contains(query);
            }
            return true;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            Collections.sort((ArrayList<AmiiboFile>) filterResults.values, new AmiiboComparator());
            filteredData = (ArrayList<AmiiboFile>) filterResults.values;
            notifyDataSetChanged();
        }
    }

    static abstract class AmiiboVewHolder extends RecyclerView.ViewHolder {
        private final BrowserSettings settings;
        private OnAmiiboClickListener listener = null;
        private OnHighlightListener collector = null;

        public final TextView txtError;
        public final TextView txtName;
        public final TextView txtTagId;
        public final TextView txtAmiiboSeries;
        public final TextView txtAmiiboType;
        public final TextView txtGameSeries;
        // public final TextView txtCharacter;
        public final TextView txtPath;
        public final ImageView imageAmiibo;

        AmiiboFile amiiboFile = null;

        CustomTarget<Bitmap> target = new CustomTarget<Bitmap>() {
            @Override
            public void onLoadStarted(@Nullable Drawable placeholder) {
                imageAmiibo.setVisibility(View.GONE);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                imageAmiibo.setVisibility(View.GONE);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }

            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                imageAmiibo.setImageBitmap(resource);
                imageAmiibo.setVisibility(View.VISIBLE);
            }
        };

        public AmiiboVewHolder(View itemView, BrowserSettings settings,
                               OnAmiiboClickListener listener) {
            super(itemView);

            this.settings = settings;
            this.listener = listener;

            this.txtError = itemView.findViewById(R.id.txtError);
            this.txtName = itemView.findViewById(R.id.txtName);
            this.txtTagId = itemView.findViewById(R.id.txtTagId);
            this.txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries);
            this.txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType);
            this.txtGameSeries = itemView.findViewById(R.id.txtGameSeries);
            // this.txtCharacter = itemView.findViewById(R.id.txtCharacter);
            this.txtPath = itemView.findViewById(R.id.txtPath);
            this.imageAmiibo = itemView.findViewById(R.id.imageAmiibo);
        }

        public AmiiboVewHolder(View itemView, BrowserSettings settings,
                               OnHighlightListener collector) {
            super(itemView);

            this.settings = settings;
            this.collector = collector;

            this.txtError = itemView.findViewById(R.id.txtError);
            this.txtName = itemView.findViewById(R.id.txtName);
            this.txtTagId = itemView.findViewById(R.id.txtTagId);
            this.txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries);
            this.txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType);
            this.txtGameSeries = itemView.findViewById(R.id.txtGameSeries);
            // this.txtCharacter = itemView.findViewById(R.id.txtCharacter);
            this.txtPath = itemView.findViewById(R.id.txtPath);
            this.imageAmiibo = itemView.findViewById(R.id.imageAmiibo);
        }

        void bind(final AmiiboFile item) {
            this.amiiboFile = item;

            String tagInfo = null;
            String amiiboHexId = "";
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            // String character = "";
            String amiiboImageUrl;

            long amiiboId = item.getId();
            Amiibo amiibo = null;
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (amiiboManager != null) {
                amiibo = amiiboManager.amiibos.get(amiiboId);
                if (amiibo == null)
                    amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
            }
            if (amiibo != null) {
                amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
                amiiboImageUrl = amiibo.getImageUrl();
                if (amiibo.name != null)
                    amiiboName = amiibo.name;
                if (amiibo.getAmiiboSeries() != null)
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (amiibo.getAmiiboType() != null)
                    amiiboType = amiibo.getAmiiboType().name;
                if (amiibo.getGameSeries() != null)
                    gameSeries = amiibo.getGameSeries().name;
                // if (amiibo.getCharacter() != null)
                //     gameSeries = amiibo.getCharacter().name;
            } else {
                tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
            }

            String query = settings.getQuery().toLowerCase();

            if (tagInfo == null) {
                this.txtError.setVisibility(View.GONE);
            } else {
                setAmiiboInfoText(this.txtError, tagInfo, false);
            }
            setAmiiboInfoText(this.txtName, amiiboName, false);
            setAmiiboInfoText(this.txtTagId, boldStartText(amiiboHexId, query), tagInfo != null);
            setAmiiboInfoText(this.txtAmiiboSeries, boldMatchingText(amiiboSeries, query), tagInfo != null);
            setAmiiboInfoText(this.txtAmiiboType, boldMatchingText(amiiboType, query), tagInfo != null);
            setAmiiboInfoText(this.txtGameSeries, boldMatchingText(gameSeries, query), tagInfo != null);
            // setAmiiboInfoText(this.txtCharacter, boldMatchingText(character, query), tagInfo != null);
            if (item.getFilePath() != null) {
                this.itemView.setEnabled(true);
                String relativeFile = TagMo.friendlyPath(item.getFilePath()).replace(
                        TagMo.getPrefs().browserRootFolder().get(), "");
                this.txtPath.setText(boldMatchingText(relativeFile, query));
                this.txtPath.setTextColor(this.txtPath.getResources().getColor(isDarkTheme() ?
                        R.color.tag_text_dark : R.color.tag_text_light));
            } else {
                this.itemView.setEnabled(false);
                this.txtPath.setText("");
                this.txtPath.setTextColor(Color.RED);
            }
            this.txtPath.setVisibility(View.VISIBLE);

            if (this.imageAmiibo != null) {
                this.imageAmiibo.setVisibility(View.GONE);
                Glide.with(itemView).clear(target);
                if (amiiboImageUrl != null) {
                    Glide.with(itemView)
                            .setDefaultRequestOptions(new RequestOptions().onlyRetrieveFromCache(onlyRetrieveFromCache()))
                            .asBitmap()
                            .load(amiiboImageUrl)
                            .into(target);
                }
            }
        }

        boolean onlyRetrieveFromCache() {
            String imageNetworkSetting = settings.getImageNetworkSettings();
            if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
                return true;
            } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
                ConnectivityManager cm = (ConnectivityManager)
                        itemView.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork == null || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI;
            } else {
                return false;
            }
        }

        SpannableStringBuilder boldMatchingText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (query.isEmpty())
                return str;

            text = text.toLowerCase();
            int j = 0;
            while (j < text.length()) {
                int i = text.indexOf(query, j);
                if (i == -1)
                    break;

                j = i + query.length();
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        SpannableStringBuilder boldStartText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (!query.isEmpty() && text.toLowerCase().startsWith(query)) {
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
            if (hasTagInfo) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                if (text.length() == 0) {
                    textView.setText(R.string.unknown);
                    textView.setEnabled(false);
                } else {
                    textView.setText(text);
                    textView.setEnabled(true);
                }
            }
        }

        private boolean isDarkTheme() {
            return (TagMo.getContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    static class SimpleViewHolder extends AmiiboVewHolder {
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings,
                                OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener
            );
        }
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings,
                                OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, collector
            );
        }
    }

    static class CompactViewHolder extends AmiiboVewHolder {
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings,
                                 OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, listener
            );
        }
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings,
                                 OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, collector
            );
        }
    }

    static class LargeViewHolder extends AmiiboVewHolder {
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, listener
            );
        }
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, collector
            );
        }
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(AmiiboFile amiiboFile);

        void onAmiiboImageClicked(AmiiboFile amiiboFile);
    }

    public interface OnHighlightListener {
        void onAmiiboClicked(ArrayList<AmiiboFile> amiiboList);

        void onAmiiboImageClicked(ArrayList<AmiiboFile> amiiboList);
    }
}