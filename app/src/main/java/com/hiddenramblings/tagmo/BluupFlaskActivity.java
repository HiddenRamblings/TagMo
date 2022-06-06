package com.hiddenramblings.tagmo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.TagLostException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.adapter.BluupFlaskAdapter;
import com.hiddenramblings.tagmo.adapter.WriteBanksAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.FlaskAmiibo;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.shawnlin.numberpicker.NumberPicker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class BluupFlaskActivity extends AppCompatActivity implements
        BluupFlaskAdapter.OnAmiiboClickListener {

    private CardView amiiboTile;
    private CardView amiiboCard;
    private Toolbar toolbar;
    private RecyclerView flaskDetails;
    private TextView flaskStats;
    private NumberPicker writeCount;
    private AppCompatButton writeSlots;
    private RelativeLayout writeSlotsLayout;
    private RecyclerView amiiboFilesView;
    private Snackbar statusBar;
    private Dialog uploadDialog;

    private KeyManager keyManager;
    private BrowserSettings settings;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private WriteBanksAdapter writeFileAdapter;
    private WriteBanksAdapter writeListAdapter;

    private BluetoothAdapter mBluetoothAdapter;
    private ScanCallback scanCallbackLP;
    private BluetoothAdapter.LeScanCallback scanCallback;
    private BluetoothLeService flaskService;
    private String flaskProfile;
    private String flaskAddress;

    private int currentCount;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    ActivityResultLauncher<String[]> onRequestLocationQ = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isLocationAvailable = false;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (entry.getKey().equals(Manifest.permission.ACCESS_FINE_LOCATION)
                    && entry.getValue()) isLocationAvailable = true;
        }
        if (isLocationAvailable) {
            activateBluetooth();
        } else {
            new Toasty(this).Long(R.string.flask_permissions);
            finish();
        }
    });

    @RequiresApi(api = Build.VERSION_CODES.Q)
    ActivityResultLauncher<String> onRequestBackgroundQ = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), permission -> {});

    ActivityResultLauncher<String[]> onRequestBluetoothS = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isBluetoothAvailable = false;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (entry.getValue()) isBluetoothAvailable = true;
        }
        if (isBluetoothAvailable) {
            mBluetoothAdapter = getBluetoothAdapter();
            if (null != mBluetoothAdapter) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                }
                selectBluetoothDevice();
            } else {
                new Toasty(this).Long(R.string.flask_bluetooth);
                finish();
            }
        } else {
            new Toasty(this).Long(R.string.flask_bluetooth);
            finish();
        }
    });
    ActivityResultLauncher<Intent> onRequestBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        mBluetoothAdapter = getBluetoothAdapter();
        if (null != mBluetoothAdapter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
            selectBluetoothDevice();
        } else {
            new Toasty(this).Long(R.string.flask_bluetooth);
           finish();
        }
    });
    ActivityResultLauncher<String[]> onRequestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> { boolean isLocationAvailable = false;
        for (Map.Entry<String,Boolean> entry : permissions.entrySet()) {
            if (entry.getValue()) isLocationAvailable = true;
        }
        if (isLocationAvailable) {
            mBluetoothAdapter = getBluetoothAdapter();
            if (null != mBluetoothAdapter)
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            new Toasty(this).Long(R.string.flask_permissions);
            finish();
        }
    });
    protected ServiceConnection mServerConn = new ServiceConnection() {
        boolean isServiceDiscovered = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            BluetoothLeService.LocalBinder localBinder = (BluetoothLeService.LocalBinder) binder;
            flaskService = localBinder.getService();
            if (flaskService.initialize()) {
                if (flaskService.connect(flaskAddress)) {
                    flaskService.setListener(new BluetoothLeService.BluetoothGattListener() {
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            runOnUiThread(() -> ((TextView) findViewById(
                                    R.id.hardware_info)).setText(flaskProfile));
                            try {
                                flaskService.setFlaskCharacteristicRX();
                                flaskService.getDeviceAmiibo();
                            } catch (TagLostException tle) {
                                stopFlaskService();
                                new Toasty(BluupFlaskActivity.this).Short(R.string.flask_invalid);
                            }
                        }

                        @Override
                        public void onFlaskActiveChanged(JSONObject jsonObject) {
                            try {
                                String[] hash =  jsonObject.getString("name").split("\\|");
                                getActiveAmiibo(getAmiiboByName(hash[0]), amiiboTile);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFlaskActiveDeleted(JSONObject jsonObject) {
                            amiiboTile.setVisibility(View.INVISIBLE);
                            flaskService.getDeviceAmiibo();
                        }

                        @Override
                        public void onFlaskListRetrieved(JSONArray jsonArray) {
                            currentCount = jsonArray.length();
                            ArrayList<FlaskAmiibo> flaskAmiibos = new ArrayList<>();
                            for (int i = 0; i < currentCount; i++) {
                                try {
                                    String[] hash = jsonArray.getString(i).split("\\|");
                                    FlaskAmiibo flaskAmiibo = new FlaskAmiibo();
                                    flaskAmiibo.setAmiibo(getAmiiboByName(hash[0]));
                                    flaskAmiibo.setFlaskID(hash[1]);
                                    flaskAmiibos.add(flaskAmiibo);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            BluupFlaskAdapter adapter = new BluupFlaskAdapter(
                                    settings, BluupFlaskActivity.this);
                            adapter.setFlaskAmiibos(flaskAmiibos);
                            runOnUiThread(() -> {
                                dismissSnackbarNotice();
                                flaskDetails.setAdapter(adapter);
                            });
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                flaskService.getActiveAmiibo();
                            }, 50L);
                        }

                        @Override
                        public void onFlaskActiveLocated(JSONObject jsonObject) {
                            try {
                                String[] hash = jsonObject.getString("name").split("\\|");
                                Amiibo amiibo = getAmiiboByName(hash[0]);
                                getActiveAmiibo(amiibo, amiiboTile);
                                if (bottomSheetBehavior.getState() ==
                                        BottomSheetBehavior.STATE_COLLAPSED)
                                    getActiveAmiibo(amiibo, amiiboCard);
                                String index = jsonObject.getString("index");
                                runOnUiThread(() -> {
                                    flaskStats.setText(getString(
                                            R.string.flask_count, index, currentCount));
                                    int maxSlots = 85 - currentCount;
                                    writeCount.setMaxValue(maxSlots);
                                    writeCount.setValue(maxSlots);
                                    writeSlots.setText(getString(R.string.write_slots, maxSlots));
                                });
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFlaskFilesUploaded() {
                            runOnUiThread(() -> {
                                if (null != uploadDialog && uploadDialog.isShowing())
                                    uploadDialog.dismiss();
                            });
                        }

                        @Override
                        public void onGattConnectionLost() {
                            runOnUiThread(BluupFlaskActivity.this::showDisconnectNotice);
                            stopFlaskService();
                        }
                    });
                } else {
                    stopFlaskService();
                    new Toasty(BluupFlaskActivity.this).Short(R.string.flask_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unbindService(mServerConn);
            stopService(new Intent(BluupFlaskActivity.this, BluetoothLeService.class));
            if (!isServiceDiscovered) {
                showPurchaseNotice();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_bluup_flask);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        keyManager = new KeyManager(this);

        amiiboTile = findViewById(R.id.active_tile_layout);
        amiiboTile.setVisibility(View.INVISIBLE);

        amiiboCard = findViewById(R.id.active_card_layout);
        amiiboCard.findViewById(R.id.txtError).setVisibility(View.GONE);
        amiiboCard.findViewById(R.id.txtPath).setVisibility(View.GONE);
        toolbar = findViewById(R.id.toolbar);

        flaskDetails = findViewById(R.id.flask_details);
        flaskDetails.setHasFixedSize(true);
        flaskDetails.setLayoutManager(new LinearLayoutManager(this));

        flaskStats = findViewById(R.id.flask_stats);
        AppCompatButton writeFile = findViewById(R.id.write_slot_file);
        writeCount = findViewById(R.id.bank_number_picker);
        writeCount.setMaxValue(85);
        writeSlots = findViewById(R.id.write_slot_count);

        writeSlotsLayout = findViewById(R.id.write_banks_layout);
        amiiboFilesView = findViewById(R.id.amiibo_files_list);

        settings = new BrowserSettings().initialize();

        AppCompatImageView toggle = findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            if (writeSlotsLayout.getVisibility() == View.VISIBLE)
                                onBottomSheetChanged(true);
                            toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                        } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                            toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                        ViewGroup mainLayout = findViewById(R.id.flask_details);
                        if (mainLayout.getBottom() >= bottomSheet.getTop()) {
                            int bottomHeight = bottomSheet.getMeasuredHeight()
                                    - bottomSheetBehavior.getPeekHeight();
                            mainLayout.setPadding(0, 0, 0, slideOffset > 0
                                    ? (int) (bottomHeight * slideOffset) : 0);
                        }
                    }
                });

        toggle.setOnClickListener(view -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        toolbar.inflateMenu(R.menu.flask_menu);

        this.loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());

        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiiboFilesView.setLayoutManager(new GridLayoutManager(this, getColumnCount()));
        else
            amiiboFilesView.setLayoutManager(new LinearLayoutManager(this));

        writeFileAdapter = new WriteBanksAdapter(settings,
                new WriteBanksAdapter.OnAmiiboClickListener() {
            @Override
            public void onAmiiboClicked(AmiiboFile amiiboFile) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                showUploadingNotice();
                uploadAmiiboFile(amiiboFile);
            }

            @Override
            public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                showUploadingNotice();
                uploadAmiiboFile(amiiboFile);
            }
        });
        this.settings.addChangeListener(writeFileAdapter);

        writeListAdapter = new WriteBanksAdapter(settings, this::writeAmiiboCollection);
        this.settings.addChangeListener(writeListAdapter);

        SearchView searchView = findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        searchView.setIconifiedByDefault(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                settings.setQuery(query);
                settings.notifyChanges();
                return true;
            }
        });

        writeFile.setOnClickListener(view -> {
            onBottomSheetChanged(false);
            amiiboFilesView.setAdapter(writeFileAdapter);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        writeCount.setOnValueChangedListener((numberPicker, valueOld, valueNew) -> {
            writeSlots.setText(getString(R.string.write_slots, valueNew));
        });

        writeSlots.setOnClickListener(view -> {
            onBottomSheetChanged(false);
            amiiboFilesView.setAdapter(writeListAdapter);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        verifyPermissions();
    }

    private void onBottomSheetChanged(boolean hasAmiibo) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        amiiboCard.setVisibility(hasAmiibo ? View.VISIBLE : View.GONE);
        writeCount.setVisibility(hasAmiibo ? View.VISIBLE : View.GONE);
        writeSlots.setVisibility(hasAmiibo ? View.VISIBLE : View.GONE);
        writeSlotsLayout.setVisibility(hasAmiibo ? View.GONE : View.VISIBLE);
    }

    void setAmiiboInfoText(TextView textView, CharSequence text) {
            textView.setVisibility(View.VISIBLE);
            if (text.length() == 0) {
                textView.setText(R.string.unknown);
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setEnabled(true);
            }
    }

    private void getActiveAmiibo(Amiibo active, View amiiboView) {
        TextView txtName = amiiboView.findViewById(R.id.txtName);
        TextView txtTagId = amiiboView.findViewById(R.id.txtTagId);
        TextView txtAmiiboSeries = amiiboView.findViewById(R.id.txtAmiiboSeries);
        TextView txtAmiiboType = amiiboView.findViewById(R.id.txtAmiiboType);
        TextView txtGameSeries = amiiboView.findViewById(R.id.txtGameSeries);
        AppCompatImageView imageAmiibo = amiiboView.findViewById(R.id.imageAmiibo);

        CustomTarget<Bitmap> target = new CustomTarget<>() {
            @Override
            public void onLoadStarted(@Nullable Drawable placeholder) {
                imageAmiibo.setImageResource(0);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                imageAmiibo.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                imageAmiibo.setVisibility(View.VISIBLE);
            }

            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                imageAmiibo.setImageBitmap(resource);
                imageAmiibo.setVisibility(View.VISIBLE);
            }
        };

        runOnUiThread(() -> {
            String amiiboHexId;
            String amiiboName = "";
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            String amiiboImageUrl;

            if (null != active) {
                amiiboView.setVisibility(View.VISIBLE);
                amiiboHexId = TagUtils.amiiboIdToHex(active.id);
                amiiboName = active.name;
                amiiboImageUrl = active.getImageUrl();
                if (null != active.getAmiiboSeries())
                    amiiboSeries = active.getAmiiboSeries().name;
                if (null != active.getAmiiboType())
                    amiiboType = active.getAmiiboType().name;
                if (null != active.getGameSeries())
                    gameSeries = active.getGameSeries().name;

                setAmiiboInfoText(txtName, amiiboName);
                setAmiiboInfoText(txtTagId, amiiboHexId);
                setAmiiboInfoText(txtAmiiboSeries, amiiboSeries);
                setAmiiboInfoText(txtAmiiboType, amiiboType);
                setAmiiboInfoText(txtGameSeries, gameSeries);

                if (null != imageAmiibo) {
                    GlideApp.with(this).clear(target);
                    if (null != amiiboImageUrl) {
                        GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(target);
                    }
                }
                if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000")) {
                    txtTagId.setEnabled(false);
                }
            }
        });
    }

    private Amiibo getAmiiboByName(String amiiboName) {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            if (Thread.currentThread().isInterrupted()) return null;

            Amiibo selectedAmiibo = null;
            if (null != amiiboManager) {
                for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                    if (amiibo.name.equals(amiiboName)) {
                        selectedAmiibo = amiibo;
                    }
                }
                if (null == selectedAmiibo) {
                    for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                        if (amiibo.name.startsWith(amiiboName)) {
                            selectedAmiibo = amiibo;
                        }
                    }
                }
            }
            return selectedAmiibo;

    }

    private void verifyPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                activateBluetooth();
            } else {
                final String[] PERMISSIONS_LOCATION = {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                };
                onRequestLocationQ.launch(PERMISSIONS_LOCATION);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String[] PERMISSIONS_LOCATION = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
            onRequestLocation.launch(PERMISSIONS_LOCATION);
        } else {
            mBluetoothAdapter = getBluetoothAdapter();
            if (null != mBluetoothAdapter)
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private void activateBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final String[] PERMISSIONS_BLUETOOTH = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            };
            onRequestBluetoothS.launch(PERMISSIONS_BLUETOOTH);
        } else {
            mBluetoothAdapter = getBluetoothAdapter();
            if (null != mBluetoothAdapter) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    onRequestBackgroundQ.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                }
                selectBluetoothDevice();
            } else {
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        }
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothAdapter mBluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBluetoothAdapter = ((BluetoothManager)
                    getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            if (null != mBluetoothAdapter) {
                if (!mBluetoothAdapter.isEnabled()) mBluetoothAdapter.enable();
                return mBluetoothAdapter;
            }
        } else {
            //noinspection deprecation
            return BluetoothAdapter.getDefaultAdapter();
        }
        return null;
    }

    private void scanBluetoothServices() {
        showScanningNotice();
        flaskProfile = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            ParcelUuid FlaskUUID = new ParcelUuid(BluetoothLeService.FlaskNUS);
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(FlaskUUID).build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanCallbackLP = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    flaskProfile = result.getDevice().getName();
                    flaskAddress = result.getDevice().getAddress();
                    dismissFlaskDiscovery();
                    showConnectionNotice();
                    startFlaskService();
                }
            };
            scanner.startScan(Collections.singletonList(filter), settings, scanCallbackLP);
        } else {
            scanCallback = (bluetoothDevice, i, bytes) -> {
                flaskProfile = bluetoothDevice.getName();
                flaskAddress = bluetoothDevice.getAddress();
                dismissFlaskDiscovery();
                showConnectionNotice();
                startFlaskService();
            };
            mBluetoothAdapter.startLeScan(new UUID[]{ BluetoothLeService.FlaskNUS }, scanCallback);
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (null == flaskProfile) {
                dismissFlaskDiscovery();
                showPurchaseNotice();
            }
        }, 20000);
    }

    ActivityResultLauncher<Intent> onRequestPairing = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> scanBluetoothServices());

    private void selectBluetoothDevice() {
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                new Toasty(this).Long(R.string.flask_paired);
                dismissFlaskDiscovery();
                try {
                    onRequestPairing.launch(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (ActivityNotFoundException anf) {
                    scanBluetoothServices();
                }
            }
        }
        scanBluetoothServices();
    }

    private boolean isDirectoryHidden(File rootFolder, File directory, boolean recursive) {
        return !rootFolder.getPath().equals(directory.getPath()) && (!recursive
                || (!rootFolder.getPath().startsWith(directory.getPath())
                && !directory.getPath().startsWith(rootFolder.getPath())));
    }

    private void loadAmiiboFiles(File rootFolder, boolean recursiveFiles) {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) {
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            final AmiiboManager uiAmiiboManager = amiiboManager;
            this.runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.notifyChanges();
            });
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            final ArrayList<AmiiboFile> amiiboFiles = AmiiboManager
                    .listAmiibos(keyManager, rootFolder, recursiveFiles);
            if (this.settings.isShowingDownloads()) {
                File download = Storage.getDownloadDir(null);
                if (isDirectoryHidden(rootFolder, download, recursiveFiles))
                    amiiboFiles.addAll(AmiiboManager
                            .listAmiibos(keyManager, download, true));
            } else {
                File foomiibo = Storage.getDownloadDir("TagMo", "Foomiibo");
                if (isDirectoryHidden(rootFolder, foomiibo, recursiveFiles))
                    amiiboFiles.addAll(AmiiboManager
                            .listAmiibos(keyManager, foomiibo, true));
            }

            if (Thread.currentThread().isInterrupted()) return;

            this.runOnUiThread(() -> {
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            });
        });
    }

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList) {
        if (null != amiiboList && amiiboList.size() == writeCount.getValue()) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            showUploadingNotice();
            for (AmiiboFile amiiboFile : amiiboList) {
                uploadAmiiboFile(amiiboFile);
            }
        }
    }

    private void uploadAmiiboFile(AmiiboFile amiiboFile) {
        if (null != amiiboFile) {
            Amiibo amiibo = null;
            if (null != settings.getAmiiboManager()) {
                try {
                    long amiiboId = TagUtils.amiiboIdFromTag(amiiboFile.getData());
                    amiibo = settings.getAmiiboManager().amiibos.get(amiiboId);
                    if (null == amiibo)
                        amiibo = new Amiibo(settings.getAmiiboManager(),
                                amiiboId, null, null);
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
            if (null != amiibo) {
                flaskService.uploadAmiiboFile(amiiboFile, amiibo);
            }
        }
    }

    private void dismissSnackbarNotice() {
        if (null != statusBar && statusBar.isShown()) statusBar.dismiss();
    }

    private void showScanningNotice() {
        dismissSnackbarNotice();
        statusBar = new IconifiedSnackbar(this).buildSnackbar(
                R.string.flask_scanning, R.drawable.ic_baseline_bluetooth_searching_24dp,
                Snackbar.LENGTH_INDEFINITE, findViewById(R.id.bottom_sheet)
        );
        statusBar.show();
    }

    private void showConnectionNotice() {
        dismissSnackbarNotice();
        statusBar = new IconifiedSnackbar(this).buildSnackbar(
                R.string.flask_located, R.drawable.ic_bluup_flask_24dp,
                Snackbar.LENGTH_INDEFINITE, findViewById(R.id.bottom_sheet)
        );
        statusBar.show();
    }

    private void showDisconnectNotice() {
        dismissSnackbarNotice();
        statusBar = new IconifiedSnackbar(this).buildSnackbar(
                R.string.flask_disconnect, R.drawable.ic_baseline_bluetooth_searching_24dp,
                Snackbar.LENGTH_INDEFINITE, findViewById(R.id.bottom_sheet)
        );
        statusBar.setAction(R.string.scan, v -> {
            selectBluetoothDevice();
        });
        statusBar.show();
    }

    private void showUploadingNotice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(R.layout.upload_dialog);
        uploadDialog = builder.create();
        uploadDialog.show();
    }

    private void showPurchaseNotice() {
        dismissSnackbarNotice();
        statusBar = new IconifiedSnackbar(this).buildSnackbar(
                R.string.flask_missing, R.drawable.ic_bluup_flask_24dp,
                Snackbar.LENGTH_INDEFINITE, findViewById(R.id.bottom_sheet)
        );
        statusBar.setAction(R.string.purchase, v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.bluuplabs.com/flask/"
            )));
        });
        statusBar.show();
    }

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    public void startFlaskService() {
        Intent service = new Intent(this, BluetoothLeService.class);
        startService(service);
        bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void stopFlaskService() {
        dismissSnackbarNotice();
        if (null != flaskService) flaskService.disconnect();
    }

    private void dismissFlaskDiscovery() {
        if (null != mBluetoothAdapter) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (null != scanCallbackLP)
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallbackLP);
            } else {
                if (null != scanCallback)
                    mBluetoothAdapter.stopLeScan(scanCallback);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissFlaskDiscovery();
        stopFlaskService();
    }

    @Override
    public void onAmiiboClicked(FlaskAmiibo flaskAmiibo) {
        if (null != flaskAmiibo) {
            getActiveAmiibo(flaskAmiibo.getAmiibo(), amiiboCard);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.mnu_activate) {
                    flaskService.setActiveAmiibo(
                            flaskAmiibo.getAmiibo().name, flaskAmiibo.getFlaskID()
                    );
                    return true;
                }
                return false;
            });
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onAmiiboImageClicked(FlaskAmiibo flaskAmiibo) {
        if (null != flaskAmiibo) {
            Bundle bundle = new Bundle();
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, flaskAmiibo.getAmiibo().id);

            Intent intent = new Intent(this, ImageActivity.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        if (BottomSheetBehavior.STATE_EXPANDED == bottomSheetBehavior.getState())
            onBottomSheetChanged(false);
        else
            super.onBackPressed();
    }
}
