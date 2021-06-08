package com.hiddenramblings.tagmo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * Created by MAS on 02/02/2016.
 */

public class TagCreateDialog extends DialogFragment {
    private static final String TAG = "TagCreateDialog";

    public interface TagCreateListener {
        void onTagCreateDialogConfirm(DialogFragment dialog, byte[] parameters);
    }

    TagCreateListener tagCreateListener;

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.tag_create_activity, null);

        final Spinner spnSeries = view.findViewById(R.id.spnSeries);
        final Spinner spnCharacter = view.findViewById(R.id.spnCharacter);
        final Spinner spnVariation = view.findViewById(R.id.spnVariation);
        final Spinner spnForm = view.findViewById(R.id.spnForm);
        final Spinner spnID1 = view.findViewById(R.id.spnID1);
        final Spinner spnID2 = view.findViewById(R.id.spnID2);
        final Spinner spnSet = view.findViewById(R.id.spnSet);

        ArrayAdapter<CharSequence>[] adapters = new ArrayAdapter[7];
        for(int i=0; i<adapters.length; i++) {
            adapters[i] = ArrayAdapter.createFromResource(getActivity(),
                    R.array.hex_list, android.R.layout.simple_list_item_1);
        }

        spnSeries.setAdapter(adapters[0]);
        spnCharacter.setAdapter(adapters[0]);
        spnVariation.setAdapter(adapters[0]);
        spnForm.setAdapter(adapters[0]);
        spnID1.setAdapter(adapters[0]);
        spnID2.setAdapter(adapters[0]);
        spnSet.setAdapter(adapters[0]);


        builder.setView(view)
                .setTitle("Set Tag Parameters")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String result = "" + spnSeries.getSelectedItem() + spnCharacter.getSelectedItem() +
                                spnVariation.getSelectedItem() + spnForm.getSelectedItem() +
                                spnID1.getSelectedItem() + spnID2.getSelectedItem() +
                                spnSet.getSelectedItem() + "02";
                        byte[] resultH = Util.hexStringToByteArray(result);
                        Log.d(TAG, spnCharacter.getSelectedItem().toString());
                        if (tagCreateListener != null)
                            tagCreateListener.onTagCreateDialogConfirm(TagCreateDialog.this, resultH);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

        return builder.create();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            tagCreateListener = (TagCreateListener)activity;
        } catch (ClassCastException e) {
        }
    }

}
