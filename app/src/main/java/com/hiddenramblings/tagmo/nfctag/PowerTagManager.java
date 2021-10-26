package com.hiddenramblings.tagmo.nfctag;

import android.util.Base64;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class PowerTagManager {

    public static final String POWERTAG_KEYTABLE_FILE = "keytable.json";
    private static HashMap<String, HashMap<String, byte[]>> keys;

    public static void getPowerTagManager() throws Exception {
        if (keys != null)
            return;
        try (InputStream stream = TagMo.getContext().getAssets().open(POWERTAG_KEYTABLE_FILE)) {
//        try (InputStream stream = agMo.getContext().getResources().openRawResource(R.raw.keytable)) {
            byte[] data = new byte[stream.available()];
            stream.read(data);

            JSONObject obj = new JSONObject(new String(data));
            parseKeyTable(obj);
        }
    }

    static void parseKeyTable(JSONObject json) throws JSONException {
        HashMap<String, HashMap<String, byte[]>> keytable = new HashMap<>();
        for (Iterator<String> uidIterator = json.keys(); uidIterator.hasNext(); ) {
            String uid = uidIterator.next();
            JSONObject pageKeys = json.getJSONObject(uid);

            HashMap<String, byte[]> keyvalues = new HashMap<>();
            keytable.put(uid, keyvalues);

            for (Iterator<String> pageByteIterator = pageKeys.keys(); pageByteIterator.hasNext(); ) {
                String pageBytes = pageByteIterator.next();
                String keyStr = pageKeys.getString(pageBytes);
                byte[] key = Base64.decode(keyStr, Base64.DEFAULT);
                keyvalues.put(pageBytes, key);
            }
        }

        keys = keytable;
    }

    public static byte[] getPowerTagKey(byte[] uid, String page10bytes) throws Exception {
        if (keys == null)
            throw new Exception(TagMo.getStringRes(R.string.powertag_key_error));

        byte[] uidc = new byte[7];

        uidc[0] = (byte) (uid[0] & 0xFE);
        uidc[1] = (byte) (uid[1] & 0xFE);
        uidc[2] = (byte) (uid[2] & 0xFE);
        uidc[3] = (byte) (uid[3] & 0xFE);
        uidc[4] = (byte) (uid[4] & 0xFE);
        uidc[5] = (byte) (uid[5] & 0xFE);
        uidc[6] = (byte) (uid[6] & 0xFE);

        HashMap<String, byte[]> keymap = keys.get(TagUtils.bytesToHex(uidc));
        if (keymap == null)
            throw new Exception(TagMo.getStringRes(R.string.uid_key_missing));

        byte[] key = keymap.get(page10bytes);
        if (key == null)
            throw new Exception(TagMo.getStringRes(R.string.p10_key_missing));

        return key;
    }
}
