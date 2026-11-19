package com.campus.delivery.modules.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明订单队列、直连交换机、绑定关系，并启用 JSON 消息转换。
 *
 * <p>队列和交换机都声明为持久化（durable），Broker 重启后队列仍在。
 * 消息以 JSON 传输，管理界面里能直接看到可读内容，方便演示排查。
 */
@Configuration
public class




RabbitMqConfig {

    /** 订单创建队列（持久化） */
    @Bean
    public Queue orderCreateQueue() {
        return new Queue(MqConstants.ORDER_CREATE_QUEUE, true);
    }

    /** 订单直连交换机（持久化） */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(MqConstants.ORDER_EXCHANGE, true, false);
    }

    /** 绑定：队列通过路由键绑定到交换机 */
    @Bean
    public Binding orderCreateBinding(Queue orderCreateQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderCreateQueue)
                .to(orderExchange)
                .with(MqConstants.ORDER_CREATE_ROUTING_KEY);
    }

    /** 消息体用 JSON 序列化/反序列化 */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
