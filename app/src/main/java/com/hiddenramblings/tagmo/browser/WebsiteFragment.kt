package com.hiddenramblings.tagmo.browser

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.webkit.*
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.hiddenramblings.tagmo.NFCIntent
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.amiibo.AmiiboManager.Companion.getAmiiboManager
import com.hiddenramblings.tagmo.eightbit.io.Debug.verbose
import com.hiddenramblings.tagmo.eightbit.io.Debug.warn
import com.hiddenramblings.tagmo.eightbit.io.Debug.isNewer
import com.hiddenramblings.tagmo.eightbit.io.Debug.isOlder
import com.hiddenramblings.tagmo.eightbit.os.Storage
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
import java.util.regex.Pattern
import java.util.zip.ZipFile

class WebsiteFragment : Fragment() {
    private val webHandler = Handler(Looper.getMainLooper())
    private var mWebView: WebView? = null
    private var dialog: ProgressDialog? = null
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
                configureWebView()
            }

            override fun onProviderInstallException() {
                Toasty(requireActivity()).Long(R.string.fail_ssl_update)
            }

            override fun onProviderInstallFailed() {
                Toasty(requireActivity()).Long(R.string.fail_ssl_update)
            }
        })
    }

    @SuppressLint("AddJavascriptInterface", "SetJavaScriptEnabled")
    private fun configureWebView() {
        val webViewSettings = mWebView!!.settings
        mWebView!!.isScrollbarFadingEnabled = true
        webViewSettings.loadWithOverviewMode = true
        webViewSettings.useWideViewPort = true
        webViewSettings.allowFileAccess = true
        webViewSettings.allowContentAccess = false
        webViewSettings.javaScriptEnabled = true
        webViewSettings.domStorageEnabled = true
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        if (isOlder(Build.VERSION_CODES.KITKAT))
            @Suppress("DEPRECATION")
            webViewSettings.pluginState = WebSettings.PluginState.ON
        if (isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            val assetLoader = WebViewAssetLoader.Builder().addPathHandler(
                "/assets/",
                AssetsPathHandler(requireContext())
            ).build()
            mWebView!!.webViewClient = object : WebViewClientCompat() {
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
        }
        @Suppress("DEPRECATION")
        webViewSettings.allowFileAccessFromFileURLs =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        @Suppress("DEPRECATION")
        webViewSettings.allowUniversalAccessFromFileURLs =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
        val download = JavaScriptInterface()
        mWebView!!.addJavascriptInterface(download, "Android")
        mWebView!!.setDownloadListener { url: String, _: String?, _: String?, mimeType: String, _: Long ->
            if (url.startsWith("blob") || url.startsWith("data")) {
                verbose(WebsiteFragment::class.java, url)
                mWebView!!.loadUrl(download.getBase64StringFromBlob(url, mimeType))
            }
        }
        loadWebsite(null)
    }

    fun loadWebsite(address: String?) {
        var website = address
        if (null != mWebView) {
            if (null == website) website = NFCIntent.SITE_GITLAB_README
            val webViewSettings = mWebView!!.settings
            if (website.startsWith(NFCIntent.SITE_GITLAB_README)) {
                webViewSettings.userAgentString = webViewSettings.userAgentString.replace(
                    ("(?i)" + Pattern.quote("android")).toRegex(), "TagMo"
                )
            }
            webViewSettings.setSupportZoom(true)
            webViewSettings.builtInZoomControls = true
            mWebView!!.loadUrl(website)
        } else {
            val delayedUrl = website
            webHandler.postDelayed({ loadWebsite(delayedUrl) }, TagMo.uiDelay.toLong())
        }
    }

    fun hasGoneBack() : Boolean {
        return if (mWebView!!.canGoBack()) {
            mWebView!!.goBack()
            true
        } else {
            false
        }
    }

    private inner class UnZip(var archive: File, var outputDir: File) :
        Runnable {
        @Throws(IOException::class)
        private fun decompress() {
            val zipIn = ZipFile(archive)
            val entries = zipIn.entries()
            while (entries.hasMoreElements()) {
                // get the zip entry
                val finalEntry = entries.nextElement()
                webHandler.post {
                    dialog!!.setMessage(getString(R.string.unzip_item, finalEntry.name))
                }
                if (finalEntry.isDirectory) {
                    val dir = File(
                        outputDir, finalEntry.name.replace("/", "")
                    )
                    if (!dir.exists() && !dir.mkdirs()) throw RuntimeException(
                        getString(R.string.mkdir_failed, dir.name)
                    )
                } else {
                    val `is` = zipIn.getInputStream(finalEntry)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Files.copy(`is`, Paths.get(outputDir.absolutePath, finalEntry.name))
                    } else {
                        val fileOut = FileOutputStream(
                            File(outputDir, finalEntry.name)
                        )
                        val buffer = ByteArray(8192)
                        var len: Int
                        while (`is`.read(buffer).also { len = it } != -1) fileOut.write(
                            buffer,
                            0,
                            len
                        )
                        fileOut.close()
                    }
                    `is`.close()
                }
            }
            zipIn.close()
        }

        override fun run() {
            try {
                decompress()
            } catch (e: IOException) {
                warn(e)
            } finally {
                dialog!!.dismiss()
                archive.delete()
            }
        }
    }

    private fun saveBinFile(tagData: ByteArray, name: String) {
        try {
            val filePath = File(
                Storage.getDownloadDir(
                    "TagMo", "Downloads"
                ), "$name.bin"
            )
            val os = FileOutputStream(filePath, false)
            os.write(tagData)
            os.flush()
        } catch (e: IOException) {
            warn(e)
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
            warn(e)
        } catch (e: JSONException) {
            warn(e)
        } catch (e: ParseException) {
            warn(e)
        }
        val backupDialog: Dialog = dialog.setView(view).create()
        view.findViewById<View>(R.id.button_save).setOnClickListener {
            saveBinFile(tagData, input.text.toString())
            backupDialog.dismiss()
        }
        view.findViewById<View>(R.id.button_cancel).setOnClickListener { backupDialog.dismiss() }
        backupDialog.show()
    }

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
                val filePath = File(Storage.getDownloadDir("TagMo"), "download.zip")
                val os = FileOutputStream(filePath, false)
                os.write(
                    Base64.decode(
                        base64File.replaceFirst(
                            "^data:$zipType;base64,".toRegex(), ""
                        ), 0
                    )
                )
                os.flush()
                webHandler.post {
                    dialog = ProgressDialog.show(
                        requireContext(), "", "", true
                    )
                }
                Thread(UnZip(
                        filePath, Storage.getDownloadDir("TagMo", "Downloads")
                )).start()
            } else {
                val binTypes = resources.getStringArray(R.array.mimetype_bin)
                for (binType in binTypes) {
                    if (base64File.contains("data:$binType;")) {
                        setBinName(base64File, binType)
                        break
                    }
                }
            }
        }
    }
}