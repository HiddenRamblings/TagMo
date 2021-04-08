package com.hiddenramblings.tagmo.ptag;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;

import com.hiddenramblings.tagmo.Util;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class PTagKeyManager {
    private static final String TAG = "PTagKeyManager";
    public static final String KEY_LIST_FILE = "keytable.json";

    private static HashMap<String, HashMap<String, byte[]>> keys;

    public static void load(Context context) throws Exception {
        if (keys != null)
            return;
        AssetManager assetManager = context.getAssets();
        InputStream stream = assetManager.open(KEY_LIST_FILE);
        try {
            byte[] data = new byte[stream.available()];
            stream.read(data);

            JSONObject obj = new JSONObject(new String(data));
            loadJson(obj);
        } finally {
            stream.close();
        }
    }

    static void loadJson(JSONObject json) throws JSONException {
        HashMap<String, HashMap<String, byte[]>> keytable = new HashMap<>();
        for (Iterator uidIterator = json.keys(); uidIterator.hasNext(); ) {
            String uid = (String) uidIterator.next();
            JSONObject pageKeys = json.getJSONObject(uid);

            HashMap<String, byte[]> keyvalues = new HashMap<String, byte[]>();
            keytable.put(uid, keyvalues);

            for (Iterator pageByteIterator = pageKeys.keys(); pageByteIterator.hasNext(); ) {
                String pageBytes = (String) pageByteIterator.next();

                String keyStr = pageKeys.getString(pageBytes);

                byte[] key = Base64.decode(keyStr, Base64.DEFAULT);

                keyvalues.put(pageBytes, key);
            }
        }

        keys = keytable;
    }

    public static byte[] getKey(byte[] uid, String page10bytes) throws Exception {
        if (keys == null)
            throw new Exception("PowerTag keys not loaded");

        byte[] uidc = new byte[7];

        uidc[0] = (byte) (uid[0] & 0xFE);
        uidc[1] = (byte) (uid[1] & 0xFE);
        uidc[2] = (byte) (uid[2] & 0xFE);
        uidc[3] = (byte) (uid[3] & 0xFE);
        uidc[4] = (byte) (uid[4] & 0xFE);
        uidc[5] = (byte) (uid[5] & 0xFE);
        uidc[6] = (byte) (uid[6] & 0xFE);

        String uidStr = Util.bytesToHex(uidc);

        HashMap<String, byte[]> keymap = keys.get(uidStr);
        if (keymap == null)
            throw new Exception("No available key for UID");

        byte[] key = keymap.get(page10bytes);
        if (key == null)
            throw new Exception("No available key for P10_ID");

        return key;
    }
}
