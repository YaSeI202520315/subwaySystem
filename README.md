# 武汉地铁模拟运行系统

基于 `subway.txt` 中的线路里程数据构建武汉地铁全网图（12 条线路、274 段区间、250 个车站、33 个中转站），用 Java 实现线路查询、路径规划与票价计算，并配套 UML 设计文档。

## 功能一览

| 序号 | 功能 | 说明 | 主要实现 |
| --- | --- | --- | --- |
| 1 | 中转站查询 | 返回所有至少两条线路经过的站点及所属线路 | `MetroNetwork.transferStations()` |
| 2 | 周边站点查询 | 输入站点与距离上限 n，返回距离小于 n 的站点、所在线路与距离；非法输入抛异常 | `MetroNetwork.stationsWithinDistance(station, n)` |
| 3 | 合理乘车路线 | 输入起终点，返回最多 10 条无环、绕行可控的候选路线 | `MetroNetwork.reasonableRoutes(from, to)` |
| 4 | 最短路径 | 输入起终点，返回里程最短的站序（Dijkstra） | `MetroNetwork.shortestPath(from, to)` |
| 5 | 分段乘车指引 | 将路径打印为「先乘 X 号线从 A 到 B，在 B 站换乘 Y 号线…」 | `MetroNetwork.printRoute(path)` |
| 6 | 单程票价 | 按里程分段计价 | `FareCalculator.singleFare(km)` |
| 7 | 武汉通 / 定期票 | 武汉通 9 折；定期票（1 日 18 元、3 日 45 元、7 日 90 元）有效期内单次乘车记 0 元 | `FareCalculator.wuhanTongFare(km)` / `periodPassFare(km)` |

**合理乘车路线规则（功能 3）**：路径不重复站点；总里程 ≤ 最短里程 × 1.5；换乘次数 ≤ 全局最少换乘 + 2；删除在「里程、站数、换乘次数」三项上被另一条路线全面占优的路线；按里程升序返回，最多 10 条。

## 目录结构

```
subwaySystem/
├── subway.txt                    # 线路数据（Tab 分隔，274 段区间）
├── 要求.txt                       # 作业要求与原始数据
├── src/subway/
│   ├── Test.java                 # 测试入口（main）：依次演示功能 1)~7)
│   ├── core/MetroNetwork.java    # 路网建模 + 全部查询/路径/换乘算法
│   ├── model/MetroLine.java      # 单条线路：站序 + 相邻区间里程
│   ├── model/StationDistance.java# 周边站点查询结果（站点、线路、距离）
│   ├── pricing/FareCalculator.java # 票价计算（单程 / 武汉通 / 定期票）
│   └── exception/InvalidInputException.java # 输入非法受检异常
├── docs/
│   ├── use-case-diagram.puml     # 用例图（PlantUML）
│   ├── class-diagram.puml        # 类图（PlantUML）
│   └── uml-design.md             # 用例详细描述 + 类图说明 + 出图方式
└── out/                          # 编译输出（可删除，重新编译生成）
```

模块划分：`core` 负责图结构与算法，`model` 是数据与结果对象，`pricing` 只依赖里程、与路网解耦，`exception` 统一表达输入错误，`Test` 负责演示与验证。

## 数据文件

`subway.txt` 每行四列，以 Tab 分隔，首行为注释：

```
# 线路名	起点站	终点站	距离(km)
1号线	径河	三店	1.110
2号线	天河机场	航空总部	6.004
...
```

共线段只按站点对建一条边并取最小里程（如 2/4 号线「中南路—洪山广场」0.966 km；6/16 号线「老关村—国博中心南」取 1.57 km 而非 1.826 km）。

## 编译与运行

要求 JDK 8 及以上（开发与验证使用 JDK 21），源码为 UTF-8 编码。

Git Bash：

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -Dstdout.encoding=UTF-8 -cp out subway.Test
```

Windows cmd：

```bat
if not exist out mkdir out
javac -encoding UTF-8 -d out src\subway\*.java src\subway\core\*.java src\subway\model\*.java src\subway\pricing\*.java src\subway\exception\*.java
java -Dstdout.encoding=UTF-8 -cp out subway.Test
```

说明：

- `-Dstdout.encoding=UTF-8` 用于 Java 18+ 保证控制台中文正常输出；若使用 Java 8，请改为 `-Dfile.encoding=UTF-8`。
- 默认读取当前目录下的 `subway.txt`，也可传入自定义路径：`java -cp out subway.Test D:\data\subway.txt`。

## 运行结果（节选）

```
==== 路网加载成功：12 条线路 / 250 个站点 ====
  2号线：38 站
  ...

==== 功能1：所有中转站（至少两条线路经过） ====
共 33 个中转站：
  <中南路站，<2号线、4号线>>
  <宏图大道站，<2号线、3号线、8号线>>
  ...

==== 功能2：距某站小于 n 公里的站点 ====
与华中科技大学站距离小于 1.5 km 的站点（2 个）：
  <光谷大道站，2号线，0.817km>
  <珞雄路站，2号线，1.009km>

==== 功能3：合理乘车路线 ====
天河机场 -> 武汉东站 共 2 条合理路线：
  1) 49.528 km，31 站，换乘 2 次
  2) 52.584 km，33 站，换乘 0 次

==== 功能4/5：最短路径与简洁打印 ====
华中科技大学 -> 武汉火车站 最短路径（21 站，23.216 km）
共 2 段乘车，换乘 1 次：
  先乘坐2号线从华中科技大学站到洪山广场站
  在洪山广场站换乘4号线到武汉火车站

==== 功能6/7：从合理路线集合中选择路线并计价 ====
可选合理路线及票价（单程 / 武汉通9折 / 日票）：
  1) 49.528 km，换乘 2 次 -> 9 元 / 8.1 元 / 0.0 元
  2) 52.584 km，换乘 0 次 -> 10 元 / 9.0 元 / 0.0 元
```

## 票价规则

依据武汉地铁集团官网票务规定，按里程分段计价：

| 里程区间 | 规则 |
| --- | --- |
| 0 ~ 4 km（含） | 2 元 |
| 4 ~ 12 km | 每 4 km 加 1 元 |
| 12 ~ 24 km | 每 6 km 加 1 元 |
| 24 ~ 40 km | 每 8 km 加 1 元 |
| 40 ~ 50 km | 每 10 km 加 1 元 |
| 50 km 以上 | 每 20 km 加 1 元 |

- 武汉通：单程票价 × 0.9，保留 1 位小数；
- 定期票：1 日 18 元、3 日 45 元、7 日 90 元，有效期内不限次，单次乘车按 0 元计；
- 官网另规定每次乘车限时 240 分钟，本系统仅计算票价，不对乘车时长建模。

## 核心算法

- **最短路径**：Dijkstra（邻接表 + 优先队列），按区间里程加权；
- **最少换乘**：在「(站点, 线路)」状态图上做 0-1 BFS，同线移动代价 0、换线代价 1，得到与里程无关的全局最少换乘次数；
- **合理路线生成**：DFS 枚举无环路径，按「里程上限 1.5 × 最短」「换乘上限 最少 + 2」增量剪枝，候选总数上限 50000；
- **路线筛选**：按（里程，站数，换乘）排序后做 Pareto 去支配，截取前 10 条；
- **路线换乘次数**：对给定站序，在所有可行线路分配方案中取换乘次数最小值。

## UML 设计文档

- 用例图：`docs/use-case-diagram.puml`
  ![use-case-diagram.png](docs%2Fuse-case-diagram.png)
- 类图：`docs/class-diagram.puml`
![class-diagram.png](docs%2Fclass-diagram.png))

- 用例详细描述（查询合理乘车路线）与图说明：`docs/uml-design.md`

## 建模约定

- 站点以站名（`String`）唯一标识，未单独建立 `Station` 类；
- 同一线路上相邻两站构成一条边；共线段按站点对去重并取较小里程；
- 数据中「武汉站东广场」（5 号线）与「武汉站西广场」（19 号线）、「新荣客运站」（阳逻线）与「新荣」（1 号线）是不同车站，按原数据各自建点；
- 换乘站指至少有两条线路经过的站点，共 33 个。

## 说明

当前版本为命令行测试程序（未包含选做的图形界面）。所有功能由 `Test` 的 `main` 方法串联演示，也可直接调用 `MetroNetwork` / `FareCalculator` 的公有方法集成到其他界面中。