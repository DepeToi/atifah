package com.atifah.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证业务配置，对应 atifah.auth.*。
 * 阈值与有效期只从配置读取，禁止在代码中硬编码。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "atifah.auth")
public class AuthProperties {

    /** Refresh Token 有效期，默认 7 天。 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** 已撤销令牌的重用宽限期，默认 30 秒；超过宽限期再次使用视为泄露。 */
    private Duration refreshReuseGrace = Duration.ofSeconds(30);

    /** 登录失败锁定阈值，默认 5 次。 */
    private int maxLoginFailures = 5;

    /** 登录失败计数窗口，默认 10 分钟。 */
    private Duration loginFailureWindow = Duration.ofMinutes(10);
}
