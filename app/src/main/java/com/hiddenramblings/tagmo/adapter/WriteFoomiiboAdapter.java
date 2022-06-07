package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboComparator;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.widget.BoldSpannable;

import java.util.ArrayList;
import java.util.Collections;

public class WriteFoomiiboAdapter extends RecyclerView.Adapter<WriteFoomiiboAdapter.AmiiboViewHolder>
        implements Filterable, BrowserSettingsListener {
    private final BrowserSettings settings;
    private OnAmiiboClickListener listener = null;
    private OnHighlightListener collector = null;
    private final ArrayList<Amiibo> amiiboData = new ArrayList<>();
    private ArrayList<Amiibo> filteredData;
    private AmiiboFilter filter;
    boolean firstRun;
    private final ArrayList<Amiibo> amiiboList = new ArrayList<>();

    public WriteFoomiiboAdapter(BrowserSettings settings, OnAmiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;

        firstRun = true;
        this.filteredData = this.amiiboData;
        this.setHasStableIds(true);
    }

    public WriteFoomiiboAdapter(BrowserSettings settings, OnHighlightListener collector) {
        this.settings = settings;
        this.collector = collector;

        firstRun = true;
        this.filteredData = this.amiiboData;
        this.setHasStableIds(true);
    }

    public void setListener(OnAmiiboClickListener listener) {
        this.listener = listener;
    }

    public void resetSelections() {
        this.amiiboList.clear();
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings,
                                         BrowserSettings oldBrowserSettings) {
        if (null == newBrowserSettings || null == oldBrowserSettings) return;
        boolean refresh = firstRun ||
                !BrowserSettings.equals(newBrowserSettings.getQuery(),
                        oldBrowserSettings.getQuery()) ||
                !BrowserSettings.equals(newBrowserSettings.getSort(),
                        oldBrowserSettings.getSort()) ||
                !BrowserSettings.equals(newBrowserSettings.getGameSeriesFilter(),
                        oldBrowserSettings.getGameSeriesFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getCharacterFilter(),
                        oldBrowserSettings.getCharacterFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getAmiiboSeriesFilter(),
                        oldBrowserSettings.getAmiiboSeriesFilter()) ||
                !BrowserSettings.equals(newBrowserSettings.getAmiiboTypeFilter(),
                        oldBrowserSettings.getAmiiboTypeFilter());

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
        return filteredData.get(i).id;
    }

    public Amiibo getItem(int i) {
        return filteredData.get(i);
    }

    @Override
    public int getItemViewType(int position) {
        return settings.getAmiiboView();
    }

    @NonNull
    @Override
    public AmiiboViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (VIEW.valueOf(viewType)) {
            case COMPACT:
                return new CompactViewHolder(parent, settings, listener, collector);
            case LARGE:
                return new LargeViewHolder(parent, settings, listener, collector);
            case IMAGE:
                return new ImageViewHolder(parent, settings, listener, collector);
            case SIMPLE:
            default:
                return new SimpleViewHolder(parent, settings, listener, collector);
        }
    }

    private void setIsHighlighted(AmiiboViewHolder holder, boolean isHighlighted) {
        View highlight = holder.itemView.findViewById(R.id.highlight);
        if (isHighlighted) {
            highlight.setBackgroundResource(R.drawable.rounded_neon);
        } else {
            highlight.setBackgroundResource(0);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final AmiiboViewHolder holder, int position) {
        final int clickPosition = hasStableIds() ? holder.getBindingAdapterPosition() : position;
        holder.itemView.setOnClickListener(view -> {
            if (null != holder.collector) {
                if (amiiboList.contains(holder.amiibo)) {
                    amiiboList.remove(filteredData.get(clickPosition));
                    setIsHighlighted(holder, false);
                } else {
                    amiiboList.add(filteredData.get(clickPosition));
                    setIsHighlighted(holder, true);
                }
                holder.collector.onAmiiboClicked(amiiboList);
            } else if (null != holder.listener) {
                holder.listener.onAmiiboClicked(holder.amiibo);
            }
        });
        if (null != holder.imageAmiibo) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (null != holder.collector) {
                    if (amiiboList.contains(holder.amiibo)) {
                        amiiboList.remove(filteredData.get(clickPosition));
                        setIsHighlighted(holder, false);
                    } else {
                        amiiboList.add(filteredData.get(clickPosition));
                        setIsHighlighted(holder, true);
                    }
                    holder.collector.onAmiiboClicked(amiiboList);
                } else if (null != holder.listener) {
                    if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
                        holder.listener.onAmiiboClicked(holder.amiibo);
                    else
                        holder.listener.onAmiiboImageClicked(holder.amiibo);
                }
            });
        }
        holder.bind(getItem(clickPosition));
        setIsHighlighted(holder, amiiboList.contains(holder.amiibo));
    }

    public void refresh() {
        this.getFilter().filter(settings.getQuery());
    }

    @Override
    public AmiiboFilter getFilter() {
        if (null == this.filter) {
            this.filter = new AmiiboFilter();
        }
        return this.filter;
    }

    class AmiiboFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults filterResults = new FilterResults();
            ArrayList<Amiibo> tempList = new ArrayList<>();
            String queryText = settings.getQuery().trim().toLowerCase();
            for (Amiibo amiibo : amiiboData) {
                if (settings.amiiboContainsQuery(amiibo, queryText)) tempList.add(amiibo);
            }
            filterResults.count = tempList.size();
            filterResults.values = tempList;

            return filterResults;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            if (filteredData.isEmpty() || filteredData != filterResults.values) {
                //noinspection unchecked
                filteredData = (ArrayList<Amiibo>) filterResults.values;
                Collections.sort(filteredData, new AmiiboComparator(settings));
                notifyDataSetChanged();
            }
        }
    }

    protected static abstract class AmiiboViewHolder extends RecyclerView.ViewHolder {
        private final BrowserSettings settings;
        private final OnAmiiboClickListener listener;
        private final OnHighlightListener collector;

        public final TextView txtError;
        public final TextView txtName;
        public final TextView txtTagId;
        public final TextView txtAmiiboSeries;
        public final TextView txtAmiiboType;
        public final TextView txtGameSeries;
        // public final TextView txtCharacter;
        public final TextView txtPath;
        public final AppCompatImageView imageAmiibo;

        private Amiibo amiibo = null;

        private final BoldSpannable boldSpannable = new BoldSpannable();

        private final CustomTarget<Bitmap> target = new CustomTarget<>() {
            @Override
            public void onLoadStarted(@Nullable Drawable placeholder) {
                imageAmiibo.setImageResource(0);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                imageAmiibo.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                imageAmiibo.setVisibility(View.VISIBLE);
            }

            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                imageAmiibo.setImageBitmap(resource);
                imageAmiibo.setVisibility(View.VISIBLE);
            }
        };

        public AmiiboViewHolder(View itemView, BrowserSettings settings,
                                OnAmiiboClickListener listener,
                                OnHighlightListener collector) {
            super(itemView);

            this.settings = settings;
            this.listener = listener;
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

        void bind(final Amiibo item) {
            this.amiibo = item;

            String tagInfo = null;
            String amiiboHexId = "";
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            // String character = "";
            String amiiboImageUrl;

            long amiiboId = item.id;
            Amiibo amiibo = null;
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (null != amiiboManager) {
                amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null == amiibo)
                    amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
            }
            if (null != amiibo) {
                amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
                amiiboImageUrl = amiibo.getImageUrl();
                if (null != amiibo.name )
                    amiiboName = amiibo.name;
                if (null != amiibo.getAmiiboSeries() )
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (null != amiibo.getAmiiboType() )
                    amiiboType = amiibo.getAmiiboType().name;
                if (null != amiibo.getGameSeries() )
                    gameSeries = amiibo.getGameSeries().name;
                // if (null != amiibo.getCharacter() )
                //     gameSeries = amiibo.getCharacter().name;
            } else {
                tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
            }

            String query = settings.getQuery().toLowerCase();

            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                boolean hasTagInfo = null != tagInfo;
                if (hasTagInfo) {
                    setAmiiboInfoText(this.txtError, tagInfo, false);
                } else {
                    this.txtError.setVisibility(View.GONE);
                }
                setAmiiboInfoText(this.txtName, amiiboName, false);
                setAmiiboInfoText(this.txtTagId, boldSpannable.StartsWith(amiiboHexId, query), hasTagInfo);
                setAmiiboInfoText(this.txtAmiiboSeries,
                        boldSpannable.IndexOf(amiiboSeries, query), hasTagInfo);
                setAmiiboInfoText(this.txtAmiiboType,
                        boldSpannable.IndexOf(amiiboType, query), hasTagInfo);
                setAmiiboInfoText(this.txtGameSeries,
                        boldSpannable.IndexOf(gameSeries, query), hasTagInfo);
                // setAmiiboInfoText(this.txtCharacter,
                // boldText.Matching(character, query), hasTagInfo);
                this.txtPath.setVisibility(View.GONE);
            }
            if (null != this.imageAmiibo) {
                GlideApp.with(itemView).clear(target);
                if (null != amiiboImageUrl) {
                    GlideApp.with(itemView).asBitmap().load(amiiboImageUrl).into(target);
                }
            }
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
                                OnAmiiboClickListener listener,
                                OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener, collector
            );
        }
    }

    static class CompactViewHolder extends AmiiboViewHolder {
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings,
                                 OnAmiiboClickListener listener,
                                 OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, listener, collector
            );
        }
    }

    static class LargeViewHolder extends AmiiboViewHolder {
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnAmiiboClickListener listener,
                               OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, listener, collector
            );
        }
    }

    static class ImageViewHolder extends AmiiboViewHolder {
        public ImageViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnAmiiboClickListener listener,
                               OnHighlightListener collector) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_image_card, parent, false),
                    settings, listener, collector
            );
        }
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(Amiibo amiibo);
        void onAmiiboImageClicked(Amiibo amiibo);
    }

    public interface OnHighlightListener {
        void onAmiiboClicked(ArrayList<Amiibo> amiiboList);
    }
}