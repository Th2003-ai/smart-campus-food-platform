package com.campus.delivery.modules.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 消费者：监听订单创建 topic，异步处理下单后的业务。
 *
 * <p>与 RabbitMQ 那条链路不同，Kafka 用 offset 提交代替逐条 ACK：
 * 消费成功才手动提交 offset，失败不提交（下次从同一位点重新消费）。
 * 演示时可在管理台 / kafka-console-consumer 看到同一条消息仍保留在 topic 里，
 * 这正是 Kafka「消息可回溯」与 RabbitMQ「消费即删除」的本质差别。
 */
@Slf4j
@Component
public class KafkaOrderConsumer {

    @KafkaListener(
            topics = MqConstants.ORDER_CREATE_TOPIC,
            groupId = MqConstants.ORDER_CREATE_GROUP,
            containerFactory = "kafkaListenerContainerFactory")
    public void onOrderCreate(OrderMessage message, Acknowledgment ack) {
        try {
            log.info("【Kafka消费者】异步收到订单消息: orderNo={}, orderId={}, 金额={}, 学生={}",
                    message.getOrderNo(), message.getOrderId(),
                    message.getTotalAmount(), message.getUserId());

            // 这里模拟异步后续处理（通知商户、统计、发券等），与 RabbitMQ 消费者保持同构
            log.info("【Kafka消费者】开始异步处理订单: orderId={}", message.getOrderId());

            // 处理成功，提交 offset（manual_immediate：本条处理完立即提交）
            ack.acknowledge();
            log.info("【Kafka消费者】订单处理完成并已提交offset: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("【Kafka消费者】订单处理失败，不提交 offset: orderId={}, err={}",
                    message.getOrderId(), e.getMessage());
        }
    }
}