package com.campus.delivery.modules.cache.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 缓存策略运行期开关（答辩演示用）。
 *
 * <p>为什么需要「运行期开关」而不是只写在配置里：
 * PPT 第 14 页要给出一组对比数字（关闭防穿透时打库 52 次、开启后 1 次），
 * 现场演示时不可能改配置文件再重启，所以这里用 volatile 字段做热开关，
 * 管理端一个接口就能切，脚本跑两组实验即可产生对比。
 */
@Slf4j
@Component
public class CacheSwitch {

    /** 是否启用「空值缓存」防穿透（默认开启） */
    private volatile boolean nullValueCache = true;

    /** Redis 熔断截止时间：期间所有缓存操作直接跳过，走数据库兜底 */
    private volatile long redisDownUntil = 0L;

    /** 熔断冷却时间：30 秒内不再尝试 Redis，避免每个请求都等一次超时 */
    private static final long COOLDOWN_MS = 30_000L;

    public boolean isNullValueCacheEnabled() {
        return nullValueCache;
    }

    public void setNullValueCache(boolean enabled) {
        this.nullValueCache = enabled;
        log.warn("缓存开关变更：空值缓存（防穿透）= {}", enabled ? "开启" : "关闭");
    }

    /** Redis 是否处于可用状态（熔断期内返回 false，直接走数据库） */
    public boolean isRedisAvailable() {
        return System.currentTimeMillis() >= redisDownUntil;
    }

    /**
     * 标记 Redis 故障：接下来 30 秒内的请求不再尝试访问 Redis。
     *
     * <p>没有这个熔断，Redis 挂掉时每个请求都要等一次命令超时（实测 3 秒 × 3 次 ≈ 9 秒），
     * 降级虽然"正确"但会把接口拖垮；熔断后只有第一个请求慢，后续直接查库。
     */
    public void markRedisDown(long commandCostMs) {
        if (isRedisAvailable()) {
            log.warn("Redis 故障，开启 30 秒熔断：本次耗时 {} ms，期间缓存操作全部跳过", commandCostMs);
        }
        redisDownUntil = System.currentTimeMillis() + COOLDOWN_MS;
    }

    /** Redis 恢复正常（某次操作成功即解除熔断） */
    public void markRedisUp() {
        if (redisDownUntil > 0) {
            log.info("Redis 已恢复，解除熔断");
        }
        redisDownUntil = 0L;
    }

    /** 熔断剩余毫秒数（0 表示未熔断） */
    public long redisDownRemainingMs() {
        return Math.max(0, redisDownUntil - System.currentTimeMillis());
    }
}
