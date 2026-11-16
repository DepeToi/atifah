package com.atifah.common.web.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.web.config.AccessLogProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务访问日志测试：字段、级别、忽略路径与 query 不落日志。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class AccessLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
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
        MDC.clear();
    }

    @Test
    void logsOneLineWithoutQuery() throws Exception {
        AccessLogProperties properties = new AccessLogProperties();
        AccessLogFilter filter = new AccessLogFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/moods");
        request.setQueryString("phone=13800000000");
        request.setRemoteAddr("10.0.0.5");
        MDC.put(CommonConstants.MDC_USER_ID, "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains("event=access"));
        assertTrue(message.contains("path=/api/v1/moods"));
        assertTrue(message.contains("status=200"));
        assertTrue(message.contains("userId=user-1"));
        assertTrue(message.contains("remoteIp=10.0.0.5"));
        assertFalse(message.contains("phone"));
        assertFalse(message.contains("?"));
    }

    @Test
    void fiveHundredUsesErrorLevel() throws Exception {
        AccessLogProperties properties = new AccessLogProperties();
        AccessLogFilter filter = new AccessLogFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/moods");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (req, res) -> ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));

        assertEquals(1, appender.list.size());
        assertEquals(Level.ERROR, appender.list.get(0).getLevel());
    }

    @Test
    void skipsActuatorPath() throws Exception {
        AccessLogProperties properties = new AccessLogProperties();
        AccessLogFilter filter = new AccessLogFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertTrue(appender.list.isEmpty());
    }
}
