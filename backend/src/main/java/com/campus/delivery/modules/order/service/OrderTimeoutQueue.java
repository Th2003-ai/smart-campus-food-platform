package com.campus.delivery.modules.order.service;

import com.campus.delivery.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 订单超时未接单延时队列（Redis ZSet）。
 *
 * <p>为什么用 Redis 而不是数据库轮询全表：
 * <ul>
 *   <li>ZSet 的 score 自带排序，{@code ZRANGEBYSCORE 0 now} 直接取出到期订单，O(log N)；</li>
 *   <li>数据库按 create_time 扫「待接单」订单，随着历史订单增长会越来越慢；</li>
 *   <li>多实例部署时，{@code ZREM} 的返回值天然实现了「谁删到谁处理」的抢占语义，
 *       不需要额外的分布式锁。</li>
 * </ul>
 *
 * <p>成员设计：member = orderId，score = 到期时间（epoch 毫秒）。
 * 下单入队、接单/拒单/取消出队。
 *
 * <p>超时分钟数来自 {@code sys_config.order.auto.cancel.minutes}（带 60 秒本地缓存，
 * 避免每分钟扫描都读库）；配成 0 或负数表示关闭该功能。
 *
 * <p>这里刻意用 {@link StringRedisTemplate} 而不是全局的 {@code RedisTemplate<String,Object>}：
 * 后者的 value 序列化器是 {@code GenericJackson2JsonRedisSerializer}，
 * 会把 member 写成 {@code "orderId"}（带引号），在 redis-cli 里既难读，
 * 也没法直接用原始 ID 做 ZREM。ZSet 的 member 是业务主键，用纯字符串最直观。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTimeoutQueue {

    private static final int DEFAULT_MINUTES = 15;
    private static final long CONFIG_CACHE_MS = 60_000L;
    private static final int CLAIM_BATCH = 50;

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    private volatile Integer cachedMinutes;
    private volatile long cachedAt;

    /** 读取「超时未接单自动取消」的分钟数；0 或负数表示功能关闭 */
    public int autoCancelMinutes() {
        long now = System.currentTimeMillis();
        Integer cached = cachedMinutes;
        if (cached != null && now - cachedAt < CONFIG_CACHE_MS) {
            return cached;
        }
        int minutes = DEFAULT_MINUTES;
        try {
            Integer value = jdbcTemplate.query(
                    "SELECT config_value FROM sys_config WHERE config_key = 'order.auto.cancel.minutes'",
                    rs -> rs.next() ? Integer.valueOf(rs.getString(1).trim()) : null);
            if (value != null) {
                minutes = value;
            }
        } catch (Exception e) {
            // 配置读不到不影响下单，退回默认值
            log.warn("读取 order.auto.cancel.minutes 失败，使用默认 {} 分钟: {}", DEFAULT_MINUTES, e.getMessage());
        }
        cachedMinutes = minutes;
        cachedAt = now;
        return minutes;
    }

    /**
     * 下单后把订单放入延时队列。
     *
     * <p>Redis 异常只记日志不抛错：下单是主流程，不能因为队列不可用而失败；
     * 最坏情况是这一单不会被自动取消，商户仍可手工拒单。
     */
    public void enqueue(String orderId) {
        int minutes = autoCancelMinutes();
        if (minutes <= 0) {
            return;
        }
        try {
            long deadline = System.currentTimeMillis() + minutes * 60_000L;
            redisTemplate.opsForZSet().add(RedisKeys.ORDER_TIMEOUT_QUEUE, orderId, deadline);
            log.debug("订单进入超时队列: orderId={}, 截止={}ms", orderId, deadline);
        } catch (Exception e) {
            log.warn("订单入超时队列失败（不影响下单）: orderId={}, err={}", orderId, e.getMessage());
        }
    }

    /** 商户接单 / 拒单 / 学生取消后出队 */
    public void remove(String orderId) {
        try {
            redisTemplate.opsForZSet().remove(RedisKeys.ORDER_TIMEOUT_QUEUE, orderId);
        } catch (Exception e) {
            log.warn("订单出超时队列失败: orderId={}, err={}", orderId, e.getMessage());
        }
    }

    /**
     * 取出并「占用」已到期订单。
     *
     * <p>先查出到期成员，再逐个 {@code ZREM}：只有删除成功（返回 1）的实例才处理，
     * 避免多实例重复取消同一单。
     */
    public List<String> claimDue() {
        List<String> claimed = new ArrayList<>();
        try {
            long now = System.currentTimeMillis();
            Set<String> due = redisTemplate.opsForZSet()
                    .rangeByScore(RedisKeys.ORDER_TIMEOUT_QUEUE, 0, now, 0, CLAIM_BATCH);
            if (due == null || due.isEmpty()) {
                return claimed;
            }
            for (String orderId : due) {
                Long removed = redisTemplate.opsForZSet().remove(RedisKeys.ORDER_TIMEOUT_QUEUE, orderId);
                if (removed != null && removed > 0) {
                    claimed.add(orderId);
                }
            }
        } catch (Exception e) {
            log.warn("扫描超时队列失败: {}", e.getMessage());
        }
        return claimed;
    }

    /** 队列积压量（运维观察用） */
    public long size() {
        try {
            Long size = redisTemplate.opsForZSet().zCard(RedisKeys.ORDER_TIMEOUT_QUEUE);
            return size == null ? 0 : size;
        } catch (Exception e) {
            return -1;
        }
    }
}
