package com.atifah.common.security.jwt;

import com.atifah.common.security.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验。当前使用 HS256，生产环境建议切换为 RS256 + 公私钥。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    /**
     * 构造 JWT 处理器并初始化签名密钥。
     *
     * @param properties JWT 配置
     */
    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发访问令牌。
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @return JWT 令牌
     */
    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌，返回声明信息。
     *
     * @param token JWT 令牌
     * @return 令牌声明
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从令牌中提取用户 ID。
     *
     * @param token JWT 令牌
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }
}
