package com.hiddenramblings.tagmo;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.Executors;

public class ImageActivity extends AppCompatActivity {

    private ImageView imageView;
    private View bottomSheet;
    private ImageView toggle;
    private TextView txtTagId;
    private TextView txtName;
    private TextView txtGameSeries;
    // private TextView txtCharacter;
    private TextView txtAmiiboType;
    private TextView txtAmiiboSeries;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private Amiibo amiibo;
    private AmiiboManager amiiboManager;
    private long amiiboId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image);

        imageView = findViewById(R.id.imageAmiibo);
        bottomSheet = findViewById(R.id.bottom_sheet);
        toggle = findViewById(R.id.toggle);
        txtTagId = findViewById(R.id.txtTagId);
        txtName = findViewById(R.id.txtName);
        txtGameSeries = findViewById(R.id.txtGameSeries);
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType);
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries);

        amiiboId = getIntent().getLongExtra(TagMo.EXTRA_AMIIBO_ID, -1);

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        findViewById(R.id.group0).addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
            int height = view.getHeight() + bottomSheet.getPaddingTop();
            bottomSheetBehavior.setPeekHeight(height);
            imageView.setPadding(imageView.getPaddingLeft(), imageView.getPaddingTop(),
                    imageView.getPaddingRight(), imageView.getPaddingTop() + height);
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager = null;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager();
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
            }
            if (Thread.currentThread().isInterrupted())
                return;

            this.amiiboManager = amiiboManager;
            runOnUiThread(() -> updateView(amiiboId));
        });
        GlideApp.with(this).load(getImageUrl(amiiboId)).into(imageView);

        findViewById(R.id.toggle).setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }

    private void updateView(long amiiboId) {
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";

        amiibo = null;
        if (null != this.amiiboManager) {
            amiibo = amiiboManager.amiibos.get(amiiboId);
            if (null == amiibo)
                amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
        }
        if (null != amiibo) {
            amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
            if (null != amiibo.name)
                amiiboName = amiibo.name;
            if (null != amiibo.getAmiiboSeries())
                amiiboSeries = amiibo.getAmiiboSeries().name;
            if (null != amiibo.getAmiiboType())
                amiiboType = amiibo.getAmiiboType().name;
            if (null != amiibo.getGameSeries())
                gameSeries = amiibo.getGameSeries().name;
            // if (null != amiibo.getCharacter())
            //     character = amiibo.getCharacter().name;
        } else {
            tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
        }

        boolean hasTagInfo = null != tagInfo;
        if (hasTagInfo) {
            setAmiiboInfoText(txtName, tagInfo, false);
        } else {
            setAmiiboInfoText(txtName, amiiboName, false);
        }
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo);
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo);
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo);
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);
    }

    private void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
        if (hasTagInfo) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            if (text.length() == 0) {
                textView.setText(R.string.unknown);
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setEnabled(true);
            }
        }
    }

    private String getImageUrl(long amiiboId) {
        return Amiibo.getImageUrl(amiiboId);
    }

    void onSaveClicked(long amiiboId) {
        final View view = this.getLayoutInflater().inflate(R.layout.edit_text, null);
        final EditText editText = view.findViewById(R.id.editText);
        if (null != amiibo) {
            editText.setText(amiibo.name);
        } else {
            editText.setText(TagUtils.amiiboIdToHex(amiiboId));
        }

        (new AlertDialog.Builder(this)).setTitle(R.string.save_image)
                .setPositiveButton(R.string.save, (dialogInterface, i) ->
                        GlideApp.with(ImageActivity.this).asBitmap()
                                .load(getImageUrl(amiiboId))
                                .skipMemoryCache(true)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                saveImageToFile(resource, editText.getText().toString());
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) { }
        })).setNegativeButton(R.string.cancel, null).setView(view).show();
    }

    private void saveImageToFile(@NonNull Bitmap resource, String filename) {
        File file = new File(Storage.getDownloadDir("TagMo"), filename + ".png");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            resource.compress(Bitmap.CompressFormat.PNG, 100, fos);
            new Toasty(ImageActivity.this).Short(getString(R.string.wrote_file,
                    Storage.getRelativePath(file, TagMo.getPrefs().preferEmulated().get())));
        } catch (FileNotFoundException e) {
            Debug.Log(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.save_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.mnu_save) {
            onSaveClicked(amiiboId);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
