package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.hiddenramblings.tagmo.adapter.NoSelectionSpinnerAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.amiibo.data.AmiiboData;
import com.hiddenramblings.tagmo.amiibo.data.AppData;
import com.hiddenramblings.tagmo.amiibo.data.AppDataFragment;
import com.hiddenramblings.tagmo.amiibo.data.AppDataSSBFragment;
import com.hiddenramblings.tagmo.amiibo.data.AppDataTPFragment;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.vicmikhailau.maskededittext.MaskedEditText;

import org.androidannotations.annotations.AfterTextChange;
import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Background;
import org.androidannotations.annotations.CheckedChange;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;
import org.androidannotations.api.BackgroundExecutor;
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

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_tag_data)
@OptionsMenu(R.menu.image_menu)
public class TagDataActivity extends AppCompatActivity {

    @ViewById(R.id.txtError)
    TextView txtError;
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
    @ViewById(R.id.imageAmiibo)
    ImageView imageAmiibo;

    @ViewById(R.id.txtUID)
    MaskedEditText txtUID;
    @ViewById(R.id.txtCountryCode)
    Spinner txtCountryCode;
    @ViewById(R.id.txtInitDate)
    EditText txtInitDate;
    @ViewById(R.id.txtModifiedDate)
    EditText txtModifiedDate;
    @ViewById(R.id.txtNickname)
    EditText txtNickname;
    @ViewById(R.id.txtMiiName)
    EditText txtMiiName;
    @ViewById(R.id.txtAppId)
    MaskedEditText txtAppId;
    @ViewById(R.id.txtWriteCounter)
    EditText txtWriteCounter;
    @ViewById(R.id.txtSerialNumber)
    EditText txtSerialNumber;
    @ViewById(R.id.txtAppName)
    Spinner txtAppName;
    @ViewById(R.id.appDataSwitch)
    SwitchCompat appDataSwitch;
    @ViewById(R.id.userDataSwitch)
    SwitchCompat userDataSwitch;
    @ViewById(R.id.random_serial)
    AppCompatButton generateSerial;

    @Extra(TagMo.EXTRA_TAG_DATA)
    byte[] tagData;

    CountryCodesAdapter countryCodeAdapter;
    NoSelectionSpinnerAdapter appIdAdapter;
    boolean ignoreAppNameSelected;
    KeyManager keyManager;
    AmiiboManager amiiboManager = null;
    AmiiboData amiiboData;

    @InstanceState
    boolean initialUserDataInitialized;
    @InstanceState
    public boolean isAppDataInitialized;
    @InstanceState
    boolean initialAppDataInitialized;
    @InstanceState
    boolean isUserDataInitialized;
    @InstanceState
    Date initializedDate;
    @InstanceState
    Date modifiedDate;
    @InstanceState
    Integer appId;

    @AfterViews
    void afterViews() {
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
                Debug.Log(e);
                showErrorDialog(R.string.fail_display);
                return;
            }
        }

        loadAmiiboManager();
        updateAmiiboView();

        txtAppName.setOnItemSelectedListener(onAppNameSelected);
        txtAppId.addTextChangedListener(onAppIdChange);

        countryCodeAdapter = new CountryCodesAdapter(AmiiboData.countryCodes);
        txtCountryCode.setAdapter(countryCodeAdapter);

        appIdAdapter = new NoSelectionSpinnerAdapter(
                new AppIdAdapter(AppData.appIds), R.layout.nothing_spinner_text);
        txtAppName.setAdapter(appIdAdapter);

        userDataSwitch.setOnCheckedChangeListener((compoundButton, b) -> onUserDataSwitchClicked(b));
        appDataSwitch.setOnCheckedChangeListener((compoundButton, b) -> onAppDataSwitchClicked(b));

        loadData();
    }

    CustomTarget<Bitmap> amiiboImageTarget = new CustomTarget<Bitmap>() {
        @Override
        public void onLoadStarted(@Nullable Drawable placeholder) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            imageAmiibo.setVisibility(View.GONE);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {

        }

        @Override
        public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
            imageAmiibo.setImageBitmap(resource);
            imageAmiibo.setVisibility(View.VISIBLE);
        }
    };

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
            new Toasty(this).Short(getString(R.string.amiibo_info_parse_error));
        }

        if (Thread.currentThread().isInterrupted())
            return;
        setAmiiboManager(amiiboManager);
    }

    @UiThread
    void setAmiiboManager(AmiiboManager amiiboManager) {
        this.amiiboManager = amiiboManager;
        this.updateAmiiboView();
    }

    public void updateAmiiboView() {
        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        // String character = "";
        final String amiiboImageUrl;

        if (null == this.tagData) {
            tagInfo = getString(R.string.no_tag_loaded);
            amiiboImageUrl = null;
        } else {
            long amiiboId;
            try {
                amiiboId = TagUtils.amiiboIdFromTag(tagData);
            } catch (Exception e) {
                Debug.Log(e);
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
                    // if (null != amiibo.getCharacter())
                    //     character = amiibo.getCharacter().name;
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
            Glide.with(this).clear(amiiboImageTarget);
            if (null != amiiboImageUrl) {
                Glide.with(this)
                        .setDefaultRequestOptions(onlyRetrieveFromCache())
                        .asBitmap()
                        .load(amiiboImageUrl)
                        .into(amiiboImageTarget);
            }
        }
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

    private RequestOptions onlyRetrieveFromCache() {
        String imageNetworkSetting = TagMo.getPrefs().imageNetworkSetting().get();
        if (SettingsFragment.IMAGE_NETWORK_NEVER.equals(imageNetworkSetting)) {
            return new RequestOptions().onlyRetrieveFromCache(true);
        } else if (SettingsFragment.IMAGE_NETWORK_WIFI.equals(imageNetworkSetting)) {
            ConnectivityManager cm = (ConnectivityManager)
                    TagMo.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return new RequestOptions().onlyRetrieveFromCache(null == activeNetwork
                    || activeNetwork.getType() != ConnectivityManager.TYPE_WIFI);
        } else {
            return new RequestOptions().onlyRetrieveFromCache(false);
        }
    }

    void loadData() {
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

    AppDataFragment getAppDataFragment() {
        AppDataFragment fragment = (AppDataFragment) getSupportFragmentManager().findFragmentById(R.id.appData);
        if (null != fragment && fragment.isDetached()) {
            fragment = null;
        }

        return fragment;
    }

    @OptionsItem(R.id.mnu_save)
    void onSaveClicked() {
        AmiiboData newAmiiboData;
        try {
            newAmiiboData = new AmiiboData(this.amiiboData.array());

            newAmiiboData.setUserDataInitialized(isUserDataInitialized);
            newAmiiboData.setAppDataInitialized(isUserDataInitialized && isAppDataInitialized);
        } catch (Exception e) {
            Debug.Log(e);
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

            AppDataFragment fragment = getAppDataFragment();
            if (isAppDataInitialized && null != fragment) {
                try {
                    newAmiiboData.setAppData(fragment.onAppDataSaved());
                } catch (Exception e) {
                    return;
                }
            }
        }

        byte[] tagData;
        try {
            tagData = keyManager.encrypt(newAmiiboData.array());
        } catch (Exception e) {
            Debug.Log(e);
            showErrorDialog(R.string.fail_encrypt);
            return;
        }

        Intent intent = new Intent(TagMo.ACTION_EDIT_COMPLETE);
        intent.putExtra(TagMo.EXTRA_TAG_DATA, tagData);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @CheckedChange(R.id.userDataSwitch)
    void onUserDataSwitchClicked(boolean isUserDataInitialized) {
        this.isUserDataInitialized = isUserDataInitialized;
        updateUserDataEnabled(isUserDataInitialized);
    }

    void updateUserDataEnabled(boolean isUserDataInitialized) {
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

    @CheckedChange(R.id.appDataSwitch)
    void onAppDataSwitchClicked(boolean isAppDataInitialized) {
        this.isAppDataInitialized = isAppDataInitialized;
        updateAppDataEnabled(isAppDataInitialized);
    }

    void updateAppDataEnabled(boolean isAppDataInitialized) {
        AppDataFragment fragment = getAppDataFragment();
        if (null != fragment) {
            fragment.onAppDataChecked(isUserDataInitialized && isAppDataInitialized);
        }
    }

    void loadUID() {
        txtUID.setText(TagUtils.bytesToHex(amiiboData.getUID()));
    }

    void loadCountryCode() {
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

    void loadInitializedDate() {
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

    void updateInitializedDateView(Date date) {
        String text;
        try {
            text = getDateString(date);
        } catch (IllegalArgumentException e) {
            text = getString(R.string.invalid);
        }
        txtInitDate.setText(text);
    }

    @Click(R.id.random_serial)
    void onGenerateSerialClick() {
        txtSerialNumber.setText(TagUtils.bytesToHex(new Foomiibo().generateRandomUID()));
    }

    @Click(R.id.txtInitDate)
    void onInitDateClick() {
        final Calendar c = Calendar.getInstance();
        c.setTime(initializedDate);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                onInitDateSet,
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    DatePickerDialog.OnDateSetListener onInitDateSet = new DatePickerDialog.OnDateSetListener() {
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

    void loadModifiedDate() {
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

    void updateModifiedDateView(Date date) {
        String text;
        try {
            text = getDateString(date);
        } catch (IllegalArgumentException e) {
            text = getString(R.string.invalid);
        }
        txtModifiedDate.setText(text);
    }

    @Click(R.id.txtModifiedDate)
    void onModifiedDateClick() {
        final Calendar c = Calendar.getInstance();
        c.setTime(modifiedDate);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                onModifiedDateSet,
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    DatePickerDialog.OnDateSetListener onModifiedDateSet = new DatePickerDialog.OnDateSetListener() {
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

    void loadNickname() {
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

    void loadMiiName() {
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

    void loadAppId() {
        if (initialUserDataInitialized) {
            appId = amiiboData.getAppId();
        } else {
            appId = 0;
        }

        updateAppIdView();
        updateAppNameView();
        updateAppDataView();
    }

    void updateAppIdView() {
        if (null != appId) {
            txtAppId.setText(String.format("%08X", appId));
        } else {
            txtAppId.setText("");
        }
    }

    int parseAppId() throws Exception {
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

    TextWatcher onAppIdChange = new TextWatcher() {
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

    void updateAppNameView() {
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

    AdapterView.OnItemSelectedListener onAppNameSelected = new AdapterView.OnItemSelectedListener() {
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

    void updateAppDataView() {
        FragmentManager fm = getSupportFragmentManager();

        AppDataFragment fragment;
        if (null != appId) {
            String tag = "app_data:" + appId;
            fragment = (AppDataFragment) fm.findFragmentByTag(tag);
            if (null == fragment) {
                boolean initialAppDataInitialized = this.initialAppDataInitialized && amiiboData.getAppId() == appId;
                if (appId == AppDataTPFragment.APP_ID) {
                    fragment = AppDataTPFragment.newInstance(amiiboData.getAppData(), initialAppDataInitialized);
                } else if (appId == AppDataSSBFragment.APP_ID) {
                    fragment = AppDataSSBFragment.newInstance(amiiboData.getAppData(), initialAppDataInitialized);
                }
            }
            if (null != fragment) {
                fm.beginTransaction()
                        .replace(R.id.appData, fragment, tag)
                        .attach(fragment)
                        .commit();
                return;
            }
        }

        fragment = (AppDataFragment) fm.findFragmentById(R.id.appData);
        if (null != fragment) {
            fm.beginTransaction()
                    .detach(fragment)
                    .commit();
        }
    }

    void loadWriteCounter() {
        int writeCounter;
        if (initialUserDataInitialized) {
            writeCounter = amiiboData.getWriteCount();
        } else {
            writeCounter = 0;
        }
        txtWriteCounter.setText(String.valueOf(writeCounter));
    }

    void loadSerialNumber() {
        txtSerialNumber.setTag(txtSerialNumber.getKeyListener());
        txtSerialNumber.setKeyListener(null);
        byte[] value = amiiboData.getUID();
        txtSerialNumber.setText(TagUtils.bytesToHex(value));
    }

    @AfterTextChange(R.id.txtWriteCounter)
    void onWriteCounterTextChange() {
        try {
            int writeCounter = Integer.parseInt(txtWriteCounter.getText().toString());
            amiiboData.checkWriteCount(writeCounter);
            txtWriteCounter.setError(null);
        } catch (Exception e) {
            txtWriteCounter.setError(getString(R.string.error_min_max, AmiiboData.WRITE_COUNT_MIN_VALUE, AmiiboData.WRITE_COUNT_MAX_VALUE));
        }
    }

    static String getDateString(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date);
    }

    static class CountryCodesAdapter extends BaseAdapter implements Filterable {
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

    static class AppIdAdapter extends BaseAdapter {
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

    @UiThread
    void showErrorDialog(int msgRes) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.error_caps)
                .setMessage(msgRes)
                .setPositiveButton(R.string.close, (dialog, which) -> finish())
                .show();
        setResult(Activity.RESULT_OK, new Intent(TagMo.ACTION_FIX_BANK_DATA));
    }
}
