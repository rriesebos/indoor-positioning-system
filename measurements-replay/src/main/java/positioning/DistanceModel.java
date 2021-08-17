package positioning;

import java.util.HashMap;
import java.util.Map;

public enum DistanceModel {

    PATH_LOSS("path_loss"),
    FITTED_AVERAGE("fitted_average"),
    FITTED_LOS("fitted_los"),
    FITTED_NLOS("fitted_nlos");

    private final String name;
    private static final Map<String, DistanceModel> valuesByName;

    static {
        valuesByName = new HashMap<>(values().length);
        for (DistanceModel value : values()) {
            valuesByName.put(value.name, value);
        }
    }

    DistanceModel(String name) {
        this.name = name;
    }

    public static DistanceModel getDistanceModel(String name) {
        return valuesByName.get(name);
    }

    public String getName() {
        return name;
    }
}