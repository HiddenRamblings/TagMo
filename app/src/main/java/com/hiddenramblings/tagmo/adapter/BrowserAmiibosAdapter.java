package com.hiddenramblings.tagmo.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

public class BrowserAmiibosAdapter extends RecyclerView.Adapter<BrowserAmiibosAdapter.AmiiboVewHolder>
        implements Filterable, BrowserSettings.BrowserSettingsListener {
    private final BrowserSettings settings;
    private final OnAmiiboClickListener listener;
    private final ArrayList<AmiiboFile> data = new ArrayList<>();
    private ArrayList<AmiiboFile> filteredData;
    private AmiiboFilter filter;
    boolean firstRun = true;

    public BrowserAmiibosAdapter(BrowserSettings settings, OnAmiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;

        this.filteredData = this.data;
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {
        boolean refresh = firstRun ||
                !BrowserSettings.equals(newBrowserSettings.getQuery(), oldBrowserSettings.getQuery()) ||
                !BrowserSettings.equals(newBrowserSettings.getSort(), oldBrowserSettings.getSort()) ||
                !BrowserSettings.equals(newBrowserSettings.getGameSeriesFilter(), oldBrowserSettings.getGameSeriesFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getCharacterFilter(), oldBrowserSettings.getCharacterFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getAmiiboSeriesFilter(), oldBrowserSettings.getAmiiboSeriesFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getAmiiboTypeFilter(), oldBrowserSettings.getAmiiboTypeFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.isShowingMissingFiles(), oldBrowserSettings.isShowingMissingFiles());

        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(), oldBrowserSettings.getAmiiboFiles())) {
            this.data.clear();
            if (newBrowserSettings.getAmiiboFiles() != null)
                this.data.addAll(newBrowserSettings.getAmiiboFiles());
            refresh = true;
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboManager(), oldBrowserSettings.getAmiiboManager())) {
            refresh = true;
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboView(), oldBrowserSettings.getAmiiboView())) {
            refresh = true;
        }
        if (refresh) {
            this.refresh();
        }

        firstRun = false;
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
                return new CompactViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_LARGE:
                return new LargeViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_SIMPLE:
            default:
                return new SimpleViewHolder(parent, settings, listener);
        }
    }

    @Override
    public void onBindViewHolder(final AmiiboVewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class AmiiboComparator implements Comparator<AmiiboFile> {
        @Override
        public int compare(AmiiboFile amiiboFile1, AmiiboFile amiiboFile2) {
            int value = 0;
            int sort = settings.getSort();

            File filePath1 = amiiboFile1.getFilePath();
            File filePath2 = amiiboFile2.getFilePath();
            if (sort == BrowserActivity.SORT_FILE_PATH && !(filePath1 == null && filePath2 == null))
                value = compareFilePath(filePath1, filePath2);

            long amiiboId1 = amiiboFile1.getId();
            long amiiboId2 = amiiboFile2.getId();
            if (sort == BrowserActivity.SORT_ID) {
                value = compareAmiiboId(amiiboId1, amiiboId2);
            } else if (value == 0) {
                AmiiboManager amiiboManager = settings.getAmiiboManager();
                if (amiiboManager != null) {
                    Amiibo amiibo1 = amiiboManager.amiibos.get(amiiboId1);
                    Amiibo amiibo2 = amiiboManager.amiibos.get(amiiboId2);
                    if (amiibo1 != null || amiibo2 != null) {
                        if (amiibo1 == null)
                            value = 1;
                        else if (amiibo2 == null)
                            value = -1;
                        else if (sort == BrowserActivity.SORT_NAME) {
                            value = compareAmiiboName(amiibo1, amiibo2);
                        } else if (sort == BrowserActivity.SORT_AMIIBO_SERIES) {
                            value = compareAmiiboSeries(amiibo1, amiibo2);
                        } else if (sort == BrowserActivity.SORT_AMIIBO_TYPE) {
                            value = compareAmiiboType(amiibo1, amiibo2);
                        } else if (sort == BrowserActivity.SORT_GAME_SERIES) {
                            value = compareGameSeries(amiibo1, amiibo2);
                        } else if (sort == BrowserActivity.SORT_CHARACTER) {
                            value = compareCharacter(amiibo1, amiibo2);
                        }
                    }
                    if (value == 0 && amiibo1 != null)
                        value = amiibo1.compareTo(amiibo2);
                }
                if (value == 0)
                    value = compareAmiiboId(amiiboId1, amiiboId2);
            }

            if (value == 0)
                value = compareFilePath(filePath1, filePath2);

            return value;
        }

        int compareFilePath(File filePath1, File filePath2) {
            if (filePath1 == null) {
                return 1;
            } else if (filePath2 == null) {
                return -1;
            } else {
                return filePath1.compareTo(filePath2);
            }
        }

        int compareAmiiboId(long amiiboId1, long amiiboId2) {
            return Long.compare(amiiboId1, amiiboId2);
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

        int compareAmiiboSeries(Amiibo amiibo1, Amiibo amiibo2) {
            AmiiboSeries amiiboSeries1 = amiibo1.getAmiiboSeries();
            AmiiboSeries amiiboSeries2 = amiibo2.getAmiiboSeries();
            if (amiiboSeries1 == null && amiiboSeries2 == null ) {
                return 0;
            }
            if (amiiboSeries1 == null) {
                return 1;
            } else if (amiiboSeries2 == null) {
                return -1;
            }
            return amiiboSeries1.compareTo(amiiboSeries2);
        }

        int compareAmiiboType(Amiibo amiibo1, Amiibo amiibo2) {
            AmiiboType amiiboType1 = amiibo1.getAmiiboType();
            AmiiboType amiiboType2 = amiibo2.getAmiiboType();
            if (amiiboType1 == null && amiiboType2 == null ) {
                return 0;
            }
            if (amiiboType1 == null) {
                return 1;
            } else if (amiiboType2 == null) {
                return -1;
            }
            return amiiboType1.compareTo(amiiboType2);
        }

        int compareGameSeries(Amiibo amiibo1, Amiibo amiibo2) {
            GameSeries gameSeries1 = amiibo1.getGameSeries();
            GameSeries gameSeries2 = amiibo2.getGameSeries();
            if (gameSeries1 == null && gameSeries2 == null) {
                return 0;
            }
            if (gameSeries1 == null) {
                return 1;
            } else if (gameSeries2 == null) {
                return -1;
            }
            return gameSeries1.compareTo(gameSeries2);
        }

        int compareCharacter(Amiibo amiibo1, Amiibo amiibo2) {
            Character character1 = amiibo1.getCharacter();
            Character character2 = amiibo2.getCharacter();
            if (character1 == null && character2 == null) {
                return 0;
            }
            if (character1 == null) {
                return 1;
            } else if (character2 == null) {
                return -1;
            }
            return character1.compareTo(character2);
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
            String query = constraint != null ? constraint.toString() : "";
            settings.setQuery(query);

            data.clear();
            if (settings.getAmiiboFiles() != null) {
                data.addAll(settings.getAmiiboFiles());
            }

            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (amiiboManager != null && settings.isShowingMissingFiles()) {
                HashSet<Long> amiiboIds = new HashSet<>();
                for (AmiiboFile amiiboFile : data) {
                    amiiboIds.add(amiiboFile.getId());
                }
                for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                    if (!amiiboIds.contains(amiibo.id)) {
                        data.add(new AmiiboFile(null, amiibo.id));
                    }
                }
            }

            FilterResults filterResults = new FilterResults();
            ArrayList<AmiiboFile> tempList = new ArrayList<>();
            String queryText = query.trim().toLowerCase();
            for (AmiiboFile amiiboFile : data) {
                boolean add;

                if (amiiboManager != null) {
                    Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
                    if (amiibo == null)
                        amiibo = new Amiibo(amiiboManager, amiiboFile.getId(), null, null);
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
        private final OnAmiiboClickListener listener;

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

        public AmiiboVewHolder(View itemView, BrowserSettings settings, OnAmiiboClickListener listener) {
            super(itemView);

            this.settings = settings;
            this.listener = listener;
            this.itemView.setOnClickListener(view -> {
                if (AmiiboVewHolder.this.listener != null) {
                    AmiiboVewHolder.this.listener.onAmiiboClicked(amiiboFile);
                }
            });

            this.txtError = itemView.findViewById(R.id.txtError);
            this.txtName = itemView.findViewById(R.id.txtName);
            this.txtTagId = itemView.findViewById(R.id.txtTagId);
            this.txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries);
            this.txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType);
            this.txtGameSeries = itemView.findViewById(R.id.txtGameSeries);
            // this.txtCharacter = itemView.findViewById(R.id.txtCharacter);
            this.txtPath = itemView.findViewById(R.id.txtPath);
            this.imageAmiibo = itemView.findViewById(R.id.imageAmiibo);
            if (this.imageAmiibo != null) {
                this.imageAmiibo.setOnClickListener(view -> {
                    if (AmiiboVewHolder.this.listener != null) {
                        AmiiboVewHolder.this.listener.onAmiiboImageClicked(amiiboFile);
                    }
                });
            }
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
                this.txtPath.setText(boldMatchingText(TagMo.friendlyPath(item.getFilePath()), query));
                this.txtPath.setTextColor(this.txtPath.getResources().getColor(R.color.tag_text));
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
    }

    static class SimpleViewHolder extends AmiiboVewHolder {
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener
            );
        }
    }

    static class CompactViewHolder extends AmiiboVewHolder {
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, listener
            );
        }
    }

    static class LargeViewHolder extends AmiiboVewHolder {
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, listener
            );
        }
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(AmiiboFile amiiboFile);

        void onAmiiboImageClicked(AmiiboFile amiiboFile);
    }
}
