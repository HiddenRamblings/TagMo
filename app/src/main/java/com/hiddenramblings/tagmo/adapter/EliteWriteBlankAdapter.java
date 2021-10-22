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
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfc.FileUtils;
import com.hiddenramblings.tagmo.nfc.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class EliteWriteBlankAdapter extends RecyclerView.Adapter<EliteWriteBlankAdapter.AmiiboVewHolder> {
    private final BrowserSettings settings;
    private final OnAmiiboClickListener listener;
    private final ArrayList<AmiiboFile> amiiboFiles;

    public EliteWriteBlankAdapter(BrowserSettings settings, OnAmiiboClickListener listener, ArrayList<AmiiboFile> amiiboFiles) {
        this.settings = settings;
        this.listener = listener;

        this.amiiboFiles = amiiboFiles;
        Collections.sort(this.amiiboFiles, new AmiiboComparator());
    }

    class AmiiboComparator implements Comparator<AmiiboFile> {
        @Override
        public int compare(AmiiboFile amiiboFile1, AmiiboFile amiiboFile2) {
            int value = 0;

            long amiiboId1 = amiiboFile1.getId();
            long amiiboId2 = amiiboFile2.getId();

            Amiibo amiibo1 = settings.getAmiiboManager().amiibos.get(amiiboId1);
            Amiibo amiibo2 = settings.getAmiiboManager().amiibos.get(amiiboId2);
            if (amiibo1 != null || amiibo2 != null) {
                if (amiibo1 == null)
                    value = 1;
                else if (amiibo2 == null)
                    value = -1;
                else
                    value = compareAmiiboName(amiibo1, amiibo2);
            }
            if (value == 0 && amiibo1 != null)
                value = amiibo1.compareTo(amiibo2);

            return value;
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
    }

    @Override
    public int getItemCount() {
        return amiiboFiles.size();
    }

    @Override
    public long getItemId(int i) {
        return amiiboFiles.get(i).getId();
    }

    public AmiiboFile getItem(int i) {
        return amiiboFiles.get(i);
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
                this.txtPath.setText(boldMatchingText(FileUtils.friendlyPath(item.getFilePath()), query));
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