package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
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
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboFileComparator;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.widget.BoldSpannable;

import java.util.ArrayList;
import java.util.Collections;

public class BrowserAmiibosAdapter
        extends RecyclerView.Adapter<BrowserAmiibosAdapter.AmiiboViewHolder>
        implements Filterable, BrowserSettingsListener {

    private final BrowserSettings settings;
    private final OnAmiiboClickListener listener;
    private final ArrayList<AmiiboFile> data = new ArrayList<>();
    private ArrayList<AmiiboFile> filteredData;
    private AmiiboFilter filter;
    boolean firstRun = true;
    private static final ArrayList<String> amiiboPath = new ArrayList<>();

    public BrowserAmiibosAdapter(BrowserSettings settings, OnAmiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;

        this.filteredData = this.data;
        this.setHasStableIds(true);
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

        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(),
                oldBrowserSettings.getAmiiboFiles())) {
            this.data.clear();
            if (null != newBrowserSettings.getAmiiboFiles())
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
        return null != filteredData ? filteredData.size() : 0;
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

    private void handleClickEvent(final AmiiboViewHolder holder) {
        if (null != holder.listener) {
            String uri = null != holder.amiiboFile.getDocUri()
                    ? holder.amiiboFile.getDocUri().getUri().toString() : null;
            String path = null != holder.amiiboFile.getFilePath()
                    ? holder.amiiboFile.getFilePath().getAbsolutePath() : null;

            if (amiiboPath.contains(uri) || amiiboPath.contains(path)) {
                if (null != uri) amiiboPath.remove(uri);
                if (null != path) amiiboPath.remove(path);
            } else {
                if (null != uri) amiiboPath.add(uri);
                if (null != path) amiiboPath.add(path);
            }
            holder.listener.onAmiiboClicked(holder.itemView, holder.amiiboFile);
        }
    }

    @Override
    public void onBindViewHolder(final AmiiboViewHolder holder, int position) {
        holder.itemView.setOnClickListener(view -> handleClickEvent(holder));
        if (null != holder.imageAmiibo) {
            holder.imageAmiibo.setOnClickListener(view -> {
                    if (settings.getAmiiboView() == VIEW.IMAGE.getValue()) {
                        handleClickEvent(holder);
                    } else if (null != holder.listener) {
                        holder.listener.onAmiiboImageClicked(holder.amiiboFile);
                    }
            });
        }
        holder.bind(getItem(holder.getBindingAdapterPosition()));
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
            String query = null != constraint ? constraint.toString() : "";
            FilterResults filterResults = new FilterResults();
            if (query.trim().isEmpty()) {
                filterResults.count = data.size();
                filterResults.values = data;
            }
            settings.setQuery(query);

            data.clear();
            if (null != settings.getAmiiboFiles()) {
                data.addAll(settings.getAmiiboFiles());
            }

            ArrayList<AmiiboFile> tempList = new ArrayList<>();
            String queryText = query.trim().toLowerCase();
            for (AmiiboFile amiiboFile : data) {
                boolean add = false;
                AmiiboManager amiiboManager = settings.getAmiiboManager();
                if (null != amiiboManager) {
                    Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboFile.getId(),
                                null, null);
                    add = settings.amiiboContainsQuery(amiibo, queryText);
                }
                if (!add && null != amiiboFile.getFilePath())
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

        @SuppressWarnings("unchecked")
        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            if (null != filteredData && filteredData == filterResults.values) return;
            filteredData = (ArrayList<AmiiboFile>) filterResults.values;
            if (null != filteredData && !filteredData.isEmpty())
                Collections.sort(filteredData, new AmiiboFileComparator(settings));
            notifyDataSetChanged();
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
        public final AppCompatImageView imageAmiibo;

        AmiiboFile amiiboFile = null;

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
            String amiiboHexId;
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            // String character = "";
            String amiiboImageUrl;

            long amiiboId = item.getId();
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
                if (null != amiibo.name)
                    amiiboName = amiibo.name;
                if (null != amiibo.getAmiiboSeries())
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (null != amiibo.getAmiiboType())
                    amiiboType = amiibo.getAmiiboType().name;
                if (null != amiibo.getGameSeries())
                    gameSeries = amiibo.getGameSeries().name;
                // if (null != amiibo.getCharacter())
                //     gameSeries = amiibo.getCharacter().name;
            } else {
                amiiboHexId = TagUtils.amiiboIdToHex(amiiboId);
                tagInfo = "ID: " + amiiboHexId;
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

                if (null != item.getFilePath()) {
                    boolean expanded = amiiboPath.contains(item.getFilePath().getAbsolutePath());
                    itemView.findViewById(R.id.menu_options)
                            .setVisibility(expanded ? View.VISIBLE : View.GONE);
                    itemView.findViewById(R.id.txtUsage)
                            .setVisibility(expanded ? View.VISIBLE : View.GONE);
                    if (expanded) listener.onAmiiboRebind(itemView, amiiboFile);

                    String relativeFile = Storage.getRelativePath(item.getFilePath(),
                            TagMo.getPrefs().preferEmulated().get()).replace(
                            TagMo.getPrefs().browserRootFolder().get(), "");
                    this.itemView.setEnabled(true);
                    this.txtPath.setText(boldSpannable.IndexOf(relativeFile, query));
                    TypedValue a = new TypedValue();
                    this.txtPath.getContext().getTheme().resolveAttribute(
                            android.R.attr.textColor, a, true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && a.isColorType()) {
                        this.txtPath.setTextColor(a.data);
                    } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        this.txtPath.setTextColor(a.data);
                    }
                } else if (null != item.getDocUri()) {
                    String amiiboUri = item.getDocUri().getUri().toString();
                    boolean expanded = amiiboPath.contains(amiiboUri);
                    itemView.findViewById(R.id.menu_options)
                            .setVisibility(expanded ? View.VISIBLE : View.GONE);
                    itemView.findViewById(R.id.txtUsage)
                            .setVisibility(expanded ? View.VISIBLE : View.GONE);
                    if (expanded) listener.onAmiiboRebind(itemView, amiiboFile);

                    this.itemView.setEnabled(true);
                    this.txtPath.setText(boldSpannable.IndexOf(amiiboUri, query));
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
                    this.itemView.setEnabled(false);
                    this.txtPath.setText("");
                    this.txtPath.setTextColor(this.txtPath.getResources().getColor(R.color.tag_text));
                }
                this.txtPath.setVisibility(View.VISIBLE);
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
        void onAmiiboClicked(View itemView, AmiiboFile amiiboFile);
        void onAmiiboRebind(View itemView, AmiiboFile amiiboFile);
        void onAmiiboImageClicked(AmiiboFile amiiboFile);
    }
}
