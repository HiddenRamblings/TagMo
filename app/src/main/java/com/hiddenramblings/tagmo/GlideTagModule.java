package com.hiddenramblings.tagmo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.hiddenramblings.tagmo.settings.SettingsFragment;

@GlideModule
public class GlideTagModule extends AppGlideModule {

    public RequestOptions onlyRetrieveFromCache(Context context, RequestOptions requestOptions) {
        String imageNetworkSetting = TagMo.getPrefs().image_network_settings();
        if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
            return requestOptions.onlyRetrieveFromCache(true);
        } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
            ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return requestOptions.onlyRetrieveFromCache(null == activeNetwork
                    || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI);
        } else {
            return requestOptions.onlyRetrieveFromCache(false);
        }
    }

    @Override
    public void applyOptions(@NonNull Context context, GlideBuilder builder) {
        int diskCacheSizeBytes = 1024 * 1024 * 128; // 128 MB
        // The current size of the API image repo is 117.1 MB
        builder.setDiskCache(new ExternalPreferredCacheDiskCacheFactory(
                context, diskCacheSizeBytes));
        builder.setLogLevel(Log.ERROR);
        RequestOptions requestOptions = onlyRetrieveFromCache(context,
                new RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE));
        builder.setDefaultRequestOptions(requestOptions);
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}

