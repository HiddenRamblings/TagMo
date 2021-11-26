package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
import org.json.JSONException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_image)
@OptionsMenu({R.menu.image_menu})
public class ImageActivity extends AppCompatActivity {

    @ViewById(R.id.imageAmiibo)
    ImageView imageView;
    @ViewById(R.id.bottom_sheet)
    View bottomSheet;
    @ViewById(R.id.toggle)
    ImageView toggle;
    @ViewById(R.id.group0)
    View group0;
    @ViewById(R.id.txtTagId)
    TextView txtTagId;
    @ViewById(R.id.txtName)
    TextView txtName;
    @ViewById(R.id.txtGameSeries)
    TextView txtGameSeries;
    // @ViewById(R.id.txtCharacter)
    // TextView txtCharacter;
    @ViewById(R.id.txtAmiiboType)
    TextView txtAmiiboType;
    @ViewById(R.id.txtAmiiboSeries)
    TextView txtAmiiboSeries;

    BottomSheetBehavior<View> bottomSheetBehavior;
    Amiibo amiibo;
    AmiiboManager amiiboManager;

    @Extra(TagMo.EXTRA_AMIIBO_ID)
    long amiiboId;

    @AfterViews
    void afterViews() {
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
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            }
        });

        group0.addOnLayoutChangeListener((view, i, i1, i2, i3, i4, i5, i6, i7) -> {
            int height = view.getHeight() + bottomSheet.getPaddingTop();
            bottomSheetBehavior.setPeekHeight(height);
            imageView.setPadding(imageView.getPaddingLeft(), imageView.getPaddingTop(), imageView.getPaddingRight(), imageView.getPaddingTop() + height);
        });

        updateImage();
        loadAmiiboManager();
    }

    @Click(R.id.toggle)
    void onToggleClick() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

    void loadAmiiboManager() {
        BackgroundExecutor.cancelAll(BACKGROUND_AMIIBO_MANAGER, true);
        loadAmiiboManagerTask();
    }

    @Background(id = BACKGROUND_AMIIBO_MANAGER)
    void loadAmiiboManagerTask() {
        AmiiboManager amiiboManager = null;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager();
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
        }
        if (Thread.currentThread().isInterrupted())
            return;

        setAmiiboManager(amiiboManager);
    }

    @UiThread
    void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        this.updateView();
    }

    void updateImage() {
        Glide.with(this)
                .load(getImageUrl())
                .into(imageView);
    }

    void updateView() {
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

    void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
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

    String getImageUrl() {
        return Amiibo.getImageUrl(amiiboId);
    }

    @OptionsItem(R.id.mnu_save)
    void onSaveClicked() {
        final View view = this.getLayoutInflater().inflate(R.layout.edit_text, null);
        final EditText editText = view.findViewById(R.id.editText);
        if (null != amiibo) {
            editText.setText(amiibo.name);
        } else {
            editText.setText(TagUtils.amiiboIdToHex(amiiboId));
        }

        (new AlertDialog.Builder(this)).setTitle(R.string.save_image)
                .setPositiveButton(R.string.save, (dialogInterface, i) ->
                        Glide.with(ImageActivity.this)
                                .asBitmap().load(getImageUrl())
                                .into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(
                    @NonNull Bitmap resource, Transition transition) {
                saveImageToFile(resource, editText.getText().toString());
            }
            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }
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
}
