package com.atifah.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "atifah.gateway.auth")
public class GatewayAuthProperties {

    /** 是否启用网关鉴权，本地骨架默认关闭，接入 auth-service 后开启。 */
    private boolean enabled = false;

    /** 免鉴权路径前缀白名单。 */
    private List<String> whitelist = new ArrayList<>();
}
