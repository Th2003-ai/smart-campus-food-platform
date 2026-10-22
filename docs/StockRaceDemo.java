import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀式扣库存 · 并发对照实验（技术分享题 6 现场演示用）
 *
 * 实验组：项目里的写法 —— 把库存判断写进 WHERE，靠数据库原子完成"判断 + 扣减"
 * 对照组：先查后改 —— SELECT 读出库存判断，再 UPDATE 扣减（有竞态，会超卖）
 *
 * 运行方式（在项目 backend 同级目录下，需要本机 MySQL 已启动）：
 *   java -cp "D:\maven-repo\com\mysql\mysql-connector-j\8.3.0\mysql-connector-j-8.3.0.jar" ^
 *        docs/StockRaceDemo.java "你的数据库密码"
 *
 * 密码通过命令行参数或环境变量 CAMPUS_DB_PASSWORD 传入，不写死在代码里。
 */
public class StockRaceDemo {

    private static final String URL = "jdbc:mysql://localhost:3306/campus_ai_delivery"
            + "?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String DISH_ID = "DI0000000000000000000000000001";
    private static final int THREADS = 20;
    private static final int INITIAL_STOCK = 5;

    private static String dbPassword;

    public static void main(String[] args) throws Exception {
        dbPassword = args.length > 0 ? args[0] : System.getenv("CAMPUS_DB_PASSWORD");
        if (dbPassword == null || dbPassword.isEmpty()) {
            System.out.println("缺少数据库密码。用法：java StockRaceDemo.java <数据库密码>");
            System.out.println("或先设置环境变量：set CAMPUS_DB_PASSWORD=你的密码");
            return;
        }

        System.out.println("=== 秒杀扣库存 · 并发对照实验 ===");
        System.out.println("菜品 " + DISH_ID);
        System.out.println("初始库存 " + INITIAL_STOCK + " 份，同时发起 " + THREADS + " 个扣减请求");
        System.out.println();

        int controlSuccess = runOnce(false);
        int guardSuccess = runOnce(true);

        System.out.println();
        System.out.println("=== 结论 ===");
        System.out.println("对照组（先查后改）：成功 " + controlSuccess + " 笔");
        System.out.println("实验组（条件更新）：成功 " + guardSuccess + " 笔，其余 " + (THREADS - guardSuccess) + " 笔被库存不足挡回");
        System.out.println(controlSuccess > INITIAL_STOCK
                ? "对照组成功笔数超过了库存总量 —— 这就是超卖。"
                : "对照组本轮没有超卖（并发窗口没撞上），可以再跑一次。");
    }

    /** 跑一轮：重置库存 -> 20 线程同时扣减 -> 打印结果。guard=true 为实验组。 */
    private static int runOnce(boolean guard) throws Exception {
        resetStock();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    if (guard) {
                        deductWithGuard(success, fail);
                    } else {
                        deductCheckThenUpdate(success, fail);
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    finishGate.countDown();
                }
            });
        }

        startGate.countDown();
        finishGate.await();
        pool.shutdown();

        int stock = currentStock();
        System.out.println("[" + (guard ? "实验组" : "对照组") + "] "
                + (guard ? "条件更新：UPDATE ... WHERE stock >= 1"
                         : "先查后改：SELECT 判断后再 UPDATE")
                + "  ->  成功 " + success.get() + " 笔，失败 " + fail.get() + " 笔，库存终值 " + stock);
        return success.get();
    }

    /** 实验组：项目里的写法，判断和扣减合并成一条 SQL。 */
    private static void deductWithGuard(AtomicInteger success, AtomicInteger fail) throws Exception {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE dish SET stock = stock - 1, update_time = NOW() "
                     + "WHERE id = ? AND deleted = 0 AND stock >= 1")) {
            ps.setString(1, DISH_ID);
            if (ps.executeUpdate() == 1) {
                success.incrementAndGet();
            } else {
                fail.incrementAndGet();
            }
        }
    }

    /** 对照组：先查库存、再扣减，两步之间留一点间隔，模拟真实处理耗时。 */
    private static void deductCheckThenUpdate(AtomicInteger success, AtomicInteger fail) throws Exception {
        try (Connection conn = connect()) {
            int stock;
            try (PreparedStatement query = conn.prepareStatement("SELECT stock FROM dish WHERE id = ?")) {
                query.setString(1, DISH_ID);
                try (ResultSet rs = query.executeQuery()) {
                    rs.next();
                    stock = rs.getInt(1);
                }
            }
            if (stock <= 0) {
                fail.incrementAndGet();
                return;
            }
            Thread.sleep(50);
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE dish SET stock = stock - 1, update_time = NOW() WHERE id = ?")) {
                update.setString(1, DISH_ID);
                update.executeUpdate();
                success.incrementAndGet();
            }
        }
    }

    private static void resetStock() throws Exception {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("UPDATE dish SET stock = ? WHERE id = ?")) {
            ps.setInt(1, INITIAL_STOCK);
            ps.setString(2, DISH_ID);
            ps.executeUpdate();
        }
    }

    private static int currentStock() throws Exception {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("SELECT stock FROM dish WHERE id = ?")) {
            ps.setString(1, DISH_ID);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -999;
            }
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(URL, USER, dbPassword);
    }
}
