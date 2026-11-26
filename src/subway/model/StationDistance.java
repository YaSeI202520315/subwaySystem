package subway.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 距离查询结果：站名、所在线路、距参照站的里程（km）。 */
public final class StationDistance {

    private final String station;
    private final Set<String> lines;
    private final double km;

    public StationDistance(String station, Set<String> lines, double km) {
        this.station = station;
        this.lines = Collections.unmodifiableSet(new LinkedHashSet<>(lines));
        this.km = km;
    }

    public String station() {
        return station;
    }

    public Set<String> lines() {
        return lines;
    }

    public double km() {
        return km;
    }

    @Override
    public String toString() {
        String label = station.endsWith("站") ? station : station + "站";
        return "<" + label + "，" + String.join("、", lines) + "，"
                + String.format("%.3f", km) + "km>";
    }
}