package com.hiddenramblings.tagmo.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.net.http.SslError
import android.os.*
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.webkit.*
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.hiddenramblings.tagmo.BrowserActivity
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.getAmiiboManager
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.eightbit.os.Storage
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.eightbit.util.UnZip
import com.hiddenramblings.tagmo.eightbit.widget.ProgressAlert
import com.hiddenramblings.tagmo.nfctech.TagArray
import com.hiddenramblings.tagmo.security.SecurityHandler
import com.hiddenramblings.tagmo.widget.Toasty
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.text.ParseException
import java.util.zip.ZipFile


class WebsiteFragment : Fragment() {
    private val webHandler = Handler(Looper.getMainLooper())
    private var mWebView: WebView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_webview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mWebView = view.findViewById(R.id.webview_content)
        SecurityHandler(requireActivity(), object : SecurityHandler.ProviderInstallListener {
            override fun onProviderInstalled() {
                configureWebView(mWebView)
            }

            override fun onProviderInstallException() {
                Toasty(requireContext()).Long(R.string.fail_ssl_update)
                configureWebView(mWebView)
            }

            override fun onProviderInstallFailed() {
                Toasty(requireContext()).Long(R.string.fail_ssl_update)
                configureWebView(mWebView)
            }
        })
    }

    @SuppressLint("AddJavascriptInterface", "SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView?) {
        if (null == webView) return
        val webViewSettings = webView.settings
        webView.isScrollbarFadingEnabled = true
        webViewSettings.loadWithOverviewMode = true
        webViewSettings.useWideViewPort = true
        webViewSettings.allowFileAccess = true
        webViewSettings.allowContentAccess = false
        webViewSettings.javaScriptEnabled = true
        webViewSettings.domStorageEnabled = true
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        if (Version.isLollipop) {
            val assetLoader = WebViewAssetLoader.Builder().addPathHandler(
                "/assets/",
                AssetsPathHandler(requireContext())
            ).build()
            webView.webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    return if (request.url.lastPathSegment.equals("donate.html")) {
                        (requireActivity() as BrowserActivity).showDonationPanel()
                        true
                    } else {
                        super.shouldOverrideUrlLoading(view, request)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?, handler: SslErrorHandler, error: SslError?
                ) {
                    // handler.proceed()
                    handler.cancel()
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                    object : ServiceWorkerClientCompat() {
                        override fun shouldInterceptRequest(
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }
                    })
            }
        } else {
            @Suppress("deprecation")
            webViewSettings.allowFileAccessFromFileURLs = true
            @Suppress("deprecation")
            webViewSettings.allowUniversalAccessFromFileURLs = true
        }
        val download = JavaScriptInterface()
        webView.addJavascriptInterface(download, "Android")
        webView.setDownloadListener { url: String, _: String?, _: String?, mimeType: String, _: Long ->
            if (url.startsWith("blob") || url.startsWith("data")) {
                Debug.info(WebsiteFragment::class.java, url)
                webView.loadUrl(download.getBase64StringFromBlob(url, mimeType))
            }
        }
        loadWebsite(null)
    }

    fun loadWebsite(address: String?) {
        var website = address
        if (null != mWebView) {
            if (null == website) website = WEBSITE_README
            val webViewSettings = mWebView?.settings
            if (null != webViewSettings) {
                webViewSettings.setSupportZoom(true)
                webViewSettings.builtInZoomControls = true
            }
            mWebView?.loadUrl(website)
        } else {
            val delayedUrl = website
            webHandler.postDelayed({ loadWebsite(delayedUrl) }, TagMo.uiDelay.toLong())
        }
    }

    fun hasGoneBack() : Boolean {
        return if (mWebView?.canGoBack() == true) {
            mWebView?.goBack()
            true
        } else {
            false
        }
    }

    private fun saveBinFile(tagData: ByteArray, name: String) {
        try {
            val filePath = File(
                Storage.getDownloadDir("TagMo", "Downloads"), "$name.bin"
            )
            val os = FileOutputStream(filePath, false)
            os.write(tagData)
            os.flush()
        } catch (e: IOException) {
            Debug.warn(e)
        }
    }

    private fun setBinName(base64File: String, mimeType: String) {
        val tagData =
            Base64.decode(base64File.replaceFirst("^data:$mimeType;base64,".toRegex(), ""), 0)
        val view = layoutInflater.inflate(R.layout.dialog_save_item, null)
        val dialog = AlertDialog.Builder(requireContext())
        val input = view.findViewById<EditText>(R.id.save_item_entry)
        try {
            val amiiboManager = getAmiiboManager(requireContext().applicationContext)
            input.setText(TagArray.decipherFilename(amiiboManager, tagData, true))
        } catch (e: IOException) {
            Debug.warn(e)
        } catch (e: JSONException) {
            Debug.warn(e)
        } catch (e: ParseException) {
            Debug.warn(e)
        }
        val backupDialog: Dialog = dialog.setView(view).create()
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            saveBinFile(tagData, input.text.toString())
            backupDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener { backupDialog.dismiss() }
        backupDialog.show()
    }

    @Suppress("unused")
    private inner class JavaScriptInterface {
        @JavascriptInterface
        @Throws(IOException::class)
        fun getBase64FromBlobData(base64Data: String) {
            convertBase64StringSave(base64Data)
        }

        fun getBase64StringFromBlob(blobUrl: String, mimeType: String): String {
            return if (blobUrl.startsWith("blob") || blobUrl.startsWith("data")) {
                "javascript: var xhr = new XMLHttpRequest();" +
                        "xhr.open('GET', '" + blobUrl + "', true);" +
                        "xhr.setRequestHeader('Content-type','" + mimeType + "');" +
                        "xhr.responseType = 'blob';" +
                        "xhr.onload = function(e) {" +
                        "  if (this.status == 200) {" +
                        "    var blobFile = this.response;" +
                        "    var reader = new FileReader();" +
                        "    reader.readAsDataURL(blobFile);" +
                        "    reader.onloadend = function() {" +
                        "      base64data = reader.result;" +
                        "      Android.getBase64FromBlobData(base64data);" +
                        "    }" +
                        "  }" +
                        "};" +
                        "xhr.send();"
            } else "javascript: console.log('Not a valid blob URL');"
        }

        @Throws(IOException::class)
        private fun convertBase64StringSave(base64File: String) {
            val zipType = getString(R.string.mimetype_zip)
            if (base64File.contains("data:$zipType;")) {
                val filePath = File(TagMo.downloadDir, "download.zip")
                FileOutputStream(filePath, false).use {
                    it.write(Base64.decode(base64File.replaceFirst(
                        "^data:$zipType;base64,".toRegex(), ""
                    ), 0))
                    it.flush()
                }
                Thread(UnZip(
                    requireContext(),
                    filePath,
                    Storage.getDownloadDir("TagMo", "Downloads")
                )).start()
            } else {
                resources.getStringArray(R.array.mimetype_bin).find { binType ->
                    base64File.contains("data:$binType;")
                }?.let {
                    setBinName(base64File, it)
                }
            }
        }
    }

    companion object {
        private const val WEBSITE_README = "https://tagmo.gitlab.io/"
    }
}