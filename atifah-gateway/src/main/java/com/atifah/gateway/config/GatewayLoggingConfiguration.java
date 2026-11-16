package com.atifah.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 网关日志配置装配。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayAccessLogProperties.class)
public class GatewayLoggingConfiguration {
}
