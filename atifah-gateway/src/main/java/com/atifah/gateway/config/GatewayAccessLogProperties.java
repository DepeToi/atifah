package com.atifah.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关访问日志配置。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "atifah.logging.access")
public class GatewayAccessLogProperties {

    /** 是否启用访问日志。 */
    private boolean enabled = true;

    /** 不记录访问日志的路径前缀。 */
    private List<String> ignorePaths = new ArrayList<>(List.of("/actuator/"));

    /** 是否记录客户端 IP；IP 属于个人信息，可按合规要求关闭。 */
    private boolean logClientIp = true;
}
