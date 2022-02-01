package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

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
import java.util.Locale;

@EApplication
public class TagMo extends MultiDexApplication {

    @Pref
    Preferences_ prefs;

    private static SoftReference<Context> mContext;
    private static SoftReference<Preferences_> mPrefs;

    @Override
    public void onCreate() {
        super.onCreate();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

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
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/HiddenRamblings/TagMo/wiki#reporting-issues")));
            System.exit(1);
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

    static void setScaledTheme(Context context, int theme) {
        if (null != mPrefs && mPrefs.get().enableScaling().get())
            ScaledContext.wrap(context).setTheme(theme);
        else
            ScaledContext.restore(context).setTheme(theme);
    }
}