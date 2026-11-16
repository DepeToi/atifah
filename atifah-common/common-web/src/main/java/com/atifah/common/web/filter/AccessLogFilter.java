package com.atifah.common.web.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.web.config.AccessLogProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 记录一请求一行的访问日志：方法、路径、状态、耗时与用户 ID。
 * 不记录 query、请求体、响应体和 Header，避免敏感信息进入日志。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return properties.getIgnorePaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            if (!request.isAsyncStarted()) {
                logAccess(request, response, startNanos, failure);
            }
        }
    }

    /**
     * 输出访问日志；5xx 使用 ERROR，异常堆栈由 GlobalExceptionHandler 负责。
     *
     * @param request 请求
     * @param response 响应
     * @param startNanos 开始时间
     * @param failure 未处理的异常，可为空
     */
    private void logAccess(HttpServletRequest request, HttpServletResponse response,
                           long startNanos, Throwable failure) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        int status = failure != null
                ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                : response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String userId = textOrDash(MDC.get(CommonConstants.MDC_USER_ID));
        String remoteIp = properties.isLogRemoteIp() ? resolveRemoteIp(request) : "-";

        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            log.error("event=access method={} path={} status={} durationMs={} userId={} remoteIp={}",
                    method, path, status, durationMs, userId, remoteIp);
        } else {
            log.info("event=access method={} path={} status={} durationMs={} userId={} remoteIp={}",
                    method, path, status, durationMs, userId, remoteIp);
        }
    }

    /**
     * 解析上游地址；业务服务在网关之后，该值通常是网关地址。
     *
     * @param request 请求
     * @return 上游地址或占位符
     */
    private String resolveRemoteIp(HttpServletRequest request) {
        return textOrDash(request.getRemoteAddr());
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
}
