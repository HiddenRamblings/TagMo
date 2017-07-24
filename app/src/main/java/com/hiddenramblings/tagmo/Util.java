package com.hiddenramblings.tagmo;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.hiddenramblings.tagmo.amiibo.AmiiboManager;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

/**
 * Created by MAS on 31/01/2016.
 */
public class Util {
    final static String TAG = "Util";

    public static final String DATA_DIR = "tagmo";
    public static final String AMIIBO_DATABASE_FILE = "amiibo.json";

    public static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String md5(byte[] data) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] result = digest.digest(data);
            return bytesToHex(result);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    public static void dumpLogcat(String fileName) throws Exception {
        File file = new File(fileName);

        Process process = Runtime.getRuntime().exec("logcat -d");
        InputStream logStream = process.getInputStream();
        try {
            FileOutputStream fos = new FileOutputStream(file);
            try {
                String phoneDetails = String.format("Manufacturer: %s - Model: %s\nAndroid Ver: %s\nTagMo Version: %s\n",
                    Build.MANUFACTURER, Build.MODEL,
                    Build.VERSION.RELEASE,
                    BuildConfig.VERSION_NAME);

                fos.write(phoneDetails.getBytes());

                byte[] buf = new byte[1024];
                int read = logStream.read(buf);
                while (read >= 0) {
                    fos.write(buf, 0, read);
                    read = logStream.read(buf);
                }
            } finally {
                fos.close();
            }
        } finally {
            logStream.close();
        }
    }

    public static class AmiiboDatabaseException extends Exception {
        public AmiiboDatabaseException(String message) {
            super(message);
        }
    }

    public static void saveAmiiboDatabase(Context context, Uri uri) throws AmiiboDatabaseException {
        InputStream inputSteam = null;
        try {
            try {
                inputSteam = context.getContentResolver().openInputStream(uri);
                AmiiboManager.parse(inputSteam);
            } catch (IOException | ParseException | JSONException e) {
                e.printStackTrace();
                throw new AmiiboDatabaseException("Failed to parse amiibo database");
            }
        } finally {
            if (inputSteam != null) {
                try {
                    inputSteam.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        inputSteam = null;
        OutputStream outputStream = null;
        try {
            try {
                inputSteam = context.getContentResolver().openInputStream(uri);
                outputStream = context.openFileOutput(AMIIBO_DATABASE_FILE, Context.MODE_PRIVATE);
                byte[] data = new byte[inputSteam.available()];
                inputSteam.read(data);
                outputStream.write(data);
            } catch (IOException e) {
                e.printStackTrace();
                throw new AmiiboDatabaseException("Failed to update amiibo database");
            }
        } finally {
            if (inputSteam != null) {
                try {
                    inputSteam.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static AmiiboManager loadAmiiboManager(Context context) throws IOException, JSONException, ParseException {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = AmiiboManager.parse(context.openFileInput(AMIIBO_DATABASE_FILE));
        } catch (IOException | JSONException | ParseException e) {
            e.printStackTrace();
        }
        if (amiiboManager == null) {
            AssetManager assetManager = context.getAssets();
            amiiboManager = AmiiboManager.parse(assetManager.open(AMIIBO_DATABASE_FILE));
        }

        return amiiboManager;
    }

    public static File getDataDir() {
        return new File(Environment.getExternalStorageDirectory(), DATA_DIR);
    }
}
