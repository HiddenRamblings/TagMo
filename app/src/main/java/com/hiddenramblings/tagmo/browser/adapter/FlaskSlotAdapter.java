package com.hiddenramblings.tagmo.browser.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.hiddenramblings.tagmo.amiibo.FlaskTag;
import com.hiddenramblings.tagmo.browser.BrowserSettings;
import com.hiddenramblings.tagmo.browser.BrowserSettings.BrowserSettingsListener;
import com.hiddenramblings.tagmo.browser.BrowserSettings.VIEW;

import java.util.ArrayList;

public class FlaskSlotAdapter
        extends RecyclerView.Adapter<FlaskSlotAdapter.FlaskViewHolder>
        implements BrowserSettingsListener {

    private final BrowserSettings settings;
    private final OnAmiiboClickListener listener;
    private ArrayList<Amiibo> flaskAmiibo = new ArrayList<>();

    public FlaskSlotAdapter(BrowserSettings settings, OnAmiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;
    }

    public void setFlaskAmiibo(ArrayList<Amiibo> amiibo) {
        this.flaskAmiibo = amiibo;
    }
    public void addFlaskAmiibo(ArrayList<Amiibo> amiibo) {
        this.flaskAmiibo.addAll(amiibo);
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings,
                                         BrowserSettings oldBrowserSettings) { }

    @Override
    public int getItemCount() {
        return null != flaskAmiibo ? flaskAmiibo.size() : 0;
    }

    @Override
    public long getItemId(int i) {
        return Long.parseLong(flaskAmiibo.get(i).getFlaskTail());
    }

    public Amiibo getItem(int i) {
        return flaskAmiibo.get(i);
    }

    @Override
    public int getItemViewType(int position) {
        return settings.getAmiiboView();
    }

    @NonNull
    @Override
    public FlaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

    @Override
    public void onBindViewHolder(final FlaskViewHolder holder, int position) {
        View highlight = holder.itemView.findViewById(R.id.highlight);
        if (TagMo.getPrefs().flaskActiveSlot() == position) {
            highlight.setBackgroundResource(R.drawable.cardview_outline);
        } else {
            highlight.setBackgroundResource(0);
        }
        holder.itemView.setOnClickListener(view -> {
            if (null != holder.listener) {
                holder.listener.onAmiiboClicked(holder.amiibo);
            }
        });
        if (null != holder.imageAmiibo) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (null != holder.listener) {
                    if (settings.getAmiiboView() == VIEW.IMAGE.getValue())
                        holder.listener.onAmiiboClicked(holder.amiibo);
                    else
                        holder.listener.onAmiiboImageClicked(holder.amiibo);
                }
            });
        }
        holder.bind(getItem(position));
    }

    protected static abstract class FlaskViewHolder extends RecyclerView.ViewHolder {
        private final BrowserSettings settings;
        private final OnAmiiboClickListener listener;

        public final TextView txtError;
        public final TextView txtName;
        public final TextView txtTagId;
        public final TextView txtAmiiboSeries;
        public final TextView txtAmiiboType;
        public final TextView txtGameSeries;
        public final TextView txtPath;
        public final AppCompatImageView imageAmiibo;

        Amiibo amiibo = null;

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

        public FlaskViewHolder(
                View itemView, BrowserSettings settings,
                OnAmiiboClickListener listener) {
            super(itemView);

            this.listener = listener;
            this.settings = settings;

            this.txtError = itemView.findViewById(R.id.txtError);
            this.txtName = itemView.findViewById(R.id.txtName);
            this.txtTagId = itemView.findViewById(R.id.txtTagId);
            this.txtAmiiboSeries = itemView.findViewById(R.id.txtAmiiboSeries);
            this.txtAmiiboType = itemView.findViewById(R.id.txtAmiiboType);
            this.txtGameSeries = itemView.findViewById(R.id.txtGameSeries);
            this.txtPath = itemView.findViewById(R.id.txtPath);
            this.imageAmiibo = itemView.findViewById(R.id.imageAmiibo);
        }

        void bind(final Amiibo item) {
            this.amiibo = item;

            String amiiboHexId;
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            String amiiboImageUrl = null;

            if (amiibo instanceof FlaskTag)  {
                setAmiiboInfoText(txtName, TagMo.getContext().getString(R.string.blank_tag));
            } else {
                setAmiiboInfoText(txtName, amiibo.name);
                amiiboImageUrl = amiibo.getImageUrl();
            }
            if (settings.getAmiiboView() != VIEW.IMAGE.getValue()) {
                this.txtError.setVisibility(View.GONE);
                this.txtPath.setVisibility(View.GONE);
                if (amiibo instanceof FlaskTag) {
                    this.txtTagId.setVisibility(View.GONE);
                    this.txtAmiiboSeries.setVisibility(View.GONE);
                    this.txtAmiiboType.setVisibility(View.GONE);
                    this.txtGameSeries.setVisibility(View.GONE);
                } else {
                    amiiboHexId = Amiibo.idToHex(amiibo.id);
                    if (null != amiibo.getAmiiboSeries())
                        amiiboSeries = amiibo.getAmiiboSeries().name;
                    if (null != amiibo.getAmiiboType())
                        amiiboType = amiibo.getAmiiboType().name;
                    if (null != amiibo.getGameSeries())
                        gameSeries = amiibo.getGameSeries().name;

                    this.txtTagId.setVisibility(View.VISIBLE);
                    this.txtAmiiboSeries.setVisibility(View.VISIBLE);
                    this.txtAmiiboType.setVisibility(View.VISIBLE);
                    this.txtGameSeries.setVisibility(View.VISIBLE);
                    setAmiiboInfoText(txtTagId, amiiboHexId);
                    setAmiiboInfoText(txtAmiiboSeries, amiiboSeries);
                    setAmiiboInfoText(txtAmiiboType, amiiboType);
                    setAmiiboInfoText(txtGameSeries, gameSeries);
                    if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000")) {
                        txtTagId.setEnabled(false);
                    }
                }
            }

            if (null == amiiboImageUrl) {
                this.imageAmiibo.setImageResource(R.mipmap.ic_launcher_round);
                this.imageAmiibo.setVisibility(View.VISIBLE);
            } else if (null != this.imageAmiibo) {
                GlideApp.with(itemView).clear(target);
                GlideApp.with(itemView).asBitmap().load(amiiboImageUrl).into(target);
            }
        }

        void setAmiiboInfoText(TextView textView, CharSequence text) {
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

    static class SimpleViewHolder extends FlaskViewHolder {
        public SimpleViewHolder(
                ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener
        ) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener
            );
        }
    }

    static class CompactViewHolder extends FlaskViewHolder {
        public CompactViewHolder(
                ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener
        ) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_compact_card, parent, false),
                    settings, listener
            );
        }
    }

    static class LargeViewHolder extends FlaskViewHolder {
        public LargeViewHolder(
                ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener
        ) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_large_card, parent, false),
                    settings, listener
            );
        }
    }

    static class ImageViewHolder extends FlaskViewHolder {
        public ImageViewHolder(
                ViewGroup parent, BrowserSettings settings, OnAmiiboClickListener listener
        ) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_image_card, parent, false),
                    settings, listener
            );
        }
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(Amiibo amiibo);

        void onAmiiboImageClicked(Amiibo amiibo);
    }
}
