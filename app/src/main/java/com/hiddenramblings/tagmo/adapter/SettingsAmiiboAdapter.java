package com.hiddenramblings.tagmo.adapter;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import java.util.ArrayList;
import java.util.Collections;

public class SettingsAmiiboAdapter extends BaseAdapter {

    ArrayList<Amiibo> items;

    public SettingsAmiiboAdapter(ArrayList<Amiibo> items) {
        this.items = items;
        Collections.sort(items);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public long getItemId(int i) {
        return items.get(i).id;
    }

    @Override
    public Amiibo getItem(int i) {
        return items.get(i);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (null == convertView) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.amiibo_compact_card, parent, false);
            holder = new ViewHolder(convertView);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String amiiboHexId;
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        String amiiboImageUrl;

        Amiibo amiibo = getItem(position);
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
        //     character = amiibo.getCharacter().name;

        holder.txtError.setVisibility(android.view.View.GONE);
        setAmiiboInfoText(holder.txtName, amiiboName);
        setAmiiboInfoText(holder.txtTagId, amiiboHexId);
        setAmiiboInfoText(holder.txtAmiiboSeries, amiiboSeries);
        setAmiiboInfoText(holder.txtAmiiboType, amiiboType);
        setAmiiboInfoText(holder.txtGameSeries, gameSeries);
        // setAmiiboInfoText(holder.txtCharacter, character);
        holder.txtPath.setVisibility(android.view.View.GONE);

        if (null != holder.imageAmiibo) {
            GlideApp.with(convertView).clear(holder.target);
            if (null != amiiboImageUrl) {
                GlideApp.with(convertView).asBitmap().load(amiiboImageUrl).into(holder.target);
            }
        }

        return convertView;
    }

    private void setAmiiboInfoText(TextView textView, CharSequence text) {
        if (text.length() == 0) {
            textView.setText(R.string.unknown);
            textView.setTextColor(Color.RED);
        } else {
            textView.setText(text);
            textView.setTextColor(textView.getTextColors().getDefaultColor());
        }
    }

    protected static class ViewHolder {
        TextView txtError;
        TextView txtName;
        TextView txtTagId;
        TextView txtAmiiboSeries;
        TextView txtAmiiboType;
        TextView txtGameSeries;
        TextView txtCharacter;
        TextView txtPath;
        ImageView imageAmiibo;

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

        public ViewHolder(View view) {
            this.txtError = view.findViewById(R.id.txtError);
            this.txtName = view.findViewById(R.id.txtName);
            this.txtTagId = view.findViewById(R.id.txtTagId);
            this.txtAmiiboSeries = view.findViewById(R.id.txtAmiiboSeries);
            this.txtAmiiboType = view.findViewById(R.id.txtAmiiboType);
            this.txtGameSeries = view.findViewById(R.id.txtGameSeries);
            this.txtCharacter = view.findViewById(R.id.txtCharacter);
            this.txtPath = view.findViewById(R.id.txtPath);
            this.imageAmiibo = view.findViewById(R.id.imageAmiibo);

            view.setTag(this);
        }
    }
}
