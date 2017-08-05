package com.hiddenramblings.tagmo;

import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.List;

@EActivity(R.layout.activity_hex_viewer)
public class HexViewerActivity extends AppCompatActivity {
    private static final String TAG = "HexViewerActivity";

    @ViewById(R.id.gridView)
    RecyclerView listView;
    HexDumpAdapter adapter;

    @AfterViews
    void afterViews() {
        decryptTagData(getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA));
    }

    @Background
    void decryptTagData(byte[] data) {
        KeyManager keyManager = new KeyManager(this);
        try {
            setTagData(TagUtil.decrypt(keyManager, data));
        } catch (Exception e) {
            Log.d(TAG, "Error decyrpting data", e);
            finish();
        }
    }

    @UiThread
    void setTagData(byte[] data) {
        adapter = new HexDumpAdapter(data);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView[] textView = new TextView[17];

        ViewHolder(View view) {
            super(view);

            this.view = view;
            this.textView[0] = view.findViewById(R.id.textViewRow);
            this.textView[1] = view.findViewById(R.id.textView1);
            this.textView[2] = view.findViewById(R.id.textView2);
            this.textView[3] = view.findViewById(R.id.textView3);
            this.textView[4] = view.findViewById(R.id.textView4);
            this.textView[5] = view.findViewById(R.id.textView5);
            this.textView[6] = view.findViewById(R.id.textView6);
            this.textView[7] = view.findViewById(R.id.textView7);
            this.textView[8] = view.findViewById(R.id.textView8);
            this.textView[9] = view.findViewById(R.id.textView9);
            this.textView[10] = view.findViewById(R.id.textView10);
            this.textView[11] = view.findViewById(R.id.textView11);
            this.textView[12] = view.findViewById(R.id.textView12);
            this.textView[13] = view.findViewById(R.id.textView13);
            this.textView[14] = view.findViewById(R.id.textView14);
            this.textView[15] = view.findViewById(R.id.textView15);
            this.textView[16] = view.findViewById(R.id.textView16);
        }
    }

    class HexDumpAdapter extends RecyclerView.Adapter<ViewHolder> {
        public static final int HEX = 16;

        private final String[][] data;

        public HexDumpAdapter(byte[] tagData) {
            this.data = new String[((int)Math.floor(tagData.length) / HEX) + 2][HEX + 1];
            for (int i = -1; i < this.data.length - 1; i++) {
                String[] row = this.data[i + 1];
                for (int j = -1; j < row.length - 1; j++) {
                    String text;
                    if (i == -1 && j == -1) {
                        text = null;
                    } else if (i == -1) {
                        text = String.format("%02X", j);
                    } else if (j == -1) {
                        text = String.format("%04X", i * HEX);
                    } else {
                        int index = (i * HEX) + j;
                        if (index >= tagData.length) {
                            text = null;
                        } else {
                            text = String.format("%02X", Byte.valueOf(tagData[index]).intValue() & 0xFF);
                        }
                    }
                    row[j + 1] = text;
                }
            }
            this.setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return data.length;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        public String[] getItem(int i) {
            return this.data[i];
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.hexdump_line, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (position % 2 == 0) {
                holder.view.setBackgroundColor(Color.WHITE);
            } else {
                holder.view.setBackgroundColor(Color.LTGRAY);
            }

            String[] row = getItem(position);
            for (int i = 0; i < holder.textView.length; i++) {
                if (row[i] == null) {
                    holder.textView[i].setVisibility(View.INVISIBLE);
                } else {
                    holder.textView[i].setText(row[i]);
                    holder.textView[i].setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
