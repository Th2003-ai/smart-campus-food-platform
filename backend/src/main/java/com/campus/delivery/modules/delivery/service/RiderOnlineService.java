package com.campus.delivery.modules.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.modules.delivery.entity.Rider;
import com.campus.delivery.modules.delivery.mapper.RiderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 骑手在线状态服务（对应 {@code RedisKeys.RIDER_ONLINE}）。
 *
 * <p><b>为什么用 Redis 而不是数据库字段</b>：
 * 在线状态是「高频写、低频读、且天然可丢失」的数据 ——
 * 骑手的 WebSocket 连接建立/心跳/断开都要刷新它。
 * 若落数据库，每次心跳都是一次 UPDATE，纯属浪费；
 * 放内存库并用 TTL 表达「多久没心跳就算离线」，连清理逻辑都省了。
 *
 * <p><b>TTL 语义</b>：值为最后心跳时间戳，键的有效期即「离线判定窗口」。
 * 骑手进程崩溃 / 断网时不会主动上报下线，靠 TTL 自然过期即可判定离线，
 * 这就是用「过期」代替「定时扫描」的典型手法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderOnlineService {

    /** 心跳超时时间（秒）：超过该时长没有心跳即视为离线 */
    private static final long ONLINE_TTL_SECONDS = 60L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RiderMapper riderMapper;

    // ------------------------------------------------------------------
    // 场景 9（PPT 第 13、17 页）：骑手在线状态
    //   结构：String —— rider:online:{riderId}，value = 最后一次心跳的毫秒时间戳
    //   过期：60 秒（键的有效期就是「离线判定窗口」）
    //   核心技巧：不存「在线 / 离线」布尔值，而是让 TTL 承担离线判定——
    //           骑手进程崩溃或断网时不会主动上报下线，靠键自然过期即可
    //   对比：如果用数据库字段，就得跑定时任务扫「多久没心跳」，代价高得多
    // ------------------------------------------------------------------
    /**
     * 标记骑手在线（WebSocket 连接建立 / 收到心跳时调用）。
     *
     * @param userId 登录账号ID（sys_user.id），内部解析为 rider.id
     */
    public void markOnlineByUserId(String userId) {
        Rider rider = findByUserId(userId);
        if (rider == null) {
            return;
        }
        markOnline(rider.getId());
    }

    /** 标记骑手在线，并刷新过期时间 */
    public void markOnline(String riderId) {
        if (riderId == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    RedisKeys.RIDER_ONLINE + riderId,
                    System.currentTimeMillis(),
                    ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入骑手在线状态失败: riderId={}, {}", riderId, e.getMessage());
        }
    }

    /** 标记骑手离线（WebSocket 断开时调用） */
    public void markOfflineByUserId(String userId) {
        Rider rider = findByUserId(userId);
        if (rider == null) {
            return;
        }
        try {
            redisTemplate.delete(RedisKeys.RIDER_ONLINE + rider.getId());
        } catch (Exception e) {
            log.warn("删除骑手在线状态失败: riderId={}, {}", rider.getId(), e.getMessage());
        }
    }

    /** 判断骑手是否在线；Redis 异常时返回 false，不阻断业务 */
    public boolean isOnline(String riderId) {
        if (riderId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.RIDER_ONLINE + riderId));
        } catch (Exception e) {
            log.warn("查询骑手在线状态失败: riderId={}, {}", riderId, e.getMessage());
            return false;
        }
    }

    /** 取最后心跳时间戳（毫秒），离线返回 null */
    public Long lastHeartbeat(String riderId) {
        try {
            Object value = redisTemplate.opsForValue().get(RedisKeys.RIDER_ONLINE + riderId);
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Rider findByUserId(String userId) {
        if (userId == null) {
            return null;
        }
        return riderMapper.selectOne(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getUserId, userId).last("LIMIT 1"));
    }
}
