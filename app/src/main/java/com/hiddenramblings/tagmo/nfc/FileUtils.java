package com.hiddenramblings.tagmo.nfc;

import android.content.Context;
import android.os.Build;

import com.endgames.environment.Storage;
import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.ParseException;

public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();

    public static final String AMIIBO_DATABASE_FILE = "amiibo.json";

    public static void dumpLogcat(String fileName) throws Exception {
        final StringBuilder log = new StringBuilder();
        String separator = System.getProperty("line.separator");
        log.append(android.os.Build.MANUFACTURER);
        log.append(" ");
        log.append(android.os.Build.MODEL);
        log.append(separator);
        log.append("Android SDK ");
        log.append(Build.VERSION.SDK_INT);
        log.append(" (");
        log.append(Build.VERSION.RELEASE);
        log.append(")");
        log.append(separator);
        log.append("TagMo Version " + BuildConfig.VERSION_NAME);

        try {
            String line;
            Process mLogcatProc = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-d",
                    BuildConfig.APPLICATION_ID,
                    "com.smartrac.nfc",
                    "-t", "2048"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    mLogcatProc.getInputStream()));
            log.append(separator);
            log.append(separator);
            while ((line = reader.readLine()) != null) {
                log.append(line);
                log.append(separator);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (FileOutputStream fos = new FileOutputStream(new File(fileName))) {
            fos.write(log.toString().getBytes());
        }
    }

    public static File getSDCardDir() {
        return Storage.getStorageFile();
    }

    public static File getFilesDir() {
        return TagMo.getContext().getExternalFilesDir(null);
    }

    @SuppressWarnings("unused")
    public static class AmiiboInfoException extends Exception {
        public AmiiboInfoException(String message) {
            super(message);
        }
    }

    public static AmiiboManager loadDefaultAmiiboManager() throws IOException, JSONException, ParseException {
//        return AmiiboManager.parse(TagMo.getContext().getAssets().open(AMIIBO_DATABASE_FILE));
        return AmiiboManager.parse(TagMo.getContext().getResources().openRawResource(R.raw.amiibo));
    }

    public static AmiiboManager loadAmiiboManager() throws IOException, JSONException, ParseException {
        AmiiboManager amiiboManager;
        if (new File(getFilesDir(), AMIIBO_DATABASE_FILE).exists()) {
            try {
                amiiboManager = AmiiboManager.parse(
                        TagMo.getContext().openFileInput(AMIIBO_DATABASE_FILE));
            } catch (IOException | JSONException | ParseException e) {
                amiiboManager = null;
                TagMo.Error(TAG, R.string.amiibo_parse_error, e);
            }
        } else {
            amiiboManager = null;
        }
        if (amiiboManager == null) {
            amiiboManager = loadDefaultAmiiboManager();
        }

        return amiiboManager;
    }

    public static void saveAmiiboInfo(AmiiboManager amiiboManager, OutputStream outputStream) throws JSONException, IOException {
        OutputStreamWriter streamWriter = null;
        try {
            streamWriter = new OutputStreamWriter(outputStream);
            streamWriter.write(amiiboManager.toJSON().toString());
            outputStream.flush();
        } finally {
            if (streamWriter != null) {
                try {
                    streamWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void saveLocalAmiiboInfo(AmiiboManager amiiboManager) throws IOException, JSONException {
        OutputStream outputStream = null;
        try {
            outputStream = TagMo.getContext().openFileOutput(
                    FileUtils.AMIIBO_DATABASE_FILE, Context.MODE_PRIVATE);
            FileUtils.saveAmiiboInfo(amiiboManager, outputStream);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String friendlyPath(File file) {
        String dirPath = file.getAbsolutePath();
        String sdcardPath = getSDCardDir().getAbsolutePath();
        if (dirPath.startsWith(sdcardPath)) {
            dirPath = dirPath.substring(sdcardPath.length());
        }

        return dirPath;
    }

    public static boolean equals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        } else if (o1 == null || o2 == null) {
            return false;
        } else {
            return o1.equals(o2);
        }
    }
}
