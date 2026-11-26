package subway;

import subway.core.MetroNetwork;
import subway.core.MetroNetwork.Route;
import subway.exception.InvalidInputException;
import subway.model.StationDistance;
import subway.pricing.FareCalculator;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 测试类：读取 subway.txt 构建路网，依次验证题目要求的 7 项功能。 */
public class Test {

    private static final String START = "华中科技大学";
    private static final String END = "武汉火车站";
    private static final String LONG_START = "天河机场";
    private static final String LONG_END = "武汉东站";

    public static void main(String[] args) {
        Path dataFile = Paths.get(args.length > 0 ? args[0] : "subway.txt");
        try {
            MetroNetwork network = MetroNetwork.load(dataFile);
            printSummary(network);
            testTransferStations(network);
            testStationsWithinDistance(network);
            testReasonableRoutes(network);
            testShortestPath(network);
            testPathFare(network);
        } catch (IOException e) {
            System.err.println("读取数据文件失败：" + e.getMessage());
        } catch (InvalidInputException e) {
            System.err.println("输入不合法：" + e.getMessage());
        }
    }

    private static void printSummary(MetroNetwork network) throws InvalidInputException {
        System.out.println("==== 路网加载成功：" + network.lineCount() + " 条线路 / "
                + network.stationCount() + " 个站点 ====");
        System.out.println("各线路站点数：");
        for (String lineName : network.lineNames()) {
            System.out.println("  " + lineName + "：" + network.stationCountOfLine(lineName) + " 站");
        }
        System.out.println("示例查询：" + START + "站有 " + network.lineCountOfStation(START)
                + " 条线路 -> " + String.join("、", network.linesOfStation(START)));
    }

    private static void testTransferStations(MetroNetwork network) {
        System.out.println("\n==== 功能1：所有中转站（至少两条线路经过） ====");
        Map<String, Set<String>> transferStations = network.transferStations();
        System.out.println("共 " + transferStations.size() + " 个中转站：");
        for (Map.Entry<String, Set<String>> entry : transferStations.entrySet()) {
            String station = entry.getKey().endsWith("站") ? entry.getKey() : entry.getKey() + "站";
            System.out.println("  <" + station + "，<"
                    + String.join("、", entry.getValue()) + ">>");
        }
    }

    private static void testStationsWithinDistance(MetroNetwork network) {
        System.out.println("\n==== 功能2：距某站小于 n 公里的站点 ====");
        double[] limits = {1.0, 1.5};
        for (double n : limits) {
            try {
                Map<String, StationDistance> result = network.stationsWithinDistance(START, n);
                System.out.println("与" + START + "站距离小于 " + n + " km 的站点（"
                        + result.size() + " 个）：");
                for (StationDistance item : result.values()) {
                    System.out.println("  " + item);
                }
            } catch (InvalidInputException e) {
                System.out.println("异常：" + e.getMessage());
            }
        }
        System.out.println("-- 异常处理演示 --");
        try {
            network.stationsWithinDistance("不存在站", 1.0);
        } catch (InvalidInputException e) {
            System.out.println("  非法站名 -> " + e.getMessage());
        }
        try {
            network.stationsWithinDistance(START, -1);
        } catch (InvalidInputException e) {
            System.out.println("  非法距离参数 -> " + e.getMessage());
        }
        try {
            network.shortestPath("不存在站", END);
        } catch (InvalidInputException e) {
            System.out.println("  最短路径非法站名 -> " + e.getMessage());
        }
    }

    private static void testReasonableRoutes(MetroNetwork network) {
        System.out.println("\n==== 功能3：合理乘车路线 ====");
        System.out.println("规则：无环；里程≤最短×1.5；换乘≤最少+2；删除三项指标全面占优的路线；"
                + "按里程升序，最多 " + MetroNetwork.MAX_REASONABLE_ROUTES + " 条");
        try {
            List<Route> routes = network.reasonableRoutes(LONG_START, LONG_END);
            System.out.println(LONG_START + " -> " + LONG_END + " 共 " + routes.size() + " 条合理路线：");
            for (int i = 0; i < routes.size(); i++) {
                Route route = routes.get(i);
                System.out.println("  " + (i + 1) + ") " + route.km() + " km，"
                        + route.stationCount() + " 站，换乘 " + route.transfers() + " 次");
            }
        } catch (InvalidInputException e) {
            System.out.println("异常：" + e.getMessage());
        }
    }

    private static void testShortestPath(MetroNetwork network) {
        System.out.println("\n==== 功能4/5：最短路径与简洁打印 ====");
        try {
            List<String> shortest = network.shortestPath(START, END);
            System.out.println(START + " -> " + END + " 最短路径（" + shortest.size() + " 站，"
                    + network.pathDistance(shortest) + " km）：");
            System.out.println("  " + String.join(" -> ", shortest));
            System.out.println("功能5 简洁打印：");
            network.printRoute(shortest);
        } catch (InvalidInputException e) {
            System.out.println("异常：" + e.getMessage());
        }
    }

    private static void testPathFare(MetroNetwork network) {
        System.out.println("\n==== 功能6/7：从合理路线集合中选择路线并计价 ====");
        try {
            List<Route> routes = network.reasonableRoutes(LONG_START, LONG_END);
            System.out.println("可选合理路线及票价（单程 / 武汉通9折 / 日票）：");
            for (int i = 0; i < routes.size(); i++) {
                Route route = routes.get(i);
                double km = route.km();
                System.out.println("  " + (i + 1) + ") " + km + " km，换乘 " + route.transfers()
                        + " 次 -> " + FareCalculator.singleFare(km) + " 元 / "
                        + FareCalculator.wuhanTongFare(km) + " 元 / "
                        + FareCalculator.periodPassFare(km) + " 元");
            }
            int choice = routes.size() >= 2 ? 2 : 1;
            Route selected = routes.get(choice - 1);
            System.out.println("用户选择第 " + choice + " 条路线：");
            network.printRoute(selected.stations());
            double km = selected.km();
            System.out.println("  里程：" + km + " km");
            System.out.println("功能6 普通单程票：" + FareCalculator.singleFare(km) + " 元");
            System.out.println("功能7 武汉通（9折）：" + FareCalculator.wuhanTongFare(km) + " 元");
            System.out.println("功能7 日票（有效期内不限次）："
                    + FareCalculator.periodPassFare(km) + " 元");
        } catch (InvalidInputException e) {
            System.out.println("异常：" + e.getMessage());
        }

        try {
            double km = network.pathDistance(network.shortestPath(START, END));
            System.out.println("\n参考：" + START + " -> " + END + " 最短 " + km + " km -> 单程 "
                    + FareCalculator.singleFare(km) + " 元，武汉通 "
                    + FareCalculator.wuhanTongFare(km) + " 元，日票 "
                    + FareCalculator.periodPassFare(km) + " 元");
        } catch (InvalidInputException e) {
            System.out.println("异常：" + e.getMessage());
        }
    }
}