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
        public static final int COLUMNS = HEX + 1;

        private final List<String[]> data;

        public HexDumpAdapter(byte[] data) {
            this.data = new ArrayList<>();

            for (int i = 0; i < (Math.floor(data.length) / HEX) + 1; i++) {
                String[] row = new String[COLUMNS];
                for (int j = 0; j < COLUMNS; j++) {
                    String text;
                    if (i == 0 && j == 0) {
                        text = null;
                    } else if (i == 0) {
                        text = String.format("%02X", j - 1);
                    } else if (j == 0) {
                        text = String.format("%04X", (i - 1) * HEX);
                    } else {
                        int index = ((i - 1) * HEX) + (j - 1);
                        if (index >= data.length) {
                            text = null;
                        } else {
                            text = String.format("%02X", Byte.valueOf(data[index]).intValue() & 0xFF);
                        }
                    }
                    row[j] = text;
                }
                this.data.add(row);
            }
            this.setHasStableIds(true);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        public String[] getItem(int i) {
            return this.data.get(i);
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
