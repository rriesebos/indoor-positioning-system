import model.Beacon;
import model.Coordinates;
import model.Parameters;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import positioning.*;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {

    private static final String BASE_PATH = "src/main/resources/traces/";

    private static DistanceProvider distanceProvider = DistanceProvider.getInstance();
    private static PositionProvider positionProvider = PositionProvider.getInstance();

    private static List<Beacon> measurementsBuffer = new ArrayList<>();
    private static List<List<Beacon>> allMeasurements = new ArrayList<>();

    private static Map<Parameters, List<Coordinates>> traces = new HashMap<>();

    public static void main(String[] args) {
        for (int i = 1; i <= 10; i++) {
            String traceName = "trace" + i;
            System.out.println(traceName);

            JSONArray measurements = readJson(BASE_PATH + traceName + "/measurements.json");
            measurements.forEach(measurement -> readMeasurement((JSONObject) measurement));
            // Add final measurements buffer
            allMeasurements.add(new ArrayList<>(measurementsBuffer));

            tryAllParameters(traceName);

            // Clear measurements window and buffers
            distanceProvider.clearMeasurements();
            measurementsBuffer.clear();
            allMeasurements.clear();
            traces.clear();
        }
    }

    private static JSONArray readJson(String path) {
        JSONParser jsonParser = new JSONParser();

        try (Reader reader = new FileReader(path)) {
            JSONArray jsonArray = (JSONArray) jsonParser.parse(reader);

            // Sort json array by time (ascending)
            jsonArray.sort((Comparator<JSONObject>) (a, b) -> {
                String firstDateString = (String) a.get("system.totimestamp(timeuuid)");
                String secondDateString = (String) b.get("system.totimestamp(timeuuid)");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                try {
                    Date firstDate = sdf.parse(firstDateString);
                    Date secondDate = sdf.parse(secondDateString);

                    return firstDate.compareTo(secondDate);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return 0;
            });

            return jsonArray;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static void readMeasurement(JSONObject measurement) {
        final long scanPeriodThreshold = 400;

        String beaconAddress = (String) measurement.get("beacon_address");
        int rssi = ((Long) measurement.get("rssi")).intValue();
        String lastMeasurementString = (String) measurement.get("system.totimestamp(timeuuid)");

        Date lastMeasurement = null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        try {
            lastMeasurement = sdf.parse(lastMeasurementString);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Beacon beacon = new Beacon(beaconAddress, rssi, lastMeasurement);

        // Recover beacons detected in each scan interval (500 ms)
        if (!measurementsBuffer.isEmpty()) {
            Date previousDate = measurementsBuffer.get(measurementsBuffer.size() - 1).getLastMeasurement();
            if (previousDate != null && lastMeasurement != null) {
                long difference = lastMeasurement.getTime() - previousDate.getTime();

                if (difference > scanPeriodThreshold) {
                    allMeasurements.add(new ArrayList<>(measurementsBuffer));
                    measurementsBuffer.clear();
                }
            }
        }

        measurementsBuffer.add(beacon);
    }

    private static void addToTrace(Parameters parameters, Coordinates coordinates) {
        List<Coordinates> trace = traces.get(parameters);
        if (trace == null) {
            trace = new ArrayList<>();
        }

        trace.add(coordinates);
        traces.put(new Parameters(parameters), trace);
    }

    private static void getAllPositions(List<Beacon> beaconList) {
        final int[] windowSizes = {1, 5, 10, 15, 20};
        final List<DistanceMethod> distanceMethods = new ArrayList<>(
                Arrays.asList(DistanceMethod.MEAN, DistanceMethod.MEDIAN, DistanceMethod.MODE)
        );
        final List<DistanceModel> distanceModels = new ArrayList<>(
                Arrays.asList(DistanceModel.PATH_LOSS,
                        DistanceModel.FITTED_AVERAGE,
                        DistanceModel.FITTED_LOS,
                        DistanceModel.FITTED_NLOS)
        );

        final List<PositioningMethod> positioningMethods = new ArrayList<>(
                Arrays.asList(PositioningMethod.TRILATERATION,
                        PositioningMethod.WEIGHTED_CENTROID,
                        PositioningMethod.PROBABILITY)
        );

        Parameters params = new Parameters();
        for (int windowSize : windowSizes) {
            params.setWindowSize(windowSize);

            if (windowSize == 1) {
                params.setDistanceMethod(null);

                for (DistanceModel distanceModel : distanceModels) {
                    params.setDistanceModel(distanceModel);

                    if (distanceModel == DistanceModel.PATH_LOSS) {
                        for (double exponent = 1.5; exponent < 3.6; exponent += 0.1) {
                            params.setPathLossExponent(exponent);

                            distanceProvider.updateParameters(distanceModel, DistanceMethod.MEAN, windowSize, exponent);

                            getPositions(beaconList, positioningMethods, params);
                        }
                    } else {
                        params.setPathLossExponent(-1);

                        distanceProvider.updateParameters(distanceModel, DistanceMethod.MEAN, windowSize);

                        getPositions(beaconList, positioningMethods, params);
                    }
                }
            } else {
                for (DistanceMethod distanceMethod : distanceMethods) {
                    params.setDistanceMethod(distanceMethod);

                    for (DistanceModel distanceModel : distanceModels) {
                        params.setDistanceModel(distanceModel);

                        if (distanceModel == DistanceModel.PATH_LOSS) {
                            for (double exponent = 1.5; exponent < 3.6; exponent += 0.1) {
                                params.setPathLossExponent(exponent);

                                distanceProvider.updateParameters(distanceModel, distanceMethod, windowSize, exponent);

                                getPositions(beaconList, positioningMethods, params);
                            }
                        } else {
                            params.setPathLossExponent(-1);

                            distanceProvider.updateParameters(distanceModel, distanceMethod, windowSize);

                            getPositions(beaconList, positioningMethods, params);
                        }
                    }
                }
            }
        }
    }

    private static void getPositions(List<Beacon> beaconList, List<PositioningMethod> positioningMethods, Parameters params) {
        for (PositioningMethod positioningMethod : positioningMethods) {
            params.setPositioningMethod(positioningMethod);

            // Reset weight exponent and pdf sharpness
            params.setWeightExponent(-1);
            params.setPdfSharpness(-1);

            try {
                switch (positioningMethod) {
                    case WEIGHTED_CENTROID:
                        for (double weightExponent = 0.5; weightExponent < 3.6; weightExponent += 0.5) {
                            params.setWeightExponent(weightExponent);
                            positionProvider.updateWeightExponent(weightExponent);

                            Coordinates position = positionProvider.getPosition(beaconList, positioningMethod);
                            addToTrace(params, position);
                        }
                        break;
                    case PROBABILITY:
                        for (double pdfSharpness = 0.5; pdfSharpness < 3.6; pdfSharpness += 0.5) {
                            params.setPdfSharpness(pdfSharpness);
                            positionProvider.updatePdfSharpness(pdfSharpness);

                            Coordinates position = positionProvider.getPosition(beaconList, positioningMethod);
                            addToTrace(params, position);
                        }
                        break;
                    default:
                        Coordinates position = positionProvider.getPosition(beaconList, positioningMethod);
                        addToTrace(params, position);
                }
            } catch (PositioningException e) {
                // Ignore positioning errors generated while replaying
            }
        }
    }

    private static void tryAllParameters(String traceName) {
        for (List<Beacon> measurements : allMeasurements) {
            distanceProvider.addMeasurements(measurements);

            getAllPositions(measurements);
        }

        for (Map.Entry<Parameters, List<Coordinates>> entry : traces.entrySet()) {
            Parameters params = entry.getKey();
            List<Coordinates> trace = entry.getValue();

            saveTrace(params, trace, traceName);
        }
    }

    private static void saveTrace(Parameters parameters, List<Coordinates> trace, String traceName) {
        try {
            Files.createDirectories(Paths.get("replayed-traces/" + traceName));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (FileWriter file = new FileWriter("replayed-traces/" + traceName + "/" + parameters.toFileName() + ".json")) {
            JSONArray traceJson = new JSONArray();
            JSONArray traceTimestamps = readJson(BASE_PATH + traceName + "/trace.json");

            for (int i = 0; i < trace.size(); i++) {
                Coordinates coordinates = trace.get(i);
                JSONObject coordinatesJson = coordinatesToJson(coordinates);

                // Add positioning timestamps (used for ground truth interpolation)
                String timestamp = (String) ((JSONObject) traceTimestamps.get(i)).get("system.totimestamp(timeuuid)");
                coordinatesJson.put("time", timestamp);

                traceJson.add(coordinatesJson);
            }

            file.write(traceJson.toJSONString());
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JSONObject coordinatesToJson(Coordinates coordinates) {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("x", coordinates.getX());
        jsonObject.put("y", coordinates.getY());
        jsonObject.put("confidence", coordinates.getConfidence());

        return jsonObject;
    }
}
