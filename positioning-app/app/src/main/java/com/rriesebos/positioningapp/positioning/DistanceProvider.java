package com.rriesebos.positioningapp.positioning;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.api.BeaconApi;
import com.rriesebos.positioningapp.api.RetrofitClient;
import com.rriesebos.positioningapp.model.BeaconInformation;

import org.altbeacon.beacon.Beacon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DistanceProvider {

    private static final String LOG_TAG = DistanceProvider.class.getSimpleName();

    private DistanceModel mDistanceModel = DistanceModel.PATH_LOSS;
    private DistanceMethod mDistanceMethod = DistanceMethod.MEDIAN;
    // TODO: implement dynamic window size
    private boolean mUseDynamicWindowSize = false;
    private int mWindowSize = 5;
    private double mPathLossExponent = 2;

    private static DistanceProvider instance;

    private final Map<String, LinkedList<Integer>> mBeaconMeasurementsMap = new HashMap<>();
    private final Map<String, Integer> txPowerMap = new HashMap<>();

    private DistanceProvider(Context context) {
        // Initialize parameters from shared preferences
        updateParameters(context);

        // Retrieve beacon tx power information used for distance estimation
        BeaconApi mBeaconApi = RetrofitClient.getBeaconApi();
        final Call<List<BeaconInformation>> call = mBeaconApi.getBeacons();

        call.enqueue(new Callback<List<BeaconInformation>>() {
            @Override
            public void onResponse(Call<List<BeaconInformation>> call, Response<List<BeaconInformation>> response) {
                List<BeaconInformation> beaconsInformation = response.body();
                if (beaconsInformation == null) {
                    Log.d(LOG_TAG, "No beacon information stored");
                    return;
                }

                // Initialize hash map to map beaconAddresses to the obtained tx power values
                for (BeaconInformation beaconInformation : beaconsInformation) {
                    txPowerMap.put(beaconInformation.getBeaconAddress(), beaconInformation.getTxPower());
                }
            }

            @Override
            public void onFailure(Call<List<BeaconInformation>> call, Throwable t) {
                Log.e(LOG_TAG, "Failed to GET beacon tx power values: " + t.getLocalizedMessage());
            }
        });
    }

    public static DistanceProvider getInstance(Context context) {
        if (instance == null) {
            instance = new DistanceProvider(context.getApplicationContext());
        }

        return instance;
    }

    public void updateParameters(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        String distanceModelName = sharedPreferences.getString(context.getString(R.string.key_distance_model), "path_loss");
        mDistanceModel = DistanceModel.getDistanceModel(distanceModelName);

        String distanceMethodName = sharedPreferences.getString(context.getString(R.string.key_distance_method), "mean");
        mDistanceMethod = DistanceMethod.getDistanceMethod(distanceMethodName);
        mUseDynamicWindowSize = sharedPreferences.getBoolean(context.getString(R.string.key_dynamic_window), false);
        mWindowSize = sharedPreferences.getInt(context.getString(R.string.key_window_size), 5);
        mPathLossExponent = Float.parseFloat(sharedPreferences.getString(context.getString(R.string.key_path_loss_exponent), "2.0"));

        Log.d(LOG_TAG, "Updated parameters:");
        Log.d(LOG_TAG, "Distance model: " + mDistanceModel.getName());
        Log.d(LOG_TAG, "Distance method: " + mDistanceMethod.getName());
        Log.d(LOG_TAG, "Use dynamic window size: " + mUseDynamicWindowSize);
        Log.d(LOG_TAG, "Window size: " + mWindowSize);
        Log.d(LOG_TAG, "Path loss exponent: " + mPathLossExponent);
    }

    public void addMeasurement(String beaconAddress, int rssi) {
        LinkedList<Integer> rssiQueue = mBeaconMeasurementsMap.get(beaconAddress);

        if (rssiQueue == null) {
            rssiQueue = new LinkedList<>();
        }

        // Remove last value if the limit is reached
        if (rssiQueue.size() >= mWindowSize) {
            rssiQueue.remove();
        }

        rssiQueue.add(rssi);
        mBeaconMeasurementsMap.put(beaconAddress, rssiQueue);
    }

    public void addMeasurements(List<Beacon> beaconList) {
        for (Beacon beacon : beaconList) {
            addMeasurement(beacon.getBluetoothAddress(), beacon.getRssi());
        }
    }

    private double getFilteredRssi(Beacon beacon) {
        double rssi;

        switch (mDistanceMethod) {
            case MEAN:
            case AVERAGE:
                // Calculate average value of the last {WINDOW_SIZE} RSSI measurements to decrease variance
                rssi = calculateAverage(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()));
                break;
            case MEDIAN:
                // Calculate median of the last {WINDOW_SIZE} RSSI measurements to account for outliers
                rssi = calculateMedian(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()));
                break;
            case MODE:
                // Calculate mode of the last {WINDOW_SIZE} RSSI measurements to account for outliers
                rssi = calculateMode(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()));

                // Use median of no mode exists (all values occur only once)
                if (rssi == 1) {
                    Log.d(LOG_TAG, "No mode exists, using median");
                    return calculateMedian(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()));
                }
                break;
            default:
                // If no adequate distance method is specified, use single measurement
                rssi = beacon.getRssi();
        }

        return rssi;
    }

    public double getDistance(Beacon beacon) {
        // Use calibrated tx power if available, else fallback to received tx power value
        Integer txPower = txPowerMap.get(beacon.getBluetoothAddress());
        if (txPower == null) {
            txPower = beacon.getTxPower();
        }

        double rssi = getFilteredRssi(beacon);
        switch (mDistanceModel) {
            case PATH_LOSS:
                return Math.pow(10, (txPower - rssi) / (10 * mPathLossExponent));
            case FITTED_AVERAGE:
                return Math.exp((rssi + 71.317) / -5.094);
            case FITTED_LOS:
                return Math.exp((rssi + 66.765) / -6.338);
            case FITTED_NLOS:
                return Math.exp((rssi + 75.869) / -3.851);
        }

        // Default to log distance path loss model
        return Math.pow(10, (txPower - rssi) / (10 * mPathLossExponent));
    }

    private double calculateAverage(LinkedList<Integer> rssiList) {
        double average = 0.0;
        if (rssiList == null || rssiList.isEmpty()) {
            return average;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            average = rssiList.stream().mapToDouble(val -> val).average().orElse(0.0);
        } else {
            for (Integer rssiValue : rssiList) {
                average += rssiValue;
            }

            average /= rssiList.size();
        }

        return average;
    }

    private double calculateMedian(LinkedList<Integer> rssiList) {
        if (rssiList == null || rssiList.isEmpty()) {
            return 0.0;
        }

        List<Integer> sortedList = new ArrayList<>(rssiList);
        Collections.sort(sortedList);

        int middle = sortedList.size() / 2;
        if (sortedList.size() > 0 && sortedList.size() % 2 == 0) {
            return (sortedList.get(middle - 1) + sortedList.get(middle)) / 2.0;
        }

        return sortedList.get(middle);
    }

    private int calculateMode(LinkedList<Integer> rssiList) {
        if (rssiList == null || rssiList.isEmpty()) {
            return 0;
        }

        Map<Integer, Integer> frequencies = new HashMap<>();

        int mode = rssiList.get(0), maxCount = 0;
        for (Integer rssiValue : rssiList) {
            int count = frequencies.get(rssiValue) == null ? 1 : frequencies.get(rssiValue) + 1;
            frequencies.put(rssiValue, count);

            if (count > maxCount) {
                maxCount = count;
                mode = rssiValue;
            }
        }

        if (maxCount == 1) {
            return 1;
        }

        return mode;
    }

    /**
     * @param beaconList List of beacons involved in the calculation
     * @return Returns the confidence score based on the average standard deviation within the
     * sliding window
     */
    private double getDeviationConfidence(List<Beacon> beaconList) {
        double confidence = 0;
        for (Beacon beacon : beaconList) {
            LinkedList<Integer> rssiList = mBeaconMeasurementsMap.get(beacon.getBluetoothAddress());
            if (rssiList == null) {
                continue;
            }

            double mean = calculateAverage(rssiList);
            double standardDeviation = 0;
            for (Integer rssi : rssiList) {
                standardDeviation += Math.pow(rssi - mean, 2);
            }

            standardDeviation = Math.sqrt(standardDeviation / rssiList.size());
            
            confidence += standardDeviation;
        }

        confidence /= beaconList.size();

        return Math.exp(-confidence);
    }

    /**
     * @param beaconList List of beacons involved in the calculation
     * @return Returns the confidence score based on the filtered distance to the beacons
     */
    private double getDistanceConfidence(List<Beacon> beaconList) {
        // TODO: make range configurable, if other beacon models are going to be introduced
        int minimumRSSI = -100, maximumRSSI = -40;
        double slope = 1.0 / (maximumRSSI - minimumRSSI);
        double intercept = -(slope * minimumRSSI);

        double averageRSSI = 0;
        for (Beacon beacon : beaconList) {
            double rssi = getFilteredRssi(beacon);
            averageRSSI += rssi;
        }

        averageRSSI /= beaconList.size();

        // (Linearly) Interpolate between minimum and maximum RSSI to get a value in the range [0, 1]
        return slope * averageRSSI + intercept;
    }

    public double getConfidence(List<Beacon> beaconList) {
        if (beaconList.size() < 3) {
            return 0;
        }

        double deviationConfidence = getDeviationConfidence(beaconList);
        double distanceConfidence = getDistanceConfidence(beaconList);

        Log.d(LOG_TAG, "deviation conf: " + deviationConfidence + ", distance conf: " + distanceConfidence);

        return (deviationConfidence + distanceConfidence) / 2;
    }
}
