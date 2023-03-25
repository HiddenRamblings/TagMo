package com.hiddenramblings.tagmo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.hiddenramblings.tagmo.eightbit.os.Version

@GlideModule
class GlideTagModule : AppGlideModule() {
    private fun isConnectionWiFi(context: Context): Boolean {
        var result = false
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).run {
            if (Version.isMarshmallow) {
                getNetworkCapabilities(activeNetwork)?.run {
                    result = when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> false
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                activeNetworkInfo?.run {
                    result = type == ConnectivityManager.TYPE_WIFI
                            || type == ConnectivityManager.TYPE_ETHERNET
                }
            }
        }
        return result
    }


    private fun onlyRetrieveFromCache(context: Context, requestOptions: RequestOptions): RequestOptions {
        return when (Preferences(context.applicationContext).imageNetwork()) {
            IMAGE_NETWORK_NEVER -> { requestOptions.onlyRetrieveFromCache(true) }
            IMAGE_NETWORK_WIFI -> { requestOptions.onlyRetrieveFromCache(!isConnectionWiFi(context)) }
            else -> { requestOptions.onlyRetrieveFromCache(false) }
        }
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // Current size of AmiiboAPI images folder is 131 MB
        val diskCacheSizeBytes = 1024 * 1024 * 192 // 192 MB
        builder.setDiskCache(ExternalPreferredCacheDiskCacheFactory(
            context, diskCacheSizeBytes.toLong()
        ))
        builder.setLogLevel(Log.ERROR)
        builder.setDefaultRequestOptions(onlyRetrieveFromCache(
            context.applicationContext,
            RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        ))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    companion object {
        const val IMAGE_NETWORK_NEVER = "NEVER"
        const val IMAGE_NETWORK_WIFI = "WIFI_ONLY"
        const val IMAGE_NETWORK_ALWAYS = "ALWAYS"
    }
}