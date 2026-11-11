package com.atifah.auth.service;

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
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册幂等组件单元测试。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AuthIdempotencyGuardTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private static final String PHONE = "13800005678";

    private static final String OTHER_PHONE = "13900001234";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AuthIdempotencyGuard(stringRedisTemplate);
    }

    @Test
    void validateAcceptsUuid() {
        guard.validate(IDEMPOTENCY_KEY);
    }

    @Test
    void validateRejectsMissingKey() {
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.validate(null));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void validateRejectsInvalidFormat() {
        BusinessException ex = assertThrows(BusinessException.class, () -> guard.validate("not-a-uuid"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void buildKeyUsesRegisteredCacheKeyTemplate() {
        assertEquals(String.format(CacheKeys.IDEMPOTENT, "auth:register", IDEMPOTENCY_KEY),
                guard.buildKey(IDEMPOTENCY_KEY));
    }

    @Test
    void saveStoresFingerprintAndUserIdWithTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        guard.save(IDEMPOTENCY_KEY, PHONE, 42L);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(guard.buildKey(IDEMPOTENCY_KEY)), valueCaptor.capture(),
                eq(Duration.ofHours(24)));
        String value = valueCaptor.getValue();
        assertTrue(value.startsWith(sha256(PHONE) + ":"));
        assertEquals(sha256(PHONE) + ":42", value);
    }

    @Test
    void findUserIdReturnsReplayResultForSamePhone() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(guard.buildKey(IDEMPOTENCY_KEY))).thenReturn(sha256(PHONE) + ":42");

        Optional<Long> userId = guard.findUserId(IDEMPOTENCY_KEY, PHONE);

        assertTrue(userId.isPresent());
        assertEquals(42L, userId.get());
    }

    @Test
    void findUserIdRejectsDifferentPhone() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(guard.buildKey(IDEMPOTENCY_KEY))).thenReturn(sha256(PHONE) + ":42");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.findUserId(IDEMPOTENCY_KEY, OTHER_PHONE));

        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void findUserIdReturnsEmptyWhenNoRecord() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        assertFalse(guard.findUserId(IDEMPOTENCY_KEY, PHONE).isPresent());
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
