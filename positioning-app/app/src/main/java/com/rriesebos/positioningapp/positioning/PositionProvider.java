package com.rriesebos.positioningapp.positioning;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.rriesebos.positioningapp.R;
import com.rriesebos.positioningapp.api.BeaconApi;
import com.rriesebos.positioningapp.api.RetrofitClient;
import com.rriesebos.positioningapp.model.BeaconInformation;
import com.rriesebos.positioningapp.model.Coordinates;

import org.altbeacon.beacon.Beacon;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class PositionProvider {

    private static final String LOG_TAG = PositionProvider.class.getSimpleName();

    private boolean mAllowLessThanThreeBeacons = false;
    private PositioningMethod mDefaultPositioningMethod = PositioningMethod.WEIGHTED_CENTROID;
    private double mWeightExponent = 1.0;
    private double mPdfSharpness = 0.5;

    private static PositionProvider instance;

    private final Map<String, Coordinates> mBeaconCoordinatesMap = new HashMap<>();
    private final DistanceProvider mDistanceProvider;

    private PositionProvider(Context context) {
        // Initialize parameters from shared preferences
        updateParameters(context);

        mDistanceProvider = DistanceProvider.getInstance(context);

        // Retrieve beacon coordinates
        BeaconApi mBeaconApi = RetrofitClient.getBeaconApi();
        final Call<List<BeaconInformation>> call = mBeaconApi.getBeacons();

        call.enqueue(new Callback<List<BeaconInformation>>() {
            @Override
            public void onResponse(Call<List<BeaconInformation>> call, Response<List<BeaconInformation>> response) {
                List<BeaconInformation> beaconsInformation = response.body();
                if (beaconsInformation == null) {
                    Log.d(LOG_TAG, "No beacon information stored");
                    return;
                }

                // Initialize hash map to map beaconAddresses to the obtained beacon coordinates
                for (BeaconInformation beaconInformation : beaconsInformation) {
                    mBeaconCoordinatesMap.put(beaconInformation.getBeaconAddress(), beaconInformation.getCoordinates());
                }
            }

            @Override
            public void onFailure(Call<List<BeaconInformation>> call, Throwable t) {
                Log.e(LOG_TAG, "Failed to GET beacon coordinates: " + t.getLocalizedMessage());
            }
        });
    }

    public static PositionProvider getInstance(Context context) {
        if (instance == null) {
            instance = new PositionProvider(context.getApplicationContext());
        }

        return instance;
    }

    public void updateParameters(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        String positioningMethodName = sharedPreferences.getString(context.getString(R.string.key_positioning_method), "weighted_centroid");
        mDefaultPositioningMethod = PositioningMethod.getPositioningMethod(positioningMethodName);
        mAllowLessThanThreeBeacons = sharedPreferences.getBoolean(context.getString(R.string.key_allow_less_than_three_beacons), false);
        mWeightExponent = Float.parseFloat(sharedPreferences.getString(context.getString(R.string.key_weight_exponent), "1.0"));
        mPdfSharpness = Float.parseFloat(sharedPreferences.getString(context.getString(R.string.key_pdf_sharpness), "0.5"));

        Log.d(LOG_TAG, "Updated parameters:");
        Log.d(LOG_TAG, "Default positioning method: " + mDefaultPositioningMethod.getName());
        Log.d(LOG_TAG, "Weight function exponent: " + mWeightExponent);
        Log.d(LOG_TAG, "Probability density function sharpness: " + mPdfSharpness);
        Log.d(LOG_TAG, "Allow less than three beacons: " + mAllowLessThanThreeBeacons);
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

            Log.d(LOG_TAG, "Positioned at beacon");
            return beacon1Coordinates;
        }

        if (beaconList.size() == 2) {
            Beacon beacon1 = beaconList.get(0);
            Beacon beacon2 = beaconList.get(1);

            Log.d(LOG_TAG, "Positioned between beacons, using weighted midpoint");
            return weightedMidPoint(beacon1, beacon2);
        }

        // List contains at least 3 beacons, perform lateration
        // Sort list by distance
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            beaconList.sort(Comparator.comparingDouble(mDistanceProvider::getDistance));
        } else {
            Collections.sort(beaconList, (beacon, beacon2) ->
                    Double.compare(
                            mDistanceProvider.getDistance(beacon),
                            mDistanceProvider.getDistance(beacon2)
                    )
            );
        }

        Beacon beacon1 = beaconList.get(0);
        Beacon beacon2 = beaconList.get(1);
        Beacon beacon3 = beaconList.get(2);

        Coordinates position = new Coordinates();
        switch (method) {
            case TRILATERATION:
                position = trilateration(beacon1, beacon2, beacon3);
                break;
            case TRILATERATION2:
                position = trilateration2(beacon1, beacon2, beacon3);
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

        Log.d(LOG_TAG,"Weighted mid point: " + weightedMidPoint);
        return weightedMidPoint;
    }

    // Reference: http://paulbourke.net/geometry/circlesphere/
    private Coordinates trilateration(Beacon beacon1, Beacon beacon2, Beacon beacon3) throws PositioningException {
        Log.d(LOG_TAG, "Using beacons: " + beacon1.getBluetoothName() + " and " + beacon2.getBluetoothName());

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
            Log.d(LOG_TAG, "No intersections (one circle inside the other), using weighted centroid");
            return weightedCentroid(Arrays.asList(beacon1, beacon2, beacon3));
        }

        if (distance > r1 + r2) {
            // No intersections (due to inaccuracy), use weighted centroid
            Log.d(LOG_TAG, "No intersections, using weighted centroid");
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
            Log.d(LOG_TAG, "Current position: " + p);
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
            Log.d(LOG_TAG, "Current position: " + firstIntersection);
            return firstIntersection;
        }

        Log.d(LOG_TAG, "Current position: " + secondIntersection);
        return secondIntersection;
    }

    // Line intersection algorithm
    // Reference: https://www.researchgate.net/publication/332805083_Hybrid_TOA_Trilateration_Algorithm_Based_on_Line_Intersection_and_Comparison_Approach_of_Intersection_Distances
    private Coordinates trilateration2(Beacon beacon1, Beacon beacon2, Beacon beacon3) throws PositioningException {
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

        double x3 = beacon3Coordinates.getX();
        double y3 = beacon3Coordinates.getY();

        // Convert distances to centimeters
        double r1 = mDistanceProvider.getDistance(beacon1) * 100;
        double r2 = mDistanceProvider.getDistance(beacon2) * 100;
        double r3 = mDistanceProvider.getDistance(beacon3) * 100;

        double distance = Math.hypot((x2 - x1), (y2 - y1));
        if (distance < Math.abs(r1 - r2)) {
            // No intersections exist, one circle is contained in the other
            Log.d(LOG_TAG, "No intersections, one circle inside the other");
        }

        if (distance > r1 + r2) {
            // No intersections (due to inaccuracy), use weighted midpoint
            Log.d(LOG_TAG, "No intersections, no overlap");
        }

        // r_i = sqrt((x - x_i)^2 + (y - y_i)^2), i = 1, 2, 3
        // x + y + a_i*x + b_i*y + c_i = 0
        double a1 = -2 * x1;
        double a2 = -2 * x2;
        double a3 = -2 * x3;

        double b1 = -2 * y1;
        double b2 = -2 * y2;
        double b3 = -2 * y3;

        double c1 = (x1 * x1) + (y1 * y1) - (r1 * r1);
        double c2 = (x2 * x2) + (y2 * y2) - (r2 * r2);
        double c3 = (x3 * x3) + (y3 * y3) - (r3 * r3);

        double denominator = (a1 - a2) * (b2 - b3) - (a2 - a3) * (b1 - b2);
        double x = ((c2 - c1) * (b2 - b3) - (c3 - c2) * (b1 - b2)) / denominator;
        double y = ((c3 - c2) * (a1 - a2) - (c2 - c1) * (a2 - a3)) / denominator;

        return new Coordinates(x, y);
    }

    private Coordinates weightedCentroid(List<Beacon> beaconList) {
        double x = 0, y = 0, weightSum = 0;
        for (Beacon beacon : beaconList) {
            Coordinates beaconCoordinates = getCoordinates(beacon);
            if (beaconCoordinates == null) {
                continue;
            }

            double distance = mDistanceProvider.getDistance(beacon);
            double weight = 1 / Math.pow(distance, mWeightExponent);

            x += beaconCoordinates.getX() * weight;
            y += beaconCoordinates.getY() * weight;
            weightSum += weight;
        }

        x /= weightSum;
        y /= weightSum;

        return new Coordinates(x, y);
    }

    private Coordinates probabilityBased(List<Beacon> beaconList) {
        int stepSize = 20;

        double maxProbability = -1;
        Coordinates bestCoordinates = null;

        // TODO: make boundaries configurable
        int xBoundary = 1200, yBoundary = 960;
        for (int x = 0; x <= xBoundary; x += stepSize) {
            for (int y = 0; y <= yBoundary; y += stepSize) {
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

    private double getProbability(double distance, Beacon beacon) {
        return 1 / (Math.pow(distance - mDistanceProvider.getDistance(beacon), 2) + mPdfSharpness);
    }

    private double getProbability(Coordinates position, List<Beacon> beaconList) {
        double probability = 1;
        for (Beacon beacon : beaconList) {
            Coordinates beaconCoordinates = getCoordinates(beacon);
            if (beaconCoordinates == null) {
                continue;
            }

            double distance = Coordinates.calculateDistance(position, beaconCoordinates) / 100;
            probability *= getProbability(distance, beacon);
        }

        return probability;
    }
}
