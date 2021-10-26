package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.nfctag.TagUtils;

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

    public static final String BACKGROUND_AMIIBO_MANAGER = "amiibo_manager";

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
            e.printStackTrace();
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
        if (this.amiiboManager != null) {
            amiibo = amiiboManager.amiibos.get(amiiboId);
            if (amiibo == null)
                amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
        }
        if (amiibo != null) {
            amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
            if (amiibo.name != null)
                amiiboName = amiibo.name;
            if (amiibo.getAmiiboSeries() != null)
                amiiboSeries = amiibo.getAmiiboSeries().name;
            if (amiibo.getAmiiboType() != null)
                amiiboType = amiibo.getAmiiboType().name;
            if (amiibo.getGameSeries() != null)
                gameSeries = amiibo.getGameSeries().name;
            // if (amiibo.getCharacter() != null)
            //     character = amiibo.getCharacter().name;
        } else {
            tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
        }

        if (tagInfo == null) {
            setAmiiboInfoText(txtName, amiiboName, false);
        } else {
            setAmiiboInfoText(txtName, tagInfo, false);
        }
        setAmiiboInfoText(txtTagId, amiiboHexId, tagInfo != null);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, tagInfo != null);
        setAmiiboInfoText(txtAmiiboType, amiiboType, tagInfo != null);
        setAmiiboInfoText(txtGameSeries, gameSeries, tagInfo != null);
        // setAmiiboInfoText(txtCharacter, character, tagInfo != null);
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
        if (amiibo != null) {
            editText.setText(amiibo.name);
        } else {
            editText.setText(TagUtils.amiiboIdToHex(amiiboId));
        }

        (new AlertDialog.Builder(this))
                .setTitle(R.string.save_image)
                .setNegativeButton(R.string.save, (dialogInterface, i) -> {
                    final File file = new File(TagMo.getTagMoFiles().getAbsolutePath(),
                            editText.getText().toString() + ".png");

                    Glide.with(ImageActivity.this)
                            .asBitmap()
                            .load(getImageUrl())
                            .into(new CustomTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                                    FileOutputStream fos = null;
                                    try {
                                        fos = new FileOutputStream(file);
                                        resource.compress(Bitmap.CompressFormat.PNG, 100, fos);

                                        String text = "Saved file as " + TagMo.friendlyPath(file);
                                        Toast.makeText(ImageActivity.this, text, Toast.LENGTH_SHORT).show();
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    } finally {
                                        if (fos != null) {
                                            try {
                                                fos.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                @Override
                                public void onLoadCleared(@Nullable Drawable placeholder) {

                                }
                            });
                })
                .setPositiveButton(R.string.cancel, null)
                .setView(view)
                .show();
    }
}
