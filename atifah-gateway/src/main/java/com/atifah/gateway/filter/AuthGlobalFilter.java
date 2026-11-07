package com.atifah.gateway.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.security.jwt.JwtTokenProvider;
import com.atifah.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * JWT 鉴权全局过滤器。开启后校验 Token 并把用户身份写入下游请求头。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "atifah.gateway.auth", name = "enabled", havingValue = "true")
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** Bearer 令牌前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final GatewayAuthProperties authProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean whitelisted = authProperties.getWhitelist().stream().anyMatch(path::startsWith);
        if (whitelisted) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }

        try {
            Claims claims = jwtTokenProvider.parseToken(authorization.substring(BEARER_PREFIX.length()));
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header(CommonConstants.HEADER_USER_ID, claims.getSubject())
                    .header(CommonConstants.HEADER_USERNAME, String.valueOf(claims.get("username")))
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (Exception ex) {
            return unauthorized(exchange);
        }
    }

    /**
     * 返回 401 未认证响应。
     *
     * @param exchange 请求交换上下文
     * @return 响应流
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":40100,\"message\":\"未认证或登录已过期\"}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
