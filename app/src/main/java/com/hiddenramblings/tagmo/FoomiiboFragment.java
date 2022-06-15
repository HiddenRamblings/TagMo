package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.GamesManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.eightbit.Foomiibo;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.widget.Toasty;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class FoomiiboFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener,
        FoomiiboAdapter.OnFoomiiboClickListener{

    private final Foomiibo foomiibo = new Foomiibo();
    private File directory;
    private RecyclerView amiibosView;
    private SwipeRefreshLayout swipeRefreshLayout;

    private KeyManager keyManager;
    private BrowserSettings settings;
    private boolean ignoreTagId;

    private final ArrayList<byte[]> resultData = new ArrayList<>();

    private final ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || null == result.getData()) return;

        if (!NFCIntent.ACTION_NFC_SCANNED.equals(result.getData().getAction())
                && !NFCIntent.ACTION_UPDATE_TAG.equals(result.getData().getAction())
                && !NFCIntent.ACTION_EDIT_COMPLETE.equals(result.getData().getAction())) return;

        byte[] tagData = result.getData().getByteArrayExtra(NFCIntent.EXTRA_TAG_DATA);
        if (null != tagData && tagData.length > 0) {
            boolean updated = false;
            for (byte[] data : resultData) {
                try {
                    if (data.length > 0 && TagUtils.amiiboIdFromTag(data) ==
                            TagUtils.amiiboIdFromTag(tagData)) {
                        updated = true;
                        resultData.set(resultData.indexOf(data), tagData);
                        break;
                    }
                } catch (Exception ignored) { }
            }
            if (!updated) resultData.add(tagData);
        }
    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_foomiibo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        directory = new File(requireActivity().getFilesDir(), "Foomiibo");
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();

        BrowserActivity activity = (BrowserActivity) requireActivity();
        keyManager = new KeyManager(activity);
        this.settings = activity.getSettings();

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.swipeRefreshLayout.setProgressViewOffset(false, 0, (int) getResources().getDimension(R.dimen.swipe_progress_end));

        amiibosView = view.findViewById(R.id.amiibos_list);
        amiibosView.setHasFixedSize(true);
        ArrayList<Long> missingIds = activity.getMissingIds();
        amiibosView.setLayoutManager(settings.getAmiiboView()
                == BrowserSettings.VIEW.IMAGE.getValue()
                ? new GridLayoutManager(activity, activity.getColumnCount())
                : new LinearLayoutManager(activity));
        amiibosView.setAdapter(new FoomiiboAdapter(settings, missingIds, this));
        this.settings.addChangeListener((BrowserSettings.BrowserSettingsListener) amiibosView.getAdapter());
    }

    public RecyclerView getAmiibosView() {
        return amiibosView;
    }

    private void deleteDir(Handler handler, ProgressDialog dialog, File dir) {
        if (!directory.exists()) return;
        File[] files = dir.listFiles();
        if (null != files && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (null != handler) {
                        handler.post(() -> dialog.setMessage(getString(
                                R.string.foomiibo_removing, file.getName())));
                    }
                    deleteDir(handler, dialog, file);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    void clearFoomiiboSet() {
        ProgressDialog dialog = ProgressDialog.show(requireActivity(),
                "", "", true);
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            deleteDir(handler, dialog, directory);

            handler.post(dialog::dismiss);
            onRefresh();
        });
    }

    private void buildFoomiiboFile(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, TagUtils.decipherFilename(
                    settings.getAmiiboManager(), tagData, false), tagData);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    private void buildFoomiiboFile(byte[] tagData) {
        try {
            Amiibo amiibo = settings.getAmiiboManager().amiibos
                    .get(TagUtils.amiiboIdFromTag(tagData));
            if (null == amiibo) return;
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, TagUtils.decipherFilename(
                    settings.getAmiiboManager(), tagData, false), tagData);
            new IconifiedSnackbar(requireActivity(), amiibosView).buildSnackbar(
                    getString(R.string.wrote_foomiibo, amiibo.name), Snackbar.LENGTH_SHORT
            ).show();
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    private void deleteFoomiiboFile(byte[] tagData) {
        try {
            Amiibo amiibo = settings.getAmiiboManager().amiibos
                    .get(TagUtils.amiiboIdFromTag(tagData));
            if (amiibo == null) throw new Exception();
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            File amiiboFile = new File(directory,
                    TagUtils.decipherFilename(settings.getAmiiboManager(), tagData, false));
            new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.warn_delete_file, amiiboFile.getName()))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        if (!amiiboFile.delete())
                            new Toasty(requireActivity()).Short(R.string.delete_virtual);
                        else
                            new IconifiedSnackbar(requireActivity(), amiibosView).buildSnackbar(
                                    getString(R.string.delete_foomiibo, amiibo.name),
                                    Snackbar.LENGTH_SHORT
                            ).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
        } catch (Exception e) {
            new Toasty(requireActivity()).Short(R.string.delete_virtual);
        }
    }

    void buildFoomiiboSet() {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;
        ProgressDialog dialog = ProgressDialog.show(requireActivity(),
                "", "", true);
        Handler handler = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {
            deleteDir(null, null, directory);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();

            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                buildFoomiiboFile(amiibo);
                handler.post(() -> dialog.setMessage(getString(
                        R.string.foomiibo_progress, amiibo.getCharacter().name)));
            }

            handler.post(dialog::dismiss);
            onRefresh();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        this.onRefresh();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onRefresh() {
        BrowserActivity activity = (BrowserActivity) requireActivity();
        activity.onRootFolderChanged(false);
        ArrayList<Long> missingIds = ((BrowserActivity) requireActivity()).getMissingIds();
        activity.runOnUiThread(() -> {
            if (null != amiibosView.getAdapter()) {
                ((FoomiiboAdapter) amiibosView.getAdapter()).setMissingIds(missingIds);
                amiibosView.getAdapter().notifyDataSetChanged();
            }
            swipeRefreshLayout.setRefreshing(false);
        });
    }

    void getToolbarOptions(Toolbar toolbar, byte[] tagData) {
        if (!toolbar.getMenu().hasVisibleItems())
            toolbar.inflateMenu(R.menu.amiibo_menu);
        toolbar.getMenu().findItem(R.id.mnu_scan).setVisible(false);
        toolbar.setOnMenuItemClickListener(item -> {
            Bundle args = new Bundle();
            Intent scan = new Intent(requireContext(), NfcActivity.class);
            if (item.getItemId() == R.id.mnu_write) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_FULL);
                onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_update) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                scan.setAction(NFCIntent.ACTION_WRITE_TAG_DATA);
                scan.putExtra(NFCIntent.EXTRA_IGNORE_TAG_ID, ignoreTagId);
                onUpdateTagResult.launch(scan.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_save) {
                buildFoomiiboFile(tagData);
                return true;
            } else if (item.getItemId() == R.id.mnu_edit) {
                args.putByteArray(NFCIntent.EXTRA_TAG_DATA, tagData);
                Intent tagEdit = new Intent(requireContext(), TagDataActivity.class);
                onUpdateTagResult.launch(tagEdit.putExtras(args));
                return true;
            } else if (item.getItemId() == R.id.mnu_view_hex) {
                Intent hexView = new Intent(requireContext(), HexViewerActivity.class);
                hexView.putExtra(NFCIntent.EXTRA_TAG_DATA, tagData);
                startActivity(hexView);
                return true;
            } else if (item.getItemId() == R.id.mnu_validate) {
                try {
                    TagUtils.validateData(tagData);
                    new IconifiedSnackbar(requireActivity(), amiibosView).buildSnackbar(
                            R.string.validation_success, Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    new IconifiedSnackbar(requireActivity(), amiibosView).buildSnackbar(e.getMessage(),
                            R.drawable.ic_baseline_bug_report_24dp, Snackbar.LENGTH_LONG).show();
                }
                return true;
            } else if (item.getItemId() == R.id.mnu_delete) {
                deleteFoomiiboFile(tagData);
                return true;
            } else if (item.getItemId() == R.id.mnu_ignore_tag_id) {
                ignoreTagId = !item.isChecked();
                item.setChecked(ignoreTagId);
                return true;
            }
            return false;
        });
    }

    private void getGameCompatibility(TextView txtUsage, byte[] tagData) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long amiiboId = TagUtils.amiiboIdFromTag(tagData);
                GamesManager gamesManager = GamesManager.getGamesManager(requireContext());
                String usage = gamesManager.getGamesCompatibility(amiiboId);
                requireActivity().runOnUiThread(() -> txtUsage.setText(usage));
            } catch (Exception ex) {
                Debug.Log(ex);
            }
        });
    }

    public void onFoomiiboClicked(View itemView, Amiibo amiibo) {
        byte[] tagData = new byte[0];
        for (byte[] data : resultData) {
            try {
                if (data.length > 0 && TagUtils.amiiboIdFromTag(data) == amiibo.id) {
                    tagData = data;
                    break;
                }
            } catch (Exception ignored) { }
        }
        if (tagData.length == 0)
            tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
        try {
            tagData = TagUtils.getValidatedData(keyManager, tagData);
        } catch (Exception ignored) { }

        if (settings.getAmiiboView() != BrowserSettings.VIEW.IMAGE.getValue()) {
            LinearLayout menuOptions = itemView.findViewById(R.id.menu_options);
            Toolbar toolbar = menuOptions.findViewById(R.id.toolbar);
            if (menuOptions.getVisibility() == View.VISIBLE) {
                menuOptions.setVisibility(View.GONE);
            } else {
                menuOptions.setVisibility(View.VISIBLE);
                getToolbarOptions(toolbar, tagData);
            }
            TextView txtUsage = itemView.findViewById(R.id.txtUsage);
            if (txtUsage.getVisibility() == View.VISIBLE) {
                txtUsage.setVisibility(View.GONE);
            } else {
                txtUsage.setVisibility(View.VISIBLE);
                getGameCompatibility(txtUsage, tagData);
            }
        } else {
            ((BrowserActivity) requireActivity()).updateAmiiboView(tagData, null);
        }
    }

    @Override
    public void onFoomiiboRebind(View itemView, Amiibo amiibo) {
        byte[] tagData = new byte[0];
        for (byte[] data : resultData) {
            try {
                if (data.length > 0 && TagUtils.amiiboIdFromTag(data) == amiibo.id) {
                    tagData = data;
                    break;
                }
            } catch (Exception ignored) { }
        }
        if (tagData.length == 0)
            tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
        try {
            tagData = TagUtils.getValidatedData(keyManager, tagData);
        } catch (Exception ignored) { }

        if (settings.getAmiiboView() != BrowserSettings.VIEW.IMAGE.getValue()) {
            getToolbarOptions(itemView.findViewById(R.id.menu_options)
                    .findViewById(R.id.toolbar), tagData);
            getGameCompatibility(itemView.findViewById(R.id.txtUsage), tagData);
        }
    }

    @Override
    public void onFoomiiboImageClicked(Amiibo amiibo) {
        Bundle bundle = new Bundle();
        bundle.putLong(NFCIntent.EXTRA_AMIIBO_ID, amiibo.id);

        Intent intent = new Intent(requireContext(), ImageActivity.class);
        intent.putExtras(bundle);

        this.startActivity(intent);
    }
}

