package com.campus.delivery.modules.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者：下单成功后把订单消息投递到交换机。
 *
 * <p>发送动作很快，调用方不需要等待后续业务处理，真正的处理交给消费者异步完成，
 * 这就是消息队列削峰、异步的价值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送订单创建消息。
     *
     * @param message 订单消息
     */
    public void sendOrderCreate(OrderMessage message) {
        rabbitTemplate.convertAndSend(
                MqConstants.ORDER_EXCHANGE,
                MqConstants.ORDER_CREATE_ROUTING_KEY,
                message);
        log.info("【MQ生产者】订单消息已投递: orderNo={}, orderId={}",
                message.getOrderNo(), message.getOrderId());
    }
}
