package positioning;

import java.util.HashMap;
import java.util.Map;

public enum DistanceMethod {

    AVERAGE("average"),
    MEAN("mean"),
    MEDIAN("median"),
    MODE("mode");

    private final String name;
    private static final Map<String, DistanceMethod> valuesByName;

    static {
        valuesByName = new HashMap<>(values().length);
        for (DistanceMethod value : values()) {
            valuesByName.put(value.name, value);
        }
    }

    DistanceMethod(String name) {
        this.name = name;
    }

    public static DistanceMethod getDistanceMethod(String name) {
        return valuesByName.get(name);
    }

    public String getName() {
        return name;
    }
}
