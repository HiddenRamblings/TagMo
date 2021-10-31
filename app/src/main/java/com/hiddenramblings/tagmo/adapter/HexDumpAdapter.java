package com.hiddenramblings.tagmo.adapter;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.nfctech.hex.HexHeader;
import com.hiddenramblings.tagmo.nfctech.hex.HexItem;
import com.hiddenramblings.tagmo.nfctech.hex.TagMap;

public class HexDumpAdapter extends RecyclerView.Adapter<HexDumpAdapter.ViewHolder> {

    private static final int HEX = 16;
    private final HexItem[][] data;

    public HexDumpAdapter(byte[] tagData) {
        this.setHasStableIds(true);
        int rowCount = ((tagData.length - 1) / HEX) + 2;

        this.data = new HexItem[rowCount][HEX + 1];
        for (int rowIndex = -1; rowIndex < this.data.length - 1; rowIndex++) {
            HexItem[] row = this.data[rowIndex + 1];
            for (int columnIndex = -1; columnIndex < row.length - 1; columnIndex++) {
                HexItem hexItem;
                if (rowIndex == -1 && columnIndex == -1) {
                    hexItem = null;
                } else if (rowIndex == -1) {
                    hexItem = new HexHeader(String.format("%02X",
                            columnIndex), Color.TRANSPARENT);
                } else if (columnIndex == -1) {
                    hexItem = new HexHeader(String.format("%04X",
                            rowIndex * HEX), Color.TRANSPARENT);
                } else {
                    int index = (rowIndex * HEX) + columnIndex;
                    if (index >= tagData.length) {
                        hexItem = null;
                    } else {
                        String text = String.format("%02X",
                                Byte.valueOf(tagData[index]).intValue() & 0xFF);
                        int color = Color.WHITE;
                        for (TagMap t : TagMap.getTagMap) {
                            if (t.getIndex() <= index) {
                                color = t.getColor();
                                break;
                            }
                        }
                        hexItem = new HexItem(text, color);
                    }
                }
                row[columnIndex + 1] = hexItem;
            }
        }
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

    @NonNull
    @Override
    public HexDumpAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new HexDumpAdapter.ViewHolder(LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.hexdump_line, parent, false));
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onBindViewHolder(HexDumpAdapter.ViewHolder holder, int position) {
        HexItem[] row = getItem(position);
        for (int i = 0; i < holder.textView.length; i++) {
            HexItem hexItem = row[i];
            TextView view = holder.textView[i];
            if (hexItem == null) {
                view.setTextColor(Color.TRANSPARENT);
                view.setBackgroundColor(Color.TRANSPARENT);
            } else {
                view.setText(hexItem.getText());
                view.setTextColor(hexItem instanceof HexHeader && isDarkTheme()
                        ? Color.WHITE : Color.BLACK);
                view.setTypeface(Typeface.MONOSPACE, hexItem.getTextStyle());
                view.setBackgroundColor(hexItem.getBackgroundColor());
                view.setVisibility(View.VISIBLE);
            }
        }
    }

    private boolean isDarkTheme() {
        return (TagMo.getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View view;
        TextView[] textView = new TextView[16 + 1];

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
}
