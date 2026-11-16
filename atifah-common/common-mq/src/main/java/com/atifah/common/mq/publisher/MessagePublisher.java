package com.atifah.common.mq.publisher;

import com.atifah.common.core.constant.CommonConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

/**
 * 统一消息发送入口：使用业务 Key 保证同一用户事件可顺序消费。
 * 消息属性携带 TraceId，消息体不打印、不改变。
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
        String traceId = currentTraceId();
        Message<Object> message = buildMessage(key, payload, traceId);
        SendResult result = rocketMQTemplate.syncSend(topic, message);
        log.debug("mq send success, topic={}, key={}, msgId={}, traceId={}",
                topic, key, result.getMsgId(), traceIdOrDash(traceId));
        return result;
    }

    /**
     * 异步发送消息，结果通过回调处理。
     *
     * @param topic 主题
     * @param key 业务键，通常为 userId
     * @param payload 消息体
     */
    public void sendAsync(String topic, String key, Object payload) {
        String traceId = currentTraceId();
        Message<Object> message = buildMessage(key, payload, traceId);
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("mq send success, topic={}, key={}, msgId={}, traceId={}",
                        topic, key, sendResult.getMsgId(), traceIdOrDash(traceId));
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("mq send failed, topic={}, key={}, traceId={}",
                        topic, key, traceIdOrDash(traceId), throwable);
            }
        });
    }

    /**
     * 构建带业务键与 TraceId 的消息；TraceId 属于消息属性，不进入消息体。
     *
     * @param key 业务键
     * @param payload 消息体
     * @param traceId 链路 ID
     * @return 消息对象
     */
    private Message<Object> buildMessage(String key, Object payload, String traceId) {
        MessageBuilder<Object> builder = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, key);
        if (StringUtils.hasText(traceId)) {
            builder.setHeader(CommonConstants.HEADER_TRACE_ID, traceId);
        }
        return builder.build();
    }

    /**
     * 读取当前线程 MDC 中的 TraceId。
     *
     * @return TraceId，可能为空
     */
    private String currentTraceId() {
        return MDC.get(CommonConstants.MDC_TRACE_ID);
    }

    /**
     * 空 TraceId 统一输出为占位符。
     *
     * @param traceId 链路 ID
     * @return 原值或占位符
     */
    private String traceIdOrDash(String traceId) {
        return StringUtils.hasText(traceId) ? traceId : "-";
    }
}
