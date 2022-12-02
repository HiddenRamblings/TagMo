package com.hiddenramblings.tagmo.browser.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
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
import com.hiddenramblings.tagmo.browser.BrowserSettings;
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW;
import com.hiddenramblings.tagmo.browser.Preferences;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.widget.BoldSpannable;

import java.util.ArrayList;
import java.util.Collections;

public class WriteTagAdapter extends RecyclerView.Adapter<WriteTagAdapter.AmiiboViewHolder>
        implements Filterable, BrowserSettingsListener {

    static Preferences mPrefs = new Preferences(TagMo.getContext());

    private final BrowserSettings settings;
    private OnAmiiboClickListener listener = null;
    private int listSize = 1;
    private ArrayList<AmiiboFile> amiiboFiles = new ArrayList<>();
    private ArrayList<AmiiboFile> filteredData;
    private AmiiboFilter filter;
    boolean firstRun;
    private final ArrayList<AmiiboFile> amiiboList = new ArrayList<>();

    public WriteTagAdapter(BrowserSettings settings) {
        this.settings = settings;

        firstRun = true;
        this.filteredData = this.amiiboFiles;
        this.setHasStableIds(true);
    }

    public void setListener(OnAmiiboClickListener listener, int listSize) {
        this.amiiboList.clear();
        this.listener = listener;
        this.listSize = listSize;
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings,
                                         BrowserSettings oldBrowserSettings) {
        boolean refresh = firstRun ||
                !BrowserSettings.equals(newBrowserSettings.getQuery(),
                        oldBrowserSettings.getQuery()) ||
                !BrowserSettings.equals(newBrowserSettings.getSort(),
                        oldBrowserSettings.getSort()) ||
                BrowserSettings.hasFilterChanged(oldBrowserSettings, newBrowserSettings);
        
        if (firstRun || !BrowserSettings.equals(newBrowserSettings.getAmiiboFiles(),
                oldBrowserSettings.getAmiiboFiles())) {
            this.amiiboFiles = new ArrayList<>();
            if (null != newBrowserSettings.getAmiiboFiles())
                this.amiiboFiles.addAll(newBrowserSettings.getAmiiboFiles());
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
        switch (VIEW.valueOf(viewType)) {
            case COMPACT:
                return new CompactViewHolder(parent, settings);
            case LARGE:
                return new LargeViewHolder(parent, settings);
            case IMAGE:
                return new ImageViewHolder(parent, settings);
            case SIMPLE:
            default:
                return new SimpleViewHolder(parent, settings);
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

    private void handleClickEvent(final AmiiboViewHolder holder, int position) {
        if (null != listener) {
            if (listSize > 1) {
                if (amiiboList.contains(holder.amiiboFile)) {
                    amiiboList.remove(filteredData.get(position));
                    setIsHighlighted(holder, false);
                } else {
                    amiiboList.add(filteredData.get(position));
                    setIsHighlighted(holder, true);
                }
                if (amiiboList.size() == listSize)
                    listener.onAmiiboListClicked(amiiboList);
            } else {
                listener.onAmiiboClicked(holder.amiiboFile);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final AmiiboViewHolder holder, int position) {
        final int clickPosition = hasStableIds() ? holder.getBindingAdapterPosition() : position;
        holder.itemView.setOnClickListener(view -> handleClickEvent(holder, clickPosition));
        if (null != holder.imageAmiibo) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
                    handleClickEvent(holder, clickPosition);
                else if (null != listener)
                    listener.onAmiiboImageClicked(holder.amiiboFile);
            });
        }
        holder.bind(getItem(clickPosition));
        setIsHighlighted(holder, amiiboList.contains(holder.amiiboFile));
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
            ArrayList<AmiiboFile> tempList = new ArrayList<>();
            String queryText = settings.getQuery().trim().toLowerCase();
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            for (AmiiboFile amiiboFile : amiiboFiles) {
                boolean add = false;
                if (null != amiiboManager) {
                    Amiibo amiibo = amiiboManager.amiibos.get(amiiboFile.getId());
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboFile.getId(), null, null);
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
            return !TextUtils.isEmpty(query) && settings.isFilterEmpty()
                    && path.toLowerCase().contains(query);
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            if (null != filteredData && filteredData == filterResults.values) return;
            //noinspection unchecked
            filteredData = (ArrayList<AmiiboFile>) filterResults.values;
            if (null != filteredData && null != settings)
                Collections.sort(filteredData, new AmiiboFileComparator(settings));
            notifyDataSetChanged();
        }
    }

    protected static abstract class AmiiboViewHolder extends RecyclerView.ViewHolder {
        private final BrowserSettings settings;

        public final TextView txtError;
        public final TextView txtName;
        public final TextView txtTagId;
        public final TextView txtAmiiboSeries;
        public final TextView txtAmiiboType;
        public final TextView txtGameSeries;
        // public final TextView txtCharacter;
        public final TextView txtPath;
        public final AppCompatImageView imageAmiibo;

        private AmiiboFile amiiboFile = null;

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

        public AmiiboViewHolder(View itemView, BrowserSettings settings) {
            super(itemView);

            this.settings = settings;

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
            if (null != amiiboManager) {
                amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null == amiibo)
                    amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
            }
            if (null != amiibo) {
                amiiboHexId = Amiibo.idToHex(amiibo.id);
                amiiboImageUrl = amiibo.getImageUrl();
                if (null != amiibo.name )
                    amiiboName = amiibo.name;
                if (null != amiibo.getAmiiboSeries() )
                    amiiboSeries = amiibo.getAmiiboSeries().name;
                if (null != amiibo.getAmiiboType() )
                    amiiboType = amiibo.getAmiiboType().name;
                if (null != amiibo.getGameSeries() )
                    gameSeries = amiibo.getGameSeries().name;
            } else {
                tagInfo = "ID: " + Amiibo.idToHex(amiiboId);
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
                setAmiiboInfoText(this.txtTagId, boldSpannable.startsWith(amiiboHexId, query), hasTagInfo);
                setAmiiboInfoText(this.txtAmiiboSeries,
                        boldSpannable.indexOf(amiiboSeries, query), hasTagInfo);
                setAmiiboInfoText(this.txtAmiiboType,
                        boldSpannable.indexOf(amiiboType, query), hasTagInfo);
                setAmiiboInfoText(this.txtGameSeries,
                        boldSpannable.indexOf(gameSeries, query), hasTagInfo);
                if (null != item.getDocUri()) {
                    this.itemView.setEnabled(true);
                    String relativeDocument = Storage.getRelativeDocument(
                            item.getDocUri().getUri()
                    );
                    this.txtPath.setText(boldSpannable.indexOf(relativeDocument, query));
                    TypedValue a = new TypedValue();
                    this.txtPath.getContext().getTheme().resolveAttribute(
                            android.R.attr.textColor, a, true);
                    if (Debug.INSTANCE.isNewer(Build.VERSION_CODES.Q) && a.isColorType()) {
                        this.txtPath.setTextColor(a.data);
                    } else if (a.type >= TypedValue.TYPE_FIRST_COLOR_INT
                            && a.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                        this.txtPath.setTextColor(a.data);
                    }
                } else if (null != item.getFilePath()) {
                    this.itemView.setEnabled(true);
                    String relativeFile = Storage.getRelativePath(item.getFilePath(),
                            mPrefs.preferEmulated()).replace(
                            mPrefs.browserRootFolder(), "");
                    this.txtPath.setText(boldSpannable.indexOf(relativeFile, query));
                    TypedValue a = new TypedValue();
                    this.txtPath.getContext().getTheme().resolveAttribute(
                            android.R.attr.textColor, a, true);
                    if (Debug.INSTANCE.isNewer(Build.VERSION_CODES.Q) && a.isColorType()) {
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
                GlideApp.with(imageAmiibo).clear(imageAmiibo);
                if (null != amiiboImageUrl) {
                    GlideApp.with(imageAmiibo).asBitmap().load(amiiboImageUrl).into(target);
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
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings
            );
        }
    }

    static class CompactViewHolder extends AmiiboViewHolder {
        public CompactViewHolder(ViewGroup parent, BrowserSettings settings) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings
            );
        }
    }

    static class LargeViewHolder extends AmiiboViewHolder {
        public LargeViewHolder(ViewGroup parent, BrowserSettings settings) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings
            );
        }
    }

    static class ImageViewHolder extends AmiiboViewHolder {
        public ImageViewHolder(ViewGroup parent, BrowserSettings settings) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_image_card, parent, false),
                    settings
            );
        }
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(AmiiboFile amiiboFile);
        void onAmiiboImageClicked(AmiiboFile amiiboFile);
        void onAmiiboListClicked(ArrayList<AmiiboFile> amiiboList);
    }
}