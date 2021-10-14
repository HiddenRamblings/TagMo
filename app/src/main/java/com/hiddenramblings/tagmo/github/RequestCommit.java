package com.hiddenramblings.tagmo.github;

import android.os.AsyncTask;

import com.hiddenramblings.tagmo.TagMo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RequestCommit extends AsyncTask<String, Integer, String> {

    private RequestCommitListener listener;

    @Override
    protected String doInBackground(String... urls) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
            conn.setDoInput(true);

            return lineReader(conn);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (listener != null) {
            listener.onRequestCommitFinished(result);
        }
    }

    public RequestCommit setListener(RequestCommitListener listener) {
        this.listener = listener;
        return this;
    }

    public interface RequestCommitListener {
        void onRequestCommitFinished(String result);
    }

    private String lineReader(HttpURLConnection conn) throws IOException {
        InputStream in = conn.getInputStream();

        BufferedReader streamReader = new BufferedReader(
                new InputStreamReader(in, TagMo.UTF_8));
        StringBuilder responseStrBuilder = new StringBuilder();

        String inputStr;
        while ((inputStr = streamReader.readLine()) != null)
            responseStrBuilder.append(inputStr);

        return responseStrBuilder.toString();
    }
}