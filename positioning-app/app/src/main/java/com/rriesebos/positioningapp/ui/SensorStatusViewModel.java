package com.rriesebos.positioningapp.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SensorStatusViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isBluetoothEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> isLocationEnabled = new MutableLiveData<>(true);

    public void setBluetoothEnabled(boolean isEnabled) {
        isBluetoothEnabled.postValue(isEnabled);
    }

    public void setLocationEnabled(boolean isEnabled) {
        isLocationEnabled.postValue(isEnabled);
    }

    public LiveData<Boolean> isBluetoothEnabled() {
        return isBluetoothEnabled;
    }

    public LiveData<Boolean> isLocationEnabled() {
        return isLocationEnabled;
    }
}
