package com.atifah.common.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务服务访问日志配置。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "atifah.logging.access")
public class AccessLogProperties {

    /** 是否启用访问日志。 */
    private boolean enabled = true;

    /** 不记录访问日志的路径前缀。 */
    private List<String> ignorePaths = new ArrayList<>(List.of("/actuator/", "/error"));

    /** 是否记录上游地址；业务服务在网关之后，该值通常是网关地址，可按合规要求关闭。 */
    private boolean logRemoteIp = true;
}
