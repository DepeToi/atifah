package com.atifah.common.web.filter;

import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.core.trace.TraceIdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 为每个请求生成/校验 TraceId，写入 MDC 与响应头，便于链路追踪。
 * 用户 ID 只接受安全字符，避免日志注入。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 用户 ID 安全字符与长度限制。 */
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdUtils.normalizeOrGenerate(
                request.getHeader(CommonConstants.HEADER_TRACE_ID));
        MDC.put(CommonConstants.MDC_TRACE_ID, traceId);
        response.setHeader(CommonConstants.HEADER_TRACE_ID, traceId);

        String userId = request.getHeader(CommonConstants.HEADER_USER_ID);
        if (userId != null && USER_ID_PATTERN.matcher(userId).matches()) {
            MDC.put(CommonConstants.MDC_USER_ID, userId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CommonConstants.MDC_TRACE_ID);
            MDC.remove(CommonConstants.MDC_USER_ID);
        }
    }
}
