package com.atifah.auth.service.impl;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.service.LoginAttemptService;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 登录失败计数实现：按手机号 SHA-256 哈希计数，避免手机号明文进入 Redis key。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    @Override
    public void assertAllowed(String phone) {
        String count = stringRedisTemplate.opsForValue().get(failKey(phone));
        if (count == null) {
            return;
        }
        if (Integer.parseInt(count) >= authProperties.getMaxLoginFailures()) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), "登录失败次数过多，请稍后再试");
        }
    }

    @Override
    public void recordFailure(String phone) {
        String key = failKey(phone);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, authProperties.getLoginFailureWindow());
        }
    }

    @Override
    public void clear(String phone) {
        stringRedisTemplate.delete(failKey(phone));
    }

    private String failKey(String phone) {
        return CacheKeys.AUTH_LOGIN_FAIL.formatted(sha256Hex(phone));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
