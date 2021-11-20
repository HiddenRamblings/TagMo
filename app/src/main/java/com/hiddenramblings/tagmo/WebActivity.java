package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
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

import com.eightbit.io.Debug;
import com.eightbit.os.Storage;
import com.hiddenramblings.tagmo.TagMo.Website;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
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

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_webview)
public class WebActivity extends AppCompatActivity {

    public static String WEBSITE = "WEBSITE";

    @ViewById(R.id.webview_content)
    WebView mWebView;

    private ProgressDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @AfterViews
    void afterViews() {
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
                @Override
                public void onReceivedHttpError (
                        @NonNull WebView view, @NonNull WebResourceRequest request,
                        @NonNull WebResourceResponse errorResponse) {
                    if (errorResponse.getStatusCode() == 404) {
                        if (action != null) {
                            if (TagMo.ACTION_BUILD_WUMIIBO.equals(action)
                                    && !request.getUrl().toString().equals(Website.WUMIIBO_WEB))
                                view.loadUrl(Website.WUMIIBO_WEB);
                            else if (TagMo.ACTION_BROWSE_GITLAB.equals(action)
                                    && !request.getUrl().toString().equals(Website.GITLAB_WEB))
                                view.loadUrl(Website.GITLAB_WEB);
                        }
                    }
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

        setResult(RESULT_CANCELED);

        if (action != null) {
            if (TagMo.ACTION_BUILD_WUMIIBO.equals(action)) {
                JavaScriptInterface download = new JavaScriptInterface();
                mWebView.addJavascriptInterface(download, "Android");
                mWebView.setDownloadListener((url, userAgent, contentDisposition,
                                              mimeType, contentLength) -> {
                    if (url.startsWith("blob") || url.startsWith("data")) {
                        Log.d("DATA", url);
                        mWebView.loadUrl(download.getBase64StringFromBlob(url, mimeType));
                    }
                });
                mWebView.loadUrl(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? Website.WUMIIBO_APP : Website.WUMIIBO_URI);
            } else if (TagMo.ACTION_BROWSE_GITLAB.equals(action)) {
                webViewSettings.setUserAgentString(webViewSettings.getUserAgentString().replaceAll(
                        "(?i)" + Pattern.quote("android"), "TagMo"));
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                mWebView.loadUrl(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        ? Website.GITLAB_APP : Website.GITLAB_URI);
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
                for (String line; (line = r.readLine()) != null; ) {
                    total.append(line).append("\n");
                }
                mWebView.loadData(total.toString(), getString(R.string.mimetype_text)
                        + "; charset=" + TagMo.UTF_8.displayName(), null);
            } catch (Exception e) {
                Debug.Log(e);
                new Toasty(this).Long(R.string.fail_logcat);
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
                while ((entry = zipIn.getNextEntry()) != null) {
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
                setResult(Activity.RESULT_OK);
                finish();
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory())
                    deleteDir(file);
                else
                    file.delete();
            }
        }
        dir.delete();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @UiThread
    void unzipFile(File zipFile) {
        dialog = ProgressDialog.show(this,
                getString(R.string.wait_unzip), "", true);
        File destination = Storage.getDownloads("Wumiibo(Decrypted)");
        if (destination.exists()) deleteDir(destination);
        destination.mkdirs();
        new Thread(new UnZip(zipFile, destination)).start();
    }

    private void saveBinFile(byte[] tagData, String name) {
        try {
            File filePath = new File(Storage.getDownloads("Wumiibo(Decrypted)"),
                    name + "(Decrypted).bin");
            FileOutputStream os = new FileOutputStream(filePath, false);
            os.write(tagData);
            os.flush();
            setResult(Activity.RESULT_OK);
            finish();
        } catch (IOException e) {
            Debug.Log(e);
        }
    }

    @UiThread
    void setBinName(String base64File, String mimeType) {
        byte[] tagData = Base64.decode(base64File.replaceFirst(
                "^data:" + mimeType + ";base64,", ""), 0);
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        final EditText input = view.findViewById(R.id.backup_entry);
        try {
            AmiiboManager amiiboManager = AmiiboManager.getAmiiboManager();
            input.setText(TagUtils.decipherFilename(amiiboManager, tagData, true));
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
                File filePath = new File(Storage.getDownloads(null), "amiibo.zip");
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
