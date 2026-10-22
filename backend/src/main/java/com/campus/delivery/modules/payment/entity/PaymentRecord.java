package com.campus.delivery.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟支付记录（payment_record 表）。
 *
 * <p>一个订单一条记录（表上有 {@code uk_order_id} 唯一索引）：
 * 支付成功写 status=1，退款时把同一条记录改成 status=3。
 */
@Data
@TableName("payment_record")
public class PaymentRecord implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    private String orderId;

    /** 支付人（sys_user.id） */
    private String userId;

    private BigDecimal amount;

    /** 1余额 2模拟第三方 */
    private Integer payType;

    /** 0待支付 1成功 2失败 3已退款 */
    private Integer status;

    /** 模拟交易号 */
    private String tradeNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
