package com.atifah.gateway.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atifah.gateway.config.GatewayAccessLogProperties;
import com.atifah.gateway.constant.GatewayConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关访问日志测试：字段、query 不落日志与健康检查跳过。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class AccessLogGlobalFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(AccessLogGlobalFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void logsOneLineWithoutQuery() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/moods?phone=13800000000")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayConstants.ATTR_TRACE_ID, "trace-1");
        exchange.getAttributes().put(GatewayConstants.ATTR_USER_ID, "user-1");
        AccessLogGlobalFilter filter = new AccessLogGlobalFilter(new GatewayAccessLogProperties());

        filter.filter(exchange, ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }).block();

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("event=gateway_access"));
        assertTrue(message.contains("path=/api/v1/moods"));
        assertTrue(message.contains("status=200"));
        assertTrue(message.contains("userId=user-1"));
        assertFalse(message.contains("phone"));
        assertFalse(message.contains("?"));
    }

    @Test
    void skipsActuatorPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        AccessLogGlobalFilter filter = new AccessLogGlobalFilter(new GatewayAccessLogProperties());

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertTrue(appender.list.isEmpty());
    }
}
