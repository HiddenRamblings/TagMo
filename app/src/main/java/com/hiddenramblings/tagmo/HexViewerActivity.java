package com.hiddenramblings.tagmo;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

import java.util.List;

@EActivity(R.layout.activity_hex_viewer)
public class HexViewerActivity extends AppCompatActivity {
    private static final String TAG = "HexViewerActivity";

    @ViewById(R.id.listView)
    ListView listView;

    @AfterViews
    void afterViews() {
        try {
            KeyManager keyManager = new KeyManager(this);
            byte[] tagData = getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA);
            tagData = TagUtil.decrypt(keyManager, tagData);
            this.loadData(tagData);
        } catch (Exception e) {
            Log.d(TAG, "Error decyrpting data", e);
            finish();
        }
    }

    void loadData(byte[] tagData) {
        int rows = (int)Math.ceil((float)tagData.length / 0x10);


        String[] data = new String[rows];

        for(int i=0; i< rows; i++) {
            data[i] = bytesToHex(tagData, i * 0x10, 0x10);
        }

        HexDumpAdapter adapter = new HexDumpAdapter(this, data);
        listView.setAdapter(adapter);
    }

    static String bytesToHex(byte[] bytes, int offset, int count) {
        count = Math.min(bytes.length - offset, count);

        char[] hexChars = new char[count * 3];
        int len = Math.min(bytes.length, offset + count);
        int q = 0;
        for ( int j = offset; j < len; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[q * 3] = Util.hexArray[v >>> 4];
            hexChars[q * 3 + 1] = Util.hexArray[v & 0x0F];
            hexChars[q * 3 + 2] = ' ';
            q++;
        }
        return new String(hexChars);
    }

    private class HexDumpAdapter extends ArrayAdapter<String> {
        //based on https://raw.githubusercontent.com/CyanogenMod/android_packages_apps_CMFileManager/cm-13.0/src/com/cyanogenmod/filemanager/activities/EditorActivity.java
        //Apache 2.0
        public HexDumpAdapter(Context context, String[] data) {
            super(context, R.layout.hexdump_line, data);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                final Context context = getContext();
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.hexdump_line, parent, false);
                convertView.setTag(convertView.findViewById(R.id.textView2));
            }

            if (position % 2 == 0) {
                convertView.setBackgroundColor(Color.LTGRAY);
            } else {
                convertView.setBackgroundColor(Color.WHITE);
            }

            TextView view = (TextView)convertView.getTag();

            TextView posView = (TextView)convertView.findViewById(R.id.textView1);

            String strpos = String.format("%04X ", position * 0x10);
            posView.setText(strpos);
            String text = getItem(position);
            view.setText(text);

            return convertView;
        }

    }

}
