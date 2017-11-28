package com.hiddenramblings.tagmo;

import android.graphics.Color;
import android.graphics.Typeface;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

@EActivity(R.layout.activity_hex_viewer)
public class HexViewerActivity extends AppCompatActivity {
    private static final String TAG = "HexViewerActivity";

    static class TagMap {
        int index;
        int color;
        String label;

        TagMap(int index, int color, String label) {
            this.index = index;
            this.color = color;
            this.label = label;
        }
    }

    //positions must be sorted in descending order to facilitate lookup
    static TagMap[] tagMap = new TagMap[] {
        new TagMap(0x1E4, Color.parseColor("#F0E68C"), "Crypto Seed"),
        new TagMap(0x1DC, Color.parseColor("#F0F8FF"), "Char. ID"),
        new TagMap(0x1D4, Color.parseColor("#FA8072"), "NTAG UID"),
        new TagMap(0x1B4, Color.parseColor("#CD853F"), "Tag HMAC"),
        new TagMap(0xDC, Color.parseColor("#40E0D0"), "App Data"),
        new TagMap(0xBC, Color.parseColor("#D2B48C"), "Hash"),
        new TagMap(0xB6, Color.parseColor("#FF7F50"), "App ID"),
        new TagMap(0xB4, Color.parseColor("#FF7F50"), "Write Counter"),
        new TagMap(0x4C, Color.parseColor("#98FB98"), "Mii"),
        new TagMap(0x38, Color.parseColor("#00BFFF"), "Nickname"),
        new TagMap(0x34, Color.parseColor("#DDA0DD"), "Console #"),
        new TagMap(0x2E, Color.parseColor("#F08080"), "Hash?"),
        new TagMap(0x2C, Color.parseColor("#32CD32"), "Modified Date"),
        new TagMap(0x2A, Color.parseColor("#FFA07A"), "Init Date"),
        new TagMap(0x28, Color.YELLOW, "Counter"),
        new TagMap(0x27, Color.parseColor("#BC8F8F"), "Country Code"),
        new TagMap(0x26, Color.parseColor("#FFDEAD"), "Flags"),
        new TagMap(0x08, Color.LTGRAY, "Data HMAC"),
        new TagMap(0x00, Color.GRAY, "Lock/CC"),
    };


    @ViewById(R.id.gridView)
    RecyclerView listView;
    HexDumpAdapter adapter;

    @AfterViews
    void afterViews() {
        decryptTagData(getIntent().getByteArrayExtra(Actions.EXTRA_TAG_DATA));
    }

    void decryptTagData(byte[] data) {
        KeyManager keyManager = new KeyManager(this);
        try {
            setTagData(TagUtil.decrypt(keyManager, data));
        } catch (Exception e) {
            Log.d(TAG, "Error decyrpting data", e);
            finish();
        }
    }

    void setTagData(byte[] data) {
        adapter = new HexDumpAdapter(data);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(adapter);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView[] textView = new TextView[16+1];

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

    public static class HexItem {
        String text;
        int textStyle;
        int backgroundColor;

        public HexItem(String text, int textStyle, int backgroundColor) {
            this.text = text;
            this.textStyle = textStyle;
            this.backgroundColor = backgroundColor;
        }

        public HexItem(String text, int backgroundColor) {
            this(text, Typeface.BOLD, backgroundColor);
        }
    }

    public static class HexHeader extends HexItem {
        public HexHeader(String text, int backgroundColor) {
            super(text, Typeface.NORMAL, backgroundColor);
        }
    }

    static class HexDumpAdapter extends RecyclerView.Adapter<ViewHolder> {
        public static final int HEX = 16;

        private final HexItem[][] data;

        public HexDumpAdapter(byte[] tagData) {
            int rowCount = ((tagData.length - 1) / HEX) + 2;

            this.data = new HexItem[rowCount][HEX + 1];
            for (int rowIndex = -1; rowIndex < this.data.length - 1; rowIndex++) {
                HexItem[] row = this.data[rowIndex + 1];
                for (int columnIndex = -1; columnIndex < row.length - 1; columnIndex++) {
                    HexItem hexItem;
                    if (rowIndex == -1 && columnIndex == -1) {
                        hexItem = null;
                    } else if (rowIndex == -1) {
                        hexItem = new HexHeader(String.format("%02X", columnIndex), Color.TRANSPARENT);
                    } else if (columnIndex == -1) {
                        hexItem = new HexHeader(String.format("%04X", rowIndex * HEX), Color.TRANSPARENT);
                    } else {
                        int index = (rowIndex * HEX) + columnIndex;
                        if (index >= tagData.length) {
                            hexItem = null;
                        } else {
                            String text = String.format("%02X", Byte.valueOf(tagData[index]).intValue() & 0xFF);
                            int color = Color.WHITE;
                            for (TagMap t: tagMap) {
                                if (t.index <= index) {
                                    color = t.color;
                                    break;
                                }
                            }
                            hexItem = new HexItem(text, color);
                        }
                    }
                    row[columnIndex + 1] = hexItem;
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

        public HexItem[] getItem(int i) {
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
            HexItem[] row = getItem(position);
            for (int i = 0; i < holder.textView.length; i++) {
                HexItem hexItem = row[i];
                TextView view = holder.textView[i];
                if (hexItem == null) {
                    view.setTextColor(Color.TRANSPARENT);
                    view.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    view.setText(hexItem.text);
                    view.setTextColor(Color.BLACK);
                    view.setTypeface(Typeface.MONOSPACE, hexItem.textStyle);
                    view.setBackgroundColor(hexItem.backgroundColor);
                    view.setVisibility(View.VISIBLE);
                }
            }
        }
    }
}
