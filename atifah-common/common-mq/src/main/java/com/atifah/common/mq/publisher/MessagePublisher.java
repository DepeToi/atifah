package com.atifah.common.mq.publisher;

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
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class MessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 同步发送消息，使用业务键保证同一用户事件顺序。
     *
     * @param topic 主题
     * @param key 业务键，通常为 userId
     * @param payload 消息体
     * @return 发送结果
     */
    public SendResult send(String topic, String key, Object payload) {
        Message<Object> message = buildMessage(key, payload);
        return rocketMQTemplate.syncSend(topic, message);
    }

    /**
     * 异步发送消息，结果通过回调处理。
     *
     * @param topic 主题
     * @param key 业务键，通常为 userId
     * @param payload 消息体
     */
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

    /**
     * 构建带业务键的消息。
     *
     * @param key 业务键
     * @param payload 消息体
     * @return 消息对象
     */
    private Message<Object> buildMessage(String key, Object payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, key)
                .build();
    }
}
