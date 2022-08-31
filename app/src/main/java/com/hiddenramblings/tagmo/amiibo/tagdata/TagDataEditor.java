package com.hiddenramblings.tagmo.amiibo.tagdata;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.vicmikhailau.maskededittext.MaskedEditText;

import org.json.JSONException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class TagDataEditor extends AppCompatActivity {

    private TextView txtError;
    private TextView txtTagId;
    private TextView txtName;
    private TextView txtGameSeries;
    // private TextView txtCharacter;
    private TextView txtAmiiboType;
    private TextView txtAmiiboSeries;
    private AppCompatImageView imageAmiibo;

    private MaskedEditText txtUID;
    private Spinner txtCountryCode;
    private EditText txtInitDate;
    private EditText txtModifiedDate;
    private EditText txtNickname;
    private EditText txtMiiName;
    private MaskedEditText txtAppId;
    private EditText txtWriteCounter;
    private EditText txtSerialNumber;
    private Spinner txtAppName;
    private SwitchCompat appDataSwitch;
    private SwitchCompat userDataSwitch;
    private AppCompatButton generateSerial;

    private LinearLayout appDataViewTP;
    private LinearLayout appDataViewSBU;
    private LinearLayout appDataViewSSB;

    private CountryCodesAdapter countryCodeAdapter;
    private NSSpinnerAdapter appIdAdapter;
    private boolean ignoreAppNameSelected;
    private KeyManager keyManager;
    private AmiiboManager amiiboManager = null;
    private AmiiboData amiiboData;

    private boolean initialUserDataInitialized;
    public boolean isAppDataInitialized;
    private boolean initialAppDataInitialized;
    private boolean isUserDataInitialized;
    private Date initializedDate;
    private Date modifiedDate;
    private Integer appId;

    public static final int APP_ID_TP = 0x1019C800;

    private EditText txtHearts1;
    private Spinner txtHearts2;
    private EditText txtLevelTP;

    private AppDataTP appDataTP;

    public static final int APP_ID_SBU = 0x000EDF00;

    private EditText txtLevelSBU;

    private AppDataSBU appDataSBU;

    public static final int APP_ID_SSB = 0x10110E00;

    private Spinner spnAppearance;
    private EditText txtLevelSSB;

    private Spinner spnSpecialNeutral;
    private Spinner spnSpecialSide;
    private Spinner spnSpecialUp;
    private Spinner spnSpecialDown;

    private Spinner spnEffect1;
    private Spinner spnEffect2;
    private Spinner spnEffect3;

    private EditText txtStatAttack;
    private EditText txtStatDefense;
    private EditText txtStatSpeed;

    private AppDataSSB appDataSSB;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tag_data);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        txtError = findViewById(R.id.txtError);
        txtTagId = findViewById(R.id.txtTagId);
        txtName = findViewById(R.id.txtName);
        txtGameSeries = findViewById(R.id.txtGameSeries);
        // txtCharacter = findViewById(R.id.txtCharacter);
        txtAmiiboType = findViewById(R.id.txtAmiiboType);
        txtAmiiboSeries = findViewById(R.id.txtAmiiboSeries);
        imageAmiibo = findViewById(R.id.imageAmiibo);

        txtUID = findViewById(R.id.txtUID);
        txtCountryCode = findViewById(R.id.txtCountryCode);
        txtInitDate = findViewById(R.id.txtInitDate);
        txtModifiedDate = findViewById(R.id.txtModifiedDate);
        txtNickname = findViewById(R.id.txtNickname);
        txtMiiName = findViewById(R.id.txtMiiName);
        txtAppId = findViewById(R.id.txtAppId);
        txtWriteCounter = findViewById(R.id.txtWriteCounter);
        txtSerialNumber = findViewById(R.id.txtSerialNumber);
        txtAppName = findViewById(R.id.txtAppName);
        appDataSwitch = findViewById(R.id.appDataSwitch);
        userDataSwitch = findViewById(R.id.userDataSwitch);
        generateSerial = findViewById(R.id.random_serial);

        appDataViewTP = findViewById(R.id.appDataTP);
        appDataViewSBU = findViewById(R.id.appDataSBU);
        appDataViewSSB = findViewById(R.id.appDataSSB);

        byte[] tagData = getIntent().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);

        keyManager = new KeyManager(this);
        if (keyManager.isKeyMissing()) {
            showErrorDialog(R.string.no_decrypt_key);
            return;
        }
        try {
            this.amiiboData = new AmiiboData(keyManager.decrypt(tagData));
        } catch (Exception e) {
            try {
                tagData = TagUtils.getValidatedData(keyManager, tagData);
                this.amiiboData = new AmiiboData(tagData);
            } catch (Exception ex) {
                Debug.Warn(e);
                showErrorDialog(R.string.fail_display);
                return;
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.edit_props);
        toolbar.inflateMenu(R.menu.save_menu);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.mnu_save) {
                onSaveClicked();
                return true;
            }
            return false;
        });

        userDataSwitch.setOnCheckedChangeListener((compoundButton, checked) ->
                onUserDataSwitchClicked(checked));

        appDataSwitch.setOnCheckedChangeListener((compoundButton, checked) ->
                onAppDataSwitchClicked(checked));

        findViewById(R.id.random_serial).setOnClickListener(view ->
                txtSerialNumber.setText(TagUtils.bytesToHex(new Foomiibo().generateRandomUID())));

        findViewById(R.id.txtInitDate).setOnClickListener(view -> {
            final Calendar c = Calendar.getInstance();
            c.setTime(initializedDate);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    TagDataEditor.this,
                    onInitDateSet,
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        findViewById(R.id.txtModifiedDate).setOnClickListener(view -> {
            final Calendar c = Calendar.getInstance();
            c.setTime(modifiedDate);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    TagDataEditor.this,
                    onModifiedDateSet,
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        final byte[] finalTagData = tagData;
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager = null;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Warn(e);
                new Toasty(this).Short(getString(R.string.amiibo_info_parse_error));
            }

            if (Thread.currentThread().isInterrupted()) return;

            this.amiiboManager = amiiboManager;
            runOnUiThread(() -> updateAmiiboView(finalTagData));
        });
        updateAmiiboView(tagData);

        txtAppName.setOnItemSelectedListener(onAppNameSelected);
        txtAppId.addTextChangedListener(onAppIdChange);

        countryCodeAdapter = new CountryCodesAdapter(AmiiboData.countryCodes);
        txtCountryCode.setAdapter(countryCodeAdapter);

        appIdAdapter = new NSSpinnerAdapter(
                new AppIdAdapter(AppData.appIds), R.layout.nothing_spinner_text);
        txtAppName.setAdapter(appIdAdapter);

        txtWriteCounter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void afterTextChanged(Editable editable) {
                try {
                    int writeCounter = Integer.parseInt(txtWriteCounter.getText().toString());
                    amiiboData.checkWriteCount(writeCounter);
                    txtWriteCounter.setError(null);
                } catch (Exception e) {
                    txtWriteCounter.setError(getString(R.string.error_min_max,
                            AmiiboData.WRITE_COUNT_MIN_VALUE, AmiiboData.WRITE_COUNT_MAX_VALUE));
                }
            }
        });

        loadData();
    }

    private final CustomTarget<Bitmap> imageTarget = new CustomTarget<>() {

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) { }

        @Override
        public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
            imageAmiibo.setImageBitmap(resource);
            imageAmiibo.setVisibility(View.VISIBLE);
        }
    };

    private void updateAmiiboView(byte[] tagData) {
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        final String amiiboImageUrl;

        if (null == tagData) {
            tagInfo = getString(R.string.no_tag_loaded);
            amiiboImageUrl = null;
        } else {
            long amiiboId;
            try {
                amiiboId = TagUtils.amiiboIdFromTag(tagData);
            } catch (Exception e) {
                Debug.Info(e);
                amiiboId = -1;
            }
            if (amiiboId == 0) {
                tagInfo = getString(R.string.blank_tag);
                amiiboImageUrl = null;
            } else {
                Amiibo amiibo = null;
                if (null != this.amiiboManager) {
                    amiibo = amiiboManager.amiibos.get(amiiboId);
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
                }
                if (null != amiibo) {
                    amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
                    amiiboImageUrl = amiibo.getImageUrl();
                    if (null != amiibo.name)
                        amiiboName = amiibo.name;
                    if (null != amiibo.getAmiiboSeries())
                        amiiboSeries = amiibo.getAmiiboSeries().name;
                    if (null != amiibo.getAmiiboType())
                        amiiboType = amiibo.getAmiiboType().name;
                    if (null != amiibo.getGameSeries())
                        gameSeries = amiibo.getGameSeries().name;
                } else {
                    tagInfo = "ID: " + TagUtils.amiiboIdToHex(amiiboId);
                    amiiboImageUrl = Amiibo.getImageUrl(amiiboId);
                }
            }
        }

        if (null == tagInfo) {
            txtError.setVisibility(View.GONE);
        } else {
            setAmiiboInfoText(txtError, tagInfo, false);
        }
        boolean hasTagInfo = null != tagInfo;
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo);
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo);
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo);
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo);
        // setAmiiboInfoText(txtCharacter, character, hasTagInfo);

        if (null != imageAmiibo) {
            imageAmiibo.setVisibility(View.GONE);
            GlideApp.with(this).clear(imageTarget);
            if (null != amiiboImageUrl) {
                GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(imageTarget);
            }
        }
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

    private void loadData() {
        boolean isUserDataInitialized = amiiboData.isUserDataInitialized();
        this.initialUserDataInitialized = isUserDataInitialized;
        userDataSwitch.setChecked(isUserDataInitialized);
        onUserDataSwitchClicked(isUserDataInitialized);

        boolean isAppDataInitialized = amiiboData.isAppDataInitialized();
        this.initialAppDataInitialized = isAppDataInitialized;
        appDataSwitch.setChecked(isAppDataInitialized);
        onAppDataSwitchClicked(isAppDataInitialized);

        loadUID();
        loadCountryCode();
        loadInitializedDate();
        loadModifiedDate();
        loadNickname();
        loadMiiName();
        loadWriteCounter();
        loadSerialNumber();
        loadAppId();
    }

    void onUserDataSwitchClicked(boolean isUserDataInitialized) {
        this.isUserDataInitialized = isUserDataInitialized;
        updateUserDataEnabled(isUserDataInitialized);
    }

    void onAppDataSwitchClicked(boolean isAppDataInitialized) {
        this.isAppDataInitialized = isAppDataInitialized;
        updateAppDataEnabled(isAppDataInitialized);
    }

    private void onSaveClicked() {
        AmiiboData newAmiiboData;
        try {
            newAmiiboData = new AmiiboData(this.amiiboData.array());

            newAmiiboData.setUserDataInitialized(isUserDataInitialized);
            newAmiiboData.setAppDataInitialized(isUserDataInitialized && isAppDataInitialized);
        } catch (Exception e) {
            Debug.Warn(e);
            showErrorDialog(R.string.fail_save_data);
            return;
        }

        if (isUserDataInitialized) {
            try {
                //noinspection unchecked
                int countryCode = ((HashMap.Entry<Integer, String>)
                        txtCountryCode.getSelectedItem()).getKey();
                newAmiiboData.setCountryCode(countryCode);
            } catch (Exception e) {
                txtCountryCode.requestFocus();
                return;
            }

            try {
                newAmiiboData.setInitializedDate(initializedDate);
            } catch (Exception e) {
                txtInitDate.requestFocus();
                return;
            }
            try {
                newAmiiboData.setModifiedDate(modifiedDate);
            } catch (Exception e) {
                txtModifiedDate.requestFocus();
                return;
            }

            try {
                String nickname = txtNickname.getText().toString();
                newAmiiboData.setNickname(nickname);
            } catch (Exception e) {
                txtNickname.requestFocus();
                return;
            }

            try {
                String miiName = txtMiiName.getText().toString();
                newAmiiboData.setMiiName(miiName);
            } catch (Exception e) {
                txtMiiName.requestFocus();
                return;
            }

            try {
                int writeCounter = Integer.parseInt(txtWriteCounter.getText().toString());
                newAmiiboData.setWriteCount(writeCounter);
            } catch (Exception e) {
                txtWriteCounter.requestFocus();
                return;
            }

            try {
                byte[] serialNumber = TagUtils.hexToByteArray(txtSerialNumber.getText().toString());
                newAmiiboData.setUID(serialNumber);
            } catch (Exception e) {
                txtSerialNumber.requestFocus();
                return;
            }

            try {
                int appId = parseAppId();
                newAmiiboData.setAppId(appId);
            } catch (Exception e) {
                txtAppId.requestFocus();
                return;
            }

            if (appDataSwitch.isChecked() && null != appDataTP) {
                try {
                    newAmiiboData.setAppData(onAppDataTPSaved());
                } catch (Exception e) {
                    return;
                }
            }

            if (appDataSwitch.isChecked() && null != appDataSBU) {
                try {
                    newAmiiboData.setAppData(onAppDataSBUSaved());
                } catch (Exception e) {
                    return;
                }
            }

            if (appDataSwitch.isChecked() && null != appDataSSB) {
                try {
                    newAmiiboData.setAppData(onAppDataSSBSaved());
                } catch (Exception e) {
                    return;
                }
            }
        }

        byte[] tagData;
        try {
            tagData = keyManager.encrypt(newAmiiboData.array());
        } catch (Exception e) {
            Debug.Warn(e);
            showErrorDialog(R.string.fail_encrypt);
            return;
        }

        Intent intent = new Intent(NFCIntent.ACTION_EDIT_COMPLETE);
        intent.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private void updateUserDataEnabled(boolean isUserDataInitialized) {
        txtCountryCode.setEnabled(isUserDataInitialized);
        txtInitDate.setEnabled(isUserDataInitialized);
        txtModifiedDate.setEnabled(isUserDataInitialized);
        txtNickname.setEnabled(isUserDataInitialized);
        txtMiiName.setEnabled(isUserDataInitialized);
        txtWriteCounter.setEnabled(isUserDataInitialized);
        txtSerialNumber.setEnabled(isUserDataInitialized);
        generateSerial.setEnabled(isUserDataInitialized);
        txtAppId.setEnabled(isUserDataInitialized);
        txtAppName.setEnabled(isUserDataInitialized);

        appDataSwitch.setEnabled(isUserDataInitialized);
        updateAppDataEnabled(isAppDataInitialized);
    }

    private void updateAppDataEnabled(boolean isAppDataInitialized) {
        if (null != appDataTP)
            onAppDataTPChecked(isUserDataInitialized && isAppDataInitialized);

        if (null != appDataSBU)
            onAppDataSBUChecked(isUserDataInitialized && isAppDataInitialized);

        if (null != appDataSSB)
            onAppDataSSBChecked(isUserDataInitialized && isAppDataInitialized);
    }

    private void loadUID() {
        txtUID.setText(TagUtils.bytesToHex(amiiboData.getUID()));
    }

    private void loadCountryCode() {
        int countryCode;
        if (initialUserDataInitialized) {
            try {
                countryCode = amiiboData.getCountryCode();
            } catch (Exception e) {
                countryCode = 0;
            }
        } else {
            countryCode = 0;
        }

        int index = 0;
        for (int i = 0; i < this.countryCodeAdapter.getCount(); i++) {
            Map.Entry<Integer, String> entry = this.countryCodeAdapter.getItem(i);
            if (entry.getKey() == countryCode) {
                index = i;
                break;
            }
        }
        txtCountryCode.setSelection(index);
    }

    private void loadInitializedDate() {
        if (initialUserDataInitialized) {
            try {
                this.initializedDate = amiiboData.getInitializedDate();
            } catch (Exception e) {
                this.initializedDate = new Date();
            }
        } else {
            this.initializedDate = new Date();
        }
        updateInitializedDateView(this.initializedDate);
    }

    private void updateInitializedDateView(Date date) {
        String text;
        try {
            text = getDateString(date);
        } catch (IllegalArgumentException e) {
            text = getString(R.string.invalid);
        }
        txtInitDate.setText(text);
    }

    private final DatePickerDialog.OnDateSetListener onInitDateSet =
            new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker datePicker, int year, int month, int day) {
            final Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);

            initializedDate = c.getTime();
            updateInitializedDateView(c.getTime());
        }
    };

    private void loadModifiedDate() {
        if (initialUserDataInitialized) {
            try {
                this.modifiedDate = amiiboData.getModifiedDate();
            } catch (Exception e) {
                this.modifiedDate = new Date();
            }
        } else {
            this.modifiedDate = new Date();
        }
        updateModifiedDateView(this.modifiedDate);
    }

    private void updateModifiedDateView(Date date) {
        String text;
        try {
            text = getDateString(date);
        } catch (IllegalArgumentException e) {
            text = getString(R.string.invalid);
        }
        txtModifiedDate.setText(text);
    }

    private final DatePickerDialog.OnDateSetListener onModifiedDateSet =
            new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker datePicker, int year, int month, int day) {
            final Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);

            modifiedDate = c.getTime();
            updateModifiedDateView(c.getTime());
        }
    };

    private void loadNickname() {
        String nickname;
        if (initialUserDataInitialized) {
            try {
                nickname = amiiboData.getNickname().trim();
            } catch (UnsupportedEncodingException e) {
                nickname = "";
            }
        } else {
            nickname = "";
        }
        txtNickname.setText(nickname);
    }

    private void loadMiiName() {
        String miiName;
        if (initialUserDataInitialized) {
            try {
                miiName = amiiboData.getMiiName().trim();
            } catch (UnsupportedEncodingException e) {
                miiName = "";
            }
        } else {
            miiName = "";
        }
        txtMiiName.setText(miiName);
    }

    private void loadAppId() {
        if (initialUserDataInitialized) {
            appId = amiiboData.getAppId();
        } else {
            appId = 0;
        }

        updateAppIdView();
        updateAppNameView();
        updateAppDataView();
    }

    private void updateAppIdView() {
        if (null != appId) {
            txtAppId.setText(String.format("%08X", appId));
        } else {
            txtAppId.setText("");
        }
    }

    private int parseAppId() throws Exception {
        String text = txtAppId.getUnMaskedText();
        if (null != text) {
            text = text.trim();
            if (text.length() != 8) {
                throw new IOException(getString(R.string.error_length));
            }
            try {
                return (int) Long.parseLong(text, 16);
            } catch (NumberFormatException e) {
                throw new NumberFormatException(getString(R.string.error_input));
            }
        } else {
            throw new IOException(getString(R.string.invalid_app_data));
        }
    }

    private final TextWatcher onAppIdChange = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            try {
                appId = parseAppId();
                txtAppId.setError(null);
            } catch (Exception e) {
                appId = null;
                txtAppId.setError(e.getMessage());
            }
            updateAppNameView();
            updateAppDataView();
        }
    };

    private void updateAppNameView() {
        int index = 0;
        for (int i = 0; i < appIdAdapter.getCount(); i++) {
            //noinspection unchecked
            HashMap.Entry<Integer, String> item =
                    (HashMap.Entry<Integer, String>) appIdAdapter.getItem(i);
            if (null != item && item.getKey().equals(appId)) {
                index = i;
            }
        }
        if (txtAppName.getSelectedItemPosition() != index) {
            ignoreAppNameSelected = true;
            txtAppName.setSelection(index);
        }
    }

    private final AdapterView.OnItemSelectedListener onAppNameSelected =
            new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (ignoreAppNameSelected) {
                ignoreAppNameSelected = false;
                return;
            }

            Object selectedItem = adapterView.getItemAtPosition(i);
            if (null != selectedItem) {
                //noinspection unchecked
                appId = ((HashMap.Entry<Integer, String>) selectedItem).getKey();
            }

            updateAppIdView();
            updateAppDataView();
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }
    };

    private void updateAppDataView() {
        appDataViewTP.setVisibility(View.GONE);
        appDataTP = null;
        appDataViewSBU.setVisibility(View.GONE);
        appDataSBU = null;
        appDataViewSSB.setVisibility(View.GONE);
        appDataSSB = null;

        if (null != appId) {
            if (appId == APP_ID_TP) {
                appDataViewTP.setVisibility(View.VISIBLE);
                enableAppDataTP(amiiboData.getAppData());
            } else if (appId == APP_ID_SBU) {
                appDataViewSBU.setVisibility(View.VISIBLE);
                enableAppDataSBU(amiiboData.getAppData());
            } else if (appId == APP_ID_SSB) {
                appDataViewSSB.setVisibility(View.VISIBLE);
                enableAppDataSSB(amiiboData.getAppData());
            }
        }
    }

    private void loadWriteCounter() {
        int writeCounter;
        if (initialUserDataInitialized) {
            writeCounter = amiiboData.getWriteCount();
        } else {
            writeCounter = 0;
        }
        txtWriteCounter.setText(String.valueOf(writeCounter));
    }

    private void loadSerialNumber() {
        txtSerialNumber.setTag(txtSerialNumber.getKeyListener());
        txtSerialNumber.setKeyListener(null);
        byte[] value = amiiboData.getUID();
        txtSerialNumber.setText(TagUtils.bytesToHex(value));
    }

    private static String getDateString(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date);
    }

    private static class CountryCodesAdapter extends BaseAdapter implements Filterable {
        public ArrayList<HashMap.Entry<Integer, String>> data;

        public CountryCodesAdapter(HashMap<Integer, String> data) {
            this.data = new ArrayList<>(data.entrySet());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Collections.sort(this.data, Map.Entry.comparingByKey());
            } else {
                //noinspection all
                Collections.sort(this.data, (entry1, entry2)
                        -> entry1.getKey().compareTo(entry2.getKey()));
            }
        }

        @Override
        public int getCount() {
            return this.data.size();
        }

        @Override
        public HashMap.Entry<Integer, String> getItem(int i) {
            return data.get(i);
        }

        @Override
        public long getItemId(int i) {
            return this.data.get(i).getKey();
        }

        @Override
        public View getDropDownView(int position, View view, ViewGroup parent) {
            if (null == view) {
                view = LayoutInflater
                        .from(parent.getContext())
                        .inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
            }
            ((TextView) view.findViewById(android.R.id.text1)).setText(this.getItem(position).getValue());
            return view;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (null == view) {
                view = LayoutInflater
                        .from(parent.getContext())
                        .inflate(R.layout.spinner_text, parent, false);
            }
            ((TextView) view).setText(this.getItem(position).getValue());
            return view;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        Filter filter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                FilterResults filterResults = new FilterResults();
                filterResults.values = data;
                filterResults.count = data.size();
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            }
        };
    }

    private static class AppIdAdapter extends BaseAdapter {
        public ArrayList<HashMap.Entry<Integer, String>> data;

        public AppIdAdapter(HashMap<Integer, String> data) {
            this.data = new ArrayList<>(data.entrySet());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Collections.sort(this.data, Map.Entry.comparingByKey());
            } else {
                //noinspection all
                Collections.sort(this.data, (entry1, entry2)
                        -> entry1.getKey().compareTo(entry2.getKey()));
            }
        }

        @Override
        public int getCount() {
            return this.data.size();
        }

        @Override
        public HashMap.Entry<Integer, String> getItem(int i) {
            return data.get(i);
        }

        @Override
        public long getItemId(int i) {
            return this.data.get(i).getKey();
        }

        @Override
        public View getDropDownView(int position, View view, ViewGroup parent) {
            if (null == view) {
                view = LayoutInflater
                        .from(parent.getContext())
                        .inflate(R.layout.spinner_text2, parent, false);
            }
            ((TextView) view.findViewById(R.id.text1)).setText(this.getItem(position).getValue());
            ((TextView) view.findViewById(R.id.text2)).setText(
                    String.format("%08X", this.getItem(position).getKey()));
            return view;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (null == view) {
                view = LayoutInflater
                        .from(parent.getContext())
                        .inflate(R.layout.spinner_text, parent, false);
            }
            ((TextView) view).setText(this.getItem(position).getValue());
            return view;
        }
    }

    void setListForSpinners(Spinner[] controls, int list) {
        for (Spinner control : controls) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    this, list, R.layout.spinner_text);
            adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
            control.setAdapter(adapter);
        }
    }

    void onHeartsUpdate() {
        try {
            int hearts = Integer.parseInt(txtHearts1.getText().toString());
            txtHearts2.setEnabled(hearts < 20);
            if (!txtHearts2.isEnabled()) {
                txtHearts2.setSelection(0);
            }
            try {
                appDataTP.checkHearts(hearts * 4);
                txtHearts1.setError(null);
            } catch (Exception e) {
                txtHearts1.setError(getString(R.string.error_min_max, 0, 20));
            }
        } catch (NumberFormatException e) {
            txtHearts1.setError(getString(R.string.error_min_max, 0, 20));
            txtHearts2.setEnabled(txtHearts1.isEnabled());
        }
    }

    void setEffectValue(Spinner spinner, int value) {
        if (value == 0xFF)
            value = 0;
        else
            value++;

        if (value > spinner.getAdapter().getCount())
            value = 0;

        spinner.setSelection(value);
    }

    int getEffectValue(Spinner spinner) {
        int value = spinner.getSelectedItemPosition();
        if (value == 0)
            value = 0xFF;
        else
            value--;

        return value;
    }

    private void enableAppDataTP(byte[] appData) {
        try {
            appDataTP = new AppDataTP(appData);
        } catch (Exception e) {
            appDataViewTP.setVisibility(View.GONE);
            return;
        }

        txtHearts1 = findViewById(R.id.txtHearts1);
        txtHearts2 = findViewById(R.id.txtHearts2);
        txtLevelTP = findViewById(R.id.txtLevelTP);

        setListForSpinners(new Spinner[]{ txtHearts2 }, R.array.editor_tp_hearts);

        int level, hearts;
        if (initialAppDataInitialized) {
            try {
                level = appDataTP.getLevel();
            } catch (Exception e) {
                level = 40;
            }
            try {
                hearts = appDataTP.getHearts();
            } catch (Exception e) {
                hearts = AppDataTP.HEARTS_MAX_VALUE;
            }
        } else {
            level = 40;
            hearts = AppDataTP.HEARTS_MAX_VALUE;
        }
        txtLevelTP.setText(String.valueOf(level));
        txtHearts1.setText(String.valueOf((hearts / 4)));
        txtHearts2.setSelection(hearts % 4);
        txtHearts2.setEnabled((hearts / 4) < 20);

        txtLevelTP.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    int level = Integer.parseInt(txtLevelTP.getText().toString());
                    try {
                        appDataTP.checkLevel(level);
                        txtLevelTP.setError(null);
                    } catch (Exception e) {
                        txtLevelTP.setError(getString(R.string.error_min_max, 0, 40));
                    }
                } catch (NumberFormatException e) {
                    txtLevelTP.setError(getString(R.string.error_min_max, 0, 40));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        txtHearts1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                onHeartsUpdate();
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        onAppDataTPChecked(isAppDataInitialized);
    }

    private void enableAppDataSBU(byte[] appData) {
        try {
            appDataSBU = new AppDataSBU(appData);
        } catch (Exception e) {
            appDataViewSBU.setVisibility(View.GONE);
            return;
        }

        txtLevelSBU = findViewById(R.id.txtLevelSBU);

        int level;
        if (initialAppDataInitialized) {
            try {
                level = appDataSBU.getLevel();
            } catch (Exception e) {
                level = 50;
            }
        } else {
            level = 50;
        }
        txtLevelSBU.setText(String.valueOf(level));

        txtLevelSBU.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    int level = Integer.parseInt(txtLevelSBU.getText().toString());
                    if (level < 1 || level > 50) {
                        txtLevelSBU.setError(
                                getString(R.string.error_min_max, 1, 50));
                    } else {
                        txtLevelSBU.setError(null);
                    }
                } catch (NumberFormatException e) {
                    txtLevelSBU.setError(
                            getString(R.string.error_min_max, 1, 50));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });
    }

    private void enableAppDataSSB(byte[] appData) {
        try {
            appDataSSB = new AppDataSSB(appData);
        } catch (Exception e) {
            appDataViewSSB.setVisibility(View.GONE);
            return;
        }

        spnAppearance = findViewById(R.id.spnAppearance);
        txtLevelSSB = findViewById(R.id.txtLevelSSB);

        spnSpecialNeutral = findViewById(R.id.spnSpecial1);
        spnSpecialSide = findViewById(R.id.spnSpecial2);
        spnSpecialUp = findViewById(R.id.spnSpecial3);
        spnSpecialDown = findViewById(R.id.spnSpecial4);

        spnEffect1 = findViewById(R.id.spnEffect1);
        spnEffect2 = findViewById(R.id.spnEffect2);
        spnEffect3 = findViewById(R.id.spnEffect3);

        txtStatAttack = findViewById(R.id.txtStatAttack);
        txtStatDefense = findViewById(R.id.txtStatDefense);
        txtStatSpeed = findViewById(R.id.txtStatSpeed);

        setListForSpinners(new Spinner[]{ spnAppearance }, R.array.ssb_appearance_values);
        setListForSpinners(new Spinner[]{ spnSpecialNeutral, spnSpecialSide,
                spnSpecialUp, spnSpecialDown }, R.array.ssb_specials_values);
        setListForSpinners(new Spinner[]{ spnEffect1, spnEffect2, spnEffect3 },
                R.array.ssb_bonus_effects);

        int appearance, level, statAttack, statDefense, statSpeed,
                specialNeutral, specialSide, specialUp, specialDown,
                bonusEffect1, bonusEffect2, bonusEffect3;
        if (initialAppDataInitialized) {
            try {
                appearance = appDataSSB.getAppearence();
            } catch (Exception e) {
                appearance = 0;
            }
            try {
                level = appDataSSB.getLevel();
            } catch (Exception e) {
                level = 50;
            }
            try {
                statAttack = appDataSSB.getStatAttack();
            } catch (Exception e) {
                statAttack = 200;
            }
            try {
                statDefense = appDataSSB.getStatDefense();
            } catch (Exception e) {
                statDefense = 200;
            }
            try {
                statSpeed = appDataSSB.getStatSpeed();
            } catch (Exception e) {
                statSpeed = 200;
            }
            try {
                specialNeutral = appDataSSB.getSpecialNeutral();
            } catch (Exception e) {
                specialNeutral = 0;
            }
            try {
                specialSide = appDataSSB.getSpecialSide();
            } catch (Exception e) {
                specialSide = 0;
            }
            try {
                specialUp = appDataSSB.getSpecialUp();
            } catch (Exception e) {
                specialUp = 0;
            }
            try {
                specialDown = appDataSSB.getSpecialDown();
            } catch (Exception e) {
                specialDown = 0;
            }
            try {
                bonusEffect1 = appDataSSB.getBonusEffect1();
            } catch (Exception e) {
                bonusEffect1 = 0xFF;
            }
            try {
                bonusEffect2 = appDataSSB.getBonusEffect2();
            } catch (Exception e) {
                bonusEffect2 = 0xFF;
            }
            try {
                bonusEffect3 = appDataSSB.getBonusEffect3();
            } catch (Exception e) {
                bonusEffect3 = 0xFF;
            }
        } else {
            appearance = 0;
            level = 50;
            statAttack = 200;
            statDefense = 200;
            statSpeed = 200;
            specialNeutral = 0;
            specialSide = 0;
            specialUp = 0;
            specialDown = 0;
            bonusEffect1 = 0xFF;
            bonusEffect2 = 0xFF;
            bonusEffect3 = 0xFF;
        }
        spnAppearance.setSelection(appearance);
        txtLevelSSB.setText(String.valueOf(level));
        txtStatAttack.setText(String.valueOf(statAttack));
        txtStatDefense.setText(String.valueOf(statDefense));
        txtStatSpeed.setText(String.valueOf(statSpeed));
        spnSpecialNeutral.setSelection(specialNeutral);
        spnSpecialSide.setSelection(specialSide);
        spnSpecialUp.setSelection(specialUp);
        spnSpecialDown.setSelection(specialDown);
        setEffectValue(spnEffect1, bonusEffect1);
        setEffectValue(spnEffect2, bonusEffect2);
        setEffectValue(spnEffect3, bonusEffect3);

        txtLevelSSB.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    int level = Integer.parseInt(txtLevelSSB.getText().toString());
                    if (level < 1 || level > 50) {
                        txtLevelSSB.setError(
                                getString(R.string.error_min_max, 1, 50));
                    } else {
                        txtLevelSSB.setError(null);
                    }
                } catch (NumberFormatException e) {
                    txtLevelSSB.setError(
                            getString(R.string.error_min_max, 1, 50));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        txtStatAttack.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    int level = Integer.parseInt(txtStatAttack.getText().toString());
                    try {
                        appDataSSB.checkStat(level);
                        txtStatAttack.setError(null);
                    } catch (Exception e) {
                        txtStatAttack.setError(
                                getString(R.string.error_min_max, -200, 200));
                    }
                } catch (NumberFormatException e) {
                    txtStatAttack.setError(
                            getString(R.string.error_min_max, -200, 200));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        txtStatDefense.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    int level = Integer.parseInt(txtStatDefense.getText().toString());
                    try {
                        appDataSSB.checkStat(level);
                        txtStatDefense.setError(null);
                    } catch (Exception e) {
                        txtStatDefense.setError(
                                getString(R.string.error_min_max, -200, 200));
                    }
                } catch (NumberFormatException e) {
                    txtStatDefense.setError(
                            getString(R.string.error_min_max, -200, 200));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        txtStatSpeed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                try {
                    int level = Integer.parseInt(txtStatSpeed.getText().toString());
                    try {
                        appDataSSB.checkStat(level);
                        txtStatSpeed.setError(null);
                    } catch (Exception e) {
                        txtStatSpeed.setError(
                                getString(R.string.error_min_max, -200, 200));
                    }
                } catch (NumberFormatException e) {
                    txtStatSpeed.setError(
                            getString(R.string.error_min_max, -200, 200));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        onAppDataSSBChecked(isAppDataInitialized);
    }

    public void onAppDataTPChecked(boolean enabled) {
        if (null == txtHearts2 )
            return;

        txtHearts1.setEnabled(enabled);
        onHeartsUpdate();
        txtLevelTP.setEnabled(enabled);
    }

    public void onAppDataSBUChecked(boolean enabled) {
        txtLevelSBU.setEnabled(enabled);
    }

    public void onAppDataSSBChecked(boolean enabled) {
        if (null == spnAppearance)
            return;

        spnAppearance.setEnabled(enabled);
        txtLevelSSB.setEnabled(enabled);
        spnSpecialNeutral.setEnabled(enabled);
        spnSpecialSide.setEnabled(enabled);
        spnSpecialUp.setEnabled(enabled);
        spnSpecialDown.setEnabled(enabled);
        txtStatAttack.setEnabled(enabled);
        txtStatDefense.setEnabled(enabled);
        txtStatSpeed.setEnabled(enabled);
        spnEffect1.setEnabled(enabled);
        spnEffect2.setEnabled(enabled);
        spnEffect3.setEnabled(enabled);
    }

    public byte[] onAppDataTPSaved() {
        try {
            int level = Integer.parseInt(txtLevelTP.getText().toString());
            appDataTP.setLevel(level);
        } catch (NumberFormatException e) {
            txtLevelTP.requestFocus();
            throw e;
        }
        try {
            int hearts1 = Integer.parseInt(txtHearts1.getText().toString());
            int hearts2 = txtHearts2.getSelectedItemPosition();
            appDataTP.setHearts((hearts1 * 4) + hearts2);
        } catch (NumberFormatException e) {
            txtHearts1.requestFocus();
            throw e;
        }

        return appDataTP.array();
    }

    public byte[] onAppDataSBUSaved() {
        try {
            int level = Integer.parseInt(txtLevelSBU.getText().toString());
            Integer oldLevel;
            try {
                oldLevel = appDataSBU.getLevel();
            } catch (Exception e) {
                oldLevel = null;
            }

            //level is a granular value as such we don't want to overwrite it in case its halfway through a level
            if (null == oldLevel  || level != oldLevel) {
                appDataSBU.setLevel(level);
            }
        } catch (NumberFormatException e) {
            txtLevelSBU.requestFocus();
            throw e;
        }

        return appDataSBU.array();
    }

    public byte[] onAppDataSSBSaved() {
        try {
            int appearance = spnAppearance.getSelectedItemPosition();
            appDataSSB.setAppearence(appearance);
        } catch (NumberFormatException e) {
            spnAppearance.requestFocus();
            throw e;
        }
        try {
            int level = Integer.parseInt(txtLevelSSB.getText().toString());
            Integer oldLevel;
            try {
                oldLevel = appDataSSB.getLevel();
            } catch (Exception e) {
                oldLevel = null;
            }

            //level is a granular value as such we don't want to overwrite it in case its halfway through a level
            if (null == oldLevel  || level != oldLevel) {
                appDataSSB.setLevel(level);
            }
        } catch (NumberFormatException e) {
            txtLevelSSB.requestFocus();
            throw e;
        }

        try {
            int specialNeutral = spnSpecialNeutral.getSelectedItemPosition();
            appDataSSB.setSpecialNeutral(specialNeutral);
        } catch (NumberFormatException e) {
            spnSpecialNeutral.requestFocus();
            throw e;
        }
        try {
            int specialSide = spnSpecialSide.getSelectedItemPosition();
            appDataSSB.setSpecialSide(specialSide);
        } catch (NumberFormatException e) {
            spnSpecialSide.requestFocus();
            throw e;
        }
        try {
            int specialUp = spnSpecialUp.getSelectedItemPosition();
            appDataSSB.setSpecialUp(specialUp);
        } catch (NumberFormatException e) {
            spnSpecialUp.requestFocus();
            throw e;
        }
        try {
            int specialDown = spnSpecialDown.getSelectedItemPosition();
            appDataSSB.setSpecialDown(specialDown);
        } catch (NumberFormatException e) {
            spnSpecialDown.requestFocus();
            throw e;
        }

        try {
            int statAttack = Integer.parseInt(txtStatAttack.getText().toString());
            appDataSSB.setStatAttack(statAttack);
        } catch (NumberFormatException e) {
            txtStatAttack.requestFocus();
            throw e;
        }
        try {
            int statDefense = Integer.parseInt(txtStatDefense.getText().toString());
            appDataSSB.setStatDefense(statDefense);
        } catch (NumberFormatException e) {
            txtStatDefense.requestFocus();
            throw e;
        }
        try {
            int statSpeed = Integer.parseInt(txtStatSpeed.getText().toString());
            appDataSSB.setStatSpeed(statSpeed);
        } catch (NumberFormatException e) {
            txtStatSpeed.requestFocus();
            throw e;
        }

        try {
            int bonusEffect1 = getEffectValue(spnEffect1);
            appDataSSB.setBonusEffect1(bonusEffect1);
        } catch (NumberFormatException e) {
            spnEffect1.requestFocus();
            throw e;
        }
        try {
            int bonusEffect2 = getEffectValue(spnEffect2);
            appDataSSB.setBonusEffect2(bonusEffect2);
        } catch (NumberFormatException e) {
            spnEffect2.requestFocus();
            throw e;
        }
        try {
            int bonusEffect3 = getEffectValue(spnEffect3);
            appDataSSB.setBonusEffect3(bonusEffect3);
        } catch (NumberFormatException e) {
            spnEffect3.requestFocus();
            throw e;
        }

        return appDataSSB.array();
    }

    private void showErrorDialog(int msgRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_caps)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, (dialog, which) -> finish())
                .show();
        setResult(Activity.RESULT_OK, new Intent(NFCIntent.ACTION_FIX_BANK_DATA));
    }
}
