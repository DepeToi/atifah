package com.microhoov.common.web.config;

import com.microhoov.common.web.filter.TraceIdFilter;
import com.microhoov.common.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Web 公共能力自动装配，业务服务无需额外扫描公共包。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class})
public class CommonWebAutoConfiguration {
}
