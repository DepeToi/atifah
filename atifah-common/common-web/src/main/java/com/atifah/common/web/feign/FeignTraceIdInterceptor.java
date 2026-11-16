package com.atifah.common.web.feign;

import com.atifah.common.core.constant.CommonConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 将入口请求的 TraceId 与用户身份透传到 OpenFeign 下游，保证跨服务链路连续。
 * 不转发 Authorization、Idempotency-Key 等入口专用头。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public class FeignTraceIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        copyTraceId(request, template);
        copyHeader(request, template, CommonConstants.HEADER_USER_ID);
        copyHeader(request, template, CommonConstants.HEADER_USERNAME);
    }

    /**
     * 复制 TraceId；请求头缺失时回退到 MDC（直接调用服务、网关未透传等场景）。
     *
     * @param request 当前入口请求
     * @param template Feign 请求模板
     */
    private void copyTraceId(HttpServletRequest request, RequestTemplate template) {
        if (template.headers().containsKey(CommonConstants.HEADER_TRACE_ID)) {
            return;
        }
        String traceId = request.getHeader(CommonConstants.HEADER_TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            traceId = MDC.get(CommonConstants.MDC_TRACE_ID);
        }
        if (StringUtils.hasText(traceId)) {
            template.header(CommonConstants.HEADER_TRACE_ID, traceId);
        }
    }

    /**
     * 复制请求头；下游模板已显式设置时不覆盖。
     *
     * @param request 当前入口请求
     * @param template Feign 请求模板
     * @param headerName 请求头名称
     */
    private void copyHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        if (template.headers().containsKey(headerName)) {
            return;
        }
        String value = request.getHeader(headerName);
        if (StringUtils.hasText(value)) {
            template.header(headerName, value);
        }
    }
}
