package com.campus.delivery.modules.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 生产者：订单创建消息投递到 topic。
 *
 * <p>与 {@link OrderMessageProducer} 并行存在，两条链路互不影响：
 * RabbitMQ 侧重「按路由键分发到不同队列」，Kafka 侧重「按订单 ID 分区、可回溯重放」。
 * 消息 key 用 orderId，保证同一订单的消息落在同一 partition（局部有序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送订单创建消息（异步，不阻塞下单主流程）。
     *
     * @param message 订单消息
     */
    public void sendOrderCreate(OrderMessage message) {
        kafkaTemplate.send(MqConstants.ORDER_CREATE_TOPIC, message.getOrderId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("【Kafka生产者】投递失败: orderId={}, err={}",
                                message.getOrderId(), ex.getMessage());
                        return;
                    }
                    var meta = result.getRecordMetadata();
                    log.info("【Kafka生产者】订单消息已投递: orderNo={}, orderId={}, topic={}, partition={}, offset={}",
                            message.getOrderNo(), message.getOrderId(),
                            meta.topic(), meta.partition(), meta.offset());
                });
    }
}