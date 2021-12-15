package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.ServiceWorkerClientCompat;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WebActivity extends AppCompatActivity {

    public static String WEBSITE = "WEBSITE";

    private WebView mWebView;

    private ProgressDialog dialog;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_webview);

        mWebView = findViewById(R.id.webview_content);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        String action = getIntent().getAction();
        WebSettings webViewSettings = mWebView.getSettings();

        mWebView.setScrollbarFadingEnabled(true);
        webViewSettings.setLoadWithOverviewMode(true);
        webViewSettings.setUseWideViewPort(true);
        webViewSettings.setAllowFileAccess(true);
        webViewSettings.setAllowContentAccess(false);
        webViewSettings.setJavaScriptEnabled(true);
        webViewSettings.setDomStorageEnabled(true);
        webViewSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            webViewSettings.setPluginState(WebSettings.PluginState.ON);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final WebViewAssetLoader assetLoader =
                    new WebViewAssetLoader.Builder().addPathHandler("/assets/",
                            new AssetsPathHandler(WebActivity.this)).build();

            mWebView.setWebViewClient(new WebViewClientCompat() {
                @Override
                public WebResourceResponse shouldInterceptRequest(
                        WebView view, WebResourceRequest request) {
                    return assetLoader.shouldInterceptRequest(request.getUrl());
                }
            });
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
                ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                        new ServiceWorkerClientCompat() {
                    @Nullable
                    @Override
                    public WebResourceResponse shouldInterceptRequest(
                            @NonNull WebResourceRequest request) {
                        return assetLoader.shouldInterceptRequest(request.getUrl());
                    }
                });
            }
        }
        webViewSettings.setAllowFileAccessFromFileURLs(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
        webViewSettings.setAllowUniversalAccessFromFileURLs(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);

        JavaScriptInterface download = new JavaScriptInterface();
        mWebView.addJavascriptInterface(download, "Android");
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url.startsWith("blob") || url.startsWith("data")) {
                Log.d("DATA", url);
                mWebView.loadUrl(download.getBase64StringFromBlob(url, mimeType));
            }
        });

        if (null != action) {
            if (NFCIntent.ACTION_BROWSE_GITLAB.equals(action)) {
                webViewSettings.setUserAgentString(webViewSettings.getUserAgentString().replaceAll(
                        "(?i)" + Pattern.quote("android"), "TagMo"));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                mWebView.loadUrl("https://tagmo.gitlab.io/");
            }
        } else {
            webViewSettings.setBuiltInZoomControls(true);
            webViewSettings.setSupportZoom(true);

            if (getIntent().hasExtra(WEBSITE)) {
                webViewSettings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
                String url = getIntent().getStringExtra(WEBSITE);
                mWebView.loadUrl(url);
                return;
            }

            try (InputStream in = getContentResolver().openInputStream(getIntent().getData());
                 BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                StringBuilder total = new StringBuilder();
                String line;
                while (null != (line = r.readLine())) {
                    total.append(line).append("\n");
                }
                mWebView.loadData(total.toString(), getString(R.string.mimetype_text)
                        + "; charset=" + CharsetCompat.UTF_8.displayName(), null);
            } catch (Exception e) {
                Debug.Log(e);
                new Toasty(this).Short(R.string.fail_logcat);
                finish();
            }
        }
    }

    private class UnZip implements Runnable {
        File archive;
        File outputDir;

        UnZip(File ziparchive, File directory) {
            this.archive = ziparchive;
            this.outputDir = directory;
        }

        private final Handler handler = new Handler(Looper.getMainLooper());
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        public void run() {
            try {
                ZipInputStream zipIn = new ZipInputStream(new FileInputStream(this.archive));
                ZipEntry entry;
                while (null != (entry = zipIn.getNextEntry())) {
                    ZipEntry finalEntry = entry;
                    handler.post(() -> dialog.setMessage(
                            getString(R.string.unzip_item, finalEntry.getName())));
                    if (finalEntry.isDirectory()) {
                        if (!new File(outputDir, finalEntry.getName()).mkdirs())
                            throw new RuntimeException(
                                    getString(R.string.mkdir_failed, finalEntry.getName()));
                    } else {
                        FileOutputStream fileOut = new FileOutputStream(
                                new File(outputDir, finalEntry.getName()));
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zipIn.read(buffer)) != -1)
                            fileOut.write(buffer, 0, len);
                        fileOut.close();
                        zipIn.closeEntry();
                    }
                }
                zipIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                dialog.dismiss();
                this.archive.delete();
            }
        }
    }

    private void unzipFile(File zipFile) {
        dialog = ProgressDialog.show(this, "", "", true);
        new Thread(new UnZip(zipFile, Storage.getDownloadDir(
                "TagMo", "Downloads"))).start();
    }

    private void saveBinFile(byte[] tagData, String name) {
        try {
            File filePath = new File(Storage.getDownloadDir("TagMo", "Downloads"),
                    name + ".bin");
            FileOutputStream os = new FileOutputStream(filePath, false);
            os.write(tagData);
            os.flush();
        } catch (IOException e) {
            Debug.Log(e);
        }
    }

    private void setBinName(String base64File, String mimeType) {
        byte[] tagData = Base64.decode(base64File.replaceFirst(
                "^data:" + mimeType + ";base64,", ""), 0);
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        try {
            AmiiboManager amiiboManager = AmiiboManager.getAmiiboManager();
            input.setText(TagUtils.decipherFilename(amiiboManager, tagData));
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
        }
        Dialog backupDialog = dialog.setView(view).show();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            saveBinFile(tagData, input.getText().toString());
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
    }

    private class JavaScriptInterface {
        @SuppressWarnings("unused")
        @JavascriptInterface
        public void getBase64FromBlobData(String base64Data) throws IOException {
            convertBase64StringSave(base64Data);
        }
        public String getBase64StringFromBlob(String blobUrl, String mimeType) {
            if (blobUrl.startsWith("blob") || blobUrl.startsWith("data")) {
                return "javascript: var xhr = new XMLHttpRequest();" +
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
                        "xhr.send();";
            }
            return "javascript: console.log('Not a valid blob URL');";
        }
        private void convertBase64StringSave(String base64File) throws IOException {
            String zipType = getString(R.string.mimetype_zip);
            if (base64File.contains("data:" + zipType + ";")) {
                File filePath = new File(Storage.getDownloadDir("TagMo"), "download.zip");
                FileOutputStream os = new FileOutputStream(filePath, false);
                os.write(Base64.decode(base64File.replaceFirst(
                        "^data:" + zipType + ";base64,", ""), 0));
                os.flush();
                unzipFile(filePath);
            } else {
                String[] binTypes = getResources().getStringArray(R.array.mimetype_bin);
                for (String binType : binTypes) {
                    if (base64File.contains("data:" + binType + ";")) {
                        setBinName(base64File, binType);
                        break;
                    }
                }
            }
        }
    }
}
