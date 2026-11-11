package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数组件单元测试，不依赖 Redis。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoginFailureGuardTest {

    private static final String PHONE = "13800005678";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginFailureGuard guard;

    @BeforeEach
    void setUp() {
        guard = new LoginFailureGuard(stringRedisTemplate, new AuthProperties());
    }

    @Test
    void buildKeyUsesSha256FingerprintWithoutPlainPhone() {
        String key = guard.buildKey(PHONE);

        assertEquals(String.format(CacheKeys.AUTH_LOGIN_FAIL, sha256(PHONE)), key);
        assertFalse(key.contains(PHONE), "Redis Key 不应包含手机号明文");
    }

    @Test
    void assertNotLockedAllowsRequestWithoutFailures() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertDoesNotThrow(() -> guard.assertNotLocked(PHONE));
    }

    @Test
    void assertNotLockedAllowsRequestBelowThreshold() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(guard.buildKey(PHONE))).thenReturn("4");

        assertDoesNotThrow(() -> guard.assertNotLocked(PHONE));
    }

    @Test
    void assertNotLockedRejectsRequestAtThreshold() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(guard.buildKey(PHONE))).thenReturn("5");

        BusinessException ex = assertThrows(BusinessException.class, () -> guard.assertNotLocked(PHONE));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        assertEquals("登录失败次数过多，请稍后再试", ex.getMessage());
    }

    @Test
    void recordFailureStartsWindowOnFirstFailure() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(guard.buildKey(PHONE))).thenReturn(1L);

        guard.recordFailure(PHONE);

        verify(stringRedisTemplate).expire(guard.buildKey(PHONE), Duration.ofMinutes(10));
    }

    @Test
    void recordFailureKeepsExistingWindowAfterFirstFailure() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(guard.buildKey(PHONE))).thenReturn(3L);

        guard.recordFailure(PHONE);

        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void clearDeletesCounterKey() {
        guard.clear(PHONE);

        verify(stringRedisTemplate).delete(guard.buildKey(PHONE));
    }

    @Test
    void assertNotLockedPropagatesRedisFailure() {
        when(stringRedisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(IllegalStateException.class, () -> guard.assertNotLocked(PHONE));
        verify(stringRedisTemplate, never()).expire(eq(guard.buildKey(PHONE)), any(Duration.class));
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
