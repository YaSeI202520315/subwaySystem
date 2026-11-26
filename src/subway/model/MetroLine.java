package subway.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一条地铁线路：按运营顺序排列的站点与相邻区间里程。 */
public final class MetroLine {

    private final String name;
    private final List<String> stations = new ArrayList<>();
    private final List<Double> segmentKm = new ArrayList<>();

    public MetroLine(String name) {
        this.name = name;
    }

    public void appendSegment(String from, String to, double km) {
        if (stations.isEmpty()) {
            stations.add(from);
        } else if (!stations.get(stations.size() - 1).equals(from)) {
            throw new IllegalArgumentException(
                    name + " 数据不连续：期望从 " + stations.get(stations.size() - 1)
                            + " 出发，实际为 " + from);
        }
        stations.add(to);
        segmentKm.add(km);
    }

    public String name() {
        return name;
    }

    public int stationCount() {
        return stations.size();
    }

    public List<String> stations() {
        return Collections.unmodifiableList(stations);
    }

    public List<Double> segmentKm() {
        return Collections.unmodifiableList(segmentKm);
    }
}