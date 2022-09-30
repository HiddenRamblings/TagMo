package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.github.anrwatchdog.ANRWatchDog;
import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.settings.Preferences_;

import org.androidannotations.annotations.EApplication;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.SoftReference;
import java.util.Objects;

@EApplication
public class TagMo extends Application {
    @Pref
    Preferences_ prefs;

    public static final String RENDER_RAW = "https://raw.githubusercontent.com/8BitDream/AmiiboAPI/";
    public static final String AMIIBO_API = "https://amiiboapi.com/api/";

    private static SoftReference<Context> mContext;
    private static SoftReference<Preferences_> mPrefs;
    public static final int uiDelay = 50;

    public static Preferences_ getPrefs() {
        return mPrefs.get();
    }

    public static Context getContext() {
        return mContext.get();
    }

    public static boolean isGooglePlay() {
        return Objects.equals(BuildConfig.FLAVOR, "google");
    }

    public static boolean isWearableUI() {
        return Objects.equals(BuildConfig.BUILD_TYPE, "wearos");
    }

    public static boolean isMainstream() {
        return isGooglePlay() || isWearableUI();
    }

    @SuppressWarnings("ConstantConditions")
    public static boolean isCompatBuild() {
        return BuildConfig.APPLICATION_ID.endsWith(".eightbit");
    }

    public void setThemePreference() {
        switch (prefs.applicationTheme().get()) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 2:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        setThemePreference();

        mPrefs = new SoftReference<>(this.prefs);
        mContext = new SoftReference<>(this);

        Thread.setDefaultUncaughtExceptionHandler((t, error) -> {
            StringWriter exception = new StringWriter();
            error.printStackTrace(new PrintWriter(exception));
            Debug.processException(this, exception.toString());
            System.exit(0);
        });

        if (!BuildConfig.DEBUG && !isGooglePlay())
            new ANRWatchDog(10000).setReportMainThreadOnly().start();
    }

    @SuppressWarnings("ConstantConditions")
    public String getVersionLabel() {
        String flavor = TagMo.isGooglePlay() ? "Google Play" : "GitHub";
        if (isWearableUI()) {
            return flavor + " Wear OS";
        } else if (Objects.equals(BuildConfig.BUILD_TYPE, "release")) {
            return flavor + " Release";
        } else {
            return flavor + " Testing";
        }
    }
}