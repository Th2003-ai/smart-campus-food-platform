package com.campus.delivery.modules.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费者：监听订单创建队列，异步处理下单后的业务。
 *
 * <p>采用手动 ACK：处理成功才确认消息；处理失败则拒绝且不重新入队，
 * 防止消息在处理过程中因宕机丢失，也避免异常消息无限重试。
 */
@Slf4j
@Component
public class OrderMessageConsumer {

    @RabbitListener(queues = MqConstants.ORDER_CREATE_QUEUE)
    public void onOrderCreate(@Payload OrderMessage message,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        try {
            log.info("【MQ消费者】异步收到订单消息: orderNo={}, orderId={}, 金额={}, 学生={}",
                    message.getOrderNo(), message.getOrderId(),
                    message.getTotalAmount(), message.getUserId());

            // 这里模拟异步后续处理（如通知商户、统计、发券等），
            // 真实项目可在此扩展具体业务，不阻塞用户下单请求。
            log.info("【MQ消费者】开始异步处理订单: orderId={}", message.getOrderId());

            // 处理成功，手动确认消息（multiple=false 只确认当前这条）
            channel.basicAck(deliveryTag, false);
            log.info("【MQ消费者】订单处理完成并已ACK: orderId={}", message.getOrderId());
        } catch (Exception e) {
            log.error("【MQ消费者】订单处理失败: orderId={}, err={}", message.getOrderId(), e.getMessage());
            // 拒绝消息，requeue=false 不再重新入队，避免无限循环
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
