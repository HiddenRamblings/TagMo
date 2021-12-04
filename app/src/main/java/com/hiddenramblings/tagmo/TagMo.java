package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import com.hiddenramblings.tagmo.eightbit.content.ScaledContext;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.settings.Preferences_;

import org.androidannotations.annotations.EApplication;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.SoftReference;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@EApplication
public class TagMo extends Application {

    public static final String ACTION_EDIT_COMPLETE = BuildConfig.APPLICATION_ID + ".EDIT_COMPLETE";
    public static final String ACTION_SCAN_TAG = BuildConfig.APPLICATION_ID + ".SCAN_TAG";
    public static final String ACTION_WRITE_TAG_FULL = BuildConfig.APPLICATION_ID + ".WRITE_TAG_FULL";
    public static final String ACTION_WRITE_TAG_RAW = BuildConfig.APPLICATION_ID + ".WRITE_TAG_RAW";
    public static final String ACTION_WRITE_TAG_DATA = BuildConfig.APPLICATION_ID + ".WRITE_TAG_DATA";
    public static final String ACTION_UPDATE_TAG = BuildConfig.APPLICATION_ID + ".UPDATE_TAG";
    public static final String ACTION_WRITE_ALL_TAGS = BuildConfig.APPLICATION_ID + ".WRITE_ALL_TAGS";
    public static final String ACTION_ERASE_ALL_TAGS = BuildConfig.APPLICATION_ID + ".CLEAR_ALL_TAGS";
    public static final String ACTION_ACTIVATE_BANK = BuildConfig.APPLICATION_ID + ".ACTIVATE_BANK";
    public static final String ACTION_SET_BANK_COUNT = BuildConfig.APPLICATION_ID + ".SET_BANK_COUNT";
    public static final String ACTION_ERASE_BANK = BuildConfig.APPLICATION_ID + ".ERASE_BANK";
    public static final String ACTION_LOCK_AMIIBO = BuildConfig.APPLICATION_ID + ".LOCK_AMIIBO";
    public static final String ACTION_UNLOCK_UNIT = BuildConfig.APPLICATION_ID + ".UNLOCK_UNIT";
    public static final String ACTION_BACKUP_AMIIBO = BuildConfig.APPLICATION_ID + ".BACKUP_AMIIBO";
    public static final String ACTION_FIX_BANK_DATA = BuildConfig.APPLICATION_ID + ".FIX_BANK_DATA";
    public static final String ACTION_DELETE_AMIIBO = BuildConfig.APPLICATION_ID + ".DELETE_AMIIBO";
    public static final String ACTION_NFC_SCANNED = BuildConfig.APPLICATION_ID + ".NFC_SCANNED";
    public static final String ACTION_BROWSE_GITLAB = BuildConfig.APPLICATION_ID + ".BROWSE_GITLAB";

    public static final String EXTRA_TAG_DATA = BuildConfig.APPLICATION_ID + ".EXTRA_TAG_DATA";
    public static final String EXTRA_AMIIBO_LIST = BuildConfig.APPLICATION_ID + ".EXTRA_AMIIBO_LIST";
    public static final String EXTRA_IGNORE_TAG_ID = BuildConfig.APPLICATION_ID + ".EXTRA_IGNORE_TAG_ID";
    public static final String EXTRA_AMIIBO_ID = BuildConfig.APPLICATION_ID + ".AMIIBO_ID";
    public static final String EXTRA_AMIIBO_FILES = BuildConfig.APPLICATION_ID + ".EXTRA_AMIIBO_FILES";
    public static final String EXTRA_SIGNATURE = BuildConfig.APPLICATION_ID + ".EXTRA_SIGNATURE";
    public static final String EXTRA_ACTIVE_BANK = BuildConfig.APPLICATION_ID + ".EXTRA_ACTIVE_BANK";
    public static final String EXTRA_BANK_COUNT = BuildConfig.APPLICATION_ID + ".EXTRA_BANK_COUNT";
    public static final String EXTRA_CURRENT_BANK = BuildConfig.APPLICATION_ID + ".EXTRA_CURRENT_BANK";

    public static class Website {
        public static final String AMIIBOAPI = "https://www.amiiboapi.com/api/amiibo/";
        public static final String LASTUPDATED = "https://www.amiiboapi.com/api/lastupdated/";
        public static final String AMIIBO_IMAGE = "https://raw.githubusercontent.com/N3evin/AmiiboAPI/master/images/icon_%08x-%08x.png";
        public static final String TAGMO_GIT_API = "https://api.github.com/repos/HiddenRamblings/TagMo/releases/tags/";
        public static final String TAGMO_GITLAB = "https://tagmo.gitlab.io/";
    }

    public static ComponentName NFCIntentFilter = new ComponentName(BuildConfig.APPLICATION_ID,
            BuildConfig.APPLICATION_ID + "." + "NFCIntentFilter");

    @Pref
    Preferences_ prefs;

    public static Charset UTF_8;
    public static Charset ISO_8859_1;
    public static Charset UTF_16BE;
    public static Charset UTF_16LE;

    private static SoftReference<Context> mContext;
    private static SoftReference<Preferences_> mPrefs;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            UTF_8 = StandardCharsets.UTF_8;
            ISO_8859_1 = StandardCharsets.ISO_8859_1;
            UTF_16BE = StandardCharsets.UTF_16BE;
            UTF_16LE = StandardCharsets.UTF_16LE;
        } else {
            UTF_8 = Charset.forName("UTF-8");
            ISO_8859_1 = Charset.forName("ISO-8859-1");
            UTF_16BE = Charset.forName("UTF-16BE");
            UTF_16LE = Charset.forName("UTF-16LE");
        }

        mPrefs = new SoftReference<>(this.prefs);
        mContext = new SoftReference<>(this);

        File[] logs = Storage.getDownloadDir("TagMo",
                "Logcat").listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).startsWith("crash_logcat"));
        if (null != logs && logs.length > 0) {
            for (File file : logs) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }

        Thread.setDefaultUncaughtExceptionHandler((t, error) -> {
            StringWriter exception = new StringWriter();
            error.printStackTrace(new PrintWriter(exception));
            Log.e("UncaughtException", exception.toString());
            error.printStackTrace();
            try {
                Debug.processLogcat(this, "crash_logcat");
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
        if (null != mPrefs && mPrefs.get().enableScaling().get())
            return ScaledContext.wrap(mContext.get());
        else
            return ScaledContext.restore(mContext.get());
    }

    public static Intent getIntent(Intent intent) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? intent.addCategory(Intent.CATEGORY_OPENABLE)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                :intent.addCategory(Intent.CATEGORY_OPENABLE);
    }

    static void setScaledTheme(Context context, int theme) {
        if (null != mPrefs && mPrefs.get().enableScaling().get())
            ScaledContext.wrap(context).setTheme(theme);
        else
            ScaledContext.restore(context).setTheme(theme);
    }

    public static boolean isDarkTheme() {
        return (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static String getStringRes(int resource) {
        return mContext.get().getString(resource);
    }

    public static String getStringRes(int resource, String params) {
        return mContext.get().getString(resource, params);
    }

    public static String getStringRes(int resource, int params) {
        try {
            Resources res = mContext.get().getResources();
            res.getIdentifier(res.getResourceName(params),
                    "string", BuildConfig.APPLICATION_ID);
            return mContext.get().getString(resource, mContext.get().getString(params));
        } catch (Resources.NotFoundException ignore) {
            return mContext.get().getString(resource, params);
        }
    }

    public static String getStringRes(int resource, String params, int digits) {
        return mContext.get().getString(resource, params, digits);
    }

    public static String getStringRes(int resource, int params, int digits) {
        return mContext.get().getString(resource, params, digits);
    }

    public static String getStringRes(int resource, String params, int length, int size) {
        return mContext.get().getString(resource, params, length, size);
    }
}