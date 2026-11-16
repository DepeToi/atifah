package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import com.atifah.common.redis.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录尝试计数组件单元测试，不依赖 Redis。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoginFailureGuardTest {

    private static final String PHONE = "13800005678";

    @Mock
    private RedisUtil redisUtil;

    private LoginFailureGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LoginFailureGuard(redisUtil, new AuthProperties());
    }

    @Test
    void buildKeyUsesSha256FingerprintWithoutPlainPhone() {
        String key = guard.buildKey(PHONE);

        assertEquals(String.format(CacheKeys.AUTH_LOGIN_FAIL, sha256(PHONE)), key);
        assertFalse(key.contains(PHONE), "Redis Key 不应包含手机号明文");
    }

    @Test
    void acquireAttemptIncrementsCounterWithLoginWindowTtl() {
        when(redisUtil.increment(anyString(), anyLong(), any(Duration.class))).thenReturn(1L);

        guard.acquireAttempt(PHONE);

        verify(redisUtil).increment(guard.buildKey(PHONE), 1L, Duration.ofMinutes(10));
    }

    @Test
    void acquireAttemptAllowsFirstFiveAttempts() {
        when(redisUtil.increment(anyString(), anyLong(), any(Duration.class))).thenReturn(5L);

        assertDoesNotThrow(() -> guard.acquireAttempt(PHONE));
    }

    @Test
    void acquireAttemptRejectsAttemptAboveThreshold() {
        when(redisUtil.increment(anyString(), anyLong(), any(Duration.class))).thenReturn(6L);

        BusinessException ex = assertThrows(BusinessException.class, () -> guard.acquireAttempt(PHONE));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        assertEquals("登录失败次数过多，请稍后再试", ex.getMessage());
    }

    @Test
    void acquireAttemptPropagatesRedisFailure() {
        when(redisUtil.increment(anyString(), anyLong(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(IllegalStateException.class, () -> guard.acquireAttempt(PHONE));
    }

    @Test
    void clearDeletesCounterKey() {
        guard.clear(PHONE);

        verify(redisUtil).delete(guard.buildKey(PHONE));
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
