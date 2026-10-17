package com.microhoov.common.mq.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 统一消息发送入口：使用业务 Key 保证同一用户事件可顺序消费。
 */
@Slf4j
@RequiredArgsConstructor
public class MessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;

    public SendResult send(String topic, String key, Object payload) {
        Message<Object> message = buildMessage(key, payload);
        return rocketMQTemplate.syncSend(topic, message);
    }

    public void sendAsync(String topic, String key, Object payload) {
        Message<Object> message = buildMessage(key, payload);
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("mq send success, topic={}, key={}, msgId={}",
                        topic, key, sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("mq send failed, topic={}, key={}", topic, key, throwable);
            }
        });
    }

    private Message<Object> buildMessage(String key, Object payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, key)
                .build();
    }
}
