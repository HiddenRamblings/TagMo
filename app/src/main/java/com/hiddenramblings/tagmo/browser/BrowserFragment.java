package com.hiddenramblings.tagmo.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.snackbar.Snackbar;
import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.ImageActivity;
import com.hiddenramblings.tagmo.NFCIntent;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.amiibo.Amiibo;
import com.hiddenramblings.tagmo.amiibo.AmiiboManager;
import com.hiddenramblings.tagmo.amiibo.KeyManager;
import com.hiddenramblings.tagmo.amiibo.games.GamesManager;
import com.hiddenramblings.tagmo.browser.adapter.BrowserAdapter;
import com.hiddenramblings.tagmo.browser.adapter.FoomiiboAdapter;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.material.IconifiedSnackbar;
import com.hiddenramblings.tagmo.nfctech.Foomiibo;
import com.hiddenramblings.tagmo.nfctech.TagArray;
import com.hiddenramblings.tagmo.widget.Toasty;
import com.robertlevonyan.views.chip.Chip;
import com.robertlevonyan.views.chip.OnCloseClickListener;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

import myinnos.indexfastscrollrecycler.IndexFastScrollRecyclerView;

public class BrowserFragment extends Fragment implements
        FoomiiboAdapter.OnFoomiiboClickListener{

    private final Preferences prefs = TagMo.getPrefs();
    private FlexboxLayout chipList;
    private RecyclerView browserContent;
    private RecyclerView foomiiboView;

    private final Foomiibo foomiibo = new Foomiibo();
    private File directory;

    private KeyManager keyManager;
    private BrowserSettings settings;

    private final ArrayList<byte[]> resultData = new ArrayList<>();

    final ActivityResultLauncher<Intent> onUpdateTagResult = registerForActivityResult(
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
                    if (data.length > 0 && Amiibo.dataToId(data) == Amiibo.dataToId(tagData)) {
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
        return inflater.inflate(R.layout.fragment_browser, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BrowserActivity activity = (BrowserActivity) requireActivity();
        keyManager = new KeyManager(activity);
        settings = activity.getSettings();

        directory = new File(requireActivity().getFilesDir(), "Foomiibo");
        //noinspection ResultOfMethodCallIgnored
        directory.mkdirs();

        chipList = view.findViewById(R.id.chip_list);
        chipList.setVisibility(View.GONE);
        browserContent = view.findViewById(R.id.browser_content);
        if (prefs.software_layer())
            browserContent.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        foomiiboView = view.findViewById(R.id.foomiibo_list);
        if (prefs.software_layer())
            foomiiboView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        browserContent.setLayoutManager(settings.getAmiiboView()
                == BrowserSettings.VIEW.IMAGE.getValue()
                ? new GridLayoutManager(activity, activity.getColumnCount())
                : new LinearLayoutManager(activity));
        this.browserContent.setAdapter(new BrowserAdapter(settings, activity));
        settings.addChangeListener((BrowserSettings.BrowserSettingsListener)
                this.browserContent.getAdapter());

        foomiiboView.setLayoutManager(settings.getAmiiboView()
                == BrowserSettings.VIEW.IMAGE.getValue()
                ? new GridLayoutManager(activity, activity.getColumnCount())
                : new LinearLayoutManager(activity));
        foomiiboView.setAdapter(new FoomiiboAdapter(settings, this));
        settings.addChangeListener((BrowserSettings.BrowserSettingsListener)
                foomiiboView.getAdapter());

        view.findViewById(R.id.list_divider).setVisibility(View.GONE);
        if (BuildConfig.WEAR_OS) browserContent.getLayoutParams().height =
                browserContent.getLayoutParams().height / 3;

        view.findViewById(R.id.list_divider).setOnTouchListener((v, event) -> {
            if (browserContent instanceof IndexFastScrollRecyclerView) {
                ((IndexFastScrollRecyclerView) browserContent).setIndexBarVisibility(false);
            }
            if (foomiiboView instanceof IndexFastScrollRecyclerView) {
                ((IndexFastScrollRecyclerView) foomiiboView).setIndexBarVisibility(false);
            }
            int srcHeight = browserContent.getLayoutParams().height;
            int y = (int) event.getY();
            if (browserContent.getLayoutParams().height + y >= -0.5f) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    browserContent.getLayoutParams().height += y;
                    if (srcHeight != browserContent.getLayoutParams().height) browserContent.requestLayout();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (browserContent.getLayoutParams().height + y < 0f) {
                        browserContent.getLayoutParams().height = 0;
                    } else {
                        float minHeight = activity.getBottomSheetBehavior()
                                .getPeekHeight() + v.getHeight() + requireContext().getResources().getDimension(R.dimen.sliding_bar_margin);
                        if (browserContent.getLayoutParams().height > view.getHeight() - (int) minHeight)
                            browserContent.getLayoutParams().height = view.getHeight() - (int) minHeight;
                    }
                    if (srcHeight != browserContent.getLayoutParams().height) browserContent.requestLayout();
                }
                prefs.foomiiboOffset(browserContent.getLayoutParams().height);
            }
            return true;
        });

        activity.onFilterContentsLoaded();
    }

    public RecyclerView getBrowserContent() {
        return browserContent;
    }

    public RecyclerView getFoomiiboView() {
        return foomiiboView;
    }

    @SuppressLint("InflateParams")
    public void addFilterItemView(String text, String tag, OnCloseClickListener listener) {
        if (null == chipList) return;
        FrameLayout chipContainer = chipList.findViewWithTag(tag);
        if (null != chipContainer) chipList.removeView(chipContainer);
        if (!TextUtils.isEmpty(text)) {
            chipContainer = (FrameLayout) getLayoutInflater().inflate(R.layout.chip_view, null);
            chipContainer.setTag(tag);
            Chip chip = chipContainer.findViewById(R.id.chip);
            chip.setText(text);
            chip.setClosable(true);
            chip.setOnCloseClickListener(listener);
            chipList.addView(chipContainer);
            chipList.setVisibility(View.VISIBLE);
        } else if (chipList.getChildCount() == 0) {
            chipList.setVisibility(View.GONE);
        }
    }

    void setFoomiiboVisibility() {
        if (null == getView()) return;
        BrowserActivity activity = (BrowserActivity) requireActivity();
        float minHeight = activity.getBottomSheetBehavior().getPeekHeight()
                + getView().findViewById(R.id.list_divider).getHeight() + requireContext()
                .getResources().getDimension(R.dimen.sliding_bar_margin);
        if (browserContent.getLayoutParams().height > getView().getHeight() - (int) minHeight) {
            browserContent.getLayoutParams().height = getView().getHeight() - (int) minHeight;
        } else {
            int valueY = prefs.foomiiboOffset();
            browserContent.getLayoutParams().height = valueY != -1
                    ? valueY : browserContent.getLayoutParams().height;
        }
        if (prefs.disable_foomiibo()) {
            getView().findViewById(R.id.list_divider).setVisibility(View.GONE);
            browserContent.getLayoutParams().height = getView().getHeight();
        } else {
            getView().findViewById(R.id.list_divider).setVisibility(View.VISIBLE);
        }
        browserContent.requestLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        ((BrowserActivity) requireActivity()).onRootFolderChanged(false);
        browserContent.postDelayed(this::setFoomiiboVisibility, TagMo.uiDelay);
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

    void deleteFoomiiboFile(byte[] tagData) {
        try {
            Amiibo amiibo = settings.getAmiiboManager().amiibos
                    .get(Amiibo.dataToId(tagData));
            if (amiibo == null) throw new Exception();
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            File amiiboFile = new File(directory, TagArray.decipherFilename(
                    settings.getAmiiboManager(), tagData, false
            ));
            new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.warn_delete_file, amiiboFile.getName()))
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        if (amiiboFile.delete()) {
                            new IconifiedSnackbar(requireActivity(), browserContent).buildSnackbar(
                                    getString(R.string.delete_foomiibo, amiibo.name),
                                    Snackbar.LENGTH_SHORT
                            ).show();
                        } else {
                            new Toasty(requireActivity()).Short(R.string.delete_virtual);
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss()).show();
        } catch (Exception e) {
            new Toasty(requireActivity()).Short(R.string.delete_virtual);
        }
    }

    void clearFoomiiboSet(Handler handler) {
        ProgressDialog dialog = ProgressDialog.show(requireActivity(),
                "", "", true);
        Executors.newSingleThreadExecutor().execute(() -> {
            deleteDir(handler, dialog, directory);

            handler.post(() -> {
                dialog.dismiss();
                ((BrowserActivity) requireActivity()).onRefresh(false);
            });
        });
    }

    private void buildFoomiiboFile(Amiibo amiibo) {
        try {
            byte[] tagData = foomiibo.getSignedData(Amiibo.idToHex(amiibo.id));
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            TagArray.writeBytesToFile(directory, TagArray.decipherFilename(
                    settings.getAmiiboManager(), tagData, false
            ), tagData);
        } catch (Exception e) {
            Debug.Warn(e);
        }
    }

    void buildFoomiiboFile(byte[] tagData) {
        try {
            Amiibo amiibo = settings.getAmiiboManager().amiibos
                    .get(Amiibo.dataToId(tagData));
            if (null == amiibo) return;
            File directory = new File(this.directory, amiibo.getAmiiboSeries().name);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
            tagData = foomiibo.getSignedData(tagData);
            TagArray.writeBytesToFile(directory, TagArray.decipherFilename(
                    settings.getAmiiboManager(), tagData, false
            ), tagData);
            new IconifiedSnackbar(requireActivity(), browserContent).buildSnackbar(
                    getString(R.string.wrote_foomiibo, amiibo.name), Snackbar.LENGTH_SHORT
            ).show();
        } catch (Exception e) {
            Debug.Warn(e);
        }
    }

    void buildFoomiiboSet(Handler handler) {
        AmiiboManager amiiboManager = null != settings ? settings.getAmiiboManager() : null;
        if (null == amiiboManager) {
            new Toasty(requireActivity()).Short(R.string.amiibo_failure_read);
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(requireActivity(),
                "", "", true);

        Executors.newSingleThreadExecutor().execute(() -> {
            deleteDir(null, null, directory);
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();

            for (Amiibo amiibo : amiiboManager.amiibos.values()) {
                buildFoomiiboFile(amiibo);
                handler.post(() -> dialog.setMessage(getString(
                        R.string.foomiibo_progress, amiibo.getCharacter().name)));
            }

            handler.post(() -> {
                dialog.dismiss();
                ((BrowserActivity) requireActivity()).onRefresh(false);
            });
        });
    }

    private void getGameCompatibility(TextView txtUsage, byte[] tagData) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                long amiiboId = Amiibo.dataToId(tagData);
                GamesManager gamesManager = GamesManager.getGamesManager(requireContext());
                String usage = gamesManager.getGamesCompatibility(amiiboId);
                txtUsage.post(() -> txtUsage.setText(usage));
            } catch (Exception ex) {
                Debug.Warn(ex);
            }
        });
    }

    public void onFoomiiboClicked(View itemView, Amiibo amiibo) {
        byte[] tagData = new byte[0];
        for (byte[] data : resultData) {
            try {
                if (data.length > 0 && Amiibo.dataToId(data) == amiibo.id) {
                    tagData = data;
                    break;
                }
            } catch (Exception ignored) { }
        }
        if (tagData.length == 0)
            tagData = foomiibo.generateData(Amiibo.idToHex(amiibo.id));
        try {
            tagData = TagArray.getValidatedData(keyManager, tagData);
        } catch (Exception ignored) { }

        BrowserActivity activity = (BrowserActivity) requireActivity();
        LinearLayout menuOptions = itemView.findViewById(R.id.menu_options);
        if (null != menuOptions) {
            Toolbar toolbar = menuOptions.findViewById(R.id.toolbar);
            if (settings.getAmiiboView() != BrowserSettings.VIEW.IMAGE.getValue()) {
                if (menuOptions.getVisibility() == View.VISIBLE) {
                    menuOptions.setVisibility(View.GONE);
                } else {
                    menuOptions.setVisibility(View.VISIBLE);
                    activity.getToolbarOptions(this, toolbar, tagData, itemView);
                }
                TextView txtUsage = itemView.findViewById(R.id.txtUsage);
                if (txtUsage.getVisibility() == View.VISIBLE) {
                    txtUsage.setVisibility(View.GONE);
                } else {
                    txtUsage.setVisibility(View.VISIBLE);
                    getGameCompatibility(txtUsage, tagData);
                }
            } else {
                activity.getToolbarOptions(this, toolbar, tagData, itemView);
                activity.updateAmiiboView(tagData, null);
            }
        }
    }

    @Override
    public void onFoomiiboRebind(View itemView, Amiibo amiibo) {
        byte[] tagData = new byte[0];
        for (byte[] data : resultData) {
            try {
                if (data.length > 0 && Amiibo.dataToId(data) == amiibo.id) {
                    tagData = data;
                    break;
                }
            } catch (Exception ignored) { }
        }
        if (tagData.length == 0)
            tagData = foomiibo.generateData(Amiibo.idToHex(amiibo.id));
        try {
            tagData = TagArray.getValidatedData(keyManager, tagData);
        } catch (Exception ignored) { }

        if (settings.getAmiiboView() != BrowserSettings.VIEW.IMAGE.getValue()) {
            BrowserActivity activity = (BrowserActivity) requireActivity();
            Toolbar toolbar = itemView.findViewById(R.id.menu_options).findViewById(R.id.toolbar);
            activity.getToolbarOptions(this, toolbar, tagData, itemView);
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

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (null == getView()) return;
        browserContent.postDelayed(this::setFoomiiboVisibility, TagMo.uiDelay);
    }
}

