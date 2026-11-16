package com.atifah.common.web.filter;

import com.atifah.common.core.constant.CommonConstants;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TraceId 过滤器测试：校验、MDC 写入与清理、用户 ID 过滤。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void writesTraceAndUserToMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/moods");
        request.addHeader(CommonConstants.HEADER_TRACE_ID, "A".repeat(32));
        request.addHeader(CommonConstants.HEADER_USER_ID, "user_123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInChain = new AtomicReference<>();
        AtomicReference<String> userInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            traceInChain.set(MDC.get(CommonConstants.MDC_TRACE_ID));
            userInChain.set(MDC.get(CommonConstants.MDC_USER_ID));
        };

        new TraceIdFilter().doFilter(request, response, chain);

        assertEquals("a".repeat(32), traceInChain.get());
        assertEquals("user_123", userInChain.get());
        assertEquals("a".repeat(32), response.getHeader(CommonConstants.HEADER_TRACE_ID));
        assertNull(MDC.get(CommonConstants.MDC_TRACE_ID));
        assertNull(MDC.get(CommonConstants.MDC_USER_ID));
    }

    @Test
    void regeneratesInvalidTraceAndIgnoresInvalidUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/moods");
        request.addHeader(CommonConstants.HEADER_TRACE_ID, "not-a-trace-id");
        request.addHeader(CommonConstants.HEADER_USER_ID, "bad user");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInChain = new AtomicReference<>();
        AtomicReference<String> userInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            traceInChain.set(MDC.get(CommonConstants.MDC_TRACE_ID));
            userInChain.set(MDC.get(CommonConstants.MDC_USER_ID));
        };

        new TraceIdFilter().doFilter(request, response, chain);

        assertTrue(traceInChain.get().matches("[0-9a-f]{32}"));
        assertNull(userInChain.get());
        assertNull(MDC.get(CommonConstants.MDC_TRACE_ID));
        assertNull(MDC.get(CommonConstants.MDC_USER_ID));
    }
}
