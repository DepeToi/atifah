package com.atifah.gateway.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.core.trace.TraceIdUtils;
import com.atifah.gateway.constant.GatewayConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 生成/校验 TraceId，剥离客户端伪造的身份头，并写入响应头。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceIdUtils.normalizeOrGenerate(
                exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID));

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(CommonConstants.HEADER_USER_ID);
                    headers.remove(CommonConstants.HEADER_USERNAME);
                    headers.set(CommonConstants.HEADER_TRACE_ID, traceId);
                })
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getAttributes().put(GatewayConstants.ATTR_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(CommonConstants.HEADER_TRACE_ID, traceId);
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
