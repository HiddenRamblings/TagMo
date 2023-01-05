package com.hiddenramblings.tagmo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class GlideTagModule : AppGlideModule() {
    private fun isConnectionWiFi(context: Context): Boolean {
        var result = false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm?.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    result = when {
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> false
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> false
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            cm?.run {
                cm.activeNetworkInfo?.run {
                    result = type == ConnectivityManager.TYPE_WIFI
                            || type == ConnectivityManager.TYPE_ETHERNET
                }
            }
        }
        return result
    }


    private fun onlyRetrieveFromCache(context: Context, requestOptions: RequestOptions): RequestOptions {
        val imageNetworkSetting = Preferences(context.applicationContext).imageNetwork()
        return if (IMAGE_NETWORK_NEVER == imageNetworkSetting) {
            requestOptions.onlyRetrieveFromCache(true)
        } else if (IMAGE_NETWORK_WIFI == imageNetworkSetting) {
            requestOptions.onlyRetrieveFromCache(!isConnectionWiFi(context))
        } else {
            requestOptions.onlyRetrieveFromCache(false)
        }
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val diskCacheSizeBytes = 1024 * 1024 * 128 // 128 MB
        // The current size of the API image repo is 117.1 MB
        builder.setDiskCache(
            ExternalPreferredCacheDiskCacheFactory(
                context, diskCacheSizeBytes.toLong()
            )
        )
        builder.setLogLevel(Log.ERROR)
        val requestOptions = onlyRetrieveFromCache(
            context.applicationContext,
            RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        )
        builder.setDefaultRequestOptions(requestOptions)
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