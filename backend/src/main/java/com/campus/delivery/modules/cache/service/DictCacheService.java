package com.campus.delivery.modules.cache.service;

import com.campus.delivery.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态字典缓存（PPT 第 21 页）。
 *
 * <p>字典类数据的特点是「读得最多、改得最少、量还很小」，
 * 所以它的缓存策略和「会变的数据」完全不同：
 * <ul>
 *   <li><b>启动预热</b>：应用启动时一次性加载进内存 + Redis，之后读请求不再查库；</li>
 *   <li><b>不设 TTL</b>：常驻，不靠过期收敛（TTL 只会带来「周期性回源 + 失效瞬间的小击穿」）；</li>
 *   <li><b>写时刷新</b>：配置/分类变更后立刻重载，回源次数从「按时间」变成「按变更」。</li>
 * </ul>
 *
 * <p>覆盖的表（与 PPT 第 21 页一致）：
 * shop_category / dish_category / sys_config / sys_role_permission / banner / sys_menu。
 *
 * <p>注意：{@code sys_menu} 需要先执行 {@code database/menu_tree.sql} 才有这张表；
 * 缺表时本轮预热会跳过它并打 WARN，不影响其它表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictCacheService implements ApplicationRunner {

    /** 需要预热的字典表；新增表在此追加即可 */
    private static final List<String> DICT_TABLES = List.of(
            "shop_category",
            "dish_category",
            "sys_config",
            "sys_role_permission",
            "banner",
            "sys_menu"
    );

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 内存镜像：表名 -> 行数据 */
    private final Map<String, List<Map<String, Object>>> memory = new ConcurrentHashMap<>();

    private volatile long loadedAt = 0L;

    /** 预热开关：默认开启；设 false 可用来对比「不预热」的表现 */
    @Value("${campus.cache.dict-preload:true}")
    private boolean preloadEnabled;

    /** 应用启动后立即预热（失败只告警，不影响启动） */
    @Override
    public void run(ApplicationArguments args) {
        if (!preloadEnabled) {
            log.warn("字典预热已关闭（campus.cache.dict-preload=false）");
            return;
        }
        refresh("应用启动预热");
    }

    /**
     * 重新加载所有字典表：同时刷新内存与 Redis。
     *
     * @param reason 触发原因（日志里能看到是启动预热还是手动刷新）
     */
    public synchronized void refresh(String reason) {
        long start = System.currentTimeMillis();
        int totalRows = 0;
        int redisFailures = 0;
        for (String table : DICT_TABLES) {
            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList("SELECT * FROM " + table);
            } catch (Exception e) {
                // 单表查库失败不影响其它表
                log.warn("字典表查询失败（跳过）: table={}, err={}", table, e.getMessage());
                continue;
            }
            // 内存镜像是真正的读路径，先更新它
            memory.put(table, rows);
            totalRows += rows.size();
            // Redis 只是持久化镜像：写失败不影响内存读取，单独计数，不拖累整表
            try {
                redisTemplate.opsForValue().set(RedisKeys.DICT_CACHE + table, rows);
            } catch (Exception e) {
                redisFailures++;
            }
        }
        loadedAt = System.currentTimeMillis();
        if (redisFailures > 0) {
            log.warn("字典缓存刷新完成（{}）：{} 张表、{} 行、耗时 {} ms；其中 {} 张表写 Redis 失败（内存仍可用）",
                    reason, memory.size(), totalRows, loadedAt - start, redisFailures);
        } else {
            log.info("字典缓存刷新完成（{}）：{} 张表、{} 行、耗时 {} ms",
                    reason, memory.size(), totalRows, loadedAt - start);
        }
    }

    /** 读内存镜像（真实业务读路径，不查库、不走 Redis） */
    public List<Map<String, Object>> get(String table) {
        return memory.getOrDefault(table, List.of());
    }

    /** 概览：每张表的行数与加载时间，供演示页展示 */
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> tables = new LinkedHashMap<>();
        int total = 0;
        for (String table : DICT_TABLES) {
            List<Map<String, Object>> rows = memory.getOrDefault(table, List.of());
            tables.put(table, rows.size());
            total += rows.size();
        }
        result.put("tables", tables);
        result.put("totalRows", total);
        result.put("loadedAt", loadedAt);
        result.put("tableCount", memory.size());
        result.put("preloadEnabled", preloadEnabled);
        return result;
    }

    /** 返回某张表的明细（演示页展开看数据用） */
    public List<Map<String, Object>> detail(String table) {
        if (!DICT_TABLES.contains(table)) {
            throw new IllegalArgumentException("不支持的字典表: " + table);
        }
        return get(table);
    }

    /**
     * 直查数据库对比：返回行数与耗时，用来证明「缓存 vs 回源」的差距。
     *
     * <p>为了公平，连查 3 次取平均，避免首次连接池预热影响结论。
     */
    public Map<String, Object> compareWithDb(String table) {
        if (!DICT_TABLES.contains(table)) {
            throw new IllegalArgumentException("不支持的字典表: " + table);
        }
        int rounds = 3;
        long dbStart = System.nanoTime();
        int dbRows = 0;
        for (int i = 0; i < rounds; i++) {
            dbRows = jdbcTemplate.queryForList("SELECT * FROM " + table).size();
        }
        long dbMs = (System.nanoTime() - dbStart) / 1_000_000;

        long cacheStart = System.nanoTime();
        int cacheRows = 0;
        for (int i = 0; i < rounds; i++) {
            cacheRows = get(table).size();
        }
        long cacheMs = (System.nanoTime() - cacheStart) / 1_000_000;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", table);
        result.put("rows", dbRows);
        result.put("cacheRows", cacheRows);
        result.put("dbCostMs", dbMs);
        result.put("cacheCostMs", cacheMs);
        result.put("rounds", rounds);
        result.put("note", "耗时包含 3 次查询总和；数据量小时差距不明显，行数越多差距越大");
        return result;
    }

    /** 已预热的表名（演示页下拉用） */
    public List<String> tables() {
        return new ArrayList<>(DICT_TABLES);
    }
}
