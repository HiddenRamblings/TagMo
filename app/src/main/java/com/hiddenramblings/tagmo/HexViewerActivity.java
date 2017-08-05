package com.hiddenramblings.tagmo;

import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
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
    RecyclerView gridView;
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
        GridLayoutManager glm = new GridLayoutManager(this, 18);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0)
                    return 2;

                switch (adapter.getItemViewType(position)) {
                    case HexDumpAdapter.VIEW_ROW_HEADER:
                        return 2;
                    default:
                        return 1;
                }
            }
        });
        gridView.setLayoutManager(glm);
        gridView.setAdapter(adapter);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView textView;

        ViewHolder(View view) {
            super(view);

            this.view = view;
            this.textView = view.findViewById(R.id.textView1);
        }
    }

    class HexDumpAdapter extends RecyclerView.Adapter<ViewHolder> {
        public static final int VIEW_HEADER = 0;
        public static final int VIEW_COLUMN_HEADER = 1;
        public static final int VIEW_ROW_HEADER = 2;
        public static final int VIEW_HEX = 3;

        public static final int HEX = 16;
        public static final int COLUMNS = HEX + 1;

        private final List<String> data;

        public HexDumpAdapter(byte[] data) {
            this.data = new ArrayList<>();

            int size = data.length + COLUMNS + (int) Math.floor(data.length / HEX);
            for (int i = 0; i < size; i++) {
                String format;
                int value;
                switch (getItemViewType(i)) {
                    case VIEW_HEADER:
                        format = "%02X";
                        value = 0;
                        break;
                    case VIEW_COLUMN_HEADER:
                        format = "%02X";
                        value = i - 1;
                        break;
                    case VIEW_ROW_HEADER:
                        format = "%04X";
                        value = (((int) Math.floor(i / COLUMNS)) - 1) * HEX;
                        break;
                    case VIEW_HEX:
                        format = "%02X";
                        value = Byte.valueOf(data[i - COLUMNS - ((int) Math.floor(i / HEX))]).intValue() & 0xFF;
                        break;
                    default:
                        throw new RuntimeException();
                }
                this.data.add(String.format(format, value));
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

        public String getItem(int i) {
            return this.data.get(i);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0)
                return VIEW_HEADER;
            else if (position < COLUMNS)
                return VIEW_COLUMN_HEADER;
            else if (position % COLUMNS == 0)
                return VIEW_ROW_HEADER;
            else
                return VIEW_HEX;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewHolder viewHolder = new ViewHolder(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.hexdump_line, parent, false));

            if (viewType == VIEW_HEX) {
                viewHolder.textView.setTypeface(null, Typeface.BOLD);
                viewHolder.textView.setTextColor(getResources().getColor(android.R.color.black));
            } else {
                viewHolder.textView.setTypeface(null, Typeface.NORMAL);
                viewHolder.textView.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }

            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            int i = 0;
            if (Math.floor(position / COLUMNS) % 2 == 1) {
                i += 1;
            }
            if (i == 0) {
                holder.view.setBackgroundColor(Color.WHITE);
            } else if (i == 1) {
                holder.view.setBackgroundColor(Color.LTGRAY);
            } else if (i == 2) {
                holder.view.setBackgroundColor(Color.DKGRAY);
            }
            holder.textView.setText(getItem(position));
        }
    }
}
