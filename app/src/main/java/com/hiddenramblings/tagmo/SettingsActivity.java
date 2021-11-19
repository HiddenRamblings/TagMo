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
@EActivity(R.layout.activity_settings)
public class SettingsActivity extends AppCompatActivity {
    static final String WUMIIBO = "WUMIIBO";
    static final String REFRESH = "REFRESH";
    static final String POWERTAG = "POWERTAG";

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

    public void setWumiiboResult() {
        setResult(Activity.RESULT_OK, resultIntent
                .putExtra(WUMIIBO, true)
                .putExtra(REFRESH, true));
    }

    public void setPowerTagResult() {
        setResult(Activity.RESULT_OK, resultIntent.putExtra(POWERTAG, true));
    }

    public void setRefreshResult() {
        setResult(Activity.RESULT_OK, resultIntent.putExtra(REFRESH, true));
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}
