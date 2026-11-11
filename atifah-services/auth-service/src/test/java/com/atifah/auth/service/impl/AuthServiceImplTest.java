package com.atifah.auth.service.impl;

import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.service.AuthIdempotencyGuard;
import com.atifah.auth.vo.RegisterResult;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册业务单元测试，不依赖 MySQL / Redis / Nacos。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PHONE = "13800005678";

    private static final String PASSWORD = "Passw0rd123";

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    @Mock
    private AuthAccountMapper authAccountMapper;

    @Mock
    private AuthIdempotencyGuard idempotencyGuard;

    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(authAccountMapper, passwordEncoder, idempotencyGuard);
    }

    @Test
    void registerSuccessStoresBcryptHashAndIdempotentResult() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE)).thenReturn(Optional.empty());
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(null);
        when(authAccountMapper.insert(any(AuthAccount.class))).thenReturn(1);

        RegisterResult result = authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY);

        assertNotNull(result.userId());
        ArgumentCaptor<AuthAccount> captor = ArgumentCaptor.forClass(AuthAccount.class);
        verify(authAccountMapper).insert(captor.capture());
        AuthAccount inserted = captor.getValue();
        assertNotEquals(PASSWORD, inserted.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, inserted.getPasswordHash()));
        assertEquals(PHONE, inserted.getPhone());
        assertEquals(0, inserted.getStatus());
        assertNotNull(inserted.getId());
        assertNotNull(inserted.getUserId());
        verify(idempotencyGuard).save(IDEMPOTENCY_KEY, PHONE, inserted.getUserId());
    }

    @Test
    void registerReplayReturnsFirstUserIdWithoutInsert() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE)).thenReturn(Optional.of(42L));

        RegisterResult result = authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY);

        assertEquals("42", result.userId());
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
        verify(authAccountMapper, never()).selectByPhone(PHONE);
    }

    @Test
    void registerWithReusedIdempotencyKeyReturnsConflict() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT.getCode(), "Idempotency-Key 已被其他请求使用"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }

    @Test
    void registerExistingPhoneReturnsConflict() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE)).thenReturn(Optional.empty());
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(new AuthAccount());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        assertEquals("手机号已注册", ex.getMessage());
        assertTrue(!ex.getMessage().contains(PHONE));
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }

    @Test
    void registerDuplicateKeyRecoversFirstResult() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(77L));
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(null);
        when(authAccountMapper.insert(any(AuthAccount.class))).thenThrow(new DuplicateKeyException("duplicate"));

        RegisterResult result = authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY);

        assertEquals("77", result.userId());
        verify(idempotencyGuard, times(3)).findUserId(IDEMPOTENCY_KEY, PHONE);
    }

    @Test
    void registerDuplicateKeyWithoutIdempotentResultReturnsConflict() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE)).thenReturn(Optional.empty());
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(null);
        when(authAccountMapper.insert(any(AuthAccount.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        verify(idempotencyGuard, times(4)).findUserId(IDEMPOTENCY_KEY, PHONE);
        verify(idempotencyGuard, never()).save(any(), any(), any());
    }

    @Test
    void registerPropagatesRedisFailure() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(IllegalStateException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }
}
