package com.atifah.gateway.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.security.jwt.JwtTokenProvider;
import com.atifah.gateway.config.GatewayAuthProperties;
import com.atifah.gateway.constant.GatewayConstants;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
import java.util.regex.Pattern;

/**
 * JWT 鉴权全局过滤器。开启后校验 Token 并把用户身份写入下游请求头。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "atifah.gateway.auth", name = "enabled", havingValue = "true")
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** Bearer 令牌前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 用户 ID 安全字符与长度限制，避免日志注入与非法下游头。 */
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

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
            return unauthorized(exchange, "missing_token");
        }

        try {
            Claims claims = jwtTokenProvider.parseToken(authorization.substring(BEARER_PREFIX.length()));
            String userId = claims.getSubject();
            if (userId == null || !USER_ID_PATTERN.matcher(userId).matches()) {
                return unauthorized(exchange, "invalid_token");
            }
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header(CommonConstants.HEADER_USER_ID, userId)
                    .header(CommonConstants.HEADER_USERNAME, String.valueOf(claims.get("username")))
                    .build();
            ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
            mutatedExchange.getAttributes().put(GatewayConstants.ATTR_USER_ID, userId);
            return chain.filter(mutatedExchange);
        } catch (Exception ex) {
            return unauthorized(exchange, "invalid_token");
        }
    }

    /**
     * 返回 401 未认证响应，并记录不包含令牌内容的失败原因。
     *
     * @param exchange 请求交换上下文
     * @param reason 失败原因
     * @return 响应流
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        logAuthFailed(exchange, reason);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":40100,\"message\":\"未认证或登录已过期\"}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 记录鉴权失败原因；只包含路径与原因，不记录 Token 与 claims。
     *
     * @param exchange 请求交换上下文
     * @param reason 失败原因
     */
    private void logAuthFailed(ServerWebExchange exchange, String reason) {
        String traceId = exchange.getAttribute(GatewayConstants.ATTR_TRACE_ID);
        String path = exchange.getRequest().getURI().getPath();
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CommonConstants.MDC_TRACE_ID,
                traceId == null ? "-" : traceId)) {
            log.warn("event=auth_failed path={} reason={}", path, reason);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
