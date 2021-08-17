package positioning;

import model.Beacon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DistanceProvider {

    private static final int MAX_WINDOW_SIZE = 10;

    private DistanceModel mDistanceModel = DistanceModel.PATH_LOSS;
    private DistanceMethod mDistanceMethod = DistanceMethod.MEDIAN;
    private int mWindowSize = 5;
    private double mPathLossExponent = 2;

    private final Map<String, LinkedList<Integer>> mBeaconMeasurementsMap = new HashMap<>();
    private final Map<String, Integer> txPowerMap = new HashMap<>();

    private static DistanceProvider instance;

    private DistanceProvider() {
        txPowerMap.put("00:CD:FF:0E:5E:B9", -68);
        txPowerMap.put("20:18:FF:00:3F:E4", -66);
        txPowerMap.put("20:18:FF:00:3F:E7", -66);
        txPowerMap.put("20:18:FF:00:40:02", -66);
        txPowerMap.put("20:18:FF:00:40:07", -66);
        txPowerMap.put("20:18:FF:00:40:08", -66);
        txPowerMap.put("20:18:FF:00:40:20", -64);
        txPowerMap.put("20:18:FF:00:40:2C", -67);
        txPowerMap.put("20:18:FF:00:40:2D", -68);
        txPowerMap.put("20:18:FF:00:40:2E", -66);
    }

    public static DistanceProvider getInstance() {
        if (instance == null) {
            instance = new DistanceProvider();
        }

        return instance;
    }

    public void updateParameters(DistanceModel distanceModel, DistanceMethod distanceMethod,
                                 int windowSize, double pathLossExponent) {
        mDistanceModel = distanceModel;
        mDistanceMethod = distanceMethod;
        mWindowSize = windowSize;
        mPathLossExponent = pathLossExponent;
    }

    public void updateParameters(DistanceModel distanceModel, DistanceMethod distanceMethod,
                                 int windowSize) {
        mDistanceModel = distanceModel;
        mDistanceMethod = distanceMethod;
        mWindowSize = windowSize;
    }

    private void addMeasurement(String beaconAddress, int rssi) {
        LinkedList<Integer> rssiQueue = mBeaconMeasurementsMap.get(beaconAddress);

        if (rssiQueue == null) {
            rssiQueue = new LinkedList<>();
        }

        // Remove last value if the limit is reached
        if (rssiQueue.size() >= MAX_WINDOW_SIZE) {
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

    public void clearMeasurements() {
        mBeaconMeasurementsMap.clear();
    }

    private double getFilteredRssi(Beacon beacon) {
        return getFilteredRssi(beacon, mDistanceMethod, mWindowSize);
    }

    private double getFilteredRssi(Beacon beacon, DistanceMethod distanceMethod, int windowSize) {
        double rssi;

        switch (distanceMethod) {
            case MEAN:
            case AVERAGE:
                // Calculate average value of the last {WINDOW_SIZE} RSSI measurements to decrease variance
                rssi = calculateAverage(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()), windowSize);
                break;
            case MEDIAN:
                // Calculate median of the last {WINDOW_SIZE} RSSI measurements to account for outliers
                rssi = calculateMedian(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()), windowSize);
                break;
            case MODE:
                // Calculate mode of the last {WINDOW_SIZE} RSSI measurements to account for outliers
                rssi = calculateMode(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()), windowSize);

                // Use median of no mode exists (all values occur only once)
                if (rssi == 1) {
                    return calculateMedian(mBeaconMeasurementsMap.get(beacon.getBluetoothAddress()), windowSize);
                }
                break;
            default:
                // If no adequate distance method is specified, use single measurement
                rssi = beacon.getRssi();
        }

        return rssi;
    }

    public double getDistance(Beacon beacon) {
        return getDistance(beacon, mDistanceModel, mDistanceMethod, mWindowSize, mPathLossExponent);
    }

    public double getDistance(Beacon beacon, DistanceModel distanceModel,
                              DistanceMethod distanceMethod, int windowSize,
                              double pathLossExponent) {
        // Use calibrated tx power if available, else fallback to received tx power value
        Integer txPower = txPowerMap.get(beacon.getBluetoothAddress());
        if (txPower == null) {
            txPower = beacon.getTxPower();
        }

        double rssi = getFilteredRssi(beacon, distanceMethod, windowSize);
        switch (distanceModel) {
            case PATH_LOSS:
                return Math.pow(10, (txPower - rssi) / (10 * pathLossExponent));
            case FITTED_AVERAGE:
                return Math.exp((rssi + 71.317) / -5.094);
            case FITTED_LOS:
                return Math.exp((rssi + 66.765) / -6.338);
            case FITTED_NLOS:
                return Math.exp((rssi + 75.869) / -3.851);
        }

        // Default to log distance path loss model
        return Math.pow(10, (txPower - rssi) / (10 * pathLossExponent));
    }

    private double calculateAverage(LinkedList<Integer> rssiList) {
        return calculateAverage(rssiList, mWindowSize);
    }

    private double calculateAverage(LinkedList<Integer> rssiList, int windowSize) {
        double average = 0.0;
        if (rssiList == null || rssiList.isEmpty()) {
            return average;
        }

        for (int i = 0; i < windowSize && i < rssiList.size(); i++) {
            int rssiValue = rssiList.get(i);
            average += rssiValue;
        }

        average /= windowSize;

        return average;
    }

    private double calculateMedian(LinkedList<Integer> rssiList) {
        return calculateMedian(rssiList, mWindowSize);
    }

    private double calculateMedian(LinkedList<Integer> rssiList, int windowSize) {
        if (rssiList == null || rssiList.isEmpty()) {
            return 0.0;
        }

        if (windowSize > MAX_WINDOW_SIZE) {
            windowSize = MAX_WINDOW_SIZE;
        }

        List<Integer> sortedList = new ArrayList<>(rssiList);
        sortedList = sortedList.subList(0, Math.min(windowSize, sortedList.size()));
        Collections.sort(sortedList);

        int middle = sortedList.size() / 2;
        if (sortedList.size() > 0 && sortedList.size() % 2 == 0) {
            return (sortedList.get(middle - 1) + sortedList.get(middle)) / 2.0;
        }

        return sortedList.get(middle);
    }

    private int calculateMode(LinkedList<Integer> rssiList) {
        return calculateMode(rssiList, mWindowSize);
    }

    private int calculateMode(LinkedList<Integer> rssiList, int windowSize) {
        if (rssiList == null || rssiList.isEmpty()) {
            return 0;
        }

        Map<Integer, Integer> frequencies = new HashMap<>();

        int mode = rssiList.get(0), maxCount = 0;
        for (int i = 0; i < windowSize && i < rssiList.size(); i++) {
            int rssiValue = rssiList.get(i);

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
            for (int i = 0; i < mWindowSize && i < rssiList.size(); i++) {
                int rssi = rssiList.get(i);
                standardDeviation += Math.pow(rssi - mean, 2);
            }

            standardDeviation = Math.sqrt(standardDeviation / (Math.min(mWindowSize, rssiList.size())));
            
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

        return (deviationConfidence + distanceConfidence) / 2;
    }
}
