package com.hiddenramblings.tagmo;

import android.media.MediaScannerConnection;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by MAS on 31/01/2016.
 */
public class Util {
    final static String TAG = "Util";

    final public static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
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
                    + Character.digit(s.charAt(i+1), 16));
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
            logStream.close();;
        }
    }
}
