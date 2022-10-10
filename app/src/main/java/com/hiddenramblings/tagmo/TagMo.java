package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;
import android.text.Html;
import android.text.Spanned;

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

import me.weishu.reflection.Reflection;

@EApplication
public class TagMo extends Application {
    @Pref
    Preferences_ prefs;

    private static SoftReference<Context> mContext;
    private static SoftReference<Preferences_> mPrefs;
    public static final int uiDelay = 50;

    public static Preferences_ getPrefs() {
        return mPrefs.get();
    }

    public static Context getContext() {
        return mContext.get();
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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
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

        if (!BuildConfig.DEBUG && !BuildConfig.GOOGLE_PLAY)
            new ANRWatchDog(10000).setReportMainThreadOnly().start();
    }

    public Spanned getVersionLabel() {
        String flavor = "TagMo " + BuildConfig.VERSION_NAME + (
                BuildConfig.GOOGLE_PLAY ? " (Google Play" : " (GitHub"
        );
        String commit = "<a href=https://github.com/HiddenRamblings/TagMo/commit/"
                + BuildConfig.COMMIT + ">#" + BuildConfig.COMMIT + "</a>";
        if (BuildConfig.WEAR_OS) {
            return Html.fromHtml(flavor + " Wear OS) " + commit);
        } else if (Objects.equals(BuildConfig.BUILD_TYPE, "release")) {
            return Html.fromHtml(flavor + " Release) " + commit);
        } else {
            return Html.fromHtml(flavor + " Debug) " + commit);
        }
    }
}