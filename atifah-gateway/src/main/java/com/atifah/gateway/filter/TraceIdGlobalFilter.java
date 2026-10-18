package com.atifah.gateway.filter;

import com.atifah.common.core.constant.CommonConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 生成/透传 TraceId，并写入响应头。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        String finalTraceId = traceId;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(CommonConstants.HEADER_TRACE_ID, finalTraceId)
                .build();
        exchange.getResponse().getHeaders().set(CommonConstants.HEADER_TRACE_ID, finalTraceId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
