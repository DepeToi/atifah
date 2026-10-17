package com.microhoov.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "microhoov.gateway.auth")
public class GatewayAuthProperties {

    /**
     * 本地骨架默认关闭，接入 auth-service 后开启。
     */
    private boolean enabled = false;

    private List<String> whitelist = new ArrayList<>();
}
