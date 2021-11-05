package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.os.Build;

import com.eightbit.content.ScaledContext;
import com.eightbit.io.Debug;
import com.eightbit.os.Storage;
import com.hiddenramblings.tagmo.settings.Preferences_;

import org.androidannotations.annotations.EApplication;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@EApplication
public class TagMo extends Application {

    public static final String ACTION_EDIT_COMPLETE = BuildConfig.APPLICATION_ID + ".EDIT_COMPLETE";
    public static final String ACTION_SCAN_TAG = BuildConfig.APPLICATION_ID + ".SCAN_TAG";
    public static final String ACTION_WRITE_TAG_FULL = BuildConfig.APPLICATION_ID + ".WRITE_TAG_FULL";
    public static final String ACTION_WRITE_TAG_RAW = BuildConfig.APPLICATION_ID + ".WRITE_TAG_RAW";
    public static final String ACTION_WRITE_TAG_DATA = BuildConfig.APPLICATION_ID + ".WRITE_TAG_DATA";
    public static final String ACTION_WRITE_ALL_TAGS = BuildConfig.APPLICATION_ID + ".WRITE_ALL_TAGS";
    public static final String ACTION_ACTIVATE_BANK = BuildConfig.APPLICATION_ID + ".ACTIVATE_BANK";
    public static final String ACTION_SET_BANK_COUNT = BuildConfig.APPLICATION_ID + ".SET_BANK_COUNT";
    public static final String ACTION_FORMAT_BANK = BuildConfig.APPLICATION_ID + ".FORMAT_BANK";
    public static final String ACTION_LOCK_AMIIBO = BuildConfig.APPLICATION_ID + ".LOCK_AMIIBO";
    public static final String ACTION_UNLOCK_UNIT = BuildConfig.APPLICATION_ID + ".UNLOCK_UNIT";
    public static final String ACTION_BACKUP_AMIIBO = BuildConfig.APPLICATION_ID + ".BACKUP_AMIIBO";
    public static final String ACTION_NFC_SCANNED = BuildConfig.APPLICATION_ID + ".NFC_SCANNED";

    public static final String EXTRA_TAG_DATA = BuildConfig.APPLICATION_ID + ".EXTRA_TAG_DATA";
    public static final String EXTRA_AMIIBO_DATA = BuildConfig.APPLICATION_ID + ".EXTRA_AMIIBO_DATA";
    public static final String EXTRA_IGNORE_TAG_ID = BuildConfig.APPLICATION_ID + ".EXTRA_IGNORE_TAG_ID";
    public static final String EXTRA_AMIIBO_ID = BuildConfig.APPLICATION_ID + ".AMIIBO_ID";
    public static final String EXTRA_AMIIBO_FILES = BuildConfig.APPLICATION_ID + ".EXTRA_AMIIBO_FILES";
    public static final String EXTRA_SIGNATURE = BuildConfig.APPLICATION_ID + ".EXTRA_SIGNATURE";
    public static final String EXTRA_ACTIVE_BANK = BuildConfig.APPLICATION_ID + ".EXTRA_ACTIVE_BANK";
    public static final String EXTRA_BANK_COUNT = BuildConfig.APPLICATION_ID + ".EXTRA_BANK_COUNT";
    public static final String EXTRA_CURRENT_BANK = BuildConfig.APPLICATION_ID + ".EXTRA_CURRENT_BANK";

    @Pref
    Preferences_ prefs;

    public static Charset UTF_8;
    public static Charset UTF_16BE;
    public static Charset UTF_16LE;

    private static WeakReference<Context> mContext;
    private static WeakReference<Preferences_> mPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            UTF_8 = StandardCharsets.UTF_8;
            UTF_16BE = StandardCharsets.UTF_16BE;
            UTF_16LE = StandardCharsets.UTF_16LE;
        } else {
            UTF_8 = Charset.forName("UTF-8");
            UTF_16BE = Charset.forName("UTF-16BE");
            UTF_16LE = Charset.forName("UTF-16LE");
        }
        mPrefs = new WeakReference<>(prefs);
        mContext = new WeakReference<>(this);
        Storage.setContext(this);

        Thread.setDefaultUncaughtExceptionHandler((t, error) -> {
            StringWriter exception = new StringWriter();
            error.printStackTrace(new PrintWriter(exception));
            Debug.Error(error.getClass(), exception.toString());
            error.printStackTrace();
            try {
                Debug.generateLogcat(new File(TagMo.getExternalFiles(), "crash_logcat.txt"));
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        });
    }

    public static Preferences_ getPrefs() {
        return mPrefs.get();
    }

    public static Context getContext() {
        if (getPrefs().enableScaling().get())
            return ScaledContext.wrap(mContext.get());
        else
            return ScaledContext.restore(mContext.get());
    }

    public static void setScaledTheme(Context context, int theme) {
        if (getPrefs().enableScaling().get())
            ScaledContext.wrap(context).setTheme(theme);
        else
            ScaledContext.restore(context).setTheme(theme);
    }

    public static String getStringRes(int resource) {
        return getContext().getString(resource);
    }

    public static String getStringRes(int resource, String params) {
        return getContext().getString(resource, params);
    }

    public static String getStringRes(int resource, String params, int digits) {
        return getContext().getString(resource, params, digits);
    }

    public static String getStringRes(int resource, int params) {
        try {
            Resources res = getContext().getResources();
            res.getIdentifier(res.getResourceName(params),
                    "string", BuildConfig.APPLICATION_ID);
            return getContext().getString(resource, getContext().getString(params));
        } catch (Resources.NotFoundException ignore) {
            return getContext().getString(resource, params);
        }
    }

    public static File getExternalFiles() {
        return getContext().getExternalFilesDir(null);
    }

    public static void scanFile(File file) {
        try {
            MediaScannerConnection.scanFile(TagMo.getContext(),
                    new String[]{file.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Debug.Error(R.string.fail_media_scan, e);
        }
    }

    public static boolean isDarkTheme() {
        return (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}