package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.redis.constant.CacheKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private StringRedisTemplate stringRedisTemplate;

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
    void acquireAttemptRunsSingleAtomicScriptWithWindowMillis() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        guard.acquireAttempt(PHONE);

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), eq(List.of(guard.buildKey(PHONE))),
                argCaptor.capture());

        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("INCR"), "计数必须走原子脚本");
        assertTrue(script.contains("PEXPIRE"), "首次计数必须在同一脚本内设置 TTL");
        assertEquals(String.valueOf(Duration.ofMinutes(10).toMillis()), argCaptor.getValue());
        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void acquireAttemptAllowsFirstFiveAttempts() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(5L);

        assertDoesNotThrow(() -> guard.acquireAttempt(PHONE));
    }

    @Test
    void acquireAttemptRejectsAttemptAboveThreshold() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(6L);

        BusinessException ex = assertThrows(BusinessException.class, () -> guard.acquireAttempt(PHONE));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        assertEquals("登录失败次数过多，请稍后再试", ex.getMessage());
    }

    @Test
    void acquireAttemptPropagatesRedisFailure() {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(IllegalStateException.class, () -> guard.acquireAttempt(PHONE));
    }

    @Test
    void clearDeletesCounterKey() {
        guard.clear(PHONE);

        verify(stringRedisTemplate).delete(guard.buildKey(PHONE));
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
