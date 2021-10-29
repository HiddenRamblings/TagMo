package com.hiddenramblings.tagmo.github;

import android.os.AsyncTask;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

@SuppressWarnings("deprecation")
public class RequestStamp extends AsyncTask<String, Integer, Long> {
    private RequestStampListener listener;

    @Override
    protected Long doInBackground(String... urls) {
        try {
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection con = (HttpURLConnection) new URL(urls[0]).openConnection();
            con.setUseCaches(false);
            con.setDefaultUseCaches(false);
            con.setRequestMethod("HEAD");
            con.setRequestProperty("Accept-Encoding", "identity");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                con = (HttpURLConnection) new URL(
                        con.getHeaderField("Location")).openConnection();
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                return null;
            }
            return con.getLastModified();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(Long result) {
        super.onPostExecute(result);
        if (listener != null) {
            listener.onRequestStampFinished(result);
        }
    }

    public RequestStamp setListener(RequestStampListener listener) {
        this.listener = listener;
        return this;
    }

    public interface RequestStampListener {
        void onRequestStampFinished(Long result);
    }
}