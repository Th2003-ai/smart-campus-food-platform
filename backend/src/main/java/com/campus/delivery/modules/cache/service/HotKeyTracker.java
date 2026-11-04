package com.campus.delivery.modules.cache.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 热 key 探测（PPT 第 15 页「客户端埋点」手段的落地）。
 *
 * <p>在缓存读取的入口处记一次访问次数，按 5 分钟一个窗口滚动统计，
 * 管理端可以随时查 TopN，用来回答「到底哪个 key 最热、该不该拆分/加本地缓存」。
 *
 * <p>为什么放内存而不是 Redis：这本身就是观察 Redis 的工具，
 * 放进 Redis 会反过来加重被观察对象的负担；单机内存计数足够，
 * 多实例时每台各看各的即可（也可以后续汇总到 Prometheus）。
 */
@Slf4j
@Component
public class HotKeyTracker {

    /** 统计窗口：5 分钟滚动一次，避免历史热度永远压着新数据 */
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    private volatile long windowStart = System.currentTimeMillis();

    /** 记录一次 key 访问 */
    public void record(String key) {
        long now = System.currentTimeMillis();
        if (now - windowStart > WINDOW_MS) {
            resetWindow(now);
        }
        counters.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /**
     * 取访问次数最多的前 N 个 key。
     *
     * @return 每项含 key / count，按 count 倒序
     */
    public List<Map<String, Object>> topN(int n) {
        int limit = n <= 0 ? 10 : n;
        List<Map.Entry<String, LongAdder>> entries = new ArrayList<>(counters.entrySet());
        entries.sort(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().sum()).reversed());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, LongAdder> entry : entries) {
            if (result.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("count", entry.getValue().sum());
            result.add(item);
        }
        return result;
    }

    /** 当前窗口内的总访问次数 */
    public long total() {
        return counters.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** 统计窗口起始时间戳（毫秒） */
    public long windowStart() {
        return windowStart;
    }

    public int keyCount() {
        return counters.size();
    }

    /** 手动清空统计（演示时想从 0 开始观察用） */
    public void clear() {
        counters.clear();
        windowStart = System.currentTimeMillis();
        log.info("热 key 统计已重置");
    }

    private synchronized void resetWindow(long now) {
        if (now - windowStart > WINDOW_MS) {
            counters.clear();
            windowStart = now;
            log.debug("热 key 统计窗口滚动，计数已清零");
        }
    }
}
