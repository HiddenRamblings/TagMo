package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.github.anrwatchdog.ANRError;
import com.github.anrwatchdog.ANRWatchDog;
import com.hiddenramblings.tagmo.browser.Preferences;
import com.hiddenramblings.tagmo.eightbit.content.ScaledContext;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.SoftReference;
import java.util.Objects;

import me.weishu.reflection.Reflection;

public class TagMo extends Application {

    private static SoftReference<Context> mContext;
    public static final int uiDelay = 50;

    public static Context getContext() {
        return mContext.get();
    }

    private final boolean isWatchingANR = !BuildConfig.DEBUG && !BuildConfig.GOOGLE_PLAY;
    private boolean isUncaughtANR(Throwable error) {
        return null != error.getCause() && (error.getCause().getCause() instanceof ANRError);
    }

    public void setThemePreference() {
        switch (new Preferences(getApplicationContext()).applicationTheme()) {
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
        if (BuildConfig.WEAR_OS) {
            mContext = new SoftReference<>(new ScaledContext(this).watch(2));
            mContext.get().setTheme(R.style.AppTheme);
        } else {
            mContext = new SoftReference<>(this);
        }
        if (Debug.INSTANCE.isNewer(Build.VERSION_CODES.P))
            HiddenApiBypass.addHiddenApiExemptions("LBluetooth");
        else if (Debug.INSTANCE.isNewer(Build.VERSION_CODES.LOLLIPOP))
            Reflection.unseal(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        if (BuildConfig.WEAR_OS) mContext.get().setTheme(R.style.AppTheme);

        if (isWatchingANR) {
            new ANRWatchDog(30000).setANRListener(error -> {
                StringWriter exception = new StringWriter();
                error.printStackTrace(new PrintWriter(exception));
                Debug.INSTANCE.processException(this, exception.toString());
            }).start();
        }

        Thread.setDefaultUncaughtExceptionHandler((t, error) -> {
            if (isWatchingANR && isUncaughtANR(error)) return;
            StringWriter exception = new StringWriter();
            error.printStackTrace(new PrintWriter(exception));
            Toast.makeText(this, R.string.logcat_crash, Toast.LENGTH_SHORT).show();
            Debug.INSTANCE.processException(this, exception.toString());
            System.exit(0);
        });

        setThemePreference();
    }

    private static final String commitHash = "#" + BuildConfig.COMMIT;
    private static final String versionLabel = "TagMo "
            + BuildConfig.VERSION_NAME + " (" + (BuildConfig.GOOGLE_PLAY
            ? "Google Play" : "GitHub") + " " + (BuildConfig.WEAR_OS
            ? "Wear OS" : Objects.equals(BuildConfig.BUILD_TYPE, "release")
            ? "Release" : "Debug") + ") " + commitHash;
    private static final String commitLink =
            "<a href=https://github.com/HiddenRamblings/TagMo/commit/"
            + BuildConfig.COMMIT + ">" + commitHash + "</a>";
    public static Spanned getVersionLabel(boolean plain) {
        return Html.fromHtml(plain ? versionLabel : versionLabel.replace(commitHash, commitLink));
    }
}