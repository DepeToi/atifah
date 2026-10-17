package com.microhoov.common.mq.config;

import com.microhoov.common.mq.publisher.MessagePublisher;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 发布器自动装配。
 */
@AutoConfiguration(after = RocketMQAutoConfiguration.class)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
public class MqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessagePublisher messagePublisher(RocketMQTemplate rocketMQTemplate) {
        return new MessagePublisher(rocketMQTemplate);
    }
}
