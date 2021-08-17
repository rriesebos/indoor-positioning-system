package model;

import java.util.Date;

public class Beacon {

    private String bluetoothAddress;
    private int txPower = -59;
    private int rssi;

    private Date lastMeasurement;

    public Beacon(String bluetoothAddress, int rssi) {
        this.bluetoothAddress = bluetoothAddress;
        this.rssi = rssi;
    }

    public Beacon(String bluetoothAddress, int rssi, Date lastMeasurement) {
        this.bluetoothAddress = bluetoothAddress;
        this.rssi = rssi;
        this.lastMeasurement = lastMeasurement;
    }

    public String getBluetoothAddress() {
        return bluetoothAddress;
    }

    public void setBluetoothAddress(String bluetoothAddress) {
        this.bluetoothAddress = bluetoothAddress;
    }

    public int getTxPower() {
        return txPower;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public Date getLastMeasurement() {
        return lastMeasurement;
    }

    public void setLastMeasurement(Date lastMeasurement) {
        this.lastMeasurement = lastMeasurement;
    }

    @Override
    public String toString() {
        return "Beacon{" +
                "bluetoothAddress='" + bluetoothAddress + '\'' +
                ", txPower=" + txPower +
                ", rssi=" + rssi +
                ", lastMeasurement=" + lastMeasurement.getTime() +
                '}';
    }
}
