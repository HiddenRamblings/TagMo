package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatToggleButton;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.GlideApp;
import com.hiddenramblings.tagmo.ImageActivity;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.FlaskTag;
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler;
import com.hiddenramblings.tagmo.bluetooth.FlaskGattService;
import com.hiddenramblings.tagmo.bluetooth.PuckGattService;
import com.hiddenramblings.tagmo.browser.adapter.FlaskSlotAdapter;
import com.hiddenramblings.tagmo.browser.adapter.WriteTagAdapter;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.nfctech.TagArray;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.shawnlin.numberpicker.NumberPicker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;

@SuppressLint("NewApi")
public class FlaskSlotFragment extends Fragment implements
        FlaskSlotAdapter.OnAmiiboClickListener,
        BluetoothHandler.BluetoothListener {

    private Preferences prefs;
    private BluetoothHandler bluetoothHandler;
    private boolean isFragmentVisible = false;

    private CoordinatorLayout rootLayout;
    private CardView amiiboTile;
    private CardView amiiboCard;
    private Toolbar toolbar;
    CustomTarget<Bitmap> amiiboTileTarget;
    CustomTarget<Bitmap> amiiboCardTarget;

    private RecyclerView flaskContent;
    private TextView flaskStats;
    private NumberPicker flaskSlotCount;
    private AppCompatButton writeSlots;
    private AppCompatButton eraseSlots;
    private LinearLayout slotOptionsMenu;
    private AppCompatToggleButton switchMenuOptions;
    private LinearLayout writeSlotsLayout;
    private WriteTagAdapter writeTagAdapter;
    private Snackbar statusBar;
    private Dialog processDialog;

    private BrowserSettings settings;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    private BluetoothAdapter mBluetoothAdapter;
    private ScanCallback scanCallbackFlaskLP;
    private BluetoothAdapter.LeScanCallback scanCallbackFlask;
    private FlaskGattService serviceFlask;
    private ScanCallback scanCallbackPuckLP;
    private BluetoothAdapter.LeScanCallback scanCallbackPuck;
    private PuckGattService servicePuck;

    private String deviceProfile;
    private String deviceAddress;
    private final int maxSlotCount = 85;
    private int currentCount;

    private enum STATE {
        NONE,
        SCANNING,
        CONNECT,
        MISSING,
        PURCHASE
    }
    private STATE noticeState = STATE.NONE;

    private enum SHEET {
        LOCKED,
        AMIIBO,
        MENU,
        WRITE
    }

    private final Handler fragmentHandler = new Handler(Looper.getMainLooper());

    protected ServiceConnection flaskServerConn = new ServiceConnection() {
        boolean isServiceDiscovered = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            FlaskGattService.LocalBinder localBinder = (FlaskGattService.LocalBinder) binder;
            serviceFlask = localBinder.getService();
            if (serviceFlask.initialize()) {
                if (serviceFlask.connect(deviceAddress)) {
                    serviceFlask.setListener(new FlaskGattService.BluetoothGattListener() {
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            onBottomSheetChanged(SHEET.MENU);
                            rootLayout.post(() -> ((TextView) rootLayout
                                    .findViewById(R.id.hardware_info)).setText(deviceProfile));
                            try {
                                serviceFlask.setFlaskCharacteristicRX();
                                serviceFlask.getDeviceAmiibo();
                            } catch (UnsupportedOperationException uoe) {
                                disconnectService();
                                new Toasty(requireActivity()).Short(R.string.device_invalid);
                            }
                        }

                        @Override
                        public void onFlaskStatusChanged(JSONObject jsonObject) {
                            if (null != processDialog && processDialog.isShowing())
                                processDialog.dismiss();
                            serviceFlask.getDeviceAmiibo();
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
                                        flaskAmiibos.add(amiibo);
                                    } catch (JSONException | NullPointerException ex) {
                                        Debug.Warn(ex);
                                    }
                                }
                                FlaskSlotAdapter adapter = new FlaskSlotAdapter(
                                        settings, FlaskSlotFragment.this);
                                adapter.setFlaskAmiibo(flaskAmiibos);
                                flaskContent.post(() -> {
                                    dismissSnackbarNotice(true);
                                    flaskContent.setAdapter(adapter);
                                    if (currentCount > 0) {
                                        serviceFlask.getActiveAmiibo();
                                        adapter.notifyItemRangeInserted(
                                                0, currentCount
                                        );
                                    } else {
                                        amiiboTile.setVisibility(View.INVISIBLE);
                                        getFlaskButtonState();
                                    }
                                });
                            });
                        }
                        
                        @Override
                        public void onFlaskRangeRetrieved(JSONArray jsonArray) {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                ArrayList<Amiibo> flaskAmiibos = new ArrayList<>();
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    try {
                                        Amiibo amiibo = getAmiiboByTail(jsonArray
                                                .getString(i).split("\\|"));
                                        flaskAmiibos.add(amiibo);
                                    } catch (JSONException | NullPointerException ex) {
                                        Debug.Warn(ex);
                                    }
                                }
                                FlaskSlotAdapter adapter = (FlaskSlotAdapter)
                                        flaskContent.getAdapter();
                                if (null != adapter) {
                                    adapter.addFlaskAmiibo(flaskAmiibos);
                                    flaskContent.post(() -> {
                                        adapter.notifyItemRangeInserted(
                                                currentCount, flaskAmiibos.size()
                                        );
                                        currentCount = adapter.getItemCount();
                                    });
                                }
                            });
                        }

                        @Override
                        public void onFlaskActiveChanged(JSONObject jsonObject) {
                            try {
                                String name = jsonObject.getString("name");
                                if ("undefined".equals(name)) {
                                    resetActiveSlot();
                                    return;
                                }
                                Amiibo amiibo = getAmiiboByTail(name.split("\\|"));
                                String index = jsonObject.getString("index");
                                getActiveAmiibo(amiibo, amiiboTile);
                                if (bottomSheetBehavior.getState() ==
                                        BottomSheetBehavior.STATE_COLLAPSED)
                                    getActiveAmiibo(amiibo, amiiboCard);
                                prefs.flaskActiveSlot(Integer.parseInt(index));
                                flaskContent.post(() -> flaskStats.setText(
                                        getString(R.string.flask_count, index, currentCount)
                                ));
                                getFlaskButtonState();
                            } catch (JSONException | NullPointerException ex) {
                                Debug.Warn(ex);
                            }
                        }

                        @Override
                        public void onFlaskFilesDownload(String dataString) {
                            try {
                                byte[] tagData = dataString.getBytes();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFlaskProcessFinish() {
                            requireActivity().runOnUiThread(() -> {
                                if (null != processDialog && processDialog.isShowing())
                                    processDialog.dismiss();
                            });
                        }

                        @Override
                        public void onGattConnectionLost() {
                            fragmentHandler.postDelayed(
                                    FlaskSlotFragment.this::showDisconnectNotice, TagMo.uiDelay
                            );
                            requireActivity().runOnUiThread(() -> bottomSheetBehavior
                                    .setState(BottomSheetBehavior.STATE_COLLAPSED));
                            stopGattService();
                        }
                    });
                } else {
                    stopGattService();
                    new Toasty(requireActivity()).Short(R.string.device_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            stopGattService();
            if (!isServiceDiscovered) {
                showPurchaseNotice();
            }
        }
    };

    protected ServiceConnection puckServerConn = new ServiceConnection() {
        boolean isServiceDiscovered = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            PuckGattService.LocalBinder localBinder = (PuckGattService.LocalBinder) binder;
            servicePuck = localBinder.getService();
            if (servicePuck.initialize()) {
                if (servicePuck.connect(deviceAddress)) {
                    servicePuck.setListener(new PuckGattService.BluetoothGattListener() {
                        @Override
                        public void onServicesDiscovered() {
                            isServiceDiscovered = true;
                            onBottomSheetChanged(SHEET.MENU);
                        }

                        @Override
                        public void onPuckActiveChanged(int slot) {

                        }

                        @Override
                        public void onPuckCountRetrieved(int count) {

                        }

                        @Override
                        public void onPuckListRetrieved(ArrayList<byte[]> slotData) {

                        }

                        @Override
                        public void onPuckFilesDownload(byte[] tagData) {

                        }

                        @Override
                        public void onPuckFilesUploaded() {

                        }

                        @Override
                        public void onGattConnectionLost() {

                        }
                    });
                } else {
                    stopGattService();
                    new Toasty(requireActivity()).Short(R.string.device_invalid);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            stopGattService();
            if (!isServiceDiscovered) {
                showPurchaseNotice();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_flask_slot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) return;

        rootLayout = (CoordinatorLayout) view;

        BrowserActivity activity = (BrowserActivity) requireActivity();
        prefs = new Preferences(activity.getApplicationContext());

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

        this.settings = activity.getSettings();

        flaskContent = rootLayout.findViewById(R.id.flask_content);
        if (prefs.software_layer())
            flaskContent.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        // flaskContent.setHasFixedSize(true);
        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            flaskContent.setLayoutManager(new GridLayoutManager(activity, activity.getColumnCount()));
        else
            flaskContent.setLayoutManager(new LinearLayoutManager(activity));

        flaskStats = rootLayout.findViewById(R.id.flask_stats);
        switchMenuOptions = rootLayout.findViewById(R.id.switch_menu_btn);
        slotOptionsMenu = rootLayout.findViewById(R.id.slot_options_menu);
        AppCompatButton writeFile = rootLayout.findViewById(R.id.write_slot_file);
        AppCompatButton createBlank = rootLayout.findViewById(R.id.create_blank);
        flaskSlotCount = rootLayout.findViewById(R.id.number_picker);
        flaskSlotCount.setMaxValue(maxSlotCount);
        writeSlots = rootLayout.findViewById(R.id.write_slot_count);
        writeSlots.setText(getString(R.string.write_slots, 1));
        eraseSlots = rootLayout.findViewById(R.id.erase_slot_count);
        eraseSlots.setText(getString(R.string.erase_slots, 0));
        writeSlotsLayout = rootLayout.findViewById(R.id.write_list_layout);
        if (prefs.software_layer())
            writeSlotsLayout.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        RecyclerView amiiboFilesView = rootLayout.findViewById(R.id.amiibo_files_list);
        if (prefs.software_layer())
            amiiboFilesView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        // amiiboFilesView.setHasFixedSize(true);

        AppCompatImageView toggle = rootLayout.findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(rootLayout.findViewById(R.id.bottom_sheet));
        setBottomSheetHidden(false);
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (writeSlotsLayout.getVisibility() == View.VISIBLE)
                        onBottomSheetChanged(SHEET.MENU);
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                ViewGroup mainLayout = rootLayout.findViewById(R.id.flask_content);
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

        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiiboFilesView.setLayoutManager(new GridLayoutManager(activity, activity.getColumnCount()));
        else
            amiiboFilesView.setLayoutManager(new LinearLayoutManager(activity));
        writeTagAdapter = new WriteTagAdapter(settings);
        amiiboFilesView.setAdapter(writeTagAdapter);
        this.settings.addChangeListener(writeTagAdapter);

        view.findViewById(R.id.switch_devices).setOnClickListener(change -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            disconnectService();
            if (isBluetoothEnabled()) selectBluetoothDevice();
        });

        switchMenuOptions.setOnClickListener(view1 -> {
            if (slotOptionsMenu.isShown()) {
                onBottomSheetChanged(SHEET.AMIIBO);
            } else {
                onBottomSheetChanged(SHEET.MENU);
            }
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        SearchView searchView = rootLayout.findViewById(R.id.amiibo_search);
        if (BuildConfig.WEAR_OS) {
            searchView.setVisibility(View.GONE);
        } else {
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
        }

        writeFile.setOnClickListener(view1 -> {
            onBottomSheetChanged(SHEET.WRITE);
            searchView.setQuery(settings.getQuery(), true);
            searchView.clearFocus();
            writeTagAdapter.setListener(new WriteTagAdapter.OnAmiiboClickListener() {
                @Override
                public void onAmiiboClicked(AmiiboFile amiiboFile) {
                    onBottomSheetChanged(SHEET.AMIIBO);
                    showProcessingNotice(true);
                    uploadAmiiboFile(amiiboFile);
                }

                @Override
                public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                    handleImageClicked(amiiboFile);
                }

                @Override
                public void onAmiiboListClicked(ArrayList<AmiiboFile> amiiboList) { }
            }, 1);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        flaskSlotCount.setOnValueChangedListener((numberPicker, valueOld, valueNew) -> {
            if (maxSlotCount - currentCount > 0)
                writeSlots.setText(getString(R.string.write_slots, valueNew));
        });

        writeSlots.setOnClickListener(view1 -> {
            onBottomSheetChanged(SHEET.WRITE);
            searchView.setQuery(settings.getQuery(), true);
            searchView.clearFocus();
            writeTagAdapter.setListener(new WriteTagAdapter.OnAmiiboClickListener() {
                @Override
                public void onAmiiboClicked(AmiiboFile amiiboFile) { }

                @Override
                public void onAmiiboImageClicked(AmiiboFile amiiboFile) { }

                @Override
                public void onAmiiboListClicked(ArrayList<AmiiboFile> amiiboList) {
                    writeAmiiboCollection(amiiboList);
                }
            }, flaskSlotCount.getValue());
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        eraseSlots.setOnClickListener(view1 -> new AlertDialog.Builder(requireContext())
                .setMessage(R.string.flask_erase_confirm)
                .setPositiveButton(R.string.proceed, (dialog, which) -> {
                    showProcessingNotice(false);
                    serviceFlask.clearStorage(currentCount);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                })
                .show());

        createBlank.setOnClickListener(view1 -> serviceFlask.createBlankTag());

        rootLayout.findViewById(R.id.screen_layered)
                .setOnClickListener(view1 -> serviceFlask.setFlaskFace(false));
        rootLayout.findViewById(R.id.screen_stacked)
                .setOnClickListener(view1 -> serviceFlask.setFlaskFace(true));

        getFlaskButtonState();
    }

    public RecyclerView getFlaskContent() {
        return flaskContent;
    }
    public BottomSheetBehavior<View> getBottomSheet() {
        return bottomSheetBehavior;
    }

    private void onBottomSheetChanged(SHEET sheet) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        switch (sheet) {
            case LOCKED:
                amiiboCard.setVisibility(View.GONE);
                switchMenuOptions.setVisibility(View.GONE);
                slotOptionsMenu.setVisibility(View.GONE);
                writeSlotsLayout.setVisibility(View.GONE);
                break;
            case AMIIBO:
                amiiboCard.setVisibility(View.VISIBLE);
                switchMenuOptions.setVisibility(View.VISIBLE);
                slotOptionsMenu.setVisibility(View.GONE);
                writeSlotsLayout.setVisibility(View.GONE);
            break;
            case MENU:
                amiiboCard.setVisibility(View.GONE);
                switchMenuOptions.setVisibility(View.VISIBLE);
                slotOptionsMenu.setVisibility(View.VISIBLE);
                writeSlotsLayout.setVisibility(View.GONE);
                break;
            case WRITE:
                amiiboCard.setVisibility(View.GONE);
                switchMenuOptions.setVisibility(View.GONE);
                slotOptionsMenu.setVisibility(View.GONE);
                writeSlotsLayout.setVisibility(View.VISIBLE);
                break;
        }
        flaskContent.requestLayout();
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

    private void getFlaskButtonState() {
        flaskContent.post(() -> {
            int openSlots = maxSlotCount - currentCount;
            flaskSlotCount.setValue(openSlots);
            if (openSlots > 0) {
                writeSlots.setEnabled(true);
                writeSlots.setText(getString(R.string.write_slots, openSlots));
            } else {
                writeSlots.setEnabled(false);
                writeSlots.setText(getString(R.string.slots_full));
            }
            if (currentCount > 0) {
                eraseSlots.setEnabled(true);
                eraseSlots.setText(getString(R.string.erase_slots, currentCount));
            } else {
                eraseSlots.setEnabled(false);
                eraseSlots.setText(getString(R.string.slots_empty));
            }
        });
    }

    private void resetActiveSlot() {
        FlaskSlotAdapter adapter = (FlaskSlotAdapter)
                flaskContent.getAdapter();
        if (null != adapter) {
            Amiibo amiibo = adapter.getItem(0);
            if (amiibo instanceof FlaskTag) {
                serviceFlask.setActiveAmiibo(
                        amiibo.name, new String(TagArray.longToBytes(amiibo.id))
                );
            } else {
                serviceFlask.setActiveAmiibo(
                        amiibo.name, amiibo.getFlaskTail()
                );
            }
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

        amiiboView.post(() -> {
            String amiiboHexId;
            String amiiboName;
            String amiiboSeries = "";
            String amiiboType = "";
            String gameSeries = "";
            String amiiboImageUrl = null;

            if (amiiboView == amiiboTile) amiiboView.setVisibility(View.VISIBLE);
            if (null == active) {
                txtName.setText(R.string.no_tag_loaded);
                txtTagId.setVisibility(View.INVISIBLE);
                txtAmiiboSeries.setVisibility(View.INVISIBLE);
                txtAmiiboType.setVisibility(View.INVISIBLE);
                txtGameSeries.setVisibility(View.INVISIBLE);
                if (amiiboView == amiiboCard) txtUsageLabel.setVisibility(View.INVISIBLE);
            } else if (active instanceof FlaskTag) {
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
                amiiboHexId = Amiibo.idToHex(active.id);
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
            } else if (amiiboView == amiiboCard && null == amiiboImageUrl) {
                imageAmiibo.setImageResource(0);
                imageAmiibo.setVisibility(View.INVISIBLE);
            } else if (null != imageAmiibo) {
                GlideApp.with(imageAmiibo).clear(imageAmiibo);
                if (null != amiiboImageUrl) {
                    GlideApp.with(imageAmiibo).asBitmap().load(amiiboImageUrl).into(
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
        if (name.length < 2) return null;
        if (name[1].length() == 0) return new FlaskTag(name);
        AmiiboManager amiiboManager;
        try {
            amiiboManager = AmiiboManager.getAmiiboManager(requireContext().getApplicationContext());
        } catch (IOException | JSONException | ParseException e) {
            Debug.Warn(e);
            amiiboManager = null;
            new Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error);
        }

        if (Thread.currentThread().isInterrupted()) return null;

        Amiibo selectedAmiibo = null;
        if (null != amiiboManager) {
            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                String flaskTail = Integer.toString(Integer.parseInt(
                        Amiibo.idToHex(amiibo.id).substring(8, 16), 16
                ), 36);
                if (name[1].equals(flaskTail)) {
                    selectedAmiibo = amiibo;
                    break;
                }
            }
        }
        return selectedAmiibo;
    }

    @SuppressLint({"MissingPermission", "NewApi"})
    private void scanBluetoothServices() {
        mBluetoothAdapter = null != mBluetoothAdapter ? mBluetoothAdapter
                : bluetoothHandler.getBluetoothAdapter(requireContext());
        if (null == mBluetoothAdapter) {
            setBottomSheetHidden(true);
            new Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter);
            return;
        }
        showScanningNotice();
        deviceProfile = null;
        if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
            BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            ParcelUuid FlaskUUID = new ParcelUuid(FlaskGattService.FlaskNUS);
            ScanFilter filterFlask = new ScanFilter.Builder().setServiceUuid(FlaskUUID).build();
            scanCallbackFlaskLP = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    deviceProfile = result.getDevice().getName();
                    deviceAddress = result.getDevice().getAddress();
                    dismissGattDiscovery();
                    showConnectionNotice();
                    startFlaskService();
                }
            };
            scanner.startScan(Collections.singletonList(filterFlask), settings, scanCallbackFlaskLP);
            ParcelUuid PuckUUID = new ParcelUuid(PuckGattService.PuckNUS);
            ScanFilter filterPuck = new ScanFilter.Builder().setServiceUuid(PuckUUID).build();
            scanCallbackPuckLP = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    deviceProfile = result.getDevice().getName();
                    deviceAddress = result.getDevice().getAddress();
                    dismissGattDiscovery();
                    showConnectionNotice();
                    startFlaskService();
                }
            };
            scanner.startScan(Collections.singletonList(filterPuck), settings, scanCallbackPuckLP);
        } else {
            scanCallbackFlask = (bluetoothDevice, i, bytes) -> {
                deviceProfile = bluetoothDevice.getName();
                deviceAddress = bluetoothDevice.getAddress();
                dismissGattDiscovery();
                showConnectionNotice();
                startFlaskService();
            };
            mBluetoothAdapter.startLeScan(new UUID[]{ FlaskGattService.FlaskNUS }, scanCallbackFlask);
            scanCallbackPuck = (bluetoothDevice, i, bytes) -> {
                deviceProfile = bluetoothDevice.getName();
                deviceAddress = bluetoothDevice.getAddress();
                dismissGattDiscovery();
                showConnectionNotice();
                startPuckService();
            };
            mBluetoothAdapter.startLeScan(new UUID[]{ PuckGattService.PuckNUS }, scanCallbackPuck);
        }
        fragmentHandler.postDelayed(() -> {
            if (null == deviceProfile) {
                dismissGattDiscovery();
                showPurchaseNotice();
            }
        }, 20000);
    }

    private void displayDevices(ArrayList<BluetoothDevice> devices) {
        final LinearLayout view = (LinearLayout) this.getLayoutInflater()
                .inflate(R.layout.dialog_devices, null);
        AlertDialog deviceDialog = (new AlertDialog.Builder(requireActivity())).setView(view).show();
        for (BluetoothDevice device : devices) {
            final View item = this.getLayoutInflater().inflate(R.layout.device_bluetooth, null);
            ((TextView) item.findViewById(R.id.device_name)).setText(device.getName());
            ((TextView) item.findViewById(R.id.device_address)).setText(
                    requireActivity().getString(R.string.device_address, device.getAddress())
            );
            item.findViewById(R.id.connect_flask).setOnClickListener(flask -> {
                deviceDialog.dismiss();
                deviceProfile = device.getName();
                deviceAddress = device.getAddress();
                showConnectionNotice();
                startFlaskService();
            });
            item.findViewById(R.id.connect_puck).setOnClickListener(puck -> {
                deviceDialog.dismiss();
//                deviceProfile = device.getName();
//                deviceAddress = device.getAddress();
//                showConnectionNotice();
//                startPuckService();
                new Toasty(requireActivity()).Short(R.string.notice_incomplete);

            });
            view.addView(item);
        }
        final View item = this.getLayoutInflater().inflate(R.layout.device_bluetooth, null);
        ((TextView) item.findViewById(R.id.device_name)).setText(R.string.scan_heading);
        item.findViewById(R.id.device_address).setVisibility(View.GONE);
        AppCompatButton connect = item.findViewById(R.id.connect_flask);
        connect.setText(R.string.scan_devices);
        item.findViewById(R.id.connect_puck).setVisibility(View.GONE);
        item.setOnClickListener(layout -> {
            deviceDialog.dismiss();
            scanBluetoothServices();
        });
        connect.setOnClickListener(button -> {
            deviceDialog.dismiss();
            scanBluetoothServices();
        });
        view.addView(item);
    }

    @SuppressLint("MissingPermission")
    private void selectBluetoothDevice() {
        ArrayList<BluetoothDevice> devices = new ArrayList<>(mBluetoothAdapter.getBondedDevices());
        boolean isFlaskPaired = false;
        for (BluetoothDevice device : mBluetoothAdapter.getBondedDevices()) {
            if (device.getName().toLowerCase(Locale.ROOT).startsWith("flask")) {
                isFlaskPaired = true;
                deviceProfile = device.getName();
                deviceAddress = device.getAddress();
                showConnectionNotice();
                startFlaskService();
                break;
            }
        }
        if (!isFlaskPaired) displayDevices(devices);
    }

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.flask_write_confirm)
                .setPositiveButton(R.string.proceed, (dialog, which) -> {
                    onBottomSheetChanged(SHEET.MENU);
                    showProcessingNotice(true);
                    for (int i = 0; i < amiiboList.size(); i++) {
                        int index = i;
                        fragmentHandler.postDelayed(() -> uploadAmiiboFile(
                                amiiboList.get(index), index == amiiboList.size() - 1
                        ), 30L * i);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    amiiboList.clear();
                    onBottomSheetChanged(SHEET.MENU);
                    dialog.dismiss();
                })
                .show();
    }

    private void uploadAmiiboFile(AmiiboFile amiiboFile, boolean complete) {
        if (null != amiiboFile) {
            Amiibo amiibo = null;
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (null != amiiboManager) {
                try {
                    long amiiboId = Amiibo.dataToId(amiiboFile.getData());
                    amiibo = amiiboManager.amiibos.get(amiiboId);
                    if (null == amiibo)
                        amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
                } catch (Exception e) {
                    Debug.Warn(e);
                }
            }
            if (null != amiibo) serviceFlask.uploadAmiiboFile(amiiboFile.getData(), amiibo, complete);
        }
    }

    private void uploadAmiiboFile(AmiiboFile amiiboFile) {
        uploadAmiiboFile(amiiboFile, true);
    }

    private void setBottomSheetHidden(boolean hidden) {
        bottomSheetBehavior.setHideable(hidden);
        if (hidden) bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
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
        if (isFragmentVisible) {
            statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                    R.string.flask_scanning,
                    R.drawable.ic_baseline_bluetooth_searching_24dp,
                    Snackbar.LENGTH_INDEFINITE
            );
            statusBar.show();
            statusBar.getView().setKeepScreenOn(true);
        }
    }

    private void showConnectionNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.CONNECT;
        if (isFragmentVisible) {
            statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                    R.string.flask_located,
                    R.drawable.ic_bluup_flask_24dp,
                    Snackbar.LENGTH_INDEFINITE
            );
            statusBar.show();
            statusBar.getView().setKeepScreenOn(true);
        }
    }

    private void showDisconnectNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.MISSING;
        if (isFragmentVisible) {
            statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                    R.string.flask_disconnect,
                    R.drawable.ic_baseline_bluetooth_searching_24dp,
                    Snackbar.LENGTH_INDEFINITE
            );
            statusBar.setAction(R.string.scan, v -> selectBluetoothDevice());
            statusBar.show();
        }
    }

    private void showProcessingNotice(boolean upload) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_process, null);
        ((TextView) view.findViewById(R.id.process_text)).setText(
                upload ? R.string.flask_upload : R.string.flask_remove
        );
        builder.setView(view);
        processDialog = builder.create();
        processDialog.show();
        processDialog.getWindow().getDecorView().setKeepScreenOn(true);
    }

    private void showPurchaseNotice() {
        dismissSnackbarNotice();
        noticeState = STATE.PURCHASE;
        if (isFragmentVisible) {
            statusBar = new IconifiedSnackbar(requireActivity()).buildSnackbar(
                    R.string.flask_missing,
                    R.drawable.ic_bluup_flask_24dp,
                    Snackbar.LENGTH_INDEFINITE
            );
            statusBar.setAction(R.string.purchase, v ->  {
                startActivity(new Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://www.bluuplabs.com/flask/")
                ));
                statusBar.dismiss();
            });
            statusBar.show();
        }
    }

    public void startFlaskService() {
        Intent service = new Intent(requireContext(), FlaskGattService.class);
        requireContext().startService(service);
        requireContext().bindService(service, flaskServerConn, Context.BIND_AUTO_CREATE);
    }

    public void startPuckService() {
        Intent service = new Intent(requireContext(), PuckGattService.class);
        requireContext().startService(service);
        requireContext().bindService(service, puckServerConn, Context.BIND_AUTO_CREATE);
    }

    public void disconnectService() {
        dismissSnackbarNotice(true);
        if (null != serviceFlask)
            serviceFlask.disconnect();
        if (null != servicePuck)
            servicePuck.disconnect();
        else
            stopGattService();
    }

    public void stopGattService() {
        onBottomSheetChanged(SHEET.LOCKED);
        deviceAddress = null;
        try {
            requireContext().unbindService(flaskServerConn);
            requireContext().stopService(new Intent(requireContext(), FlaskGattService.class));
        } catch (IllegalArgumentException ignored) { }
        try {
            requireContext().unbindService(flaskServerConn);
            requireContext().stopService(new Intent(requireContext(), FlaskGattService.class));
        } catch (IllegalArgumentException ignored) { }
    }

    @SuppressLint("MissingPermission")
    private void dismissGattDiscovery() {
        mBluetoothAdapter = null != mBluetoothAdapter ? mBluetoothAdapter
                : bluetoothHandler.getBluetoothAdapter(requireContext());
        if (null != mBluetoothAdapter) {
            if (Debug.isNewer(Build.VERSION_CODES.LOLLIPOP)) {
                if (null != scanCallbackFlaskLP)
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallbackFlaskLP);
                if (null != scanCallbackPuckLP)
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallbackPuckLP);
            } else {
                if (null != scanCallbackFlask)
                    mBluetoothAdapter.stopLeScan(scanCallbackFlask);
                if (null != scanCallbackPuck)
                    mBluetoothAdapter.stopLeScan(scanCallbackPuck);
            }
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

    private boolean isBluetoothEnabled() {
        if (null != mBluetoothAdapter && mBluetoothAdapter.isEnabled()) return true;
        bluetoothHandler = null != bluetoothHandler ? bluetoothHandler : new BluetoothHandler(
                requireContext(), requireActivity().getActivityResultRegistry(), FlaskSlotFragment.this
        );
        bluetoothHandler.requestPermissions(requireActivity());
        return false;
    }

    public void delayedBluetoothEnable() {
        fragmentHandler.postDelayed(this::isBluetoothEnabled, 125);
    }

    @Override
    public void onPause() {
        isFragmentVisible = false;
        dismissSnackbarNotice();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        try {
            dismissGattDiscovery();
        } catch (NullPointerException ignored) { }
        disconnectService();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        isFragmentVisible = true;
        super.onResume();
        if (null != statusBar && statusBar.isShown()) return;
        fragmentHandler.postDelayed(() -> {
            switch (noticeState) {
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
                default:
                    break;
            }
        }, TagMo.uiDelay);
        if (null == deviceAddress)
            onBottomSheetChanged(SHEET.LOCKED);
        else
            onBottomSheetChanged(SHEET.MENU);
    }

    @Override
    public void onAmiiboClicked(Amiibo amiibo) {
        getActiveAmiibo(amiibo, amiiboCard);
        onBottomSheetChanged(SHEET.AMIIBO);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        if (amiibo instanceof FlaskTag) {
            toolbar.getMenu().findItem(R.id.mnu_backup).setVisible(false);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.mnu_activate) {
                    serviceFlask.setActiveAmiibo(
                            amiibo.name, new String(TagArray.longToBytes(amiibo.id))
                    );
                    return true;
                } else if (item.getItemId() == R.id.mnu_delete) {
                    serviceFlask.deleteAmiibo(
                            amiibo.name, new String(TagArray.longToBytes(amiibo.id))
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
                    serviceFlask.setActiveAmiibo(
                            amiibo.name, amiibo.getFlaskTail()
                    );
                    return true;
                } else if (item.getItemId() == R.id.mnu_delete) {
                    serviceFlask.deleteAmiibo(
                            amiibo.name, amiibo.getFlaskTail()
                    );
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    return true;
                } else if (item.getItemId() == R.id.mnu_backup) {
                    serviceFlask.downloadAmiibo(
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

    @Override
    public void onPermissionsFailed() {
        setBottomSheetHidden(true);
        new Toasty(requireActivity()).Long(R.string.fail_permissions);
    }

    @Override
    public void onAdapterMissing() {
        setBottomSheetHidden(true);
        new Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter);
    }

    @Override
    public void onAdapterEnabled(BluetoothAdapter mBluetoothAdapter) {
        this.mBluetoothAdapter = mBluetoothAdapter;
        selectBluetoothDevice();
    }
}

