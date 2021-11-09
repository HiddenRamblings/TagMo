package com.hiddenramblings.tagmo.github;

import com.eightbit.io.Debug;
import com.hiddenramblings.tagmo.TagMo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebExecutor {

    ResponseListener listener;

    public WebExecutor(String url) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setUseCaches(false);
                conn.setDefaultUseCaches(false);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM)
                    conn = (HttpURLConnection) new URL(
                            conn.getHeaderField("Location")).openConnection();
                else if (responseCode != 200) return;

                InputStream in = conn.getInputStream();
                BufferedReader streamReader = new BufferedReader(
                        new InputStreamReader(in, TagMo.UTF_8));
                StringBuilder responseStrBuilder = new StringBuilder();

                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    responseStrBuilder.append(inputStr);

                listener.onResponse(responseStrBuilder.toString());
            } catch (IOException e) {
                Debug.Error(e);
            }
        });
    }

    public interface ResponseListener {
        void onResponse(String response);
    }

    public void setResponseListener(ResponseListener listener) {
        this.listener = listener;
    }
}
