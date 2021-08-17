package com.rriesebos.positioningapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationManagerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.Task;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.rriesebos.positioningapp.api.BeaconApi;
import com.rriesebos.positioningapp.api.PositioningApi;
import com.rriesebos.positioningapp.api.RetrofitClient;
import com.rriesebos.positioningapp.model.BeaconMeasurement;
import com.rriesebos.positioningapp.model.Coordinates;
import com.rriesebos.positioningapp.positioning.DistanceProvider;
import com.rriesebos.positioningapp.positioning.PositionProvider;
import com.rriesebos.positioningapp.positioning.PositioningException;
import com.rriesebos.positioningapp.ui.BeaconsViewModel;
import com.rriesebos.positioningapp.ui.RecordingViewModel;
import com.rriesebos.positioningapp.ui.SensorStatusViewModel;
import com.rriesebos.positioningapp.ui.settings.SettingsActivity;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.logging.LogManager;

import java.util.ArrayList;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_BLUETOOTH = 0;
    private static final int REQUEST_LOCATION = 1;

    private boolean mRecordPredictedCoordinates = true;
    private int mForegroundScanPeriod = 500;

    private boolean mRecordFixedNumber = false;
    private int mNumberOfMeasurements = 100;
    private int mMeasurementCount = 0;

    private final StartScanningLogger mStartScanningLogger = new StartScanningLogger();

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MenuItem recordButton;

    private BeaconManager mBeaconManager;
    private RangeNotifier mRangeNotifier;
    private final Region mRegion = new Region("allBeacons", null, null, null);
    private boolean mIsRecording = false;

    private SensorStatusViewModel mSensorStatusViewModel;
    private BeaconsViewModel mBeaconsViewModel;
    private RecordingViewModel mRecordingViewModel;

    private DistanceProvider mDistanceProvider;
    private PositionProvider mPositionProvider;

    private final PositioningApi mPositioningApi = RetrofitClient.getPositioningApi();
    private final BeaconApi mBeaconApi = RetrofitClient.getBeaconApi();

    private final ActivityResultLauncher<String> mRequestLocationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(getApplicationContext(), getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
                }
            });

    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        mSensorStatusViewModel.setBluetoothEnabled(true);
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        mSensorStatusViewModel.setBluetoothEnabled(false);
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver locationBroadcastReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action != null && action.equals(LocationManager.MODE_CHANGED_ACTION)) {
                LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                mSensorStatusViewModel.setLocationEnabled(LocationManagerCompat.isLocationEnabled(locationManager));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDistanceProvider = DistanceProvider.getInstance(getApplicationContext());
        mPositionProvider = PositionProvider.getInstance(getApplicationContext());

        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.pager);

        ViewPagerFragmentAdapter viewPagerFragmentAdapter =
                new ViewPagerFragmentAdapter(getSupportFragmentManager(), getLifecycle());
        viewPager.setAdapter(viewPagerFragmentAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.menu_beacons);
                    break;
                case 1:
                    tab.setText(R.string.menu_positioning);
                    break;
            }
        }).attach();

        mSensorStatusViewModel = new ViewModelProvider(this).get(SensorStatusViewModel.class);
        mBeaconsViewModel = new ViewModelProvider(this).get(BeaconsViewModel.class);
        mRecordingViewModel = new ViewModelProvider(this).get(RecordingViewModel.class);

        // Unbind/rebind beacon manager if bluetooth or location status changes
        mSensorStatusViewModel.isBluetoothEnabled().observe(this, isBluetoothEnabled -> {
            if (isBluetoothEnabled) {
                mBeaconManager.bind(this);
            } else {
                mBeaconManager.unbind(this);
            }
        });
        mSensorStatusViewModel.isLocationEnabled().observe(this, isLocationEnabled -> {
            if (isLocationEnabled) {
                mBeaconManager.bind(this);
            } else {
                mBeaconManager.unbind(this);
            }
        });

        // Request location permissions
        mRequestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);

        requestLocation();
        requestBluetooth();

        // Initialize beacon manager, add iBeacon layout and bind the beacon manager
        mBeaconManager = BeaconManager.getInstanceForApplication(getApplicationContext());
        mBeaconManager.getBeaconParsers().add(new BeaconParser("iBeacon")
                .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        mBeaconManager.setForegroundScanPeriod(mForegroundScanPeriod);
        mBeaconManager.setForegroundBetweenScanPeriod(0);
        mBeaconManager.setBackgroundMode(false);
        mBeaconManager.setEnableScheduledScanJobs(false);

        // Disable checking for device in remote android-distance.json,
        // found at: https://s3.amazonaws.com/android-beacon-library/android-distance.json
        BeaconManager.setDistanceModelUpdateUrl(null);

        // Show beacons separately even if they have the same UUID
        Beacon.setHardwareEqualityEnforced(true);

        mBeaconManager.bind(this);
        LogManager.setLogger(mStartScanningLogger);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter bluetoothFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, bluetoothFilter);

        // Register for broadcasts on LocationManager state change
        IntentFilter locationFilter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
        registerReceiver(locationBroadcastReceiver, locationFilter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateParameters();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_items, menu);
        recordButton = menu.findItem(R.id.record_button);

        // Update record button text to reflect isRecording state
        mRecordingViewModel.isRecording().observe(this, isRecording -> {
            if (isRecording) {
                // Reset measurement count
                mMeasurementCount = 0;

                recordButton.setTitle(getString(R.string.action_stop_recording));
            } else {
                recordButton.setTitle(getString(R.string.action_start_recording));
            }

            mIsRecording = isRecording;
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.record_button) {
            mRecordingViewModel.toggleIsRecording();

            return true;
        } else if (item.getItemId() == R.id.settings_button) {
            // Open settings activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateParameters() {
        int prevScanPeriod = mForegroundScanPeriod;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mRecordPredictedCoordinates = sharedPreferences.getBoolean(getString(R.string.key_record_predicted_coordinates), true);
        mForegroundScanPeriod = Integer.parseInt(sharedPreferences.getString(getString(R.string.key_scan_period), "500"));

        mRecordFixedNumber = sharedPreferences.getBoolean(getString(R.string.key_record_fixed_number), false);
        mNumberOfMeasurements = Integer.parseInt(sharedPreferences.getString(getString(R.string.key_number_of_measurements), "100"));

        if (prevScanPeriod == mForegroundScanPeriod) {
            try {
                mBeaconManager.updateScanPeriods();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to update foreground scan period", e);
            }
        }

        Log.d(LOG_TAG, "Updated parameters:");
        Log.d(LOG_TAG, "Record predicted coordinates: " + mRecordPredictedCoordinates);
        Log.d(LOG_TAG, "Beacon scan period: " + mForegroundScanPeriod);
        Log.d(LOG_TAG, "Record fixed number of measurements: " + mRecordFixedNumber);
        Log.d(LOG_TAG, "Number of measurements: " + mNumberOfMeasurements);
    }

    private void requestBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            // Bluetooth not supported by the device
            Toast.makeText(getApplicationContext(), getString(R.string.bluetooth_not_supported), Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            // Bluetooth is not enabled, request the user to enable it
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_BLUETOOTH);
        }
    }

    private void requestLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        // Lowest priority that still enables location
        locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
//                .setNeedBle(true)
                .setAlwaysShow(true);

        Task<LocationSettingsResponse> result =
                LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());

        result.addOnCompleteListener(task -> {
            try {
                task.getResult(ApiException.class);
            } catch (ApiException exception) {
                if (exception.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                    // Location settings are not satisfied. But could be fixed by showing the
                    // user a dialog.
                    try {
                        ResolvableApiException resolvable = (ResolvableApiException) exception;

                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        resolvable.startResolutionForResult(MainActivity.this, REQUEST_LOCATION);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if the user enables bluetooth/location when prompted
        switch (requestCode) {
            case REQUEST_BLUETOOTH:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        mSensorStatusViewModel.setBluetoothEnabled(true);
                        break;
                    case Activity.RESULT_CANCELED:
                        mSensorStatusViewModel.setBluetoothEnabled(false);
                        break;
                }
                break;
            case REQUEST_LOCATION:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        mSensorStatusViewModel.setLocationEnabled(true);
                        break;
                    case Activity.RESULT_CANCELED:
                        mSensorStatusViewModel.setLocationEnabled(false);
                        break;
                }
                break;
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        Log.d(LOG_TAG, "Connected to beacon service");

        mBeaconManager.removeAllRangeNotifiers();
        mRangeNotifier = (beacons, region) -> {
            Log.d(LOG_TAG, "Detected ranging information for " + beacons.size() + " beacons.");

            ArrayList<Beacon> beaconList = (ArrayList<Beacon>) beacons;
            mBeaconsViewModel.setBeaconList(beaconList);

            if (beaconList.isEmpty()) {
                return;
            }

            mDistanceProvider.addMeasurements(beaconList);

            if (mIsRecording) {
                // Send beacon measurements to the backend
                for (Beacon beacon : beaconList) {
                    if (beacon.getBluetoothAddress().equals("98:04:ED:BC:A6:B1")) {
                        continue;
                    }

                    sendBeaconMeasurement(beacon);
                }

                if (mRecordPredictedCoordinates) {
                    // Send predicted coordinates to the backend
                    try {
                        Coordinates predictedCoordinates = mPositionProvider.getPosition(beaconList);
                        sendPredictedCoordinates(predictedCoordinates);
                    } catch (PositioningException positioningException) {
                        Log.e(LOG_TAG, "PositioningException: " + positioningException.getLocalizedMessage());
                    }
                }
            }
        };

        mBeaconManager.addRangeNotifier(mRangeNotifier);
        try {
            mBeaconManager.startRangingBeaconsInRegion(mRegion);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private int classifyChannel(long receiveTime) {
        // Time in milliseconds
        final long scanInterval = 4096;
        // Start time is only initialized after the BeaconService of the altbeacon library is started
        long beaconScanningStartTime = mStartScanningLogger.getStartTime();
        long time = receiveTime - beaconScanningStartTime;

        // To compensate for clock drift between the clock of the internal radio and the device,
        // we restart scanning every 5 minutes (300000 milliseconds)
        if (time >= 300000) {
            Log.d(LOG_TAG, "Restarting scanning...");
            mStartScanningLogger.resetStartTime();
            stopScanning();

            mBeaconManager.bind(this);
        }

        // For robust detection we classify the channel by calculating in which scan interval
        // the receiveTime falls, disregarding the scan window. So if the time falls outside of the
        // scan window we still classify it.
        return 37 + (int) ((time / scanInterval) % 3);
    }

    private void sendBeaconMeasurement(Beacon beacon) {
        Log.d(LOG_TAG, "Sending measurement for " + beacon.getBluetoothAddress() + " to server.");

        long timestamp = System.currentTimeMillis();
        int rssi = beacon.getRssi();
        double distance =  mDistanceProvider.getDistance(beacon);
        int channel = classifyChannel(beacon.getFirstCycleDetectionTimestamp());

        final Call<ResponseBody> call = mBeaconApi.postBeaconMeasurements(
                beacon.getBluetoothAddress(),
                new BeaconMeasurement(timestamp, rssi, distance, channel)
        );

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                // Successfully send measurement to server
                if (mRecordFixedNumber) {
                    mMeasurementCount++;

                    if (mMeasurementCount >= mNumberOfMeasurements) {
                        mRecordingViewModel.setIsRecording(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(LOG_TAG, "Failed to POST beacon measurements: " + t.getLocalizedMessage());
            }
        });
    }

    private void sendPredictedCoordinates(Coordinates coordinates) {
        Log.d(LOG_TAG, "Sending predicted coordinates to server.");

        long timestamp = System.currentTimeMillis();
        final Call<ResponseBody> call = mPositioningApi.postPredictedCoordinates(
                timestamp,
                coordinates.getX(),
                coordinates.getY(),
                coordinates.getConfidence()
        );

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                // Successfully send measurement to server
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(LOG_TAG, "Failed to POST predicted coordinates: " + t.getLocalizedMessage());
            }
        });
    }

    private void stopScanning() {
        mBeaconManager.removeAllRangeNotifiers();
        mBeaconManager.unbind(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(bluetoothBroadcastReceiver);
        unregisterReceiver(locationBroadcastReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopScanning();
    }
}