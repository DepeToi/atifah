package com.atifah.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证服务本地配置。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "atifah.auth")
public class AuthProperties {

    /** 刷新令牌有效期，默认 7 天。 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** 登录失败次数阈值，默认 5 次。 */
    private int maxLoginFailures = 5;

    /** 登录失败计数窗口，默认 10 分钟。 */
    private Duration loginFailureWindow = Duration.ofMinutes(10);

    /** 注册幂等键有效期，默认 24 小时。 */
    private Duration idempotentTtl = Duration.ofHours(24);
}
