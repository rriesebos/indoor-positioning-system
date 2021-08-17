package positioning;

import java.util.HashMap;
import java.util.Map;

public enum PositioningMethod {

    TRILATERATION("trilateration"),
    WEIGHTED_CENTROID("weighted_centroid"),
    PROBABILITY("probability");

    private final String name;
    private static final Map<String, PositioningMethod> valuesByName;

    static {
        valuesByName = new HashMap<>(values().length);
        for (PositioningMethod value : values()) {
            valuesByName.put(value.name, value);
        }
    }

    PositioningMethod(String name) {
        this.name = name;
    }

    public static PositioningMethod getPositioningMethod(String name) {
        return valuesByName.get(name);
    }

    public String getName() {
        return name;
    }
}
