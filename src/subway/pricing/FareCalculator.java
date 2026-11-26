package subway.pricing;

/**
 * 武汉地铁票价计算。
 * 依据武汉地铁集团官网票务规定（按里程分段计价）：
 * 4km 以内（含）2 元；4~12km 每 4km 加 1 元；12~24km 每 6km 加 1 元；
 * 24~40km 每 8km 加 1 元；40~50km 每 10km 加 1 元；50km 以上每 20km 加 1 元。
 * 武汉通刷卡 9 折；定期票有效期内不限次数（1 日 18 元、3 日 45 元、7 日 90 元）。
 */
public final class FareCalculator {

    public static final double WUHAN_TONG_DISCOUNT = 0.9;
    public static final int DAY_TICKET_1_PRICE = 18;
    public static final int DAY_TICKET_3_PRICE = 45;
    public static final int DAY_TICKET_7_PRICE = 90;

    private FareCalculator() {
    }

    /** 普通单程票票价（元）。 */
    public static int singleFare(double km) {
        double d = Math.round(km * 1000.0) / 1000.0;
        if (d <= 4) {
            return 2;
        }
        if (d <= 12) {
            return 2 + (int) Math.ceil((d - 4) / 4.0);
        }
        if (d <= 24) {
            return 4 + (int) Math.ceil((d - 12) / 6.0);
        }
        if (d <= 40) {
            return 6 + (int) Math.ceil((d - 24) / 8.0);
        }
        if (d <= 50) {
            return 8 + (int) Math.ceil((d - 40) / 10.0);
        }
        return 9 + (int) Math.ceil((d - 50) / 20.0);
    }

    /** 武汉通刷卡票价（9 折，票价为整数元，折后精确到 0.1 元）。 */
    public static double wuhanTongFare(double km) {
        return Math.round(singleFare(km) * WUHAN_TONG_DISCOUNT * 10.0) / 10.0;
    }

    /** 定期票（1 日 / 3 日 / 7 日）在有效期内不限次数，单次乘车计价为 0 元。 */
    public static double periodPassFare(double km) {
        return 0.0;
    }
}