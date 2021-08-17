package com.hiddenramblings.tagmo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.androidannotations.annotations.EApplication;
import org.androidannotations.annotations.sharedpreferences.Pref;

import java.lang.ref.WeakReference;

@EApplication
public class TagMo extends Application {

    @Pref
    Preferences_ prefs;

    private static WeakReference<Context> mContext;
    private static WeakReference<Preferences_> mPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = new WeakReference<Context>(this);
        mPrefs = new WeakReference<Preferences_>(prefs);
    }

    public static Context getContext() {
        return mContext.get();
    }

    public static String getStringRes(int resource) {
        return TagMo.getContext().getString(resource);
    }

    public static String getStringRes(int resource, String params) {
        return TagMo.getContext().getString(resource, params);
    }

    public static String getStringRes(int resource, int params) {
        return TagMo.getContext().getString(resource, TagMo.getContext().getString(params));
    }

    public static void Debug(String TAG, String params) {
        if (!mPrefs.get().disableDebug().get())
            Log.d(TAG, params);
    }

    public static void Debug(String TAG, int resource) {
        if (!mPrefs.get().disableDebug().get())
            Log.d(TAG, getStringRes(resource));
    }

    public static void Debug(String TAG, int resource, String params) {
        if (!mPrefs.get().disableDebug().get())
            Log.d(TAG, getStringRes(resource, params));
    }

    public static void Error(String TAG, String params) {
        Log.e(TAG, params);
    }

    public static void Error(String TAG, int resource) {
        Log.e(TAG, getStringRes(resource));
    }

    public static void Error(String TAG, int resource, String params) {
        Log.e(TAG, getStringRes(resource, params));
    }

    public static void Error(String TAG, int resource, Exception e) {
        Log.e(TAG, getStringRes(resource), e);
    }
}