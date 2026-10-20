package com.campus.delivery.modules.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.api.PageResult;
import com.campus.delivery.common.constant.RedisKeys;
import com.campus.delivery.common.enums.CommonEnums;
import com.campus.delivery.common.enums.OrderStatus;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.delivery.entity.Rider;
import com.campus.delivery.modules.delivery.mapper.GrabLogMapper;
import com.campus.delivery.modules.delivery.mapper.RiderMapper;
import com.campus.delivery.modules.order.entity.Order;
import com.campus.delivery.modules.order.service.OrderService;
import com.campus.delivery.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 配送服务：订单大厅、抢单、取餐、送达、收入统计。主责：成员4（P2 可选加分）。
 *
 * <p>抢单并发控制（需求文档 5.6.2 难点二）：
 * <ol>
 *   <li>Redis 分布式锁 {@code lock:order:grab:{orderId}} 保证同一订单同一时刻只有一个骑手进入；</li>
 *   <li>数据库条件更新（{@code rider_id IS NULL}）做最终一致性兜底；</li>
 *   <li>每次抢单尝试写入 {@code grab_log}，失败原因可追溯。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final int CODE_RIDER_NOT_FOUND = 40001;
    private static final int CODE_GRAB_FAILED = 40002;
    private static final int CODE_RIDER_RESTING = 40003;
    private static final int CODE_LIMIT_EXCEEDED = 40004;

    private static final long GRAB_LOCK_SECONDS = 10L;

    private final RiderMapper riderMapper;
    private final GrabLogMapper grabLogMapper;
    private final OrderService orderService;
    private final UserService userService;
    private final RiderOnlineService riderOnlineService;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 订单大厅：可抢订单列表 */
    public PageResult<Order> orderHall(long pageNum, long pageSize) {
        return orderService.pageGrabPoolOrders(pageNum, pageSize);
    }

    /** 抢单 */
    @Transactional(rollbackFor = Exception.class)
    public Order grab(String riderId, String orderId) {
        String lockKey = RedisKeys.LOCK_ORDER_GRAB + orderId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, riderId, GRAB_LOCK_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            recordGrabLog(orderId, riderId, 0, "手慢了，订单已被其他骑手抢走");
            throw new BusinessException(CODE_GRAB_FAILED, "手慢了，订单已被其他骑手抢走");
        }

        try {
            Rider rider = requireRider(riderId);
            if (!CommonEnums.WorkStatus.ACCEPTING.getCode().equals(rider.getWorkStatus())) {
                recordGrabLog(orderId, riderId, 0, "工作状态为休息中");
                throw new BusinessException(CODE_RIDER_RESTING, "当前为休息中状态，请先切换为接单中");
            }
            if (rider.getDeliveringCount() != null
                    && rider.getMaxDelivering() != null
                    && rider.getDeliveringCount() >= rider.getMaxDelivering()) {
                recordGrabLog(orderId, riderId, 0, "超出接单上限");
                throw new BusinessException(CODE_LIMIT_EXCEEDED,
                        "您有配送中的订单，请先完成（上限 " + rider.getMaxDelivering() + " 单）");
            }

            // 绑定骑手：内部会再次校验订单状态与 rider_id 是否为空
            Order order = orderService.bindRider(orderId, riderId);

            int rows = riderMapper.increaseDelivering(riderId);
            if (rows == 0) {
                throw new BusinessException(CODE_LIMIT_EXCEEDED, "超出接单上限，抢单失败");
            }
            recordGrabLog(orderId, riderId, 1, null);
            log.info("抢单成功: riderId={}, orderId={}", riderId, orderId);
            return order;
        } catch (BusinessException e) {
            // 抢单失败时把订单重新放回抢单池，避免被锁期间其他人也抢不到
            redisTemplate.opsForZSet().add(RedisKeys.GRAB_POOL, orderId, System.currentTimeMillis());
            throw e;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /** 确认取餐：订单进入配送中 */
    @Transactional(rollbackFor = Exception.class)
    public Order pickup(String riderId, String orderId) {
        return orderService.riderTransit(orderId, riderId, OrderStatus.DELIVERING);
    }

    /** 确认送达：订单完成，骑手配送中数量 -1、收入累加 */
    @Transactional(rollbackFor = Exception.class)
    public Order deliver(String riderId, String orderId) {
        Order order = orderService.riderTransit(orderId, riderId, OrderStatus.FINISHED);
        riderMapper.decreaseDelivering(riderId, order.getDeliveryFee() == null
                ? BigDecimal.ZERO : order.getDeliveryFee());
        // TODO(成员4)：更新 delivery_record（取餐/送达时间、配送耗时、配送费）
        return order;
    }

    /** 我的配送订单 */
    public PageResult<Order> myDeliveringOrders(String riderId, Integer status, long pageNum, long pageSize) {
        return orderService.pageRiderOrders(riderId, status, pageNum, pageSize);
    }

    /** 收入统计：今日 / 本周 / 本月 */
    public Map<String, Object> income(String riderId) {
        Map<String, Object> stat = grabLogMapper.statIncome(riderId);
        stat.put("totalIncome", grabLogMapper.sumIncome(riderId));
        stat.put("online", riderOnlineService.isOnline(riderId));
        stat.put("lastHeartbeat", riderOnlineService.lastHeartbeat(riderId));
        return stat;
    }

    /**
     * 骑手上线心跳（供非 WebSocket 场景使用，如接口轮询的客户端）。
     *
     * <p>续期 Redis 在线标记；超过 60 秒没有心跳即视为离线。
     */
    public void heartbeat(String riderId) {
        Rider rider = requireRider(riderId);
        riderOnlineService.markOnline(rider.getId());
    }

    /** 骑手是否在线（Redis 在线标记是否仍有效） */
    public boolean isOnline(String riderId) {
        return riderOnlineService.isOnline(riderId);
    }

    /** 切换工作状态：0休息中 1接单中 */
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkStatus(String riderId, Integer workStatus) {
        Rider rider = requireRider(riderId);
        rider.setWorkStatus(workStatus);
        riderMapper.updateById(rider);
    }

    // ------------------------------------------------------------------

    /** 根据登录账号ID定位骑手档案 */
    public Rider getByUserId(String userId) {
        return riderMapper.selectOne(new LambdaQueryWrapper<Rider>()
                .eq(Rider::getUserId, userId).last("LIMIT 1"));
    }

    private Rider requireRider(String riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BusinessException(CODE_RIDER_NOT_FOUND, "骑手信息不存在");
        }
        if (!CommonEnums.AuditStatus.PASSED.getCode().equals(rider.getAuditStatus())) {
            throw new BusinessException(40005, "骑手资质尚未通过审核");
        }
        return rider;
    }

    private void recordGrabLog(String orderId, String riderId, int result, String failReason) {
        try {
            grabLogMapper.insert(UUID.randomUUID().toString().replace("-", ""),
                    orderId, riderId, result, failReason);
        } catch (Exception e) {
            log.warn("抢单日志写入失败: {}", e.getMessage());
        }
    }

    // ==================================================================
    // 管理端：骑手审核与封禁（供 AdminAuditService 调用）
    // ==================================================================

    /**
     * 审核骑手资质。
     *
     * <p>约束：只有「待审核」可审核；驳回必须填写原因。
     * 通过后骑手才能进入抢单流程（{@link #requireRider} 会校验 audit_status）。
     *
     * @param riderId 骑手ID（rider.id，不是 sys_user.id）
     * @param pass    true 通过，false 驳回
     * @param remark  审核意见，驳回时必填
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditRider(String riderId, boolean pass, String remark) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BusinessException(CODE_RIDER_NOT_FOUND, "骑手信息不存在");
        }
        if (!CommonEnums.AuditStatus.WAITING.getCode().equals(rider.getAuditStatus())) {
            throw new BusinessException(40006, "该骑手已审核（当前状态："
                    + auditStatusText(rider.getAuditStatus()) + "），不可重复审核");
        }
        if (!pass && !StringUtils.hasText(remark)) {
            throw new BusinessException(40007, "驳回骑手必须填写驳回原因");
        }
        rider.setAuditStatus(pass
                ? CommonEnums.AuditStatus.PASSED.getCode()
                : CommonEnums.AuditStatus.REJECTED.getCode());
        rider.setAuditRemark(remark);
        riderMapper.updateById(rider);
    }

    /**
     * 封禁 / 解封骑手。
     *
     * <p>封禁语义落在 {@code sys_user.status}（0禁用）：登录入口会直接拦截，
     * 因此封禁后骑手既无法登录也无法抢单。rider 表本身没有封禁字段
     * （见 database/schema.sql），故不新增列，以账号状态为唯一事实来源。
     *
     * <p>封禁时若骑手处于「接单中」，一并切回「休息中」，避免前端仍显示可接单。
     *
     * @param riderId 骑手ID
     * @param ban     true 封禁，false 解封
     */
    @Transactional(rollbackFor = Exception.class)
    public void banRider(String riderId, boolean ban) {
        // 注意：这里不能复用 requireRider —— 那个方法会因「资质未通过」抛异常，
        // 而管理端恰恰需要对未通过审核的骑手执行封禁。
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BusinessException(CODE_RIDER_NOT_FOUND, "骑手信息不存在");
        }
        userService.updateAccountStatus(rider.getUserId(), ban ? 0 : 1);
        if (ban && CommonEnums.WorkStatus.ACCEPTING.getCode().equals(rider.getWorkStatus())) {
            rider.setWorkStatus(CommonEnums.WorkStatus.RESTING.getCode());
            riderMapper.updateById(rider);
        }
    }

    /**
     * 管理端：骑手审核列表（含账号状态，供管理端判断是否已封禁）。
     *
     * @param auditStatus 审核状态过滤，null 表示全部
     */
    public List<Rider> listRidersForAudit(Integer auditStatus) {
        return riderMapper.selectList(new LambdaQueryWrapper<Rider>()
                .eq(auditStatus != null, Rider::getAuditStatus, auditStatus)
                .orderByAsc(Rider::getAuditStatus)
                .orderByDesc(Rider::getCreateTime));
    }

    private String auditStatusText(Integer status) {
        if (CommonEnums.AuditStatus.PASSED.getCode().equals(status)) {
            return "已通过";
        }
        if (CommonEnums.AuditStatus.REJECTED.getCode().equals(status)) {
            return "已驳回";
        }
        return "待审核";
    }
}
