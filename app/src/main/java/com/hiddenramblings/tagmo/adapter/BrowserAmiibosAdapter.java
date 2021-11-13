package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class BrowserAmiibosAdapter extends RecyclerView.Adapter<BrowserAmiibosAdapter.AmiiboViewHolder>
        implements Filterable, BrowserSettings.BrowserSettingsListener {
    private static final String decryptRegex = "(Decrypted).bin";

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
        this.setHasStableIds(true);
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings,
                                         BrowserSettings oldBrowserSettings) {
        if (newBrowserSettings == null || oldBrowserSettings == null) return;
        boolean refresh = firstRun ||
                !BrowserSettings.equals(newBrowserSettings.getQuery(), oldBrowserSettings.getQuery()) ||
                !BrowserSettings.equals(newBrowserSettings.getSort(), oldBrowserSettings.getSort()) ||
                !BrowserSettings.equals(newBrowserSettings.getGameSeriesFilter(),
                        oldBrowserSettings.getGameSeriesFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getCharacterFilter(),
                        oldBrowserSettings.getCharacterFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getAmiiboSeriesFilter(),
                        oldBrowserSettings.getAmiiboSeriesFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getAmiiboTypeFilter(),
                        oldBrowserSettings.getAmiiboTypeFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.isShowingMissingFiles(),
                        oldBrowserSettings.isShowingMissingFiles());

        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(),
                oldBrowserSettings.getAmiiboFiles())) {
            this.data.clear();
            if (newBrowserSettings.getAmiiboFiles() != null)
                this.data.addAll(newBrowserSettings.getAmiiboFiles());
            refresh = true;
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboManager(),
                oldBrowserSettings.getAmiiboManager())) {
            refresh = true;
        }
        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboView(),
                oldBrowserSettings.getAmiiboView())) {
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
    public AmiiboViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case BrowserActivity.VIEW_TYPE_COMPACT:
                return new CompactViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_LARGE:
                return new LargeViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_IMAGE:
                return new ImageViewHolder(parent, settings, listener);
            case BrowserActivity.VIEW_TYPE_SIMPLE:
            default:
                return new SimpleViewHolder(parent, settings, listener);
        }
    }

    @Override
    public void onBindViewHolder(final AmiiboViewHolder holder, int position) {
        holder.itemView.setOnClickListener(view -> {
            if (holder.listener != null) {
                holder.listener.onAmiiboClicked(holder.amiiboFile);
            }
        });
        if (holder.imageAmiibo != null) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (holder.listener != null) {
                    if (settings.getAmiiboView() == BrowserActivity.VIEW_TYPE_IMAGE)
                        holder.listener.onAmiiboClicked(holder.amiiboFile);
                    else
                        holder.listener.onAmiiboImageClicked(holder.amiiboFile);
                }
            });
        }
        holder.bind(getItem(position));
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
                        amiibo = new Amiibo(amiiboManager, amiiboFile.getId(),
                                null, null);
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
            if (filteredData != (ArrayList<AmiiboFile>) filterResults.values) {
                //noinspection unchecked
                filteredData = (ArrayList<AmiiboFile>) filterResults.values;
                Collections.sort(filteredData, new AmiiboComparator(settings));
                notifyDataSetChanged();
            }
        }
    }

    protected static abstract class AmiiboViewHolder extends RecyclerView.ViewHolder {
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

        private void setIsHighlighted(AmiiboViewHolder holder, boolean isHighlighted) {
            View highlight = holder.itemView.findViewById(R.id.highlight);
            if (isHighlighted) {
                highlight.setBackgroundColor(ContextCompat.getColor(TagMo.getContext(),
                        TagMo.isDarkTheme() ? R.color.backgroundWhite
                                : R.color.backgroundBlack));
            } else {
                highlight.setBackgroundColor(ContextCompat.getColor(
                        TagMo.getContext(), android.R.color.transparent));
            }
        }

        private void setPathTextHighlight(File filePath) {
            if (settings.isShowingMissingFiles() || filePath != null) {
                TypedValue a = new TypedValue();
                this.txtPath.getContext().getTheme().resolveAttribute(
                        android.R.attr.textColor, a, true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && a.isColorType()) {
                    this.txtPath.setTextColor(a.data);
                } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    this.txtPath.setTextColor(a.data);
                }
            } else {
                this.txtPath.setTextColor(this.txtPath.getResources().getColor(
                        TagMo.isDarkTheme() ? R.color.tag_text_dark : R.color.tag_text_light));
            }
        }

        public AmiiboViewHolder(View itemView, BrowserSettings settings, OnAmiiboClickListener listener) {
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

            if (settings.getAmiiboView() != BrowserActivity.VIEW_TYPE_IMAGE) {
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
                    setIsHighlighted(this, relativeFile.endsWith(decryptRegex));
                } else {
                    this.itemView.setEnabled(false);
                    this.txtPath.setText("");
                }
                setPathTextHighlight(item.getFilePath());
                this.txtPath.setVisibility(View.VISIBLE);
            }
            if (this.imageAmiibo != null) {
                this.imageAmiibo.setVisibility(View.GONE);
                Glide.with(itemView).clear(target);
                if (amiiboImageUrl != null) {
                    Glide.with(itemView)
                            .setDefaultRequestOptions(onlyRetrieveFromCache())
                            .asBitmap()
                            .load(amiiboImageUrl)
                            .into(target);
                }
            }
        }

        private RequestOptions onlyRetrieveFromCache() {
            String imageNetworkSetting = settings.getImageNetworkSettings();
            if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
                return new RequestOptions().onlyRetrieveFromCache(true);
            } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
                ConnectivityManager cm = (ConnectivityManager)
                        itemView.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return new RequestOptions().onlyRetrieveFromCache(activeNetwork == null
                        || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI);
            } else {
                return new RequestOptions().onlyRetrieveFromCache(false);
            }
        }

        private SpannableStringBuilder boldMatchingText(String text, String query) {
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
                str.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        private SpannableStringBuilder boldStartText(String text, String query) {
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

    static class SimpleViewHolder extends AmiiboViewHolder {
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings,
                                OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener
            );
        }
    }

    static class CompactViewHolder extends AmiiboViewHolder {
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings,
                                 OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, listener
            );
        }
    }

    static class LargeViewHolder extends AmiiboViewHolder {
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, listener
            );
        }
    }

    static class ImageViewHolder extends AmiiboViewHolder {
        public ImageViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_image_card, parent, false),
                    settings, listener
            );
        }
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(AmiiboFile amiiboFile);

        void onAmiiboImageClicked(AmiiboFile amiiboFile);
    }
}
