package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

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

        mPrefs = new SoftReference<>(this.prefs);
        mContext = new SoftReference<>(this);

        Thread.setDefaultUncaughtExceptionHandler((t, error) -> {
            StringWriter exception = new StringWriter();
            error.printStackTrace(new PrintWriter(exception));
            Log.e("UncaughtException", exception.toString());
            error.printStackTrace();
            System.exit(1);
        });
    }

    public static boolean isGooglePlay() {
        return Objects.equals(BuildConfig.BUILD_TYPE, "publish");
    }
}