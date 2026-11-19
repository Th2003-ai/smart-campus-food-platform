package com.campus.delivery.modules.mq;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单创建消息体：下单成功后投递到 RabbitMQ，由消费者异步处理。
 *
 * <p>只携带后续异步处理需要的字段，不把整个订单实体塞进来。
 */
@Data
public class OrderMessage implements Serializable {

    /** 订单ID */
    private String orderId;

    /** 订单号 */
    private String orderNo;

    /** 下单学生ID */
    private String userId;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 消息产生时间 */
    private LocalDateTime createTime;
}
