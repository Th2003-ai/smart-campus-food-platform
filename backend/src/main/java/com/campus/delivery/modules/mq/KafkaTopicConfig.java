package com.campus.delivery.modules.mq;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic 声明。
 *
 * <p>3 个分区、副本数 1（单机演示）：分区数决定同一消费组内的并行度，
 * 消息按 key（orderId）哈希落到固定分区，因此「同一订单的消息顺序」是有保证的。
 * 真实集群把副本数调到 3，并设置 min.insync.replicas=2。
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreateTopic() {
        return TopicBuilder.name(MqConstants.ORDER_CREATE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}