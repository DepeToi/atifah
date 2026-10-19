package com.atifah.common.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。生产环境请通过 KMS/环境变量注入 RSA 密钥或至少 256 位的强随机密钥。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "atifah.security.jwt")
public class JwtProperties {

    /** 签名密钥，生产环境必须替换为强随机值。 */
    private String secret = "atifah-dev-secret-change-me-please-32bytes";

    /** 令牌签发者。 */
    private String issuer = "atifah";

    /** 访问令牌有效期；短 TTL 降低令牌泄露后的影响面。 */
    private Duration accessTokenTtl = Duration.ofMinutes(15);
}
