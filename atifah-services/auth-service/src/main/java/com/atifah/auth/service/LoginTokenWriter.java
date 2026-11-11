package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.entity.AuthRefreshToken;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.mapper.AuthRefreshTokenMapper;
import com.atifah.auth.vo.AuthToken;
import com.atifah.common.security.config.JwtProperties;
import com.atifah.common.security.jwt.JwtTokenProvider;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 登录令牌签发。
 * Refresh Token 原始值只返回客户端，数据库只保存 SHA-256 哈希；
 * 插入会话与更新最后登录时间在同一事务内，Redis 操作留在事务之外（DB-04）。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class LoginTokenWriter {

    /** Refresh Token 随机位数：256 位。 */
    private static final int REFRESH_TOKEN_BYTES = 32;

    /** 令牌类型，契约固定 Bearer。 */
    private static final String TOKEN_TYPE = "Bearer";

    /** 手机号脱敏保留的前 3 位。 */
    private static final int PHONE_PREFIX_LENGTH = 3;

    /** 手机号脱敏保留的后 4 位。 */
    private static final int PHONE_SUFFIX_LENGTH = 4;

    /** 不支持无参构造的安全随机源，进程内复用。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthRefreshTokenMapper authRefreshTokenMapper;

    private final AuthAccountMapper authAccountMapper;

    private final JwtTokenProvider jwtTokenProvider;

    private final JwtProperties jwtProperties;

    private final AuthProperties authProperties;

    /**
     * 签发 Access Token 与 Refresh Token，并记录本次会话。
     *
     * @param account 已通过密码校验的账号
     * @return 返回给客户端的令牌
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthToken issue(AuthAccount account) {
        String refreshToken = generateRefreshToken();
        LocalDateTime issuedAt = LocalDateTime.now();

        AuthRefreshToken session = new AuthRefreshToken();
        session.setId(IdWorker.getId());
        session.setUserId(account.getUserId());
        session.setTokenHash(sha256(refreshToken));
        session.setExpiresAt(issuedAt.plus(authProperties.getRefreshTokenTtl()));
        authRefreshTokenMapper.insert(session);

        authAccountMapper.updateLastLoginAt(account.getId(), issuedAt);

        String accessToken = jwtTokenProvider.generateToken(account.getUserId(), maskPhone(account.getPhone()));
        return new AuthToken(
                String.valueOf(account.getUserId()),
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                (int) jwtProperties.getAccessTokenTtl().toSeconds(),
                (int) authProperties.getRefreshTokenTtl().toSeconds());
    }

    /**
     * 生成 256 位随机令牌，Base64URL 无填充编码。
     *
     * @return 原始 Refresh Token
     */
    private String generateRefreshToken() {
        byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 计算令牌的 SHA-256 十六进制摘要，落库前调用。
     *
     * @param token 原始令牌
     * @return 小写十六进制摘要
     */
    private String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    /**
     * 手机号脱敏为 138****5678 形式，用于 JWT username 声明。
     *
     * @param phone 手机号
     * @return 脱敏手机号
     */
    private String maskPhone(String phone) {
        int suffixStart = phone.length() - PHONE_SUFFIX_LENGTH;
        return phone.substring(0, PHONE_PREFIX_LENGTH) + "****" + phone.substring(suffixStart);
    }
}
