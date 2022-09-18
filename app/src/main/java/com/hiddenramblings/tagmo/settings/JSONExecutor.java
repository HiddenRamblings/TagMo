package com.hiddenramblings.tagmo.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.widget.Toasty;

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
        if (Debug.hasBuild(Build.VERSION_CODES.M)) {
            ProviderInstaller.installIfNeededAsync(
                    activity, new ProviderInstaller.ProviderInstallListener() {
                @Override
                public void onProviderInstalled() {
                    RetrieveJSON(server, path);
                }

                @Override
                public void onProviderInstallFailed(int errorCode, Intent recoveryIntent) {
                    GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
                    if (availability.isUserResolvableError(errorCode)) {
                        availability.showErrorDialogFragment(
                                activity, errorCode, 7000,
                                dialog -> onProviderInstallerNotAvailable(activity));
                    } else {
                        onProviderInstallerNotAvailable(activity);
                    }
                }
            });
        } else {
            RetrieveJSON(server, path);
        }
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
                } else if (statusCode != HttpsURLConnection.HTTP_OK
                        && TagMo.MIRRORED_API.equals(server)) {
                    conn = fixServerLocation(new URL(TagMo.FALLBACK_API + "amiibo/"));
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

    public interface ResultListener {
        void onResults(String result);
    }

    public void setResultListener(ResultListener listener) {
        this.listener = listener;
    }

    private void onProviderInstallerNotAvailable(Activity activity) {
        activity.runOnUiThread(() -> {
            new Toasty(activity).Long(R.string.fail_ssl_update);
            activity.finish();
        });
    }
}
