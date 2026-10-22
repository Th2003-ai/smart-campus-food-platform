package com.campus.delivery.modules.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.delivery.common.exception.BusinessException;
import com.campus.delivery.modules.order.entity.Order;
import com.campus.delivery.modules.order.mapper.OrderMapper;
import com.campus.delivery.modules.payment.entity.PaymentRecord;
import com.campus.delivery.modules.payment.mapper.PaymentRecordMapper;
import com.campus.delivery.modules.user.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 模拟支付服务：扣款、退款、流水。
 *
 * <p>设计原则与 PPT「选型速查表」一致 —— **金额只落磁盘库 + 事务，不放进 Redis**：
 * <ul>
 *   <li>余额是「绝不能错」的数据，扣减用带条件的单条 SQL（{@code balance >= ?}），
 *       依赖 InnoDB 行锁保证不超扣，不用「先查后改」；</li>
 *   <li>退款用 {@code orders.pay_status = 2 WHERE pay_status = 1} 的条件更新做闸门，
 *       影响行数为 0 说明已退过，直接跳过 —— 天然防重复退款；</li>
 *   <li>支付/退款都写 {@code payment_record}，便于对账。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 余额不足 */
    private static final int CODE_BALANCE_NOT_ENOUGH = 30050;

    private final StudentMapper studentMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final OrderMapper orderMapper;

    /**
     * 支付（在下单事务内调用）。
     *
     * <p>payType=1 余额支付：条件扣减余额，不足直接抛异常触发整单回滚；
     * payType=2 模拟第三方：不改余额，只写流水。
     *
     * @param order  已落库的订单（含 totalAmount / payType）
     * @param userId 支付人
     */
    @Transactional(rollbackFor = Exception.class)
    public void charge(Order order, String userId) {
        int payType = order.getPayType() == null ? 1 : order.getPayType();
        BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();

        if (payType == 1) {
            int rows = studentMapper.deductBalance(userId, amount);
            if (rows == 0) {
                throw new BusinessException(CODE_BALANCE_NOT_ENOUGH,
                        "余额不足：本单需 ¥" + amount + "，请先充值或改用模拟第三方支付");
            }
            log.info("余额支付成功: orderNo={}, userId={}, amount={}", order.getOrderNo(), userId, amount);
        } else {
            log.info("模拟第三方支付成功: orderNo={}, userId={}, amount={}", order.getOrderNo(), userId, amount);
        }

        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setUserId(userId);
        record.setAmount(amount);
        record.setPayType(payType);
        record.setStatus(1);
        record.setTradeNo((payType == 1 ? "BAL" : "MOCK")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        paymentRecordMapper.insert(record);
    }

    /**
     * 退款（取消 / 拒单 / 超时自动取消时调用）。
     *
     * <p>幂等：只有把 {@code pay_status} 从 1 改成 2 的那一次才会真正加回余额，
     * 重复调用返回 false 且不会重复加钱。
     *
     * @return true 表示本次确实执行了退款
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean refund(Order order, String reason) {
        if (order == null || order.getId() == null) {
            return false;
        }
        int rows = orderMapper.markRefunded(order.getId());
        if (rows == 0) {
            log.info("订单无需退款（已退款或未支付）: orderId={}", order.getId());
            return false;
        }

        BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        // 只有余额支付的钱才需要退回学生账户；模拟第三方视为原路退回
        if (Integer.valueOf(1).equals(order.getPayType())) {
            studentMapper.addBalance(order.getUserId(), amount);
            log.info("余额退款成功: orderNo={}, userId={}, amount={}, reason={}",
                    order.getOrderNo(), order.getUserId(), amount, reason);
        } else {
            log.info("模拟第三方退款: orderNo={}, amount={}, reason={}",
                    order.getOrderNo(), amount, reason);
        }

        order.setPayStatus(2);
        PaymentRecord record = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, order.getId())
                .last("LIMIT 1"));
        if (record != null) {
            record.setStatus(3);
            paymentRecordMapper.updateById(record);
        }
        return true;
    }
}
