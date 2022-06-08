package com.hiddenramblings.tagmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.appcompat.widget.SearchView;
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
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.nfctech.TagUtils;
import com.hiddenramblings.tagmo.settings.BrowserSettings;
import com.hiddenramblings.tagmo.widget.Toasty;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;

public class FoomiiboFragment extends Fragment implements
        SwipeRefreshLayout.OnRefreshListener,
        FoomiiboAdapter.OnFoomiiboClickListener{

    private final Foomiibo foomiibo = new Foomiibo();
    private final File directory = Storage.getDownloadDir("TagMo", "Foomiibo");
    private RecyclerView amiibosView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressDialog dialog;

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

        keyManager = new KeyManager(requireContext());
        this.settings = new BrowserSettings().initialize();

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.swipeRefreshLayout.setProgressViewOffset(false, 0, (int) getResources().getDimension(R.dimen.swipe_progress_end));

        amiibosView = view.findViewById(R.id.amiibos_list);
        amiibosView.setHasFixedSize(true);
        ArrayList<Long> missingIds = ((BrowserActivity) requireActivity()).getMissingIds();
        if (this.settings.getAmiiboView() == BrowserSettings.VIEW.IMAGE.getValue())
            amiibosView.setLayoutManager(new GridLayoutManager(requireActivity(), getColumnCount()));
        else
            amiibosView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        amiibosView.setAdapter(new FoomiiboAdapter(settings, missingIds, this));
        this.settings.addChangeListener((BrowserSettings.BrowserSettingsListener) amiibosView.getAdapter());

        SearchView searchView = view.findViewById(R.id.amiibo_search);
        SearchManager searchManager = (SearchManager) requireContext().getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(requireActivity().getComponentName()));
        searchView.setSubmitButtonEnabled(false);
        searchView.setIconifiedByDefault(false);
        LinearLayout searchBar = searchView.findViewById(R.id.search_bar);
        searchBar.getLayoutParams().height = (int)
                getResources().getDimension(R.dimen.button_height_min);
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

//        view.findViewById(R.id.clear_foomiibo_set).setOnClickListener(
//                clickView -> clearFoomiiboSet(directory)
//        );
//
//        view.findViewById(R.id.build_foomiibo_set).setOnClickListener(
//                clickView -> buildFoomiiboSet()
//        );

        clearFoomiiboSet(directory);
    }

//    private String decipherFilename(AmiiboManager amiiboManager, byte[] tagData) {
//        try {
//            long amiiboId = TagUtils.amiiboIdFromTag(tagData);
//            String name = TagUtils.amiiboIdToHex(amiiboId);
//            if (null != amiiboManager) {
//                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
//                if (null != amiibo && null != amiibo.name) {
//                    name = amiibo.name.replace(File.separatorChar, '-');
//                }
//            }
//
//            byte[] uid = Arrays.copyOfRange(tagData, 0, 9);
//            String uidHex = TagUtils.bytesToHex(uid);
//            return String.format(Locale.ROOT, "%1$s[%2$s]-Foomiibo.bin", name, uidHex);
//        } catch (Exception e) {
//            Debug.Log(TagUtils.class, e.getMessage());
//        }
//        return "";
//    }

    private int getColumnCount() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager mWindowManager = (WindowManager)
                requireContext().getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        else
            mWindowManager.getDefaultDisplay().getMetrics(metrics);
        return (int) ((metrics.widthPixels / metrics.density) / 112 + 0.5);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteDir(Handler handler, File dir) {
        if (!directory.exists()) return;
        File[] files = dir.listFiles();
        if (null != files && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (null != handler) {
                        handler.post(() -> dialog.setMessage(getString(
                                R.string.foomiibo_removing, file.getName())));
                    }
                    deleteDir(handler, file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    private void clearFoomiiboSet(File directory) {
        Handler handler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            handler.post(() -> dialog = ProgressDialog.show(requireActivity(),
                    "", "", true));

            deleteDir(handler, directory);

            handler.post(() -> {
                dialog.dismiss();
                onRefresh();
            });
        });
    }

//    private void buildFoomiiboFile(Amiibo amiibo) {
//        try {
//            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
//            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
//            //noinspection ResultOfMethodCallIgnored
//            directory.mkdirs();
//            TagUtils.writeBytesToFile(directory, decipherFilename(
//                    settings.getAmiiboManager(), tagData), tagData);
//        } catch (Exception e) {
//            Debug.Log(e);
//        }
//    }

//    private void buildFoomiiboSet() {
//        AmiiboManager amiiboManager = settings.getAmiiboManager();
//        if (null == amiiboManager) return;
//        Handler handler = new Handler(Looper.getMainLooper());
//
//        Executors.newSingleThreadExecutor().execute(() -> {
//            handler.post(() -> dialog = ProgressDialog.show(requireActivity(),
//                    "", "", true));
//
//            FoomiiboAdapter foomiiboAdapter = (FoomiiboAdapter) amiibosView.getAdapter();
//            ArrayList<Amiibo> foomiibo = new ArrayList<>();
//            if (null != foomiiboAdapter)
//                foomiibo = foomiiboAdapter.getFoomiiboQueue();
//            if (foomiibo.isEmpty()) {
//                foomiibo.addAll(amiiboManager.amiibos.values());
//                deleteDir(null, directory);
//                //noinspection ResultOfMethodCallIgnored
//                directory.mkdirs();
//            }
//
//            for (Amiibo amiibo : foomiibo) {
//                buildFoomiiboFile(amiibo);
//                handler.post(() -> dialog.setMessage(getString(
//                        R.string.foomiibo_progress, amiibo.getCharacter().name)));
//            }
//
//            handler.post(() -> {
//                dialog.dismiss();
//                onRefresh();
//            });
//        });
//    }

    @Override
    public void onResume() {
        super.onResume();
        this.onRefresh();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onRefresh() {
        ((BrowserActivity) requireActivity()).onRootFolderChanged(false);
        Executors.newSingleThreadExecutor().execute(() -> {
            AmiiboManager amiiboManager;
            try {
                amiiboManager = AmiiboManager.getAmiiboManager(
                        requireContext().getApplicationContext()
                );
            } catch (IOException | JSONException | ParseException e) {
                Debug.Log(e);
                amiiboManager = null;
                new Toasty(requireActivity()).Short(R.string.amiibo_info_parse_error);
            }

            if (Thread.currentThread().isInterrupted()) return;

            final AmiiboManager uiAmiiboManager = amiiboManager;
            requireActivity().runOnUiThread(() -> {
                settings.setAmiiboManager(uiAmiiboManager);
                settings.notifyChanges();
            });
        });
        ArrayList<Long> missingIds = ((BrowserActivity) requireActivity()).getMissingIds();
        if (null != amiibosView.getAdapter()) {
            ((FoomiiboAdapter) amiibosView.getAdapter()).setMissingIds(missingIds);
            amiibosView.getAdapter().notifyDataSetChanged();
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    private void getToolbarOptions(Toolbar toolbar, byte[] tagData) {
        if (!toolbar.getMenu().hasVisibleItems())
            toolbar.inflateMenu(R.menu.amiibo_menu);
        toolbar.getMenu().findItem(R.id.mnu_scan).setVisible(false);
        toolbar.getMenu().findItem(R.id.mnu_save).setVisible(false);
        toolbar.getMenu().findItem(R.id.mnu_delete).setVisible(false);
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

                StringBuilder usage = new StringBuilder();
                usage.append("\n3DS:");
                for (String game : gamesManager.get3DSGames(amiiboId)) {
                    if (usage.toString().endsWith(":"))
                        usage.append("  ");
                    else
                        usage.append(", ");
                    usage.append(game);
                }
                usage.append("\n\nWiiU:");
                for (String game : gamesManager.getWiiUGames(amiiboId)) {
                    if (usage.toString().endsWith(":"))
                        usage.append("  ");
                    else
                        usage.append(", ");
                    usage.append(game);
                }
                usage.append("\n\nSwitch:");
                for (String game : gamesManager.getSwitchGames(amiiboId)) {
                    if (usage.toString().endsWith(":"))
                        usage.append("  ");
                    else
                        usage.append(", ");
                    usage.append(game);
                }
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

    private String decipherFilename(AmiiboManager amiiboManager, byte[] tagData) {
        try {
            long amiiboId = TagUtils.amiiboIdFromTag(tagData);
            String name = TagUtils.amiiboIdToHex(amiiboId);
            if (null != amiiboManager) {
                Amiibo amiibo = amiiboManager.amiibos.get(amiiboId);
                if (null != amiibo && null != amiibo.name) {
                    name = amiibo.name.replace(File.separatorChar, '-');
                }
            }

            byte[] uid = Arrays.copyOfRange(tagData, 0, 9);
            String uidHex = TagUtils.bytesToHex(uid);
            return String.format(Locale.ROOT, "%1$s[%2$s]-Foomiibo.bin", name, uidHex);
        } catch (Exception e) {
            Debug.Log(TagUtils.class, e.getMessage());
        }
        return "";
    }

    private void buildFoomiiboFile(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.generateData(TagUtils.amiiboIdToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagUtils.writeBytesToFile(directory, decipherFilename(
                    settings.getAmiiboManager(), tagData), tagData);
        } catch (Exception e) {
            Debug.Log(e);
        }
    }

    private void buildFoomiiboSet() {
        AmiiboManager amiiboManager = settings.getAmiiboManager();
        if (null == amiiboManager) return;
        Handler handler = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {
            handler.post(() -> dialog = ProgressDialog.show(requireActivity(),
                    "", "", true));

            deleteDir(null, directory);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();

            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                buildFoomiiboFile(amiibo);
                handler.post(() -> dialog.setMessage(getString(
                        R.string.foomiibo_progress, amiibo.getCharacter().name)));
            }

            handler.post(() -> {
                dialog.dismiss();
                onRefresh();
            });
        });
    }
}

