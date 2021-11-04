package com.hiddenramblings.tagmo;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.settings.SettingsFragment_;

import org.androidannotations.annotations.EActivity;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.settings_layout)
public class SettingsActivity extends AppCompatActivity {
    public static final String REFRESH = "REFRESH";
    public static final String SCALING = "SCALING";
    public static final String STORAGE = "STORAGE";
    public static final String POWERTAG = "POWERTAG";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            SettingsFragment fragment = new SettingsFragment_();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.coordinator, fragment)
                    .commit();
        }
    }

    Intent resultIntent = new Intent();

    public void setRefreshResult() {
        setResult(Activity.RESULT_OK, resultIntent.putExtra(REFRESH, true));
    }

    public void setScalingResult() {
        setResult(Activity.RESULT_OK, resultIntent
                .putExtra(SCALING, true)
                .putExtra(REFRESH, true));
    }

    public void setStorageResult() {
        setResult(Activity.RESULT_OK, resultIntent
                .putExtra(STORAGE, true)
                .putExtra(REFRESH, true));
    }

    public void setPowerTagResult() {
        setResult(Activity.RESULT_OK, resultIntent.putExtra(POWERTAG, true));
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
