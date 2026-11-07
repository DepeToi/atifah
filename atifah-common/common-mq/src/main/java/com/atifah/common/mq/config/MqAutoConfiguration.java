package com.atifah.common.mq.config;

import com.atifah.common.mq.publisher.MessagePublisher;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * RocketMQ 发布器自动装配。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@AutoConfiguration(after = RocketMQAutoConfiguration.class)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
public class MqAutoConfiguration {

    /**
     * 装配消息发布器。
     *
     * @param rocketMQTemplate RocketMQ 模板
     * @return 消息发布器
     */
    @Bean
    @ConditionalOnMissingBean
    public MessagePublisher messagePublisher(RocketMQTemplate rocketMQTemplate) {
        return new MessagePublisher(rocketMQTemplate);
    }
}
