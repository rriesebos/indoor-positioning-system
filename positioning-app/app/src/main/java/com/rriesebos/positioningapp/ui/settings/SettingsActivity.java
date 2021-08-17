package com.rriesebos.positioningapp.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.positioning.DistanceProvider;
import com.rriesebos.positioningapp.positioning.PositionProvider;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        PositionProvider positionProvider = PositionProvider.getInstance(getApplicationContext());
        DistanceProvider distanceProvider = DistanceProvider.getInstance(getApplicationContext());

        if (key.equals(getString(R.string.key_positioning_method))
                || key.equals(getString(R.string.key_allow_less_than_three_beacons))
                || key.equals(getString(R.string.key_weight_exponent))
                || key.equals(getString(R.string.key_pdf_sharpness))) {
            positionProvider.updateParameters(getApplicationContext());
        }

        if (key.equals(getString(R.string.key_distance_model))
                || key.equals(getString(R.string.key_distance_method))
                || key.equals(getString(R.string.key_dynamic_window))
                || key.equals(getString(R.string.key_window_size))
                || key.equals(getString(R.string.key_path_loss_exponent))) {
            distanceProvider.updateParameters(getApplicationContext());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register shared preference listener
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister shared preference listener
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
