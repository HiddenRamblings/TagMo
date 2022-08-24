package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

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
    private static SoftReference<Context> mContext;
    private static SoftReference<Preferences_> mPrefs;
    @Pref
    Preferences_ prefs;

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

    public static Preferences_ getPrefs() {
        return mPrefs.get();
    }

    public static Context getContext() {
        return mContext.get();
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
    }

    public static int uiDelay = 50;

    public static boolean isGooglePlay() {
        return Objects.equals(BuildConfig.BUILD_TYPE, "publish");
    }

    @SuppressWarnings("ConstantConditions")
    public static boolean isCompatBuild() {
        return BuildConfig.APPLICATION_ID.endsWith(".eightbit");
    }
}