package com.hiddenramblings.tagmo.wave9

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.net.http.SslError
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.*
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.eightbit.os.Version
import com.hiddenramblings.tagmo.parcelable


class DimensionActivity : AppCompatActivity() {

    private lateinit var mWebView: WebView
    private val jsInterface = "AndroidApp"

    private var mFilters: Array<IntentFilter> = arrayOf()
    private var mPendingIntent: PendingIntent? = null

    var nfc: NfcAdapter? = null
    var tag: Tag? = null
    private var mTechLists: Array<Array<String>> = arrayOf()

    @SuppressLint("AddJavascriptInterface", "SetJavaScriptEnabled")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_webview)

        setResult(RESULT_CANCELED)

        mWebView = findViewById(R.id.webview_content)

        mPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            if (Version.isSnowCone)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            mFilters = arrayOf(IntentFilter("android.nfc.action.NDEF_DISCOVERED").apply {
                addDataType("*/*")
            })
            mTechLists = arrayOf(arrayOf(NfcA::class.java.name))
            nfc = NfcAdapter.getDefaultAdapter(this)
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("fail", e)
        }

        val webViewSettings = mWebView.settings
        mWebView.isScrollbarFadingEnabled = true
        webViewSettings.allowFileAccess = true
        webViewSettings.allowContentAccess = true
        webViewSettings.javaScriptEnabled = true
        webViewSettings.domStorageEnabled = true
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        val assetLoader = WebViewAssetLoader.Builder().addPathHandler(
            "/assets/", WebViewAssetLoader.AssetsPathHandler(this)
        ).build()
        if (Version.isLollipop) {
            webViewSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mWebView.webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val allowedHosts: ArrayList<String> = ArrayList()
                    allowedHosts.add("appassets.androidplatform.net")
                    allowedHosts.add("android_asset")
                    allowedHosts.add("127.0.0.1")
                    allowedHosts.add("cdn.jsdelivr.net")
                    if (allowedHosts.contains(request.url.host) ||
                        request.url.host?.startsWith("192.168.") == true)
                        return false

                    view.evaluateJavascript(
                        "(function(){var err = 'ACCESS TO URL ${request.url} DENIED';" +
                                "console.error(err);" +
                                "if(window.appErrorHandler) window.appErrorHandler(err);})();"
                    ) { }
                    return true
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
            WebView.setWebContentsDebuggingEnabled(true)
        } else {
            @Suppress("deprecation")
            webViewSettings.allowFileAccessFromFileURLs = true
            @Suppress("deprecation")
            webViewSettings.allowUniversalAccessFromFileURLs = true
        }
        mWebView.addJavascriptInterface(JSAPI(this, mWebView), jsInterface)
        webViewSettings.setSupportZoom(true)
        webViewSettings.builtInZoomControls = true
        mWebView.loadUrl(
            if (Version.isLollipop)
                "https://appassets.androidplatform.net/assets/wave9/index.html"
            else "file:///android_asset/wave9/index.html"
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        this.nfc?.enableForegroundDispatch(
            this, this.mPendingIntent, this.mFilters, this.mTechLists
        )
    }

    override fun onPause() {
        super.onPause()
        this.nfc?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.tag = intent.parcelable<Parcelable>(NfcAdapter.EXTRA_TAG) as? Tag
        val params: Array<Any> = arrayOf(0)
        val stringBuilder = StringBuilder()
        stringBuilder.append("javascript:try{(window.$jsInterface.tagDetected||")
        stringBuilder.append("console.warn.bind(console,'UNHANDLED','$jsInterface.tagDetected'))(")
        params.forEach { stringBuilder.append("'${if (it is String) it else it.toString()}',") }
        stringBuilder.append("'')}catch(error){console.error('ANDROID APP ERROR',error);}")
        mWebView.loadUrl(stringBuilder.toString())
    }
}