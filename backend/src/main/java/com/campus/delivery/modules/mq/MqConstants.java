package com.campus.delivery.modules.mq;

/**
 * RabbitMQ 队列、交换机、路由键常量。
 *
 * <p>统一管理名字，生产者和消费者引用同一常量，避免两边字符串写错对不上。
 */
public final class MqConstants {

    private MqConstants() {
    }

    /** 订单创建队列 */
    public static final String ORDER_CREATE_QUEUE = "q.order.create";

    /** 订单直连交换机 */
    public static final String ORDER_EXCHANGE = "ex.order";

    /** 订单创建路由键 */
    public static final String ORDER_CREATE_ROUTING_KEY = "rk.order.create";

    // ===== Kafka：与上面的队列/交换机并行的一套命名 =====

    /** 订单创建 topic（3 分区，按 orderId 哈希分区） */
    public static final String ORDER_CREATE_TOPIC = "t.order.create";

    /** 订单消费组（同一组内多实例会分摊分区，实现并行消费） */
    public static final String ORDER_CREATE_GROUP = "g.order.create";
}
