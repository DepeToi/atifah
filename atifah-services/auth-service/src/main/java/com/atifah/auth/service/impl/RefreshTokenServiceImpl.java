package com.atifah.auth.service.impl;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.service.RefreshTokenService;
import com.atifah.common.redis.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 刷新令牌实现：只存 SHA-256 哈希，原始令牌不出现在 Redis、日志或数据库。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    /** 原始令牌字节数。 */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    @Override
    public String issue(Long userId) {
        byte[] raw = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String key = CacheKeys.AUTH_REFRESH.formatted(hash(rawToken));
        stringRedisTemplate.opsForValue().set(key, String.valueOf(userId), authProperties.getRefreshTokenTtl());
        return rawToken;
    }

    @Override
    public void revoke(String rawToken) {
        stringRedisTemplate.delete(CacheKeys.AUTH_REFRESH.formatted(hash(rawToken)));
    }

    @Override
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
