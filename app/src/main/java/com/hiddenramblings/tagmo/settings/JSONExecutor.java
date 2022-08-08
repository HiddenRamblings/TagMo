package com.hiddenramblings.tagmo.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public class JSONExecutor {

    ResultListener listener;

    public JSONExecutor(Activity activity, String url) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            activity.runOnUiThread(() -> ProviderInstaller.installIfNeededAsync(
                    activity, new ProviderInstaller.ProviderInstallListener() {
                @Override
                public void onProviderInstalled() {
                    RetrieveJSON(url);
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
            }));
        } else {
            RetrieveJSON(url);
        }
    }

    public void RetrieveJSON(String url) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setDefaultUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(conn
                            .getHeaderField("Location")).openConnection();
                } else if (responseCode != 200) {
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
                Debug.Log(e);
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
        new Toasty(activity).Long(R.string.fail_ssl_update);
        activity.finish();
    }
}
