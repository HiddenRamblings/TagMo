package com.hiddenramblings.tagmo.browser.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.SectionIndexer;
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
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.nfc.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.widget.BoldSpannable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class FoomiiboAdapter
        extends RecyclerView.Adapter<FoomiiboAdapter.FoomiiboViewHolder>
        implements Filterable, BrowserSettingsListener, SectionIndexer {

    private final BrowserSettings settings;
    private final OnFoomiiboClickListener listener;
    private ArrayList<Amiibo> data = new ArrayList<>();
    private ArrayList<Amiibo> filteredData;
    private FoomiiboFilter filter;
    boolean firstRun = true;
    private static final ArrayList<Long> foomiiboId = new ArrayList<>();

    public FoomiiboAdapter(BrowserSettings settings, OnFoomiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;

        this.filteredData = this.data;
        this.setHasStableIds(true);
    }

    public static void resetVisible() {
        foomiiboId.clear();
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
                BrowserSettings.hasFilterChanged(newBrowserSettings, oldBrowserSettings);

        if (!BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(),
                oldBrowserSettings.getAmiiboFiles())) {
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
        return null != filteredData ? filteredData.size() : 0;
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

    private ArrayList<Integer> mSectionPositions;

    @Override
    public int getSectionForPosition(int position) {
        return 0;
    }

    @Override
    public Object[] getSections() {
        List<String> sections = new ArrayList<>(36);
        if (getItemCount() > 0) {
            mSectionPositions = new ArrayList<>(36);
            for (int i = 0, size = filteredData.size(); i < size; i++) {
                Amiibo amiibo = filteredData.get(i);
                String section = null;
                switch (BrowserSettings.SORT.valueOf(settings.getSort())) {
                    case NAME:
                        if (null != amiibo.name) {
                            section = String.valueOf(amiibo.name.charAt(0)).toUpperCase();
                        }
                        break;
                    case CHARACTER:
                        if (null != amiibo.getCharacter()) {
                            section = String.valueOf(amiibo
                                    .getCharacter().name.charAt(0)).toUpperCase();
                        }
                        break;
                    case GAME_SERIES:
                        if (null != amiibo.getGameSeries()) {
                            section = String.valueOf(amiibo
                                    .getGameSeries().name.charAt(0)).toUpperCase();
                        }
                        break;
                    case AMIIBO_SERIES:
                        if (null != amiibo.getAmiiboSeries()) {
                            section = String.valueOf(amiibo
                                    .getAmiiboSeries().name.charAt(0)).toUpperCase();
                        }
                        break;
                    case AMIIBO_TYPE:
                        if (null != amiibo.getAmiiboType()) {
                            section = String.valueOf(amiibo
                                    .getAmiiboType().name.charAt(0)).toUpperCase();
                        }
                        break;
                }
                if (null != section && !sections.contains(section)) {
                    sections.add(section);
                    mSectionPositions.add(i);
                }
            }
        }
        return sections.toArray(new String[0]);
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        return mSectionPositions.get(sectionIndex);
    }

    @NonNull
    @Override
    public FoomiiboViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (VIEW.valueOf(viewType)) {
            case COMPACT:
                return new CompactViewHolder(parent, settings, listener);
            case LARGE:
                return new LargeViewHolder(parent, settings, listener);
            case IMAGE:
                return new ImageViewHolder(parent, settings, listener);
            case SIMPLE:
            default:
                return new SimpleViewHolder(parent, settings, listener);
        }
    }

    private void handleClickEvent(final FoomiiboViewHolder holder) {
        if (null != holder.listener) {
            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                if (foomiiboId.contains(holder.foomiibo.id)) {
                    foomiiboId.remove(holder.foomiibo.id);
                } else {
                    foomiiboId.add(holder.foomiibo.id);
                }
            } else {
                foomiiboId.clear();
            }
            holder.listener.onFoomiiboClicked(holder.itemView, holder.foomiibo);
        }
    }

    @Override
    public void onBindViewHolder(final FoomiiboViewHolder holder, int position) {
        final int clickPosition = hasStableIds() ? holder.getBindingAdapterPosition() : position;
        holder.itemView.setOnClickListener(view -> handleClickEvent(holder));
        if (null != holder.imageAmiibo) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
                    handleClickEvent(holder);
                else if (null != holder.listener)
                    holder.listener.onFoomiiboImageClicked(holder.foomiibo);
            });
        }
        holder.bind(getItem(clickPosition));
    }

    public void refresh() {
        this.getFilter().filter(settings.getQuery());
    }

    @Override
    public FoomiiboFilter getFilter() {
        if (null == this.filter) {
            this.filter = new FoomiiboFilter();
        }
        return this.filter;
    }

    class FoomiiboFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            String query = null != constraint ? constraint.toString() : "";
            FilterResults filterResults = new FilterResults();
            if (query.trim().isEmpty()) {
                filterResults.count = data.size();
                filterResults.values = data;
            }
            settings.setQuery(query);

            if (null != settings.getAmiiboManager())
                data = new ArrayList<>(settings.getAmiiboManager().amiibos.values());
            else
                data.clear();

            ArrayList<Amiibo> tempList = new ArrayList<>();
            String queryText = query.trim().toLowerCase();
            for (Amiibo amiibo : data) {
                if (settings.amiiboContainsQuery(amiibo, queryText)) tempList.add(amiibo);
            }
            filterResults.count = tempList.size();
            filterResults.values = tempList;

            return filterResults;
        }

        @SuppressWarnings("unchecked")
        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            if (null != filteredData && filteredData == filterResults.values) return;
            filteredData = (ArrayList<Amiibo>) filterResults.values;
            if (getItemCount() > 0) {
                Collections.sort(filteredData, new AmiiboComparator(settings));

                ArrayList<Amiibo> missingFiles = new ArrayList<>();
                HashSet<Long> amiiboIds = new HashSet<>();
                for (AmiiboFile amiiboFile : settings.getAmiiboFiles()) {
                    amiiboIds.add(amiiboFile.getId());
                }

                Iterator<Amiibo> iterator = filteredData.iterator();
                while (iterator.hasNext()) {
                    Amiibo amiibo = iterator.next();
                    if (!amiiboIds.contains(amiibo.id)) {
                        iterator.remove();
                        missingFiles.add(amiibo);
                    }
                }
                if (!missingFiles.isEmpty())
                    Collections.sort(missingFiles, new AmiiboComparator(settings));

                filteredData.addAll(0, missingFiles);
            }

            notifyDataSetChanged();
        }
    }

    protected static abstract class FoomiiboViewHolder extends RecyclerView.ViewHolder {
        private final BrowserSettings settings;
        private final OnFoomiiboClickListener listener;

        public final TextView txtError;
        public final TextView txtName;
        public final TextView txtTagId;
        public final TextView txtAmiiboSeries;
        public final TextView txtAmiiboType;
        public final TextView txtGameSeries;
        // public final TextView txtCharacter;
        public final TextView txtPath;
        public final AppCompatImageView imageAmiibo;

        Amiibo foomiibo = null;

        private final BoldSpannable boldSpannable = new BoldSpannable();

        CustomTarget<Bitmap> target = new CustomTarget<>() {
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

        public FoomiiboViewHolder(
                View itemView, BrowserSettings settings,
                OnFoomiiboClickListener listener) {
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

        void bind(final Amiibo item) {
            this.foomiibo = item;

            String tagInfo = null;
            String amiiboHexId;
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
                amiiboHexId = Amiibo.idToHex(amiibo.id);
                amiiboImageUrl = amiibo.getImageUrl();
                if (null != amiibo.name)
                    amiiboName = amiibo.name;
                if (null != amiibo.getAmiiboSeries())
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (null != amiibo.getAmiiboType())
                    amiiboType = amiibo.getAmiiboType().name;
                if (null != amiibo.getGameSeries())
                    gameSeries = amiibo.getGameSeries().name;
            } else {
                amiiboHexId = Amiibo.idToHex(amiiboId);
                tagInfo = "ID: " + amiiboHexId;
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
            }

            String query = settings.getQuery().toLowerCase();

            setFoomiiboInfoText(this.txtName, amiiboName, false);
            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                boolean hasTagInfo = null != tagInfo;
                if (hasTagInfo) {
                    setFoomiiboInfoText(this.txtError, tagInfo, false);
                } else {
                    this.txtError.setVisibility(View.GONE);
                }
                setFoomiiboInfoText(this.txtTagId, boldSpannable.StartsWith(amiiboHexId, query), hasTagInfo);
                setFoomiiboInfoText(this.txtAmiiboSeries,
                        boldSpannable.IndexOf(amiiboSeries, query), hasTagInfo);
                setFoomiiboInfoText(this.txtAmiiboType,
                        boldSpannable.IndexOf(amiiboType, query), hasTagInfo);
                setFoomiiboInfoText(this.txtGameSeries,
                        boldSpannable.IndexOf(gameSeries, query), hasTagInfo);
                // setAmiiboInfoText(this.txtCharacter,
                // boldText.Matching(character, query), hasTagInfo);
                this.txtPath.setVisibility(View.GONE);

                boolean expanded = foomiiboId.contains(foomiibo.id);
                itemView.findViewById(R.id.menu_options)
                        .setVisibility(expanded ? View.VISIBLE : View.GONE);
                itemView.findViewById(R.id.txtUsage)
                        .setVisibility(expanded ? View.VISIBLE : View.GONE);
                if (expanded) listener.onFoomiiboRebind(itemView, foomiibo);
            }
            if (null != this.imageAmiibo) {
                GlideApp.with(itemView).clear(target);
                if (null != amiiboImageUrl) {
                    GlideApp.with(itemView).asBitmap().load(amiiboImageUrl).into(target);
                }
            }
            if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000")) {
                if (null != txtTagId) txtTagId.setEnabled(false);
            }
        }

        void setFoomiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
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

    static class SimpleViewHolder extends FoomiiboViewHolder {
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings,
                                OnFoomiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener
            );
        }
    }

    static class CompactViewHolder extends FoomiiboViewHolder {
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings,
                                 OnFoomiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, listener
            );
        }
    }

    static class LargeViewHolder extends FoomiiboViewHolder {
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnFoomiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, listener
            );
        }
    }

    static class ImageViewHolder extends FoomiiboViewHolder {
        public ImageViewHolder(ViewGroup parent, BrowserSettings settings,
                               OnFoomiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_image_card, parent, false),
                    settings, listener
            );
        }
    }

    public interface OnFoomiiboClickListener {
        void onFoomiiboClicked(View itemView, Amiibo amiibo);
        void onFoomiiboRebind(View itemView, Amiibo amiibo);
        void onFoomiiboImageClicked(Amiibo amiibo);
    }
}
