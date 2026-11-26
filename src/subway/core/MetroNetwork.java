package subway.core;

import subway.exception.InvalidInputException;
import subway.model.MetroLine;
import subway.model.StationDistance;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/**
 * 武汉地铁路网（加权无向图）。
 * 数据文件 subway.txt 每行 Tab 分隔：线路名、起点站、终点站、区间距离(km)。
 */
public final class MetroNetwork {

    /** 功能3：合理路线最多返回的条数。 */
    public static final int MAX_REASONABLE_ROUTES = 10;

    /** 功能3：候选路线枚举的安全上限（正常情况下远达不到）。 */
    private static final int MAX_ROUTE_CANDIDATES = 50000;

    private final Map<String, MetroLine> lines = new LinkedHashMap<>();
    private final Map<String, Set<String>> stationLines = new HashMap<>();
    private final Map<String, Map<String, Double>> adjacency = new HashMap<>();
    private final Map<String, Set<String>> segmentLines = new HashMap<>();

    private MetroNetwork() {
    }

    public static MetroNetwork load(Path file) throws IOException {
        MetroNetwork network = new MetroNetwork();
        int lineNo = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                lineNo++;
                String text = raw.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                String[] parts = text.split("\t");
                if (parts.length != 4) {
                    throw new IOException("第 " + lineNo + " 行格式错误，应为：线路名<TAB>起点站<TAB>终点站<TAB>距离");
                }
                String lineName = parts[0].trim();
                String from = parts[1].trim();
                String to = parts[2].trim();
                double km;
                try {
                    km = Double.parseDouble(parts[3].trim());
                } catch (NumberFormatException e) {
                    throw new IOException("第 " + lineNo + " 行距离不是数字：" + parts[3]);
                }
                if (lineName.isEmpty() || from.isEmpty() || to.isEmpty() || km <= 0) {
                    throw new IOException("第 " + lineNo + " 行数据不合法");
                }
                network.addSegment(lineName, from, to, km);
            }
        }
        if (network.lines.isEmpty()) {
            throw new IOException("subway.txt 中没有有效数据");
        }
        return network;
    }

    private void addSegment(String lineName, String from, String to, double km) {
        lines.computeIfAbsent(lineName, MetroLine::new).appendSegment(from, to, km);
        stationLines.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(lineName);
        stationLines.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(lineName);
        putEdge(from, to, km);
        putEdge(to, from, km);
        segmentLines.computeIfAbsent(segmentKey(from, to), k -> new LinkedHashSet<>()).add(lineName);
    }

    private void putEdge(String a, String b, double km) {
        Map<String, Double> neighbors = adjacency.computeIfAbsent(a, k -> new TreeMap<>());
        Double old = neighbors.get(b);
        if (old == null || km < old) {
            neighbors.put(b, km);
        }
    }

    private static String segmentKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public int lineCount() {
        return lines.size();
    }

    public int stationCount() {
        return stationLines.size();
    }

    public Set<String> lineNames() {
        return Collections.unmodifiableSet(lines.keySet());
    }

    public MetroLine line(String lineName) throws InvalidInputException {
        MetroLine line = lines.get(lineName);
        if (line == null) {
            throw new InvalidInputException("线路不存在：" + lineName);
        }
        return line;
    }

    public int stationCountOfLine(String lineName) throws InvalidInputException {
        return line(lineName).stationCount();
    }

    public Set<String> linesOfStation(String station) throws InvalidInputException {
        requireStation(station);
        return Collections.unmodifiableSet(stationLines.get(station));
    }

    public int lineCountOfStation(String station) throws InvalidInputException {
        requireStation(station);
        return stationLines.get(station).size();
    }

    private void requireStation(String station) throws InvalidInputException {
        if (station == null || !stationLines.containsKey(station)) {
            throw new InvalidInputException("站点不存在：" + station);
        }
    }

    /** 功能1：返回所有中转站（至少两条线路经过）：站名 -> 线路集合。 */
    public Map<String, Set<String>> transferStations() {
        Map<String, Set<String>> result = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : stationLines.entrySet()) {
            if (entry.getValue().size() >= 2) {
                result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
            }
        }
        return result;
    }

    /** 功能2：返回距 station 小于 limitKm 的所有站点（按距离升序）。 */
    public Map<String, StationDistance> stationsWithinDistance(String station, double limitKm)
            throws InvalidInputException {
        requireStation(station);
        if (Double.isNaN(limitKm) || limitKm <= 0) {
            throw new InvalidInputException("距离参数必须大于 0：" + limitKm);
        }
        Map<String, Double> distance = dijkstra(station);
        List<Map.Entry<String, Double>> hits = new ArrayList<>();
        for (Map.Entry<String, Double> entry : distance.entrySet()) {
            if (!entry.getKey().equals(station) && entry.getValue() < limitKm) {
                hits.add(entry);
            }
        }
        hits.sort(Comparator.comparingDouble(Map.Entry::getValue));
        Map<String, StationDistance> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> hit : hits) {
            result.put(hit.getKey(),
                    new StationDistance(hit.getKey(), stationLines.get(hit.getKey()), hit.getValue()));
        }
        return result;
    }

    /**
     * 功能3：返回“合理乘车路线”。
     * 约束与筛选规则：
     * 1. 路径不重复经过站点；
     * 2. 总里程不超过最短路线里程的 1.5 倍；
     * 3. 换乘次数不超过两站间最少换乘次数 + 2；
     * 4. 删除被其它路线在里程、站数、换乘次数上全面优于的路线
     *    （三项都不差且至少一项更优）；
     * 5. 按里程升序排列，最多返回 {@link #MAX_REASONABLE_ROUTES} 条。
     */
    public List<Route> reasonableRoutes(String from, String to) throws InvalidInputException {
        requireStation(from);
        requireStation(to);
        List<String> shortest = shortestPath(from, to);
        double maxKm = pathDistance(shortest) * 1.5 + 1e-9;
        int maxTransfers = minTransfers(from, to) + 2;

        List<Route> candidates = new ArrayList<>();
        List<String> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        path.add(from);
        visited.add(from);
        collectRoutes(from, to, 0.0, null, maxKm, maxTransfers, visited, path, candidates);

        candidates.sort(Comparator.comparingDouble(Route::km)
                .thenComparingInt(Route::stationCount)
                .thenComparingInt(Route::transfers));
        List<Route> kept = new ArrayList<>();
        for (Route candidate : candidates) {
            boolean dominated = false;
            for (Route better : kept) {
                if (dominates(better, candidate)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        if (kept.size() > MAX_REASONABLE_ROUTES) {
            return new ArrayList<>(kept.subList(0, MAX_REASONABLE_ROUTES));
        }
        return kept;
    }

    /** 一条候选乘车路线：站点序列、总里程、该序列可实现的最少换乘次数。 */
    public static final class Route {
        private final List<String> stations;
        private final double km;
        private final int transfers;

        Route(List<String> stations, double km, int transfers) {
            this.stations = Collections.unmodifiableList(new ArrayList<>(stations));
            this.km = Math.round(km * 1000.0) / 1000.0;
            this.transfers = transfers;
        }

        public List<String> stations() {
            return stations;
        }

        public double km() {
            return km;
        }

        public int stationCount() {
            return stations.size();
        }

        public int transfers() {
            return transfers;
        }
    }

    private static boolean dominates(Route a, Route b) {
        if (a.km > b.km || a.stationCount() > b.stationCount() || a.transfers > b.transfers) {
            return false;
        }
        return a.km < b.km || a.stationCount() < b.stationCount() || a.transfers < b.transfers;
    }

    /**
     * DFS 枚举无环候选路线，边搜索边剪枝。
     * cost 记录“到达当前站且乘坐某线路”时的最少换乘次数；
     * 换乘次数只增不减，一旦最小值超过上限即可放弃该分支。
     */
    private void collectRoutes(String current, String target, double km, Map<String, Integer> cost,
                               double maxKm, int maxTransfers, Set<String> visited,
                               List<String> path, List<Route> out) {
        if (out.size() >= MAX_ROUTE_CANDIDATES) {
            return;
        }
        if (current.equals(target)) {
            out.add(new Route(path, km, cost == null ? 0 : Collections.min(cost.values())));
            return;
        }
        int bestPrev = cost == null ? 0 : Collections.min(cost.values());
        if (cost != null && bestPrev > maxTransfers) {
            return;
        }
        for (Map.Entry<String, Double> next : adjacency.getOrDefault(current, Collections.emptyMap()).entrySet()) {
            String neighbor = next.getKey();
            if (visited.contains(neighbor)) {
                continue;
            }
            double nextKm = km + next.getValue();
            if (nextKm > maxKm) {
                continue;
            }
            Set<String> lines = segmentLines.getOrDefault(segmentKey(current, neighbor), Collections.emptySet());
            Map<String, Integer> nextCost = new HashMap<>();
            for (String line : lines) {
                if (cost == null) {
                    nextCost.put(line, 0);
                } else {
                    int same = cost.getOrDefault(line, Integer.MAX_VALUE);
                    nextCost.put(line, Math.min(same, bestPrev + 1));
                }
            }
            if (Collections.min(nextCost.values()) > maxTransfers) {
                continue;
            }
            visited.add(neighbor);
            path.add(neighbor);
            collectRoutes(neighbor, target, nextKm, nextCost, maxKm, maxTransfers, visited, path, out);
            path.remove(path.size() - 1);
            visited.remove(neighbor);
        }
    }

    /** 两站之间的最少换乘次数（与里程无关，对“站点+线路”状态做 0-1 BFS）。 */
    private int minTransfers(String from, String to) {
        if (from.equals(to)) {
            return 0;
        }
        Map<String, Map<String, Integer>> dist = new HashMap<>();
        Deque<Map.Entry<String, String>> deque = new ArrayDeque<>();
        for (String line : stationLines.get(from)) {
            setState(dist, from, line, 0);
            deque.offerLast(new AbstractMap.SimpleImmutableEntry<>(from, line));
        }
        while (!deque.isEmpty()) {
            Map.Entry<String, String> state = deque.pollFirst();
            String station = state.getKey();
            String line = state.getValue();
            int current = dist.get(station).get(line);
            if (station.equals(to)) {
                return current;
            }
            for (Map.Entry<String, Double> next
                    : adjacency.getOrDefault(station, Collections.emptyMap()).entrySet()) {
                String neighbor = next.getKey();
                if (!segmentLines.getOrDefault(segmentKey(station, neighbor), Collections.emptySet())
                        .contains(line)) {
                    continue;
                }
                Integer old = stateOrNull(dist, neighbor, line);
                if (old == null || current < old) {
                    setState(dist, neighbor, line, current);
                    deque.offerFirst(new AbstractMap.SimpleImmutableEntry<>(neighbor, line));
                }
            }
            for (String other : stationLines.get(station)) {
                if (other.equals(line)) {
                    continue;
                }
                Integer old = stateOrNull(dist, station, other);
                if (old == null || current + 1 < old) {
                    setState(dist, station, other, current + 1);
                    deque.offerLast(new AbstractMap.SimpleImmutableEntry<>(station, other));
                }
            }
        }
        throw new IllegalStateException("换乘次数计算失败：" + from + " -> " + to);
    }

    private static Integer stateOrNull(Map<String, Map<String, Integer>> dist, String station, String line) {
        Map<String, Integer> byLine = dist.get(station);
        return byLine == null ? null : byLine.get(line);
    }

    private static void setState(Map<String, Map<String, Integer>> dist, String station, String line, int value) {
        dist.computeIfAbsent(station, k -> new HashMap<>()).put(line, value);
    }

    /** 功能4：按总里程求最短路径，返回从起点到终点的站点序列。 */
    public List<String> shortestPath(String from, String to) throws InvalidInputException {
        requireStation(from);
        requireStation(to);
        Map<String, Double> distance = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Map.Entry<String, Double>> queue =
                new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
        distance.put(from, 0.0);
        queue.offer(new AbstractMap.SimpleImmutableEntry<>(from, 0.0));
        while (!queue.isEmpty()) {
            Map.Entry<String, Double> current = queue.poll();
            if (current.getValue() > distance.getOrDefault(current.getKey(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (current.getKey().equals(to)) {
                break;
            }
            for (Map.Entry<String, Double> next
                    : adjacency.getOrDefault(current.getKey(), Collections.emptyMap()).entrySet()) {
                double candidate = current.getValue() + next.getValue();
                if (candidate < distance.getOrDefault(next.getKey(), Double.POSITIVE_INFINITY) - 1e-9) {
                    distance.put(next.getKey(), candidate);
                    previous.put(next.getKey(), current.getKey());
                    queue.offer(new AbstractMap.SimpleImmutableEntry<>(next.getKey(), candidate));
                }
            }
        }
        if (!distance.containsKey(to)) {
            throw new InvalidInputException("两站之间不可达：" + from + " -> " + to);
        }
        LinkedList<String> path = new LinkedList<>();
        String station = to;
        while (station != null) {
            path.addFirst(station);
            if (station.equals(from)) {
                return path;
            }
            station = previous.get(station);
        }
        throw new IllegalStateException("最短路径回溯失败");
    }

    /** 路径总里程（公里），相邻站取两站间最小区间距离，保留 3 位小数。 */
    public double pathDistance(List<String> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        double sum = 0;
        for (int i = 0; i + 1 < path.size(); i++) {
            String a = path.get(i);
            String b = path.get(i + 1);
            Double km = adjacency.getOrDefault(a, Collections.emptyMap()).get(b);
            if (km == null) {
                throw new IllegalArgumentException("路径中相邻两站不相连：" + a + " -> " + b);
            }
            sum += km;
        }
        return Math.round(sum * 1000.0) / 1000.0;
    }

    /** 一段同一线路上的连续乘车区间。 */
    public static final class RideSegment {
        private final String line;
        private final String from;
        private final String to;

        RideSegment(String line, String from, String to) {
            this.line = line;
            this.from = from;
            this.to = to;
        }

        public String line() {
            return line;
        }

        public String from() {
            return from;
        }

        public String to() {
            return to;
        }
    }

    /** 将站点序列切分为“同一线路连续乘坐”的若干段，线路变化处即为换乘。 */
    public List<RideSegment> rideSegments(List<String> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        List<RideSegment> segments = new ArrayList<>();
        if (path.size() == 1) {
            return segments;
        }
        String currentLine = chooseLine(path.get(0), path.get(1), null);
        String segmentStart = path.get(0);
        for (int i = 0; i + 1 < path.size(); i++) {
            String a = path.get(i);
            String b = path.get(i + 1);
            Set<String> candidates = segmentLines.getOrDefault(segmentKey(a, b), Collections.emptySet());
            if (!candidates.contains(currentLine)) {
                segments.add(new RideSegment(currentLine, segmentStart, a));
                segmentStart = a;
                currentLine = chooseLine(a, b, currentLine);
            }
        }
        segments.add(new RideSegment(currentLine, segmentStart, path.get(path.size() - 1)));
        return segments;
    }

    private String chooseLine(String a, String b, String preferred) {
        Set<String> candidates = segmentLines.getOrDefault(segmentKey(a, b), Collections.emptySet());
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("两站不相连：" + a + " -> " + b);
        }
        if (preferred != null && candidates.contains(preferred)) {
            return preferred;
        }
        return candidates.iterator().next();
    }

    /** 功能5：把路径按“先乘坐X号线从A站到B站，在B站换乘Y号线到C站……”的简洁形式打印到标准输出。 */
    public void printRoute(List<String> path) {
        List<RideSegment> segments = rideSegments(path);
        if (segments.isEmpty()) {
            System.out.println("起点即终点：" + stationLabel(path.get(0)));
            return;
        }
        System.out.println("共 " + segments.size() + " 段乘车，换乘 " + (segments.size() - 1) + " 次：");
        for (int i = 0; i < segments.size(); i++) {
            RideSegment segment = segments.get(i);
            if (i == 0) {
                System.out.println("  先乘坐" + segment.line() + "从" + stationLabel(segment.from())
                        + "到" + stationLabel(segment.to()));
            } else {
                System.out.println("  在" + stationLabel(segment.from()) + "换乘" + segment.line()
                        + "到" + stationLabel(segment.to()));
            }
        }
    }

    private static String stationLabel(String station) {
        return station.endsWith("站") ? station : station + "站";
    }

    private Map<String, Double> dijkstra(String source) {
        Map<String, Double> distance = new HashMap<>();
        PriorityQueue<Map.Entry<String, Double>> queue =
                new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
        distance.put(source, 0.0);
        queue.offer(new AbstractMap.SimpleImmutableEntry<>(source, 0.0));
        while (!queue.isEmpty()) {
            Map.Entry<String, Double> current = queue.poll();
            if (current.getValue() > distance.getOrDefault(current.getKey(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            for (Map.Entry<String, Double> next
                    : adjacency.getOrDefault(current.getKey(), Collections.emptyMap()).entrySet()) {
                double candidate = current.getValue() + next.getValue();
                if (candidate < distance.getOrDefault(next.getKey(), Double.POSITIVE_INFINITY) - 1e-9) {
                    distance.put(next.getKey(), candidate);
                    queue.offer(new AbstractMap.SimpleImmutableEntry<>(next.getKey(), candidate));
                }
            }
        }
        return distance;
    }
}