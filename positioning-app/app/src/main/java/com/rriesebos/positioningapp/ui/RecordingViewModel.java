package com.rriesebos.positioningapp.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class RecordingViewModel extends ViewModel {

    private final MutableLiveData<Boolean> mIsRecording = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> mCheckpoint = new MutableLiveData<>(0);

    public LiveData<Boolean> isRecording() {
        return mIsRecording;
    }

    public void toggleIsRecording() {
        mIsRecording.postValue(!mIsRecording.getValue());

        // Reset checkpoint count when recording is stopped
        if (!mIsRecording.getValue()) {
            mCheckpoint.postValue(0);
        }
    }

    public void setIsRecording(Boolean isRecording) {
        mIsRecording.postValue(isRecording);

        // Reset checkpoint count when recording is stopped
        if (!isRecording) {
            mCheckpoint.postValue(0);
        }
    }

    public LiveData<Integer> getCheckpoint() {
        return mCheckpoint;
    }

    public int incrementCheckpoint() {
        int newValue = mCheckpoint.getValue() + 1;
        mCheckpoint.postValue(newValue);

        return newValue;
    }

    public void setCheckpoint(Integer checkpoint) {
        mCheckpoint.postValue(checkpoint);
    }
}

