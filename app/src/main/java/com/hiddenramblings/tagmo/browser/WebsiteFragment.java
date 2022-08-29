package com.hiddenramblings.tagmo.browser;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
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
import androidx.fragment.app.Fragment;
import androidx.webkit.ServiceWorkerClientCompat;
import androidx.webkit.ServiceWorkerControllerCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WebsiteFragment extends Fragment {

    private WebView mWebView;
    private ProgressDialog dialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_webview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mWebView = view.findViewById(R.id.webview_content);
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
                            new WebViewAssetLoader.AssetsPathHandler(requireContext())).build();

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

        loadWebsite(null);
    }

    public void loadWebsite(String address) {
        if (null != mWebView) {
            if (null == address) address = NFCIntent.SITE_GITLAB_README;
            WebSettings webViewSettings = mWebView.getSettings();
            if (address.startsWith(NFCIntent.SITE_GITLAB_README)) {
                webViewSettings.setUserAgentString(webViewSettings.getUserAgentString().replaceAll(
                        "(?i)" + Pattern.quote("android"), "TagMo"));
            } else {
                webViewSettings.setBuiltInZoomControls(true);
                webViewSettings.setSupportZoom(true);
                webViewSettings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
            }
            mWebView.loadUrl(address);
        } else {
            final String delayedUrl = address;
            new Handler(Looper.getMainLooper()).postDelayed((Runnable) () ->
                    loadWebsite(delayedUrl), TagMo.uiDelay);
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
                        File dir = new File(outputDir, finalEntry.getName()
                                .replace("/", ""));
                        if (!dir.exists() && !dir.mkdirs())
                            throw new RuntimeException(
                                    getString(R.string.mkdir_failed, dir.getName())
                            );
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
                Debug.Log(e);
            } finally {
                dialog.dismiss();
                this.archive.delete();
            }
        }
    }

    private void unzipFile(File zipFile) {
        dialog = ProgressDialog.show(requireContext(), "", "", true);
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
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        final EditText input = view.findViewById(R.id.backup_entry);
        try {
            AmiiboManager amiiboManager = AmiiboManager
                    .getAmiiboManager(requireContext().getApplicationContext());
            input.setText(TagUtils.decipherFilename(amiiboManager, tagData, true));
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
        }
        Dialog backupDialog = dialog.setView(view).create();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            saveBinFile(tagData, input.getText().toString());
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
        backupDialog.show();
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

