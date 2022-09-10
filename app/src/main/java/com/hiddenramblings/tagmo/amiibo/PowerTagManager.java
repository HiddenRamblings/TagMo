package com.hiddenramblings.tagmo.amiibo;

import android.util.Base64;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.nfctech.NTAG215;
import com.hiddenramblings.tagmo.eightbit.nfc.TagUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class PowerTagManager {

    // private static final String POWERTAG_KEYTABLE_FILE = "keytable.json";
    private static HashMap<String, HashMap<String, byte[]>> keys;

    public static void getPowerTagManager() throws Exception {
        if (null != keys)
            return;
        try (InputStream stream = TagMo.getContext().getResources().openRawResource(R.raw.keytable)) {
            byte[] data = new byte[stream.available()];
            //noinspection ResultOfMethodCallIgnored
            stream.read(data);
            JSONObject obj = new JSONObject(new String(data));
            parseKeyTable(obj);
        }
    }

    private static void parseKeyTable(JSONObject json) throws JSONException {
        HashMap<String, HashMap<String, byte[]>> keytable = new HashMap<>();
        for (Iterator<String> uidIterator = json.keys(); uidIterator.hasNext();) {
            String uid = uidIterator.next();
            JSONObject pageKeys = json.getJSONObject(uid);

            HashMap<String, byte[]> keyvalues = new HashMap<>();
            keytable.put(uid, keyvalues);

            for (Iterator<String> pageByteIterator = pageKeys.keys(); pageByteIterator.hasNext();) {
                String pageBytes = pageByteIterator.next();
                String keyStr = pageKeys.getString(pageBytes);
                byte[] key = Base64.decode(keyStr, Base64.DEFAULT);
                keyvalues.put(pageBytes, key);
            }
        }

        keys = keytable;
    }

    static boolean resetPowerTag(NTAG215 mifare) {
        try (InputStream stream = TagMo.getContext().getResources()
                .openRawResource(R.raw.powertag)) {
            byte[] data = new byte[stream.available()];
            //noinspection ResultOfMethodCallIgnored
            stream.read(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static byte[] getPowerTagKey(byte[] uid, String page10bytes) throws NullPointerException {
        if (null == keys )
            throw new NullPointerException(TagMo.getContext()
                    .getString(R.string.error_powertag_key));

        byte[] uidc = new byte[7];

        uidc[0] = (byte) (uid[0] & 0xFE);
        uidc[1] = (byte) (uid[1] & 0xFE);
        uidc[2] = (byte) (uid[2] & 0xFE);
        uidc[3] = (byte) (uid[3] & 0xFE);
        uidc[4] = (byte) (uid[4] & 0xFE);
        uidc[5] = (byte) (uid[5] & 0xFE);
        uidc[6] = (byte) (uid[6] & 0xFE);

        HashMap<String, byte[]> keymap = keys.get(TagUtils.bytesToHex(uidc));
        if (null == keymap)
            throw new NullPointerException(TagMo.getContext().getString(R.string.uid_key_missing));

        byte[] key = keymap.get(page10bytes);
        if (null == key)
            throw new NullPointerException(TagMo.getContext().getString(R.string.p10_key_missing));

        return key;
    }
}
