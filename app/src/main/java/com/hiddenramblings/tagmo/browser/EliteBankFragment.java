package com.hiddenramblings.tagmo.browser;

import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.hiddenramblings.tagmo.NfcActivity;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboFile;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.amiibo.tagdata.TagDataEditor;
import com.hiddenramblings.tagmo.browser.adapter.EliteBankAdapter;
import com.hiddenramblings.tagmo.browser.adapter.WriteTagAdapter;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.hexcode.HexCodeViewer;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.settings.Preferences_;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.shawnlin.numberpicker.NumberPicker;

import org.json.JSONException;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

public class EliteBankFragment extends Fragment implements
        EliteBankAdapter.OnAmiiboClickListener {

    private final Preferences_ prefs = TagMo.getPrefs();

    private CoordinatorLayout rootLayout;
    private RecyclerView amiibosView;

    private LinearLayout bankOptionsMenu;
    private AppCompatToggleButton switchMenuOptions;
    private LinearLayout writeBankLayout;
    private EliteBankAdapter bankAdapter;
    private RecyclerView amiiboFilesView;

    private CardView amiiboTile;
    private CardView amiiboCard;
    private Toolbar toolbar;
    CustomTarget<Bitmap> amiiboTileTarget;
    CustomTarget<Bitmap> amiiboCardTarget;

    private TextView bankStats;
    private NumberPicker eliteBankCount;
    private AppCompatButton writeOpenBanks;
    private AppCompatButton eraseOpenBanks;
    private LinearLayout securityOptions;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private KeyManager keyManager;

    private ArrayList<Amiibo> amiibos = new ArrayList<>();
    private SearchView searchView;
    private WriteTagAdapter writeFileAdapter;

    private int clickedPosition;
    private enum CLICKED {
        NOTHING,
        WRITE_DATA,
        EDIT_DATA,
        HEX_CODE,
        BANK_BACKUP,
        VERIFY_TAG,
        ERASE_BANK
    }
    private CLICKED status = CLICKED.NOTHING;

    private BrowserSettings settings;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_elite_bank, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootLayout = (CoordinatorLayout) view;

        BrowserActivity activity = (BrowserActivity) requireActivity();
        keyManager = new KeyManager(activity);

        amiibosView = rootLayout.findViewById(R.id.amiibos_list);
        amiibosView.setHasFixedSize(true);

        switchMenuOptions = rootLayout.findViewById(R.id.switch_menu_btn);
        bankOptionsMenu = rootLayout.findViewById(R.id.bank_options_menu);
        writeBankLayout = rootLayout.findViewById(R.id.write_list_layout);
        amiiboFilesView = rootLayout.findViewById(R.id.amiibo_files_list);
        amiiboFilesView.setHasFixedSize(true);

        securityOptions = rootLayout.findViewById(R.id.security_options);

        amiiboTile = rootLayout.findViewById(R.id.active_tile_layout);
        amiiboCard = rootLayout.findViewById(R.id.active_card_layout);
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

        bankStats = rootLayout.findViewById(R.id.bank_stats);
        eliteBankCount = rootLayout.findViewById(R.id.number_picker);
        writeOpenBanks = rootLayout.findViewById(R.id.write_open_banks);
        eraseOpenBanks = rootLayout.findViewById(R.id.erase_open_banks);

        this.settings = activity.getSettings();

        AppCompatImageView toggle = rootLayout.findViewById(R.id.toggle);
        this.bottomSheetBehavior = BottomSheetBehavior.from(rootLayout.findViewById(R.id.bottom_sheet));
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    if (writeBankLayout.getVisibility() == View.VISIBLE)
                        onBottomSheetChanged(true, false);
                    toggle.setImageResource(R.drawable.ic_expand_less_white_24dp);
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    toggle.setImageResource(R.drawable.ic_expand_more_white_24dp);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                ViewGroup mainLayout = rootLayout.findViewById(R.id.main_layout);
                if (mainLayout.getBottom() >= bottomSheet.getTop()) {
                    int bottomHeight = bottomSheet.getMeasuredHeight()
                            - bottomSheetBehavior.getPeekHeight();
                    mainLayout.setPadding(0, 0, 0, slideOffset > 0
                            ? (int) (bottomHeight * slideOffset) : 0);
                }
                if (slideOffset > 0)
                    amiibosView.smoothScrollToPosition(clickedPosition);
            }
        });

        toggle.setOnClickListener(view1 -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            } else {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        toolbar.inflateMenu(R.menu.bank_menu);

        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiibosView.setLayoutManager(new GridLayoutManager(activity, activity.getColumnCount()));
        else
            amiibosView.setLayoutManager(new LinearLayoutManager(activity));
        bankAdapter = new EliteBankAdapter(settings, this);
        amiibosView.setAdapter(bankAdapter);
        this.settings.addChangeListener(bankAdapter);

        eliteBankCount.setOnValueChangedListener((numberPicker, valueOld, valueNew) -> {
            writeOpenBanks.setText(getString(R.string.write_open_banks, valueNew));
            eraseOpenBanks.setText(getString(R.string.erase_open_banks, valueNew));
        });

        if (settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiiboFilesView.setLayoutManager(new GridLayoutManager(activity, activity.getColumnCount()));
        else
            amiiboFilesView.setLayoutManager(new LinearLayoutManager(activity));

        writeFileAdapter = new WriteTagAdapter(settings, new WriteTagAdapter.OnAmiiboClickListener() {
            @Override
            public void onAmiiboClicked(AmiiboFile amiiboFile) { }

            @Override
            public void onAmiiboImageClicked(AmiiboFile amiiboFile) { }
        });
        this.settings.addChangeListener(writeFileAdapter);

        switchMenuOptions.setOnClickListener(view1 -> {
            if (bankOptionsMenu.isShown()) {
                amiiboCard.setVisibility(View.VISIBLE);
                bankOptionsMenu.setVisibility(View.GONE);
            } else {
                bankOptionsMenu.setVisibility(View.VISIBLE);
                amiiboCard.setVisibility(View.GONE);
            }
            amiibosView.requestLayout();
        });

        searchView = rootLayout.findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) activity
                .getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(activity.getComponentName()));
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

        writeOpenBanks.setOnClickListener(view1 -> {
            onBottomSheetChanged(false, false);
            searchView.setQuery(settings.getQuery(), true);
            searchView.clearFocus();
            WriteTagAdapter writeListAdapter = new WriteTagAdapter(
                    settings, this::writeAmiiboCollection);
            writeListAdapter.resetSelections();
            this.settings.addChangeListener(writeListAdapter);
            amiiboFilesView.setAdapter(writeListAdapter);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        eraseOpenBanks.setOnClickListener(view1 -> {
            Intent collection = new Intent(activity, NfcActivity.class);
            collection.putExtra(NFCIntent.EXTRA_SIGNATURE,
                    prefs.settings_elite_signature().get());
            collection.setAction(NFCIntent.ACTION_ERASE_ALL_TAGS);
            collection.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.getValue());
            onOpenBanksActivity.launch(collection);
        });

        rootLayout.findViewById(R.id.edit_bank_count).setOnClickListener(view1 -> {
            if (prefs.eliteActiveBank().get() >= eliteBankCount.getValue()) {
                new Toasty(activity).Short(R.string.fail_active_oob);
                onBottomSheetChanged(true, false);
                return;
            }
            Intent configure = new Intent(activity, NfcActivity.class);
            configure.putExtra(NFCIntent.EXTRA_SIGNATURE,
                    prefs.settings_elite_signature().get());
            configure.setAction(NFCIntent.ACTION_SET_BANK_COUNT);
            configure.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.getValue());
            onOpenBanksActivity.launch(configure);

            onBottomSheetChanged(true, false);
        });

        view.findViewById(R.id.lock_elite).setOnClickListener(view1 ->
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.lock_elite_warning)
                        .setMessage(R.string.lock_elite_details)
                        .setPositiveButton(R.string.accept, (dialog, which) -> {
                            Intent lock = new Intent(requireContext(), NfcActivity.class);
                            lock.setAction(NFCIntent.ACTION_LOCK_AMIIBO);
                            startActivity(lock);
                            dialog.dismiss();
                        })
                        .setNegativeButton(R.string.cancel, null).show());

        view.findViewById(R.id.unlock_elite).setOnClickListener(view1 ->
                new android.app.AlertDialog.Builder(requireContext())
                        .setTitle(R.string.unlock_elite_warning)
                        .setMessage(R.string.prepare_unlock)
                        .setPositiveButton(R.string.start, (dialog, which) -> {
                            startActivity(new Intent(requireContext(), NfcActivity.class)
                                    .setAction(NFCIntent.ACTION_UNLOCK_UNIT));
                            dialog.dismiss();
                        }).show());
    }

    public RecyclerView getAmiibosView() {
        return amiibosView;
    }
    public BottomSheetBehavior<View> getBottomSheet() {
        return bottomSheetBehavior;
    }

    private void updateEliteAdapter(ArrayList<String> amiiboList) {
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

        if (null == amiiboManager) {
            return;
        }

        if (amiibos.isEmpty()) {
            bankAdapter.setAmiibos(amiibos);
            for (int x = 0; x < amiiboList.size(); x++) {
                amiibos.add(amiiboManager.amiibos.get(TagUtils.hexToLong(amiiboList.get(x))));
                bankAdapter.notifyItemInserted(x);
            }
        } else {
            for (int x = 0; x < amiiboList.size(); x++) {
                long amiiboId = TagUtils.hexToLong(amiiboList.get(x));
                if (x >= amiibos.size()) {
                    amiibos.add(amiiboManager.amiibos.get(TagUtils.hexToLong(amiiboList.get(x))));
                    bankAdapter.notifyItemInserted(x);
                } else if (null == amiibos.get(x) || amiibos.get(x).index != x
                        || amiiboId != amiibos.get(x).id) {
                    amiibos.set(x, amiiboManager.amiibos.get(amiiboId));
                    bankAdapter.notifyItemChanged(x);
                }
            }
            if (amiibos.size() > amiiboList.size()) {
                int count = amiibos.size();
                int size = amiiboList.size();
                ArrayList<Amiibo> shortList = new ArrayList<>();
                for (int x = 0; x < size; x++) {
                    shortList.add(amiibos.get(x));
                }
                amiibos = new ArrayList<>(shortList);
                bankAdapter.notifyItemRangeChanged(0, size);
                bankAdapter.notifyItemRangeRemoved(size, count - size);
            }
        }
    }

    private void onBottomSheetChanged(boolean isMenu, boolean hasAmiibo) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        amiiboCard.setVisibility(isMenu && hasAmiibo ? View.VISIBLE : View.GONE);
        switchMenuOptions.setVisibility(isMenu && hasAmiibo ? View.VISIBLE : View.GONE);
        bankOptionsMenu.setVisibility(isMenu && !hasAmiibo ? View.VISIBLE : View.GONE);
        securityOptions.setVisibility(isMenu || hasAmiibo ? View.VISIBLE : View.GONE);
        writeBankLayout.setVisibility(isMenu || hasAmiibo ? View.GONE : View.VISIBLE);
    }

    private final ActivityResultLauncher<Intent> onActivateActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        int active_bank = result.getData().getIntExtra(NFCIntent.EXTRA_ACTIVE_BANK,
                prefs.eliteActiveBank().get());

        bankAdapter.notifyItemChanged(prefs.eliteActiveBank().get());
        bankAdapter.notifyItemChanged(active_bank);

        prefs.eliteActiveBank().put(active_bank);
        updateAmiiboView(amiiboTile, null, amiibos.get(active_bank).id, active_bank);

        int bank_count = prefs.eliteBankCount().get();
        bankStats.setText(getString(R.string.bank_stats,
                getValueForPosition(eliteBankCount, active_bank), bank_count));
        writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
        eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));
    });

    private final ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !NFCIntent.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;

        if (result.getData().hasExtra(NFCIntent.EXTRA_CURRENT_BANK)) {
            clickedPosition = result.getData().getIntExtra(
                    NFCIntent.EXTRA_CURRENT_BANK, clickedPosition);
        }

        byte[] tagData = null != amiibos.get(clickedPosition)
                ? amiibos.get(clickedPosition).data : null;

        if (result.getData().hasExtra(NFCIntent.EXTRA_TAG_DATA)) {
            tagData = result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
            if (null != amiibos.get(clickedPosition)) amiibos.get(clickedPosition).data = tagData;
        }

        if (result.getData().hasExtra(NFCIntent.EXTRA_AMIIBO_LIST)) {
            updateEliteAdapter(result.getData().getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST));
        }

        updateAmiiboView(amiiboCard, tagData, -1, clickedPosition);
        int active_bank = prefs.eliteActiveBank().get();
        if (result.getData().hasExtra(NFCIntent.EXTRA_ACTIVE_BANK)) {
            active_bank = result.getData().getIntExtra(NFCIntent.EXTRA_ACTIVE_BANK, active_bank);
            prefs.eliteActiveBank().put(active_bank);
        }
        updateAmiiboView(amiiboTile, null, amiibos.get(active_bank).id, active_bank);

        if (status == CLICKED.ERASE_BANK) {
            status = CLICKED.NOTHING;
            onBottomSheetChanged(true, false);
            amiibos.set(clickedPosition, null);
        }
    });

    private final ActivityResultLauncher<Intent> onScanTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
        int current_bank = result.getData().getIntExtra(NFCIntent.EXTRA_CURRENT_BANK, clickedPosition);

        Bundle args = new Bundle();
        args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
        if (null != amiibos.get(current_bank))
            amiibos.get(current_bank).data = tagData;
        switch (status) {
            case NOTHING:
                break;
            case WRITE_DATA:
                Intent modify = new Intent(requireContext(), NfcActivity.class);
                modify.putExtra(NFCIntent.EXTRA_SIGNATURE,
                        prefs.settings_elite_signature().get());
                modify.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                modify.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank);
                onUpdateTagResult.launch(modify.putExtras(args));
                break;
            case EDIT_DATA:
                onUpdateTagResult.launch(new Intent(requireContext(),
                        TagDataEditor.class).putExtras(args));
                break;
            case HEX_CODE:
                onUpdateTagResult.launch(new Intent(requireContext(),
                        HexCodeViewer.class).putExtras(args));
                break;
            case BANK_BACKUP:
                displayBackupDialog(tagData);
                break;
            case VERIFY_TAG:
                try {
                    TagUtils.validateData(tagData);
                    new Toasty(requireActivity()).Dialog(R.string.validation_success);
                } catch (Exception e) {
                    new Toasty(requireActivity()).Dialog(e.getMessage());
                }
                break;

        }
        status = CLICKED.NOTHING;
        updateAmiiboView(amiiboCard, tagData, -1, current_bank);
        bankAdapter.notifyItemChanged(current_bank);
    });

    private void scanAmiiboBank(int current_bank) {
        Intent scan = new Intent(requireContext(), NfcActivity.class);
        scan.putExtra(NFCIntent.EXTRA_SIGNATURE,
                prefs.settings_elite_signature().get());
        scan.setAction(NFCIntent.ACTION_SCAN_TAG);
        scan.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank);
        onScanTagResult.launch(scan);
    }

    private void scanAmiiboTag(int position) {
        Intent amiiboIntent = new Intent(requireContext(), NfcActivity.class);
        amiiboIntent.putExtra(NFCIntent.EXTRA_SIGNATURE,
                prefs.settings_elite_signature().get());
        amiiboIntent.putExtra(NFCIntent.EXTRA_CURRENT_BANK, position);
        amiiboIntent.setAction(NFCIntent.ACTION_SCAN_TAG);
        onUpdateTagResult.launch(amiiboIntent);
    }

    private void writeAmiiboFile(AmiiboFile amiiboFile, int position) {
        Bundle args = new Bundle();
        if (((BrowserActivity) requireActivity()).isDocumentStorage()) {
            try {
                byte[] data = null != amiiboFile.getData() ? amiiboFile.getData()
                        : TagUtils.getValidatedDocument(keyManager, amiiboFile.getDocUri());
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data);
            } catch (Exception e) {
                Debug.Log(e);
            }
        } else {
            try {
                byte[] data = null != amiiboFile.getData() ? amiiboFile.getData()
                        : TagUtils.getValidatedFile(keyManager, amiiboFile.getFilePath());
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, data);
            } catch (Exception e) {
                Debug.Log(e);
            }
        }

        Intent intent = new Intent(requireContext(), NfcActivity.class);
        intent.putExtra(NFCIntent.EXTRA_SIGNATURE,
                prefs.settings_elite_signature().get());
        intent.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
        intent.putExtra(NFCIntent.EXTRA_CURRENT_BANK, position);
        intent.putExtras(args);
        onUpdateTagResult.launch(intent);
        onBottomSheetChanged(true, true);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void displayWriteDialog(int position) {
        onBottomSheetChanged(false, false);
        searchView.setQuery(settings.getQuery(), true);
        searchView.clearFocus();
        writeFileAdapter.setListener(new WriteTagAdapter.OnAmiiboClickListener() {
            @Override
            public void onAmiiboClicked(AmiiboFile amiiboFile) {
                if (null != amiiboFile) {
                    writeAmiiboFile(amiiboFile, position);
                }
            }

            @Override
            public void onAmiiboImageClicked(AmiiboFile amiiboFile) {
                handleImageClicked(amiiboFile);
            }
        });
        amiiboFilesView.setAdapter(writeFileAdapter);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void displayBackupDialog(byte[] tagData) {
        View view = getLayoutInflater().inflate(R.layout.dialog_backup, null);
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        final EditText input = view.findViewById(R.id.backup_entry);
        input.setText(TagUtils.decipherFilename(settings.getAmiiboManager(), tagData, true));
        Dialog backupDialog = dialog.setView(view).create();
        view.findViewById(R.id.save_backup).setOnClickListener(v -> {
            try {
                String fileName;
                BrowserActivity activity = (BrowserActivity) requireActivity();
                if (activity.isDocumentStorage()) {
                    DocumentFile rootDocument = DocumentFile.fromTreeUri(requireContext(),
                            this.settings.getBrowserRootDocument());
                    if (null == rootDocument) throw new NullPointerException();
                    fileName = TagUtils.writeBytesToDocument(requireContext(), rootDocument,
                            input.getText().toString() + ".bin", tagData);
                } else {
                    fileName = TagUtils.writeBytesToFile(
                            Storage.getDownloadDir("TagMo", "Backups"),
                            input.getText().toString(), tagData);
                }
                new Toasty(requireActivity()).Long(getString(R.string.wrote_file, fileName));
                activity.loadStoredAmiibo();
            } catch (IOException e) {
                new Toasty(requireActivity()).Short(e.getMessage());
            }
            backupDialog.dismiss();
        });
        view.findViewById(R.id.cancel_backup).setOnClickListener(v -> backupDialog.dismiss());
        backupDialog.show();
    }

    private void setAmiiboInfoText(TextView textView, CharSequence text, boolean hasTagInfo) {
        if (hasTagInfo) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            if (text.length() == 0) {
                textView.setText(getString(R.string.unknown));
                textView.setEnabled(false);
            } else {
                textView.setText(text);
                textView.setText(text);
                textView.setEnabled(true);
            }
        }
    }

    private void getAmiiboToolbar( byte[] tagData, int current_bank) {
        toolbar.setOnMenuItemClickListener(item -> {
            Toasty notice = new Toasty(requireActivity());
            Intent scan = new Intent(requireContext(), NfcActivity.class);
            scan.putExtra(NFCIntent.EXTRA_SIGNATURE,
                    prefs.settings_elite_signature().get());
            scan.putExtra(NFCIntent.EXTRA_CURRENT_BANK, current_bank);
            if (item.getItemId() == R.id.mnu_activate) {
                scan.setAction(NFCIntent.ACTION_ACTIVATE_BANK);
                onActivateActivity.launch(scan);
                return true;
            } else if (item.getItemId() == R.id.mnu_replace) {
                displayWriteDialog(current_bank);
                return true;
            } else if (item.getItemId() == R.id.mnu_write) {
                if (null != tagData && tagData.length > 0) {
                    scan.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                    scan.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                    onUpdateTagResult.launch(scan);
                } else {
                    status = CLICKED.WRITE_DATA;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_erase_bank) {
                if (prefs.eliteActiveBank().get() == current_bank) {
                    notice.Short(R.string.erase_active);
                } else {
                    scan.setAction(NFCIntent.ACTION_ERASE_BANK);
                    onUpdateTagResult.launch(scan);
                    status = CLICKED.ERASE_BANK;
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_edit) {
                if (null != tagData && tagData.length > 0) {
                    Intent editor = new Intent(requireContext(), TagDataEditor.class);
                    editor.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                    onUpdateTagResult.launch(editor);
                } else {
                    status = CLICKED.EDIT_DATA;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                if (null != tagData && tagData.length > 0) {
                    Intent viewhex = new Intent(requireContext(), HexCodeViewer.class);
                    viewhex.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                    startActivity(viewhex);
                } else {
                    status = CLICKED.HEX_CODE;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_backup) {
                if (null != tagData && tagData.length > 0) {
                    displayBackupDialog(tagData);
                } else {
                    status = CLICKED.BANK_BACKUP;
                    scanAmiiboBank(current_bank);
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
                if (null != tagData && tagData.length > 0) {
                    try {
                        TagUtils.validateData(tagData);
                        notice.Dialog(R.string.validation_success);
                    } catch (Exception e) {
                        notice.Dialog(e.getMessage());
                    }
                } else {
                    status = CLICKED.VERIFY_TAG;
                    scanAmiiboBank(current_bank);
                }
                return true;
            }
            return false;
        });
    }

    private void updateAmiiboView(View amiiboView, byte[] tagData, long amiiboId, int current_bank) {
        View amiiboInfo = rootLayout.findViewById(R.id.amiiboInfo);
        TextView txtError = rootLayout.findViewById(R.id.txtError);
        TextView txtName = amiiboView.findViewById(R.id.txtName);
        TextView txtBank = amiiboView.findViewById(R.id.txtBank);
        TextView txtTagId = amiiboView.findViewById(R.id.txtTagId);
        TextView txtAmiiboSeries = amiiboView.findViewById(R.id.txtAmiiboSeries);
        TextView txtAmiiboType = amiiboView.findViewById(R.id.txtAmiiboType);
        TextView txtGameSeries = amiiboView.findViewById(R.id.txtGameSeries);
        AppCompatImageView imageAmiibo = amiiboView.findViewById(R.id.imageAmiibo);

        if (amiiboView == amiiboCard) getAmiiboToolbar(tagData, current_bank);

        String tagInfo = null;
        String amiiboHexId = "";
        String amiiboName = "";
        String amiiboSeries = "";
        String amiiboType = "";
        String gameSeries = "";
        String amiiboImageUrl;

        Amiibo amiibo = amiibos.get(current_bank);
        if (null == amiibo) {
            if (null != tagData && tagData.length > 0) {
                try {
                    amiiboId = TagUtils.amiiboIdFromTag(tagData);
                } catch (Exception e) {
                    Debug.Log(e);
                }
            }
            AmiiboManager amiiboManager = settings.getAmiiboManager();
            if (amiiboId == -1) {
                tagInfo = getString(R.string.read_error);
            } else if (amiiboId == 0) {
                tagInfo = getString(R.string.blank_tag);
            } else if (null != amiiboManager) {
                amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null == amiibo)
                    amiibo = new Amiibo(amiiboManager, amiiboId, null, null);
            }
        }

        if (null != amiibo) {
            amiiboView.setVisibility(View.VISIBLE);
            amiiboImageUrl = amiibo.getImageUrl();
            amiiboHexId = TagUtils.amiiboIdToHex(amiibo.id);
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

        boolean hasTagInfo = null != tagInfo;

        if (hasTagInfo) {
            setAmiiboInfoText(txtError, tagInfo, false);
            amiiboInfo.setVisibility(View.GONE);
        } else {
            txtError.setVisibility(View.GONE);
            amiiboInfo.setVisibility(View.VISIBLE);
        }
        if (null != txtBank) setAmiiboInfoText(txtBank, getString(R.string.bank_number,
                getValueForPosition(eliteBankCount, current_bank)), hasTagInfo);
        setAmiiboInfoText(txtName, amiiboName, hasTagInfo);
        setAmiiboInfoText(txtTagId, amiiboHexId, hasTagInfo);
        setAmiiboInfoText(txtAmiiboSeries, amiiboSeries, hasTagInfo);
        setAmiiboInfoText(txtAmiiboType, amiiboType, hasTagInfo);
        setAmiiboInfoText(txtGameSeries, gameSeries, hasTagInfo);

        if (amiiboView == amiiboTile && null == amiiboImageUrl) {
            imageAmiibo.setImageResource(R.mipmap.ic_launcher_round);
            imageAmiibo.setVisibility(View.VISIBLE);
        } else if (null != imageAmiibo) {
            GlideApp.with(this).clear(amiiboView == amiiboCard
                    ? amiiboCardTarget : amiiboTileTarget);
            if (null != amiiboImageUrl) {
                GlideApp.with(this).asBitmap().load(amiiboImageUrl).into(
                        amiiboView == amiiboCard ? amiiboCardTarget : amiiboTileTarget);
                final long amiiboTagId = amiiboId;
                imageAmiibo.setOnClickListener(view -> {
                    if (amiiboTagId == -1) {
                        return;
                    }

                    Bundle bundle = new Bundle();
                    bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiiboTagId);

                    Intent intent = new Intent(requireContext(), ImageActivity.class);
                    intent.putExtras(bundle);

                    startActivity(intent);
                });
            }
        }
    }

    private final ActivityResultLauncher<Intent> onOpenBanksActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

                if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())) return;

                int bank_count = result.getData().getIntExtra(NFCIntent.EXTRA_BANK_COUNT,
                        prefs.eliteBankCount().get());

                prefs.eliteBankCount().put(bank_count);

                eliteBankCount.setValue(bank_count);
                updateEliteAdapter(result.getData().getStringArrayListExtra(NFCIntent.EXTRA_AMIIBO_LIST));
                bankStats.setText(getString(R.string.bank_stats, getValueForPosition(
                        eliteBankCount, prefs.eliteActiveBank().get()), bank_count)
                );
                writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
                eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));
            });

    private void writeAmiiboCollection(ArrayList<AmiiboFile> amiiboList) {
        if (null != amiiboList && amiiboList.size() == eliteBankCount.getValue()) {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.write_confirm)
                    .setPositiveButton(R.string.proceed, (dialog, which) -> {
                        Intent collection = new Intent(requireContext(), NfcActivity.class);
                        collection.putExtra(NFCIntent.EXTRA_SIGNATURE,
                                prefs.settings_elite_signature().get());
                        collection.setAction(NFCIntent.ACTION_WRITE_ALL_TAGS);
                        collection.putExtra(NFCIntent.EXTRA_BANK_COUNT, eliteBankCount.getValue());
                        collection.putExtra(NFCIntent.EXTRA_AMIIBO_FILES, amiiboList);
                        onOpenBanksActivity.launch(collection);
                        onBottomSheetChanged(true, false);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        amiiboList.clear();
                        onBottomSheetChanged(true, false);
                        dialog.dismiss();
                    })
                    .show();
        }
    }

    public int getValueForPosition(NumberPicker picker, int value) {
        return value + picker.getMinValue();
    }

    private void onHardwareLoaded() {
        try {
            int bank_count = requireArguments().getInt(NFCIntent.EXTRA_BANK_COUNT,
                    prefs.eliteBankCount().get());
            int active_bank = requireArguments().getInt(NFCIntent.EXTRA_ACTIVE_BANK,
                    prefs.eliteActiveBank().get());

            setBottomSheetSecure(false);

            ((TextView) rootLayout.findViewById(R.id.hardware_info)).setText(getString(
                    R.string.elite_signature, requireArguments().getString(NFCIntent.EXTRA_SIGNATURE)
            ));
            eliteBankCount.setValue(bank_count);

            updateEliteAdapter(requireArguments().getStringArrayList(NFCIntent.EXTRA_AMIIBO_LIST));
            bankStats.setText(getString(R.string.bank_stats,
                    getValueForPosition(eliteBankCount, active_bank), bank_count));
            writeOpenBanks.setText(getString(R.string.write_open_banks, bank_count));
            eraseOpenBanks.setText(getString(R.string.erase_open_banks, bank_count));

            if (null == amiibos.get(active_bank)) {
                onBottomSheetChanged(true, false);
            } else {
                updateAmiiboView(amiiboCard, null, amiibos.get(active_bank).id, active_bank);
                updateAmiiboView(amiiboTile, null, amiibos.get(active_bank).id, active_bank);
                onBottomSheetChanged(true, true);
                new Handler(Looper.getMainLooper()).postDelayed(() -> bottomSheetBehavior
                        .setState(BottomSheetBehavior.STATE_EXPANDED), TagMo.uiDelay);
            }

            setArguments(null);
        } catch (Exception ignored) {
            if (amiibos.isEmpty()) setBottomSheetSecure(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (prefs.enable_elite_support().get()) {
            onHardwareLoaded();
        } else {
            setBottomSheetSecure(true);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Snackbar compatNotice = new IconifiedSnackbar(getActivity()).buildSnackbar(
                        R.string.enable_elite,
                        R.drawable.ic_settings_24dp, Snackbar.LENGTH_LONG
                );
                compatNotice.setAction(R.string.enable, v -> {
                    prefs.enable_elite_support().put(true);
                    onHardwareLoaded();
                });
                compatNotice.show();
            }, TagMo.uiDelay);
        }

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
    public void onAmiiboClicked(Amiibo amiibo, int position) {
        if (null == amiibo) {
            displayWriteDialog(position);
            return;
        }
        clickedPosition = position;
        status = CLICKED.NOTHING;
        onBottomSheetChanged(true, true);
        if (null != amiibo.data  && amiibo.index == position) {
            updateAmiiboView(amiiboCard, amiibo.data, -1, position);
        } else if (amiibo.id != 0) {
            updateAmiiboView(amiiboCard, null, amiibo.id, position);
        } else {
            scanAmiiboTag(position);
        }
        this.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onAmiiboImageClicked(Amiibo amiibo, int position) {
        handleImageClicked(amiibo);
    }

    @Override
    public boolean onAmiiboLongClicked(Amiibo amiibo, int position) {
        if (null != amiibo )
            scanAmiiboTag(position);
        else
            displayWriteDialog(position);
        return true;
    }

    private void setBottomSheetSecure(boolean hidden) {
        bankStats.setVisibility(hidden ? View.GONE : View.VISIBLE);
        amiiboCard.setVisibility(hidden ? View.GONE : View.VISIBLE);
        bankOptionsMenu.setVisibility(hidden ? View.GONE : View.VISIBLE);
        switchMenuOptions.setVisibility(hidden ? View.GONE : View.VISIBLE);
    }
}

