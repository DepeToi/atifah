package com.atifah.gateway.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.gateway.constant.GatewayConstants;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关 TraceId 过滤器测试：规范化、身份头剥离与响应头回写。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class TraceIdGlobalFilterTest {

    @Test
    void normalizesTraceAndStripsIdentityHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/moods")
                .header(CommonConstants.HEADER_TRACE_ID, "A".repeat(32))
                .header(CommonConstants.HEADER_USER_ID, "spoofed")
                .header(CommonConstants.HEADER_USERNAME, "spoofed")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        new TraceIdGlobalFilter().filter(exchange, ex -> {
            captured.set(ex);
            return Mono.empty();
        }).block();

        ServerWebExchange downstream = captured.get();
        assertEquals("a".repeat(32),
                downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID));
        assertNull(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID));
        assertNull(downstream.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USERNAME));
        assertEquals("a".repeat(32),
                exchange.getResponse().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID));
        assertEquals("a".repeat(32), exchange.getAttribute(GatewayConstants.ATTR_TRACE_ID));
    }

    @Test
    void generatesTraceForInvalidHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/moods")
                .header(CommonConstants.HEADER_TRACE_ID, "not-a-trace-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        new TraceIdGlobalFilter().filter(exchange, ex -> {
            captured.set(ex);
            return Mono.empty();
        }).block();

        String traceId = captured.get().getRequest().getHeaders()
                .getFirst(CommonConstants.HEADER_TRACE_ID);
        assertNotNull(traceId);
        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }
}
