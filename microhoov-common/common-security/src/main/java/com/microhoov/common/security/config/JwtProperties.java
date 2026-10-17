package com.microhoov.common.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。生产环境请通过 KMS/环境变量注入 RSA 密钥或至少 256 位的强随机密钥。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "microhoov.security.jwt")
public class JwtProperties {

    private String secret = "microhoov-dev-secret-change-me-please-32bytes";
    private String issuer = "microhoov";
    private Duration accessTokenTtl = Duration.ofHours(2);
}
