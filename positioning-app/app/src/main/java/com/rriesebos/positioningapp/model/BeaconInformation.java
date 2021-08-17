package com.rriesebos.positioningapp.model;

public class BeaconInformation {

    String beaconAddress;
    int txPower;
    Coordinates coordinates;

    public BeaconInformation(String beaconAddress, int txPower, Coordinates coordinates) {
        this.beaconAddress = beaconAddress;
        this.txPower = txPower;
        this.coordinates = coordinates;
    }

    public String getBeaconAddress() {
        return beaconAddress;
    }

    public void setBeaconAddress(String beaconAddress) {
        this.beaconAddress = beaconAddress;
    }

    public int getTxPower() {
        return txPower;
    }

    public void setTxPower(int txPower) {
        this.txPower = txPower;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    @Override
    public String toString() {
        return "BeaconInformation{" +
                "beaconAddress: '" + beaconAddress + '\'' +
                ", txPower: " + txPower +
                ", coordinates: " + coordinates +
                '}';
    }
}
