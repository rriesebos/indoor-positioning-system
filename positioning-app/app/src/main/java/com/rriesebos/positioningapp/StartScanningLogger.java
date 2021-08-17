package com.rriesebos.positioningapp;

import android.util.Log;

import org.altbeacon.beacon.logging.Logger;

public class StartScanningLogger implements Logger {

    private long mStartTime = -1;

    private String formatString(String message, Object... args) {
        // If no varargs are supplied, treat it as a request to log the string without formatting.
        return args.length == 0 ? message : String.format(message, args);
    }

    @Override
    public void v(String tag, String message, Object... args) {
    }

    @Override
    public void v(Throwable t, String tag, String message, Object... args) {
    }

    @Override
    public void d(String tag, String message, Object... args) {
        // If 'Scan started' is logged, the altbeacon library has started the Bluetooth LE scan
        if (message.equals("Scan started") && mStartTime == -1) {
            mStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void d(Throwable t, String tag, String message, Object... args) {
        Log.d(tag, formatString(message, args), t);
    }

    @Override
    public void i(String tag, String message, Object... args) {
    }

    @Override
    public void i(Throwable t, String tag, String message, Object... args) {
    }

    @Override
    public void w(String tag, String message, Object... args) {
    }

    @Override
    public void w(Throwable t, String tag, String message, Object... args) {
    }

    @Override
    public void e(String tag, String message, Object... args) {
    }

    @Override
    public void e(Throwable t, String tag, String message, Object... args) {
    }

    public long getStartTime() {
        return mStartTime;
    }

    public void resetStartTime() {
        mStartTime = -1;
    }
}
