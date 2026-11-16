package com.atifah.common.mq.publisher;

import com.atifah.common.core.constant.CommonConstants;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息发布器 TraceId 消息属性测试。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class MessagePublisherTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsTraceIdHeader() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        SendResult result = mock(SendResult.class);
        when(result.getMsgId()).thenReturn("msg-1");
        when(template.syncSend(eq("topic"), any(Message.class))).thenReturn(result);
        MDC.put(CommonConstants.MDC_TRACE_ID, "trace-1");

        new MessagePublisher(template).send("topic", "user-1", new Object());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(template).syncSend(eq("topic"), captor.capture());
        Message<?> message = captor.getValue();
        assertEquals("trace-1", message.getHeaders().get(CommonConstants.HEADER_TRACE_ID));
        assertEquals("user-1", message.getHeaders().get(RocketMQHeaders.KEYS));
    }
}
