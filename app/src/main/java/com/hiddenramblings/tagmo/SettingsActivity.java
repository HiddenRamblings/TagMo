package com.hiddenramblings.tagmo;


import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hiddenramblings.tagmo.settings.SettingsFragment;
import com.hiddenramblings.tagmo.settings.SettingsFragment_;

import org.androidannotations.annotations.EActivity;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.settings_layout)
public class SettingsActivity extends AppCompatActivity {
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
}
