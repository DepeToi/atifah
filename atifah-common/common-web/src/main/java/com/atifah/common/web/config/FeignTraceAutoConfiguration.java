package com.atifah.common.web.config;

import com.atifah.common.web.feign.FeignTraceIdInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * OpenFeign TraceId 透传自动装配；classpath 没有 Feign 时不生效。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignTraceAutoConfiguration {

    /**
     * 装配 Feign 请求头透传拦截器。
     *
     * @return Feign 拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public FeignTraceIdInterceptor feignTraceIdInterceptor() {
        return new FeignTraceIdInterceptor();
    }
}
