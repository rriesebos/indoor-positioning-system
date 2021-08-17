package com.rriesebos.positioningapp.ui.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.api.BeaconApi;
import com.rriesebos.positioningapp.api.PositioningApi;
import com.rriesebos.positioningapp.api.RetrofitClient;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String LOG_TAG = SettingsFragment.class.getSimpleName();

    private final PositioningApi mPositioningApi = RetrofitClient.getPositioningApi();
    private final BeaconApi mBeaconApi = RetrofitClient.getBeaconApi();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference deleteMeasurementsButton = findPreference(getString(R.string.key_delete_measurements));
        deleteMeasurementsButton.setOnPreferenceClickListener(preference -> {
            // Show alert dialog
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());
            alertBuilder.setTitle(R.string.title_delete_all_measurements);
            alertBuilder.setMessage(R.string.message_delete_all_measurements);
            alertBuilder.setCancelable(true);

            alertBuilder.setNegativeButton(R.string.action_cancel, null);
            alertBuilder.setPositiveButton(R.string.action_delete, (dialog, id) -> {
                // Delete RSSI measurements, predicted positions and checkpoint timestamps
                final Call<ResponseBody> deleteBeaconMeasurementsCall = mBeaconApi.deleteAllBeaconMeasurements();
                deleteBeaconMeasurementsCall.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e(LOG_TAG, "Failed to DELETE beacon measurements: " + t.getLocalizedMessage());
                        Toast.makeText(getContext(), "Failed to delete beacon measurements", Toast.LENGTH_LONG).show();
                    }
                });

                final Call<ResponseBody> deletePredictedCoordinatesCall = mPositioningApi.deleteAllPredictedCoordinates();
                deletePredictedCoordinatesCall.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e(LOG_TAG, "Failed to DELETE predicted coordinates: " + t.getLocalizedMessage());
                        Toast.makeText(getContext(), "Failed to delete predicted coordinates", Toast.LENGTH_LONG).show();
                    }
                });

                final Call<ResponseBody> deleteCheckpointTimestampsCall = mPositioningApi.deleteAllCheckpointTimestamps();
                deleteCheckpointTimestampsCall.enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Log.e(LOG_TAG, "Failed to DELETE checkpoint timestamps: " + t.getLocalizedMessage());
                        Toast.makeText(getContext(), "Failed to delete checkpoint timestamps", Toast.LENGTH_LONG).show();
                    }
                });
            });

            AlertDialog alertDialog = alertBuilder.create();
            alertDialog.show();

            return true;
        });

        ListPreference distanceModelPreference = findPreference(getString(R.string.key_distance_model));
        if (distanceModelPreference != null) {
            ListPreference pathLossExponentPreference = findPreference(getString(R.string.key_path_loss_exponent));

            // Show/hide positioning method related parameters based on the current selected method
            String selectedDistanceModel = distanceModelPreference.getValue();
            pathLossExponentPreference.setVisible(selectedDistanceModel.equals("path_loss"));

            distanceModelPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                pathLossExponentPreference.setVisible(newValue.equals("path_loss"));
                return true;
            });
        }

        SwitchPreferenceCompat dynamicWindowPreference = findPreference(getString(R.string.key_dynamic_window));
        if (dynamicWindowPreference != null) {
            SeekBarPreference windowSizePreference = findPreference(getString(R.string.key_window_size));

            // Enable window size seek bar when dynamic window size is disabled
            boolean useDynamicWindowSize = getPreferenceScreen().getSharedPreferences()
                    .getBoolean(dynamicWindowPreference.getKey(), false);
            windowSizePreference.setEnabled(!useDynamicWindowSize);

            dynamicWindowPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                windowSizePreference.setEnabled(!(boolean) newValue);
                return true;
            });
        }

        ListPreference positioningMethodPreference = findPreference(getString(R.string.key_positioning_method));
        if (positioningMethodPreference != null) {
            ListPreference weightExponentPreference = findPreference(getString(R.string.key_weight_exponent));
            ListPreference pdfSharpnessPreference = findPreference(getString(R.string.key_pdf_sharpness));

            // Show/hide positioning method related parameters based on the current selected method
            String selectedPositioningMethod = positioningMethodPreference.getValue();
            weightExponentPreference.setVisible(selectedPositioningMethod.equals("weighted_centroid"));
            pdfSharpnessPreference.setVisible(selectedPositioningMethod.equals("probability"));

            positioningMethodPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                weightExponentPreference.setVisible(newValue.equals("weighted_centroid"));
                pdfSharpnessPreference.setVisible(newValue.equals("probability"));
                return true;
            });
        }

        SwitchPreferenceCompat recordFixedNumberPreference = findPreference(getString(R.string.key_record_fixed_number));
        if (recordFixedNumberPreference != null) {
            ListPreference numberOfMeasurementsPreference = findPreference(getString(R.string.key_number_of_measurements));

            // Enable number of measurements list preference when record fixed number is enabled
            boolean recordFixedNumber = getPreferenceScreen().getSharedPreferences()
                    .getBoolean(recordFixedNumberPreference.getKey(), false);
            numberOfMeasurementsPreference.setEnabled(recordFixedNumber);

            recordFixedNumberPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                numberOfMeasurementsPreference.setEnabled((boolean) newValue);
                return true;
            });
        }
    }
}