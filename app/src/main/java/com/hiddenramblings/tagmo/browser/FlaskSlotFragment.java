package com.hiddenramblings.tagmo.browser;

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
import android.content.res.Resources;
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatToggleButton;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.ImageActivity;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.FlaskTag;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.browser.adapter.FlaskSlotAdapter;
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.browser.adapter.WriteTagAdapter;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.Preferences_;
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
public class FlaskSlotFragment extends Fragment implements
        FlaskSlotAdapter.OnAmiiboClickListener {

    private final Preferences_ prefs = TagMo.getPrefs();
    BrowserActivity browser;

    private CoordinatorLayout rootLayout;
    private CardView amiiboTile;
    private CardView amiiboCard;
    private Toolbar toolbar;
    CustomTarget<Bitmap> amiiboTileTarget;
    CustomTarget<Bitmap> amiiboCardTarget;

    private RecyclerView flaskDetails;
    private TextView flaskStats;
    private AppCompatButton writeFile;
    private AppCompatButton createBlank;
    private NumberPicker writeCount;
    private AppCompatButton writeSlots;
    private LinearLayout writeSlotsLayout;
    private RecyclerView amiiboFilesView;
    private Snackbar statusBar;
    private Dialog uploadDialog;

    private KeyManager keyManager;
    private BrowserSettings settings;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private WriteTagAdapter writeFileAdapter;
    private FoomiiboAdapter writeDataAdapter;
    private AppCompatToggleButton sourceToggle;

    private enum WRITE {
        FILE,
        DATA,
        LIST,
        SETS
    }
    private WRITE writeAdapter = WRITE.FILE;

    private enum STATE {
        NONE,
        SCANNING,
        CONNECT,
        MISSING,
        PURCHASE
    }
    private STATE noticeState = STATE.NONE;

    private BluetoothAdapter mBluetoothAdapter;
    private ScanCallback scanCallbackLP;
    private BluetoothAdapter.LeScanCallback scanCallback;
    private FlaskGattService flaskService;
    private String flaskProfile;
    private String flaskAddress;

    private int currentCount;

    private final Foomiibo foomiibo = new Foomiibo();

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
                    new Toasty(requireActivity()).Long(R.string.flask_permissions);
                    browser.showBrowserPage();
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
                        new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
                        browser.showBrowserPage();
                    }
                } else {
                    new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
                    browser.showBrowserPage();
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
                    new Toasty(requireActivity()).Long(R.string.flask_bluetooth);
                    browser.showBrowserPage();
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
                    new Toasty(requireActivity()).Long(R.string.flask_permissions);
                    browser.showBrowserPage();
                }
            });
    protected ServiceConnection mServerConn = new ServiceConnection() {
        boolean isServiceDiscovered = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            FlaskGattService.LocalBinder localBinder = (FlaskGattService.LocalBinder) binder;
            flaskService = localBinder.getService();
            if (flaskService.initialize()) {
                if (flaskService.connect(flaskAddress)) {
                    flaskService.setListener(new FlaskGattService.BluetoothGattListener() {
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            requireActivity().runOnUiThread(() -> ((TextView) rootLayout
                                    .findViewById(R.id.hardware_info)).setText(flaskProfile));
                            try {
                                flaskService.setFlaskCharacteristicRX();
                                flaskService.getDeviceAmiibo();
                            } catch (TagLostException tle) {
                                disconnectFlask();
                                new Toasty(requireActivity())
                                        .Short(R.string.flask_invalid);
                            }
                        }

                        @Override
                        public void onFlaskStatusChanged(JSONObject jsonObject) {
                            flaskService.getDeviceAmiibo();
                        }

                        @Override
                        public void onFlaskListRetrieved(JSONArray jsonArray) {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                currentCount = jsonArray.length();
                                ArrayList<Amiibo> flaskAmiibos = new ArrayList<>();
                                for (int i = 0; i < currentCount; i++) {
                                    try {
                                        Amiibo amiibo = getAmiiboByTail(jsonArray
                                                .getString(i).split("\\|"));
                                        if (null != amiibo) amiibo.index = i;
                                        flaskAmiibos.add(amiibo);
                                    } catch (JSONException jex) {
                                        Debug.Log(jex);
                                    }
                                }
                                FlaskSlotAdapter adapter = new FlaskSlotAdapter(
                                        settings, FlaskSlotFragment.this);
                                adapter.setFlaskAmiibo(flaskAmiibos);
                                requireActivity().runOnUiThread(() -> {
                                    dismissSnackbarNotice(true);
                                    flaskDetails.setAdapter(adapter);
                                });
                                flaskService.getActiveAmiibo();
                            });
                        }

                        @SuppressLint("NotifyDataSetChanged")
                        @Override
                        public void onFlaskActiveChanged(JSONObject jsonObject) {
                            try {
                                Amiibo amiibo = getAmiiboByTail(jsonObject
                                        .getString("name").split("\\|"));
                                getActiveAmiibo(amiibo, amiiboTile);
                                if (bottomSheetBehavior.getState() ==
                                        BottomSheetBehavior.STATE_COLLAPSED)
                                    getActiveAmiibo(amiibo, amiiboCard);
                                String index = jsonObject.getString("index");
                                prefs.flaskActiveSlot().put(Integer.parseInt(index));
                                requireActivity().runOnUiThread(() -> {
                                    if (null != flaskDetails.getAdapter())
                                        flaskDetails.getAdapter().notifyDataSetChanged();
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
                        public void onFlaskFilesDownload(String dataString) {
                            try {
                                Amiibo amiibo = new Amiibo(
                                        settings.getAmiiboManager(), dataString.getBytes(), -1
                                );
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFlaskFilesUploaded() {
                            requireActivity().runOnUiThread(() -> {
                                if (null != uploadDialog && uploadDialog.isShowing())
                                    uploadDialog.dismiss();
                            });
                        }

                        @Override
                        public void onGattConnectionLost() {
                            new Handler(Looper.getMainLooper()).postDelayed(
                                    FlaskSlotFragment.this::showDisconnectNotice, 250
                            );
                            requireActivity().runOnUiThread(() -> bottomSheetBehavior
                                    .setState(BottomSheetBehavior.STATE_COLLAPSED));
                            flaskAddress = null;
                            stopFlaskService();
                        }
                    });
                } else {
                    stopFlaskService();
                    new Toasty(requireActivity()).Short(R.string.flask_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            stopFlaskService();
            if (!isServiceDiscovered) {
                showPurchaseNotice();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flask_slot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootLayout = (CoordinatorLayout) view;

        BrowserActivity activity = (BrowserActivity) requireActivity();
        keyManager = new KeyManager(activity);

        amiiboTile = rootLayout.findViewById(R.id.active_tile_layout);

        amiiboCard = rootLayout.findViewById(R.id.active_card_layout);
        amiiboCard.findViewById(R.id.txtError).setVisibility(View.GONE);
        amiiboCard.findViewById(R.id.txtPath).setVisibility(View.GONE);
        toolbar = rootLayout.findViewById(R.id.toolbar);

        amiiboTileTarget = new CustomTarget<>() {
            final AppCompatImageView imageAmiibo = amiiboTile.findViewById(R.id.imageAmiibo);

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                imageAmiibo.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                imageAmiibo.setImageResource(0);
            }

            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                imageAmiibo.setMaxHeight(Resources.getSystem().getDisplayMetrics().heightPixels / 4);
                imageAmiibo.requestLayout();
                imageAmiibo.setImageBitmap(resource);
                imageAmiibo.setVisibility(View.VISIBLE);
            }
        };

        amiiboCardTarget = new CustomTarget<>() {
            final AppCompatImageView imageAmiibo = amiiboCard.findViewById(R.id.imageAmiibo);

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                imageAmiibo.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                imageAmiibo.setImageResource(0);
            }

            @Override
            public void onResourceReady(@NonNull Bitmap resource, Transition transition) {
                imageAmiibo.setMaxHeight(Resources.getSystem().getDisplayMetrics().heightPixels / 4);
                imageAmiibo.requestLayout();
                imageAmiibo.setImageBitmap(resource);
                imageAmiibo.setVisibility(View.VISIBLE);
            }
        };

        this.settings = new BrowserSettings().initialize();

        flaskDetails = rootLayout.findViewById(R.id.flask_details);
        // flaskDetails.setHasFixedSize(true);
        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            flaskDetails.setLayoutManager(new GridLayoutManager(activity, activity.getColumnCount()));
        else
            flaskDetails.setLayoutManager(new LinearLayoutManager(activity));

        flaskStats = rootLayout.findViewById(R.id.flask_stats);
        writeFile = rootLayout.findViewById(R.id.write_slot_file);
        createBlank = rootLayout.findViewById(R.id.create_blank);
        writeCount = rootLayout.findViewById(R.id.number_picker);
        writeCount.setMaxValue(85);
        writeSlots = rootLayout.findViewById(R.id.write_slot_count);

        writeSlotsLayout = rootLayout.findViewById(R.id.write_list_layout);
        sourceToggle = rootLayout.findViewById(R.id.switch_source_btn);
        amiiboFilesView = rootLayout.findViewById(R.id.amiibo_files_list);
        // amiiboFilesView.setHasFixedSize(true);

        settings = new BrowserSettings().initialize();

        AppCompatImageView toggle = rootLayout.findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(rootLayout.findViewById(R.id.bottom_sheet));
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
                        ViewGroup mainLayout = rootLayout.findViewById(R.id.flask_details);
                        if (mainLayout.getBottom() >= bottomSheet.getTop()) {
                            int bottomHeight = bottomSheet.getMeasuredHeight()
                                    - bottomSheetBehavior.getPeekHeight();
                            mainLayout.setPadding(0, 0, 0, slideOffset > 0
                                    ? (int) (bottomHeight * slideOffset) : 0);
                        }
                    }
                });

        toggle.setOnClickListener(view1 -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        toolbar.inflateMenu(R.menu.flask_menu);

        try {
            DocumentFile rootDocument = DocumentFile.fromTreeUri(activity,
                    this.settings.getBrowserRootDocument());
            this.loadAmiiboDocuments(rootDocument, settings.isRecursiveEnabled());
        } catch (IllegalArgumentException iae) {
            this.loadAmiiboFiles(settings.getBrowserRootFolder(), settings.isRecursiveEnabled());
        }

        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiiboFilesView.setLayoutManager(new GridLayoutManager(activity, activity.getColumnCount()));
        else
            amiiboFilesView.setLayoutManager(new LinearLayoutManager(activity));

        writeFileAdapter = new WriteTagAdapter(settings,
                new WriteTagAdapter.OnAmiiboClickListener() {
                    @Override
                    public void onAmiiboClicked(AmiiboFile amiiboFile) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        showUploadingNotice();
                        uploadAmiiboFile(amiiboFile);
                    }

                    @Override
                    public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                        handleImageClicked(amiiboFile);
                    }
                });
        this.settings.addChangeListener(writeFileAdapter);

        writeDataAdapter = new FoomiiboAdapter(settings,
                new FoomiiboAdapter.OnFoomiiboClickListener() {
                    @Override
                    public void onFoomiiboClicked(View itemView, Amiibo amiibo) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        showUploadingNotice();
                        uploadFoomiiboData(amiibo);
                    }

                    @Override
                    public void onFoomiiboRebind(View itemView, Amiibo amiibo) { }

                    @Override
                    public void onFoomiiboImageClicked(Amiibo amiibo) {
                        handleImageClicked(amiibo);
                    }
                });
        this.settings.addChangeListener(writeDataAdapter);

        SearchView searchView = rootLayout.findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) activity
                .getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(activity.getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        searchView.setIconifiedByDefault(false);
        LinearLayout searchBar = searchView.findViewById(R.id.search_bar);
        searchBar.getLayoutParams().height = (int) getResources()
                .getDimension(R.dimen.button_height_min);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
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

        writeFile.setOnClickListener(view1 -> {
            onBottomSheetChanged(false);
            setWriteAdapter(WRITE.FILE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        writeCount.setOnValueChangedListener((numberPicker, valueOld, valueNew) ->
                writeSlots.setText(getString(R.string.write_slots, valueNew)));

        writeSlots.setOnClickListener(view1 -> {
            onBottomSheetChanged(false);
            setWriteAdapter(WRITE.LIST);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        createBlank.setOnClickListener(view1 -> flaskService.createBlankTag());

        sourceToggle.setOnClickListener(view1 -> {
            if (writeSlotsLayout.getVisibility() == View.VISIBLE) {
                switch(writeAdapter) {
                    case FILE:
                        setWriteAdapter(WRITE.DATA);
                        break;
                    case DATA:
                        setWriteAdapter(WRITE.FILE);
                        break;
                    case LIST:
                        setWriteAdapter(WRITE.SETS);
                        break;
                    case SETS:
                        setWriteAdapter(WRITE.LIST);
                        break;
                }
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(this::verifyPermissions, 250);
    }

    public RecyclerView getAmiibosView() {
        return flaskDetails;
    }

    private void onBottomSheetChanged(boolean hasAmiibo) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        amiiboCard.setVisibility(hasAmiibo ? View.VISIBLE : View.GONE);
        writeFile.setVisibility(hasAmiibo ? View.VISIBLE : View.GONE);
        createBlank.setVisibility(hasAmiibo ? View.VISIBLE : View.GONE);
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
        TextView txtUsageLabel = amiiboView.findViewById(R.id.txtUsageLabel);

        requireActivity().runOnUiThread(() -> {
            String amiiboHexId;
            String amiiboName;
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            String amiiboImageUrl = null;

            amiiboView.setVisibility(View.VISIBLE);
            if (active instanceof FlaskTag) {
                txtName.setText(R.string.blank_tag);
                txtTagId.setVisibility(View.INVISIBLE);
                txtAmiiboSeries.setVisibility(View.INVISIBLE);
                txtAmiiboType.setVisibility(View.INVISIBLE);
                txtGameSeries.setVisibility(View.INVISIBLE);
                if (amiiboView == amiiboCard) txtUsageLabel.setVisibility(View.INVISIBLE);
            } else {
                txtTagId.setVisibility(View.VISIBLE);
                txtAmiiboSeries.setVisibility(View.VISIBLE);
                txtAmiiboType.setVisibility(View.VISIBLE);
                txtGameSeries.setVisibility(View.VISIBLE);
                if (amiiboView == amiiboCard) txtUsageLabel.setVisibility(View.VISIBLE);
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

                if (amiiboHexId.endsWith("00000002") && !amiiboHexId.startsWith("00000000")) {
                    txtTagId.setEnabled(false);
                }
            }

            if (amiiboView == amiiboTile && null == amiiboImageUrl) {
                imageAmiibo.setImageResource(R.mipmap.ic_launcher_round);
                imageAmiibo.setVisibility(View.VISIBLE);
            } else if (null != imageAmiibo) {
                GlideApp.with(this).clear(amiiboView == amiiboCard
                        ? amiiboCardTarget : amiiboTileTarget);
                if (null != amiiboImageUrl) {
                    GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(
                            amiiboView == amiiboCard ? amiiboCardTarget : amiiboTileTarget);
                    imageAmiibo.setOnClickListener(view -> {
                        Bundle bundle = new Bundle();
                        bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, active.id);

                        Intent intent = new Intent(requireContext(), ImageActivity.class);
                        intent.putExtras(bundle);

                        startActivity(intent);
                    });
                }
            }
        });
    }

    private Amiibo getAmiiboByTail(String[] name) {
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager(requireContext().getApplicationContext());
        } catch (IOException | JSONException | ParseException e) {
            Debug.Log(e);
            amiiboManager = null;
            new Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error);
        }

        if (Thread.currentThread().isInterrupted()) return null;

        Amiibo selectedAmiibo = null;
        if (null != amiiboManager) {
            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                String flaskTail = Integer.toString(Integer.parseInt(TagUtils
                        .amiiboIdToHex(amiibo.id).substring(8, 16), 16), 36);
                if (name[1].equals(flaskTail)) {
                    selectedAmiibo = amiibo;
                    break;
                }
            }
        }
        return null != selectedAmiibo ? selectedAmiibo : new FlaskTag(Long.parseLong(name[2]));
    }

    private void verifyPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mBluetoothAdapter = getBluetoothAdapter();
            if (null != mBluetoothAdapter)
                selectBluetoothDevice();
            else
                onRequestBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
                activateBluetooth();
            } else {
                if (TagMo.isGooglePlay()) {
                    new AlertDialog.Builder(requireContext())
                            .setMessage(R.string.location_disclosure)
                            .setCancelable(false)
                            .setPositiveButton(R.string.accept, (dialog, which) -> {
                                dialog.dismiss();
                                final String[] PERMISSIONS_LOCATION = {
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                };
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    onRequestLocationQ.launch(PERMISSIONS_LOCATION);
                                } else {
                                    onRequestLocation.launch(PERMISSIONS_LOCATION);
                                }
                            }).setNegativeButton(R.string.deny, (dialog, which)
                                    -> browser.showBrowserPage()).show();
                } else {
                    final String[] PERMISSIONS_LOCATION = {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    };
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        onRequestLocationQ.launch(PERMISSIONS_LOCATION);
                    } else {
                        onRequestLocation.launch(PERMISSIONS_LOCATION);
                    }
                }
            }
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

    @SuppressLint("MissingPermission")
    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothAdapter mBluetoothAdapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBluetoothAdapter = ((BluetoothManager) requireContext()
                    .getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
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

    @SuppressLint("MissingPermission")
    private void scanBluetoothServices() {
        showScanningNotice();
        flaskProfile = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            ParcelUuid FlaskUUID = new ParcelUuid(FlaskGattService.FlaskNUS);
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
            mBluetoothAdapter.startLeScan(new UUID[]{ FlaskGattService.FlaskNUS }, scanCallback);
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

    @SuppressLint("MissingPermission")
    private void selectBluetoothDevice() {
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                new Toasty(requireActivity()).Long(R.string.flask_paired);
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
                amiiboManager = AmiiboManager.getAmiiboManager(requireContext().getApplicationContext());
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                new Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error);
            }

            final AmiiboManager uiAmiiboManager = amiiboManager;
            requireActivity().runOnUiThread(() -> {
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
            }
            File foomiibo = new File(requireContext().getFilesDir(), "Foomiibo");
            amiiboFiles.addAll(AmiiboManager
                    .listAmiibos(keyManager, foomiibo, true));

            if (Thread.currentThread().isInterrupted()) return;

            requireActivity().runOnUiThread(() -> {
                settings.setAmiiboFiles(amiiboFiles);
                settings.notifyChanges();
            });
        });
    }

    private void loadAmiiboDocuments(DocumentFile rootFolder, boolean recursiveFiles) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final ArrayList<AmiiboFile> amiiboFiles = AmiiboManager
                    .listAmiiboDocuments(requireContext(), keyManager, rootFolder, recursiveFiles);
            File foomiibo = new File(requireContext().getFilesDir(), "Foomiibo");
            amiiboFiles.addAll(AmiiboManager
                    .listAmiibos(keyManager, foomiibo, true));
            requireActivity().runOnUiThread(() -> {
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

    private void writeFoomiiboCollection(ArrayList<Amiibo> amiiboList) {
        if (null != amiiboList && amiiboList.size() == writeCount.getValue()) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            showUploadingNotice();
            for (Amiibo amiibo : amiiboList) {
                uploadFoomiiboData(amiibo);
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
                flaskService.uploadAmiiboFile(amiiboFile.getData(), amiibo);
            }
        }
    }

    private void uploadFoomiiboData(Amiibo amiibo) {
        if (null != amiibo) {
            try {
                byte[] data = foomiibo.generateData(amiibo.id);
                byte[] tagData = TagUtils.getValidatedData(keyManager, data);
                flaskService.uploadAmiiboFile(tagData, amiibo);
            } catch (Exception e) {
                Debug.Log(e);
            }
        }
    }

    private void dismissSnackbarNotice(boolean finite) {
        if (finite) noticeState = STATE.NONE;
        if (null != statusBar && statusBar.isShown()) statusBar.dismiss();
    }

    private void dismissSnackbarNotice() {
        dismissSnackbarNotice(false);
    }

    private void showScanningNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.SCANNING;
        statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_scanning, R.drawable.ic_baseline_bluetooth_searching_24dp,
                Snackbar.LENGTH_INDEFINITE, rootLayout.findViewById(R.id.bottom_sheet)
        );
        statusBar.show();
    }

    private void showConnectionNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.CONNECT;
        statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_located, R.drawable.ic_bluup_flask_24dp,
                Snackbar.LENGTH_INDEFINITE, rootLayout.findViewById(R.id.bottom_sheet)
        );
        statusBar.show();
    }

    private void showDisconnectNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.MISSING;
        statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_disconnect, R.drawable.ic_baseline_bluetooth_searching_24dp,
                Snackbar.LENGTH_INDEFINITE, rootLayout.findViewById(R.id.bottom_sheet)
        );
        statusBar.setAction(R.string.scan, v -> selectBluetoothDevice());
        statusBar.show();
    }

    private void showUploadingNotice() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(R.layout.upload_dialog);
        uploadDialog = builder.create();
        uploadDialog.show();
    }

    private void showPurchaseNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.PURCHASE;
        statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                R.string.flask_missing, R.drawable.ic_bluup_flask_24dp,
                Snackbar.LENGTH_INDEFINITE, rootLayout.findViewById(R.id.bottom_sheet)
        );
        statusBar.setAction(R.string.purchase, v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bluuplabs.com/flask/"))
        ));
        statusBar.show();
    }

    public void startFlaskService() {
        Intent service = new Intent(requireContext(), FlaskGattService.class);
        requireContext().startService(service);
        requireContext().bindService(service, mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void disconnectFlask() {
        dismissSnackbarNotice(true);
        if (null != flaskService)
            flaskService.disconnect();
        else
            stopFlaskService();
    }

    public void stopFlaskService() {
        try {
            requireContext().unbindService(mServerConn);
            requireContext().stopService(new Intent(requireContext(), FlaskGattService.class));
        } catch (IllegalArgumentException ignored) { }
    }

    @SuppressLint("MissingPermission")
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

    private void setWriteAdapter(WRITE format) {
        switch(format) {
            case FILE:
                amiiboFilesView.setAdapter(writeFileAdapter);
                sourceToggle.setChecked(false);
                break;
            case DATA:
                amiiboFilesView.setAdapter(writeDataAdapter);
                sourceToggle.setChecked(true);
                break;
            case LIST:
                WriteTagAdapter writeListAdapter = new WriteTagAdapter(
                        settings, this::writeAmiiboCollection);
                writeListAdapter.resetSelections();
                this.settings.addChangeListener(writeListAdapter);
                amiiboFilesView.setAdapter(writeListAdapter);
                sourceToggle.setChecked(false);
                break;
            case SETS:
                FoomiiboAdapter writeSetsAdapter = new FoomiiboAdapter(
                        settings, this::writeFoomiiboCollection);
                writeSetsAdapter.resetSelections();
                this.settings.addChangeListener(writeSetsAdapter);
                amiiboFilesView.setAdapter(writeSetsAdapter);
                sourceToggle.setChecked(true);
                break;
        }
        writeAdapter = format;
    }

    private void handleImageClicked(Amiibo amiibo) {
        if (null != amiibo) {
            Bundle bundle = new Bundle();
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(requireContext(), ImageActivity.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    }

    private void handleImageClicked(AmiiboFile amiiboFile) {
        if (null != amiiboFile) {
            Bundle bundle = new Bundle();
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboFile.getId());

            Intent intent = new Intent(requireContext(), ImageActivity.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissFlaskDiscovery();
        disconnectFlask();
    }

    @Override
    public void onPause() {
        dismissSnackbarNotice();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (null != statusBar && statusBar.isShown()) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            switch (noticeState) {
                case NONE:
                    break;
                case SCANNING:
                    showScanningNotice();
                    break;
                case CONNECT:
                    showConnectionNotice();
                    break;
                case MISSING:
                    showDisconnectNotice();
                    break;
                case PURCHASE:
                    showPurchaseNotice();
                    break;
            }
        }, 100);
    }

    @Override
    public void onAmiiboClicked(Amiibo amiibo) {
        getActiveAmiibo(amiibo, amiiboCard);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        if (amiibo instanceof FlaskTag) {
            toolbar.getMenu().findItem(R.id.mnu_backup).setVisible(false);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.mnu_activate) {
                    flaskService.setActiveAmiibo(
                            amiibo.name, String.valueOf(amiibo.id)
                    );
                    return true;
                } else if (item.getItemId() == R.id.mnu_delete) {
                    flaskService.deleteAmiibo(
                            amiibo.name, String.valueOf(amiibo.id)
                    );
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    return true;
                }
                return false;
            });
        } else {
            toolbar.getMenu().findItem(R.id.mnu_backup).setVisible(true);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.mnu_activate) {
                    flaskService.setActiveAmiibo(
                            amiibo.name, amiibo.getFlaskTail()
                    );
                    return true;
                } else if (item.getItemId() == R.id.mnu_delete) {
                    flaskService.deleteAmiibo(
                            amiibo.name, amiibo.getFlaskTail()
                    );
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    return true;
                } else if (item.getItemId() == R.id.mnu_backup) {
                    flaskService.downloadAmiibo(
                            amiibo.name, amiibo.getFlaskTail()
                    );
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo) {
        if (null != amiibo) {
            Bundle bundle = new Bundle();
            bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id);

            Intent intent = new Intent(requireContext(), ImageActivity.class);
            intent.putExtras(bundle);

            startActivity(intent);
        }
    }
}

