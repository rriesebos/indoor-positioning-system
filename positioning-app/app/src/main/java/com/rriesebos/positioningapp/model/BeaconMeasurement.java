package com.rriesebos.positioningapp.model;

import org.altbeacon.beacon.Beacon;

public class BeaconMeasurement {

    long timestamp;
    int rssi;
    double distance;
    int channel;

    public BeaconMeasurement(long timestamp, int rssi, double distance, int channel) {
        this.timestamp = timestamp;
        this.rssi = rssi;
        this.distance = distance;
        this.channel = channel;
    }

    public BeaconMeasurement(Beacon beacon) {
        this.rssi = beacon.getRssi();
        this.distance = beacon.getDistance();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }
}
