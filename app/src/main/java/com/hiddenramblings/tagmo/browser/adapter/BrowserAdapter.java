package com.hiddenramblings.tagmo.browser.adapter;

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
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboFileComparator;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.nfc.TagUtils;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.settings.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.widget.BoldSpannable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BrowserAdapter
        extends RecyclerView.Adapter<BrowserAdapter.AmiiboViewHolder>
        implements Filterable, BrowserSettingsListener, SectionIndexer {

    private final BrowserSettings settings;
    private final OnAmiiboClickListener listener;
    private ArrayList<AmiiboFile> data = new ArrayList<>();
    private ArrayList<AmiiboFile> filteredData;
    private AmiiboFilter filter;
    boolean firstRun = true;
    private static final ArrayList<String> amiiboPath = new ArrayList<>();

    public BrowserAdapter(BrowserSettings settings, OnAmiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;

        this.filteredData = this.data;
        this.setHasStableIds(true);
    }

    public static void resetVisible() {
        amiiboPath.clear();
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
                BrowserSettings.hasFilterChanged(oldBrowserSettings, newBrowserSettings);

        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(),
                oldBrowserSettings.getAmiiboFiles())) {
            this.data = new ArrayList<>();
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
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (null != amiiboManager) {
                for (int i = 0, size = filteredData.size(); i < size; i++) {
                    long amiiboId = filteredData.get(i).getId();
                    Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
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
        }
        return sections.toArray(new String[0]);
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        return mSectionPositions.get(sectionIndex);
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

            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                if (amiiboPath.contains(uri) || amiiboPath.contains(path)) {
                    if (null != uri) amiiboPath.remove(uri);
                    if (null != path) amiiboPath.remove(path);
                } else {
                    if (null != uri) amiiboPath.add(uri);
                    if (null != path) amiiboPath.add(path);
                }
            } else {
                amiiboPath.clear();
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

            if (null != settings.getAmiiboFiles())
                data = new ArrayList<>(settings.getAmiiboFiles());
            else
                data.clear();

            ArrayList<AmiiboFile> tempList = new ArrayList<>();
            String queryText = query.trim().toLowerCase();
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            ArrayList<AmiiboFile> amiiboFiles = settings.getAmiiboFiles();
            for (AmiiboFile amiiboFile : amiiboFiles) {
                boolean add = false;
                if (null != amiiboManager) {
                    Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboFile.getId(),
                                null, null);
                    add = settings.amiiboContainsQuery(amiibo, queryText);
                }
                if (!add && null != amiiboFile.getDocUri())
                    add = pathContainsQuery(amiiboFile.getDocUri().toString(), queryText);
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
            return !query.isEmpty() && settings.isFilterEmpty()
                    && path.toLowerCase().contains(query);
        }

        @SuppressWarnings("unchecked")
        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            if (null != filteredData && filteredData == filterResults.values) return;
            filteredData = (ArrayList<AmiiboFile>) filterResults.values;
            if (getItemCount() > 0)
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

        private void setIsHighlighted(boolean isHighlighted) {
            View highlight = this.itemView.findViewById(R.id.highlight);
            if (isHighlighted) {
                highlight.setBackgroundResource(R.drawable.rounded_view);
            } else {
                highlight.setBackgroundResource(0);
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
            } else {
                amiiboHexId = TagUtils.amiiboIdToHex(amiiboId);
                tagInfo = "ID: " + amiiboHexId;
                amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
            }

            String query = settings.getQuery().toLowerCase();

            setAmiiboInfoText(this.txtName, amiiboName, false);
            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                boolean hasTagInfo = null != tagInfo;
                if (hasTagInfo) {
                    setAmiiboInfoText(this.txtError, tagInfo, false);
                } else {
                    this.txtError.setVisibility(View.GONE);
                }
                setAmiiboInfoText(this.txtTagId, boldSpannable.StartsWith(amiiboHexId, query), hasTagInfo);
                setAmiiboInfoText(this.txtAmiiboSeries,
                        boldSpannable.IndexOf(amiiboSeries, query), hasTagInfo);
                setAmiiboInfoText(this.txtAmiiboType,
                        boldSpannable.IndexOf(amiiboType, query), hasTagInfo);
                setAmiiboInfoText(this.txtGameSeries,
                        boldSpannable.IndexOf(gameSeries, query), hasTagInfo);

                if (null != item.getDocUri()) {
                    String relativeDocument = Storage.getRelativeDocument(
                            item.getDocUri().getUri()
                    );
                    boolean expanded = amiiboPath.contains(relativeDocument);
                    itemView.findViewById(R.id.menu_options)
                            .setVisibility(expanded ? View.VISIBLE : View.GONE);
                    itemView.findViewById(R.id.txtUsage)
                            .setVisibility(expanded ? View.VISIBLE : View.GONE);
                    if (expanded) listener.onAmiiboRebind(itemView, amiiboFile);

                    this.itemView.setEnabled(true);
                    this.txtPath.setText(boldSpannable.IndexOf(relativeDocument, query));
                    TypedValue a = new TypedValue();
                    this.txtPath.getContext().getTheme().resolveAttribute(
                            android.R.attr.textColor, a, true);
                    if (Debug.isNewer(Build.VERSION_CODES.Q) && a.isColorType()) {
                        this.txtPath.setTextColor(a.data);
                    } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        this.txtPath.setTextColor(a.data);
                    }
                    setIsHighlighted(relativeDocument.startsWith("/Foomiibo/"));
                } else if (null != item.getFilePath()) {
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
                    if (Debug.isNewer(Build.VERSION_CODES.Q) && a.isColorType()) {
                        this.txtPath.setTextColor(a.data);
                    } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        this.txtPath.setTextColor(a.data);
                    }
                    setIsHighlighted(relativeFile.startsWith("/Foomiibo/"));
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
