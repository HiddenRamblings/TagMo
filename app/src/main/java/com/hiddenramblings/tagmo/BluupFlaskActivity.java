package com.hiddenramblings.tagmo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
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
import android.nfc.TagLostException;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
@SuppressLint("MissingPermission")
public class BluupFlaskActivity extends AppCompatActivity {

    private ViewGroup fragmentView;
    private CardView amiiboCard;
    private LinearLayout flaskDetails;
    private ProgressBar progressBar;
    private Snackbar statusBar;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothAdapter.LeScanCallback scanCallback;
    private BluetoothLeService flaskService;
    private String flaskProfile;
    private String flaskAddress;

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
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d("FlaskService", "onServiceConnected");
            BluetoothLeService.LocalBinder localBinder = (BluetoothLeService.LocalBinder) binder;
            flaskService = localBinder.getService();
            if (flaskService.initialize()) {
                if (flaskService.connect(flaskAddress)) {
                    flaskService.setListener(new BluetoothLeService.BluetoothGattListener() {
                        boolean isServiceDiscovered = false;
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            runOnUiThread(() -> {
                                setTitle(flaskProfile);
                                invalidateOptionsMenu();
                            });
                            try {
                                flaskService.setFlaskCharacteristicRX();
                                dismissConnectionNotice();
                                String command = "\\x15\\x10tag.getList()\\n";
                                flaskService.writeDelayedCharacteristic(
                                        command.getBytes(CharsetCompat.UTF_8)
                                );
//                                flaskService.writeDelayedCharacteristic(
//                                        "\\x15\\x10tag.getList()\\n"
//                                );
                            } catch (TagLostException tle) {
                                stopFlaskService();
                                new Toasty(BluupFlaskActivity.this).Short(R.string.flask_invalid);
                            }
                        }

                        @Override
                        public void onServicesDisconnect() {
                            if (!isServiceDiscovered) {
                                stopFlaskService();
                                new Toasty(BluupFlaskActivity.this).Short(R.string.flask_missing);
                            }
                        }

                        @Override
                        public void onFlaskActiveChanged(JSONObject jsonObject) {
                            try {
                                getAmiiboByName(jsonObject.getString("name"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFlaskActiveDeleted(JSONObject jsonObject) {
                            amiiboCard.setVisibility(View.INVISIBLE);
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
            Log.d("FlaskService", "onServiceDisconnected");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_bluup_flask);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        setTitle(R.string.bluup_flask_ble);

        amiiboCard = findViewById(R.id.active_tile_layout);
        amiiboCard.setVisibility(View.INVISIBLE);
        flaskDetails = findViewById(R.id.flask_details);
        progressBar = findViewById(R.id.scanner_progress);

        verifyPermissions();
    }

    void setFoomiiboInfoText(TextView textView, CharSequence text) {
            textView.setVisibility(View.VISIBLE);
            if (text.length() == 0) {
                textView.setText(R.string.unknown);
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setEnabled(true);
            }
    }

    private void getAmiiboByName(String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(this).Short(R.string.amiibo_info_parse_error);
            }

            if (Thread.currentThread().isInterrupted()) return;

            Amiibo selectedAmiibo = null;
            if (null != amiiboManager) {
                String amiiboName = name.split("\\|")[0];
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
            final Amiibo currentAmiibo = selectedAmiibo;
            runOnUiThread(() -> {
                TextView txtName = amiiboCard.findViewById(R.id.txtName);
                TextView txtTagId = amiiboCard.findViewById(R.id.txtTagId);
                TextView txtAmiiboSeries = amiiboCard.findViewById(R.id.txtAmiiboSeries);
                TextView txtAmiiboType = amiiboCard.findViewById(R.id.txtAmiiboType);
                TextView txtGameSeries = amiiboCard.findViewById(R.id.txtGameSeries);
                AppCompatImageView imageAmiibo = amiiboCard.findViewById(R.id.imageAmiibo);

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

                String amiiboHexId;
                String amiiboName = "";
                String amiiboSeries = "";
                String amiiboType = "";
                String gameSeries = "";
                // String character = "";
                String amiiboImageUrl;

                if (null != currentAmiibo) {
                    amiiboCard.setVisibility(View.VISIBLE);
                    amiiboHexId = TagUtils.amiiboIdToHex(currentAmiibo.id);
                    amiiboName = currentAmiibo.name;
                    amiiboImageUrl = currentAmiibo.getImageUrl();
                    if (null != currentAmiibo.getAmiiboSeries())
                        amiiboSeries = currentAmiibo.getAmiiboSeries().name;
                    if (null != currentAmiibo.getAmiiboType())
                        amiiboType = currentAmiibo.getAmiiboType().name;
                    if (null != currentAmiibo.getGameSeries())
                        gameSeries = currentAmiibo.getGameSeries().name;

                    setFoomiiboInfoText(txtName, amiiboName);
                    setFoomiiboInfoText(txtTagId, amiiboHexId);
                    setFoomiiboInfoText(txtAmiiboSeries, amiiboSeries);
                    setFoomiiboInfoText(txtAmiiboType, amiiboType);
                    setFoomiiboInfoText(txtGameSeries, gameSeries);

                    if (null != imageAmiibo) {
                        GlideApp.with(amiiboCard).clear(target);
                        if (null != amiiboImageUrl) {
                            GlideApp.with(amiiboCard).asBitmap().load(amiiboImageUrl).into(target);
                        }
                    }
                    if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000")) {
                        txtTagId.setEnabled(false);
                    }
                }
            });
        });
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
        progressBar.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            ParcelUuid FlaskUUID = new ParcelUuid(BluetoothLeService.FlaskNUS);
            ScanFilter filter = new ScanFilter.Builder().setServiceUuid(FlaskUUID).build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            ScanCallback callback = new ScanCallback() {
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
            scanner.startScan(Collections.singletonList(filter), settings, callback);
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

    private void showConnectionNotice() {
        statusBar = new IconifiedSnackbar(this).buildSnackbar(
                R.string.flask_located, R.drawable.ic_bluup_flask_24dp, Snackbar.LENGTH_INDEFINITE
        );
        statusBar.show();
    }

    private void dismissConnectionNotice() {
        if (null != statusBar && statusBar.isShown()) statusBar.dismiss();
    }

    public void startFlaskService() {
        Intent service = new Intent(this, BluetoothLeService.class);
        startService(service);
        bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void stopFlaskService() {
        dismissConnectionNotice();
        if (isServiceRunning()) {
            flaskService.disconnect();
            flaskService.close();
            unbindService(mServerConn);
            stopService(new Intent(this, BluetoothLeService.class));
        }
    }

    private void dismissFlaskDiscovery() {
        if (null != mBluetoothAdapter) {
            if (null != scanCallback)
                mBluetoothAdapter.stopLeScan(scanCallback);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager)
                getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (BluetoothLeService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissFlaskDiscovery();
        stopFlaskService();
    }
}
