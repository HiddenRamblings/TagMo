package com.hiddenramblings.tagmo.settings;

import android.app.Activity;

import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.browser.BrowserActivity;
import com.hiddenramblings.tagmo.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.security.SecurityHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

public class JSONExecutor {

    ResultListener listener;

    public JSONExecutor(Activity activity, String server, String path) {
        new SecurityHandler(activity, new SecurityHandler.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
                RetrieveJSON(server, path);
            }

            @Override
            public void onProviderInstallException() {
                RetrieveJSON(server, path);
            }

            @Override
            public void onProviderInstallFailed() {
                if (activity instanceof BrowserActivity) {
                    TagMo.getPrefs().image_network_settings().put("NEVER");
                    activity.runOnUiThread(() ->
                            ((BrowserActivity) activity).getSettings().notifyChanges()
                    );
                }
                listener.onResults(null);
            }
        });
    }

    private HttpsURLConnection fixServerLocation(URL url) throws IOException {
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setUseCaches(false);
        urlConnection.setDefaultUseCaches(false);
        return urlConnection;
    }

    public void RetrieveJSON(String server, String path) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpsURLConnection conn = (HttpsURLConnection)
                        new URL(server + path).openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setDefaultUseCaches(false);

                int statusCode = conn.getResponseCode();
                if (statusCode == HttpsURLConnection.HTTP_MOVED_PERM) {
                    String address = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = fixServerLocation(new URL(address));
                    statusCode = conn.getResponseCode();
                } else if (statusCode != HttpsURLConnection.HTTP_OK && isRenderAPI(conn)) {
                    conn.disconnect();
                    conn = fixServerLocation(new URL(AmiiboManager.AMIIBO_API + "amiibo/"));
                    statusCode = conn.getResponseCode();
                }

                if (statusCode != HttpsURLConnection.HTTP_OK) {
                    conn.disconnect();
                    return;
                }

                InputStream in = conn.getInputStream();
                BufferedReader streamReader = new BufferedReader(
                        new InputStreamReader(in, CharsetCompat.UTF_8));
                StringBuilder responseStrBuilder = new StringBuilder();

                String inputStr;
                while (null != (inputStr = streamReader.readLine()))
                    responseStrBuilder.append(inputStr);

                listener.onResults(responseStrBuilder.toString());
                streamReader.close();
                in.close();
                conn.disconnect();
            } catch (IOException e) {
                Debug.Warn(e);
            }
        });
    }

    private boolean isRenderAPI(HttpsURLConnection urlConnection) {
        String render = AmiiboManager.RENDER_RAW + "database/amiibo.json";
        return render.equals(urlConnection.getURL().toString());
    }

    public interface ResultListener {
        void onResults(String result);
    }

    public void setResultListener(ResultListener listener) {
        this.listener = listener;
    }
}
