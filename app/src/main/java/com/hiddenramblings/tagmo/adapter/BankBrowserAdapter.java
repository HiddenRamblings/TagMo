package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

import java.util.ArrayList;

public class BankBrowserAdapter extends RecyclerView.Adapter<BankBrowserAdapter.AmiiboVewHolder>
        implements BrowserSettings.BrowserSettingsListener {

    private final BrowserSettings settings;
    private final OnAmiiboClickListener listener;
    private ArrayList<Amiibo> amiibos = new ArrayList<>();

    public BankBrowserAdapter(BrowserSettings settings, OnAmiiboClickListener listener) {
        this.settings = settings;
        this.listener = listener;
    }

    public void setAmiibos(ArrayList<Amiibo> amiibos) {
        this.amiibos = amiibos;
    }

    @Override
    public void onBrowserSettingsChanged(BrowserSettings newBrowserSettings, BrowserSettings oldBrowserSettings) {

    }

    @Override
    public int getItemCount() {
        return amiibos.size();
    }

    @Override
    public long getItemId(int i) {
        return amiibos.get(i).id;
    }

    public Amiibo getItem(int i) {
        return amiibos.get(i);
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
    public void onBindViewHolder(@NonNull final AmiiboVewHolder holder, int position) {
        View highlight = holder.itemView.findViewById(R.id.highlight);
        if (TagMo.getPrefs().eliteActiveBank().get() == position) {
            highlight.setBackgroundColor(ContextCompat.getColor(TagMo.getContext(),
                    TagMo.isDarkTheme() ? android.R.color.holo_green_dark
                            : android.R.color.holo_green_light));
        } else {
            highlight.setBackgroundColor(ContextCompat.getColor(
                    TagMo.getContext(), android.R.color.transparent));
        }
        holder.itemView.setOnClickListener(view -> {
            if (holder.listener != null)
                holder.listener.onAmiiboClicked(holder.amiiboItem, position);
        });
        if (holder.imageAmiibo != null) {
            holder.imageAmiibo.setOnClickListener(view -> {
                if (holder.listener != null)
                    holder.listener.onAmiiboImageClicked(holder.amiiboItem, position);
            });
        }
        holder.bind(getItem(position));
    }

    protected static abstract class AmiiboVewHolder extends RecyclerView.ViewHolder {

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

        Amiibo amiiboItem = null;

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

        @SuppressLint("SetTextI18n")
        void bind(final Amiibo amiibo) {
            this.amiiboItem = amiibo;

            String amiiboHexId = "";
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            // String character = "";
            String amiiboImageUrl = null;
            boolean isAmiibo = amiibo != null;

            if (isAmiibo) {
                this.amiiboItem.bank = getAbsoluteAdapterPosition();
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
            }

            String query = settings.getQuery().toLowerCase();
            String value = String.valueOf(getAbsoluteAdapterPosition() + 1);

            this.txtError.setVisibility(View.GONE);
            if (isAmiibo) {
                setAmiiboInfoText(this.txtName, value + ": " + amiiboName);
                setAmiiboInfoText(this.txtTagId, boldStartText(amiiboHexId, query));
                setAmiiboInfoText(this.txtAmiiboSeries, boldMatchingText(amiiboSeries, query));
                setAmiiboInfoText(this.txtAmiiboType, boldMatchingText(amiiboType, query));
                setAmiiboInfoText(this.txtGameSeries, boldMatchingText(gameSeries, query));
                // setAmiiboInfoText(this.txtCharacter, boldMatchingText(character, query));
            } else {
                this.txtName.setVisibility(View.VISIBLE);
                this.txtName.setText(TagMo.getStringRes(R.string.blank_bank, value));
                this.txtTagId.setVisibility(View.GONE);
                this.txtAmiiboSeries.setVisibility(View.GONE);
                this.txtAmiiboType.setVisibility(View.GONE);
                this.txtGameSeries.setVisibility(View.GONE);
                // this.txtCharacter.setVisibility(View.GONE);
            }

            if (this.imageAmiibo != null) {
                this.imageAmiibo.setVisibility(View.GONE);
                Glide.with(itemView).clear(target);
                Glide.with(itemView)
                        .setDefaultRequestOptions(onlyRetrieveFromCache())
                        .asBitmap()
                        .load(amiiboImageUrl != null ? amiiboImageUrl: R.mipmap.ic_launcher)
                        .into(target);
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
                str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                        i, j, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        private SpannableStringBuilder boldStartText(String text, String query) {
            SpannableStringBuilder str = new SpannableStringBuilder(text);
            if (!query.isEmpty() && text.toLowerCase().startsWith(query)) {
                str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                        0, query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return str;
        }

        private void setAmiiboInfoText(TextView textView, CharSequence text) {
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

    static class SimpleViewHolder extends AmiiboVewHolder {
        public SimpleViewHolder(ViewGroup parent, BrowserSettings settings,
                                OnAmiiboClickListener listener) {
            super(
                    LayoutInflater.from(parent.getContext()).inflate(
                            R.layout.amiibo_simple_card, parent, false),
                    settings, listener
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
    }

    public interface OnAmiiboClickListener {
        void onAmiiboClicked(Amiibo amiibo, int position);

        void onAmiiboImageClicked(Amiibo amiibo, int position);
    }
}
