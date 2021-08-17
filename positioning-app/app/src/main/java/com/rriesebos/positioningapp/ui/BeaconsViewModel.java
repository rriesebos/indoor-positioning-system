package com.rriesebos.positioningapp.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.altbeacon.beacon.Beacon;

import java.util.ArrayList;
import java.util.List;

public class BeaconsViewModel extends ViewModel {

    private final MutableLiveData<List<Beacon>> mBeaconList = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<Beacon>> getBeaconList() {
        return mBeaconList;
    }

    public void setBeaconList(List<Beacon> beaconList) {
        mBeaconList.postValue(beaconList);
    }
}
