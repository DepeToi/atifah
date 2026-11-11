package com.atifah.auth.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.service.AuthIdempotencyGuard;
import com.atifah.auth.service.LoginFailureGuard;
import com.atifah.auth.service.LoginTokenWriter;
import com.atifah.auth.vo.AuthToken;
import com.atifah.auth.vo.RegisterResult;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册与登录业务单元测试，不依赖 MySQL / Redis / Nacos。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PHONE = "13800005678";

    private static final String PASSWORD = "Passw0rd123";

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private static final long USER_ID = 42L;

    @Mock
    private AuthAccountMapper authAccountMapper;

    @Mock
    private AuthIdempotencyGuard idempotencyGuard;

    @Mock
    private LoginFailureGuard loginFailureGuard;

    @Mock
    private LoginTokenWriter loginTokenWriter;

    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    private Logger authServiceLogger;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        passwordEncoder = spy(new BCryptPasswordEncoder());
        authService = new AuthServiceImpl(authAccountMapper, passwordEncoder, idempotencyGuard,
                loginFailureGuard, loginTokenWriter);
        authServiceLogger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        authServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        authServiceLogger.detachAppender(logAppender);
        logAppender.stop();
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
        assertNoSensitiveLog();
    }

    @Test
    void registerReplayReturnsFirstUserIdWithoutInsert() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE)).thenReturn(Optional.of(42L));

        RegisterResult result = authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY);

        assertEquals("42", result.userId());
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
        verify(authAccountMapper, never()).selectByPhone(PHONE);
        assertNoSensitiveLog();
    }

    @Test
    void registerWithReusedIdempotencyKeyReturnsConflict() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT.getCode(), "Idempotency-Key 已被其他请求使用"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
        assertNoSensitiveLog();
    }

    @Test
    void registerExistingPhoneReturnsConflict() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE)).thenReturn(Optional.empty());
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(new AuthAccount());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
        assertEquals("手机号已注册", ex.getMessage());
        assertFalse(ex.getMessage().contains(PHONE));
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
        assertNoSensitiveLog();
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
        assertNoSensitiveLog();
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
        assertNoSensitiveLog();
    }

    @Test
    void registerPropagatesRedisFailure() {
        when(idempotencyGuard.findUserId(IDEMPOTENCY_KEY, PHONE))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(IllegalStateException.class,
                () -> authService.register(new RegisterRequest(PHONE, PASSWORD), IDEMPOTENCY_KEY));

        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }

    @Test
    void loginSuccessClearsFailuresAndReturnsToken() {
        AuthAccount account = buildAccount(PASSWORD, 0);
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(account);
        AuthToken expected = new AuthToken(String.valueOf(USER_ID), "access", "refresh", "Bearer", 900, 604800);
        when(loginTokenWriter.issue(account)).thenReturn(expected);

        AuthToken token = authService.login(new LoginRequest(PHONE, PASSWORD));

        assertEquals(expected, token);
        verify(loginFailureGuard).clear(PHONE);
        verify(loginFailureGuard, never()).recordFailure(anyString());
        assertNoSensitiveLog();
    }

    @Test
    void loginWithUnknownPhoneRunsDummyCompareAndReturnsUnauthorized() {
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(PHONE, PASSWORD)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("手机号或密码错误", ex.getMessage());
        assertFalse(ex.getMessage().contains(PHONE));
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(eq(PASSWORD), hashCaptor.capture());
        assertTrue(hashCaptor.getValue().startsWith("$2"), "账号不存在时也必须执行一次 BCrypt 比对");
        verify(loginFailureGuard).recordFailure(PHONE);
        verify(loginTokenWriter, never()).issue(any(AuthAccount.class));
        assertNoSensitiveLog();
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorizedAndCountsFailure() {
        AuthAccount account = buildAccount("OtherPassw0rd", 0);
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(account);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(PHONE, PASSWORD)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("手机号或密码错误", ex.getMessage());
        verify(loginFailureGuard).recordFailure(PHONE);
        verify(loginFailureGuard, never()).clear(anyString());
        verify(loginTokenWriter, never()).issue(any(AuthAccount.class));
        assertNoSensitiveLog();
    }

    @Test
    void loginWithDisabledAccountReturnsForbidden() {
        AuthAccount account = buildAccount(PASSWORD, 1);
        when(authAccountMapper.selectByPhone(PHONE)).thenReturn(account);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(PHONE, PASSWORD)));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("账号已禁用", ex.getMessage());
        verify(loginFailureGuard).clear(PHONE);
        verify(loginTokenWriter, never()).issue(any(AuthAccount.class));
        assertNoSensitiveLog();
    }

    @Test
    void loginWhenLockedReturnsTooManyRequestsWithoutQueryingAccount() {
        doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), "登录失败次数过多，请稍后再试"))
                .when(loginFailureGuard).assertNotLocked(PHONE);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest(PHONE, PASSWORD)));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS.getCode(), ex.getCode());
        verify(authAccountMapper, never()).selectByPhone(anyString());
        verify(loginTokenWriter, never()).issue(any(AuthAccount.class));
    }

    @Test
    void loginPropagatesRedisFailureInsteadOfBypassingLock() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(loginFailureGuard).assertNotLocked(PHONE);

        assertThrows(IllegalStateException.class, () -> authService.login(new LoginRequest(PHONE, PASSWORD)));

        verify(authAccountMapper, never()).selectByPhone(anyString());
    }

    private AuthAccount buildAccount(String rawPassword, int status) {
        AuthAccount account = new AuthAccount();
        account.setId(11L);
        account.setUserId(USER_ID);
        account.setPhone(PHONE);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setStatus(status);
        return account;
    }

    /**
     * 断言认证链路日志不含手机号与密码明文。
     */
    private void assertNoSensitiveLog() {
        String logs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(logs.contains(PHONE), "日志不应包含手机号");
        assertFalse(logs.contains(PASSWORD), "日志不应包含密码");
    }
}
