package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
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
import com.eightbit.os.Storage;
import com.hiddenramblings.tagmo.BrowserActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboComparator;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.AmiiboSeries;
import com.hiddenramblings.tagmo.amiibo.AmiiboType;
import com.hiddenramblings.tagmo.amiibo.Character;
import com.hiddenramblings.tagmo.amiibo.GameSeries;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class WriteBlankAdapter extends RecyclerView.Adapter<WriteBlankAdapter.AmiiboVewHolder>
        implements Filterable, BrowserSettings.BrowserSettingsListener {
    private final BrowserSettings settings;
    private OnAmiiboClickListener listener = null;
    private OnHighlightListener collector = null;
    private final ArrayList<AmiiboFile> amiiboFiles;
    private ArrayList<AmiiboFile> filteredData;
    private AmiiboFilter filter;
    private final ArrayList<AmiiboFile> amiiboList = new ArrayList<>();

    public WriteBlankAdapter(BrowserSettings settings, OnAmiiboClickListener listener, ArrayList<AmiiboFile> amiiboFiles) {
        this.settings = settings;
        this.listener = listener;

        Collections.sort(amiiboFiles, new AmiiboComparator(settings));
        this.filteredData = this.amiiboFiles = amiiboFiles;
    }

    public WriteBlankAdapter(BrowserSettings settings, OnHighlightListener collector, ArrayList<AmiiboFile> amiiboFiles) {
        this.settings = settings;
        this.collector = collector;

        Collections.sort(amiiboFiles, new AmiiboComparator(settings));
        this.filteredData = this.amiiboFiles = amiiboFiles;
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
                if (collector != null)
                    return new CompactViewHolder(parent, settings, collector);
                else
                    return new CompactViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_LARGE:
                if (collector != null)
                    return new LargeViewHolder(parent, settings, collector);
                else
                    return new LargeViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_SIMPLE:
            default:
                if (collector != null)
                    return new SimpleViewHolder(parent, settings, collector);
                else
                    return new SimpleViewHolder(parent, settings, listener);
        }
    }

    private void setIsHighlighted(AmiiboVewHolder holder, boolean isHighlighted) {
        View highlight = holder.itemView.findViewById(R.id.highlight);
        if (isHighlighted) {
            highlight.setBackgroundColor(ContextCompat.getColor(TagMo.getContext(),
                    TagMo.isDarkTheme() ? android.R.color.holo_green_dark
                            : android.R.color.holo_green_light));
        } else {
            highlight.setBackgroundColor(ContextCompat.getColor(
                    TagMo.getContext(), android.R.color.transparent));
        }
    }

    @Override
    public void onBindViewHolder(final AmiiboVewHolder holder, int position) {
        holder.itemView.setOnClickListener(view -> {
            if (holder.collector != null) {
                if (amiiboList.contains(holder.amiiboFile)) {
                    amiiboList.remove(filteredData.get(holder.getAbsoluteAdapterPosition()));
                    setIsHighlighted(holder, false);
                } else {
                    amiiboList.add(filteredData.get(holder.getAbsoluteAdapterPosition()));
                    setIsHighlighted(holder, true);
                }
                holder.collector.onAmiiboClicked(amiiboList);
            } else if (holder.listener != null) {
                holder.listener.onAmiiboClicked(holder.amiiboFile);
            }
        });
        if (holder.imageAmiibo != null) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (holder.collector != null) {
                    if (amiiboList.contains(holder.amiiboFile)) {
                        amiiboList.remove(filteredData.get(holder.getAbsoluteAdapterPosition()));
                        setIsHighlighted(holder, false);
                    } else {
                        amiiboList.add(filteredData.get(holder.getAbsoluteAdapterPosition()));
                        setIsHighlighted(holder, true);
                    }
                    holder.collector.onAmiiboImageClicked(amiiboList);
                } else if (holder.listener != null) {
                    holder.listener.onAmiiboImageClicked(holder.amiiboFile);
                }
            });
        }
        holder.bind(getItem(position));
        setIsHighlighted(holder, amiiboList.contains(holder.amiiboFile));
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

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            //noinspection unchecked
            filteredData = (ArrayList<AmiiboFile>) filterResults.values;
            Collections.sort(filteredData, new AmiiboComparator(settings));
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

            boolean hasTagInfo = tagInfo != null;
            if (hasTagInfo) {
                setAmiiboInfoText(this.txtError, tagInfo, false);
            } else {
                this.txtError.setVisibility(View.GONE);
            }
            setAmiiboInfoText(this.txtName, amiiboName, false);
            setAmiiboInfoText(this.txtTagId, boldStartText(amiiboHexId, query), hasTagInfo);
            setAmiiboInfoText(this.txtAmiiboSeries, boldMatchingText(amiiboSeries, query), hasTagInfo);
            setAmiiboInfoText(this.txtAmiiboType, boldMatchingText(amiiboType, query), hasTagInfo);
            setAmiiboInfoText(this.txtGameSeries, boldMatchingText(gameSeries, query), hasTagInfo);
            // setAmiiboInfoText(this.txtCharacter, boldMatchingText(character, query), hasTagInfo);
            if (item.getFilePath() != null) {
                this.itemView.setEnabled(true);
                String relativeFile = Storage.getRelativePath(item.getFilePath()).replace(
                        TagMo.getPrefs().browserRootFolder().get(), "");
                this.txtPath.setText(boldMatchingText(relativeFile, query));
            } else {
                this.itemView.setEnabled(false);
                this.txtPath.setText("");
                this.txtPath.setTextColor(this.txtPath.getResources().getColor(
                        TagMo.isDarkTheme() ? R.color.tag_text_dark : R.color.tag_text_light));
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
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0, query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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