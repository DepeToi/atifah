package com.atifah.common.web.feign;

import com.atifah.common.core.constant.CommonConstants;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feign 链路头透传测试。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class FeignTraceIdInterceptorTest {

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void copiesTraceAndIdentityHeadersOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CommonConstants.HEADER_TRACE_ID, "trace-1");
        request.addHeader(CommonConstants.HEADER_USER_ID, "user-1");
        request.addHeader(CommonConstants.HEADER_USERNAME, "alice");
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("Idempotency-Key", "idem-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        new FeignTraceIdInterceptor().apply(template);

        assertEquals("trace-1",
                template.headers().get(CommonConstants.HEADER_TRACE_ID).iterator().next());
        assertEquals("user-1",
                template.headers().get(CommonConstants.HEADER_USER_ID).iterator().next());
        assertEquals("alice",
                template.headers().get(CommonConstants.HEADER_USERNAME).iterator().next());
        assertFalse(template.headers().containsKey("Authorization"));
        assertFalse(template.headers().containsKey("Idempotency-Key"));
    }

    @Test
    void fallsBackToMdcWhenRequestHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put(CommonConstants.MDC_TRACE_ID, "trace-from-mdc");
        RequestTemplate template = new RequestTemplate();

        new FeignTraceIdInterceptor().apply(template);

        assertEquals("trace-from-mdc",
                template.headers().get(CommonConstants.HEADER_TRACE_ID).iterator().next());
    }

    @Test
    void noRequestContextDoesNothing() {
        RequestTemplate template = new RequestTemplate();

        new FeignTraceIdInterceptor().apply(template);

        assertTrue(template.headers().isEmpty());
    }
}
