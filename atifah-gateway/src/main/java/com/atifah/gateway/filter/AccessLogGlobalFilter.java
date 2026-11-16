package com.atifah.gateway.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.gateway.config.GatewayAccessLogProperties;
import com.atifah.gateway.constant.GatewayConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 网关访问日志：一请求一行，记录方法、路径、状态、耗时、路由与用户 ID。
 * 不记录 query、请求体、响应体和 Header。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayAccessLogProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!properties.isEnabled() || shouldIgnore(path)) {
            return chain.filter(exchange);
        }

        String traceId = exchange.getAttribute(GatewayConstants.ATTR_TRACE_ID);
        String method = exchange.getRequest().getMethod() == null
                ? "-"
                : exchange.getRequest().getMethod().name();
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? null : route.getId();
        long startNanos = System.nanoTime();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        return chain.filter(exchange)
                .doOnError(error -> {
                    errorRef.set(error);
                    logGatewayError(traceId, method, path, error);
                })
                .doFinally(signal -> logAccess(exchange, traceId, method, path, routeId,
                        startNanos, errorRef.get()));
    }

    /**
     * 判断路径是否在忽略列表中。
     *
     * @param path 请求路径
     * @return 是否忽略
     */
    private boolean shouldIgnore(String path) {
        return properties.getIgnorePaths().stream().anyMatch(path::startsWith);
    }

    /**
     * 记录网关下游错误；异常堆栈只在这里保留一次。
     *
     * @param traceId 链路 ID
     * @param method 请求方法
     * @param path 请求路径
     * @param error 异常
     */
    private void logGatewayError(String traceId, String method, String path, Throwable error) {
        withTraceId(traceId, () -> log.error("event=gateway_error method={} path={}", method, path, error));
    }

    /**
     * 输出访问日志；5xx 使用 ERROR，异常堆栈不重复打印。
     *
     * @param exchange 请求交换上下文
     * @param traceId 链路 ID
     * @param method 请求方法
     * @param path 请求路径
     * @param routeId 路由 ID
     * @param startNanos 开始时间
     * @param error 下游异常，可为空
     */
    private void logAccess(ServerWebExchange exchange, String traceId, String method, String path,
                           String routeId, long startNanos, Throwable error) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        String status = statusCode == null ? (error == null ? "NA" : "500")
                : String.valueOf(statusCode.value());
        String userId = textOrDash(exchange.getAttribute(GatewayConstants.ATTR_USER_ID));
        String clientIp = properties.isLogClientIp() ? resolveClientIp(exchange) : "-";
        String route = textOrDash(routeId);

        if (error != null || (statusCode != null && statusCode.is5xxServerError())) {
            withTraceId(traceId, () -> log.error(
                    "event=gateway_access method={} path={} status={} durationMs={} routeId={} userId={} clientIp={}",
                    method, path, status, durationMs, route, userId, clientIp));
        } else {
            withTraceId(traceId, () -> log.info(
                    "event=gateway_access method={} path={} status={} durationMs={} routeId={} userId={} clientIp={}",
                    method, path, status, durationMs, route, userId, clientIp));
        }
    }

    /**
     * 解析客户端地址；反向代理场景下记录的是代理地址，可信代理透传另行配置。
     *
     * @param exchange 请求交换上下文
     * @return 客户端地址或占位符
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "-";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 空值统一输出为占位符，保持日志字段固定。
     *
     * @param value 原始值
     * @return 原值或占位符
     */
    private String textOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    /**
     * 只围绕单次同步日志调用设置 MDC，避免响应式线程间串号。
     *
     * @param traceId 链路 ID
     * @param action 日志动作
     */
    private void withTraceId(String traceId, Runnable action) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CommonConstants.MDC_TRACE_ID,
                StringUtils.hasText(traceId) ? traceId : "-")) {
            action.run();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
