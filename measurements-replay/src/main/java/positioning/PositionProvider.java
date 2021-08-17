package positioning;

import model.Beacon;
import model.Coordinates;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PositionProvider {

    private boolean mAllowLessThanThreeBeacons = false;
    private PositioningMethod mDefaultPositioningMethod = PositioningMethod.WEIGHTED_CENTROID;
    private double mWeightExponent = 1.0;
    private double mPdfSharpness = 0.5;

    private static PositionProvider instance;

    private final Map<String, Coordinates> mBeaconCoordinatesMap = new HashMap<>();
    private final DistanceProvider mDistanceProvider;

    private PositionProvider() {
        mDistanceProvider = DistanceProvider.getInstance();

        mBeaconCoordinatesMap.put("00:CD:FF:0E:5E:B9", new Coordinates(25, 438));
        mBeaconCoordinatesMap.put("20:18:FF:00:3F:E4", new Coordinates(230, 608));
        mBeaconCoordinatesMap.put("20:18:FF:00:3F:E7", new Coordinates(390, 283));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:02", new Coordinates(575, 283));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:07", new Coordinates(600, 593));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:08", new Coordinates(820, 598));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:20", new Coordinates(920, 78));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:2C", new Coordinates(1120, 298));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:2D", new Coordinates(1100, 580));
        mBeaconCoordinatesMap.put("20:18:FF:00:40:2E", new Coordinates(740, 320));
    }

    public static PositionProvider getInstance() {
        if (instance == null) {
            instance = new PositionProvider();
        }

        return instance;
    }

    public void updateWeightExponent(double weightExponent) {
        mWeightExponent = weightExponent;
    }

    public void updatePdfSharpness(double pdfSharpness) {
        mPdfSharpness = pdfSharpness;
    }

    private Coordinates getCoordinates(Beacon beacon) {
        return mBeaconCoordinatesMap.get(beacon.getBluetoothAddress());
    }

    public Coordinates getPosition(List<Beacon> beaconList) throws PositioningException {
        return getPosition(beaconList, mDefaultPositioningMethod);
    }

    public Coordinates getPosition(List<Beacon> beaconList, PositioningMethod method) throws PositioningException {
        if (beaconList == null || beaconList.isEmpty()) {
            throw new PositioningException("No beacons detected, unable to determine position");
        }

        if (beaconList.size() < 3 && !mAllowLessThanThreeBeacons) {
            throw new PositioningException("Less than three beacons detected");
        }

        if (mBeaconCoordinatesMap.isEmpty()) {
            throw new PositioningException("Could not retrieve beacon coordinates, please check your internet connection");
        }

        if (beaconList.size() == 1) {
            Beacon beacon1 = beaconList.get(0);
            Coordinates beacon1Coordinates = getCoordinates(beacon1);

            return beacon1Coordinates;
        }

        if (beaconList.size() == 2) {
            Beacon beacon1 = beaconList.get(0);
            Beacon beacon2 = beaconList.get(1);

            return weightedMidPoint(beacon1, beacon2);
        }

        // List contains at least 3 beacons, perform lateration
        // Sort list by distance
        beaconList.sort(Comparator.comparingDouble(mDistanceProvider::getDistance));

        Beacon beacon1 = beaconList.get(0);
        Beacon beacon2 = beaconList.get(1);
        Beacon beacon3 = beaconList.get(2);

        Coordinates position = new Coordinates();
        switch (method) {
            case TRILATERATION:
                position = trilateration(beacon1, beacon2, beacon3);
                break;
            case WEIGHTED_CENTROID:
                position = weightedCentroid(beaconList);
                break;
            case PROBABILITY:
                position = probabilityBased(beaconList);
                break;
        }

        position.setConfidence(mDistanceProvider.getConfidence(Arrays.asList(beacon1, beacon2, beacon3)));
        return position;
    }

    private Coordinates weightedMidPoint(Beacon beacon1, Beacon beacon2) throws PositioningException {
        Coordinates beacon1Coordinates = getCoordinates(beacon1);
        Coordinates beacon2Coordinates = getCoordinates(beacon2);
        if (beacon1Coordinates == null || beacon2Coordinates == null) {
            throw new PositioningException("Could not retrieve beacon coordinates, please check your internet connection");
        }

        double x1 = beacon1Coordinates.getX();
        double y1 = beacon1Coordinates.getY();

        double x2 = beacon2Coordinates.getX();
        double y2 = beacon2Coordinates.getY();

        double r1 = mDistanceProvider.getDistance(beacon1) * 100;
        double r2 = mDistanceProvider.getDistance(beacon2) * 100;
        double weight = r1 / (r1 + r2);

        double x = (1 - weight) * x1 + weight * x2;
        double y = (1 - weight) * y1 + weight * y2;
        Coordinates weightedMidPoint = new Coordinates(x, y);

        return weightedMidPoint;
    }

    // Reference: http://paulbourke.net/geometry/circlesphere/
    private Coordinates trilateration(Beacon beacon1, Beacon beacon2, Beacon beacon3) throws PositioningException {
        Coordinates beacon1Coordinates = getCoordinates(beacon1);
        Coordinates beacon2Coordinates = getCoordinates(beacon2);
        Coordinates beacon3Coordinates = getCoordinates(beacon3);
        if (beacon1Coordinates == null || beacon2Coordinates == null || beacon3Coordinates == null) {
            throw new PositioningException("Could not retrieve beacon coordinates, please check your internet connection");
        }

        double x1 = beacon1Coordinates.getX();
        double y1 = beacon1Coordinates.getY();

        double x2 = beacon2Coordinates.getX();
        double y2 = beacon2Coordinates.getY();

        // Convert distances to centimeters
        double r1 = mDistanceProvider.getDistance(beacon1) * 100;
        double r2 = mDistanceProvider.getDistance(beacon2) * 100;

        double distance = Math.hypot((x2 - x1), (y2 - y1));
        if (distance < Math.abs(r1 - r2)) {
            // No intersections exist, one circle is contained in the other
            return weightedCentroid(Arrays.asList(beacon1, beacon2, beacon3));
        }

        if (distance > r1 + r2) {
            // No intersections (due to inaccuracy), use weighted centroid
            return weightedCentroid(Arrays.asList(beacon1, beacon2, beacon3));
        }

        // Calculate point p where the line through the intersection points crosses the line through
        // the circle centers
        Coordinates p = new Coordinates();
        double a = (r1 * r1 - r2 * r2 + distance * distance) / (2 * distance);
        p.setX(x1 + (a / distance) * (x2 - x1));
        p.setY(y1 + (a / distance) * (y2 - y1));

        if (distance == r1 + r2) {
            // Only one intersection exists
            return p;
        }

        // Two intersections exist (due to inaccuracy)
        double h = Math.sqrt(r1 * r1 - a * a);

        // First intersection point
        Coordinates firstIntersection = new Coordinates();
        firstIntersection.setX(p.getX() + (h / distance) * (y2 - y1));
        firstIntersection.setY(p.getY() - (h / distance) * (x2 - x1));

        // Second intersection point
        Coordinates secondIntersection = new Coordinates();
        secondIntersection.setX(p.getX() - (h / distance) * (y2 - y1));
        secondIntersection.setY(p.getY() + (h / distance) * (x2 - x1));

        // Choose intersection point closest to the third beacon
        if (Coordinates.calculateDistance(firstIntersection, beacon3Coordinates)
                < Coordinates.calculateDistance(secondIntersection, beacon3Coordinates)) {
            return firstIntersection;
        }

        return secondIntersection;
    }

    private Coordinates weightedCentroid(List<Beacon> beaconList) {
        return weightedCentroid(beaconList, mWeightExponent);
    }

    private Coordinates weightedCentroid(List<Beacon> beaconList, double weightExponent) {
        double x = 0, y = 0, weightSum = 0;
        for (Beacon beacon : beaconList) {
            Coordinates beaconCoordinates = getCoordinates(beacon);
            if (beaconCoordinates == null) {
                continue;
            }

            double distance = mDistanceProvider.getDistance(beacon);
            double weight = 1 / Math.pow(distance, weightExponent);

            x += beaconCoordinates.getX() * weight;
            y += beaconCoordinates.getY() * weight;
            weightSum += weight;
        }

        x /= weightSum;
        y /= weightSum;

        return new Coordinates(x, y);
    }

    private Coordinates probabilityBased(List<Beacon> beaconList) {
        return probabilityBased(beaconList, mPdfSharpness);
    }

    private Coordinates probabilityBased(List<Beacon> beaconList, double pdfSharpness) {
        int stepSize = 20;

        double maxProbability = -1;
        Coordinates bestCoordinates = null;

        // TODO: make boundaries configurable
        int xBoundary = 1200, yBoundary = 960;
        for (int x = 0; x <= xBoundary; x += stepSize) {
            for (int y = 0; y <= yBoundary; y += stepSize) {
                // Only include coordinates within the building
                if ((x > 700 && (x < 1140 || y > 620)) || (x <= 700 && y < 640 && y > 240)) {
                    Coordinates coordinates = new Coordinates(x + stepSize / 2.0, y + stepSize / 2.0);
                    double probability = getProbability(coordinates, beaconList, pdfSharpness);

                    if (probability > maxProbability) {
                        maxProbability = probability;
                        bestCoordinates = coordinates;
                    }
                }
            }
        }

        return bestCoordinates;
    }

    private double getProbability(Coordinates position, List<Beacon> beaconList, double pdfSharpness) {
        double probability = 1;
        for (Beacon beacon : beaconList) {
            Coordinates beaconCoordinates = getCoordinates(beacon);
            if (beaconCoordinates == null) {
                continue;
            }

            double distance = Coordinates.calculateDistance(position, beaconCoordinates) / 100;
            probability *= getProbability(distance, beacon, pdfSharpness);
        }

        return probability;
    }

    private double getProbability(double distance, Beacon beacon, double pdfSharpness) {
        return 1 / (Math.pow(distance - mDistanceProvider.getDistance(beacon), 2) + pdfSharpness);
    }
}
