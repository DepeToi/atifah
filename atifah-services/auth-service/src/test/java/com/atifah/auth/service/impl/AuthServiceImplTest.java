package com.atifah.auth.service.impl;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.enums.AuthAccountStatus;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.service.LoginAttemptService;
import com.atifah.auth.service.RefreshTokenService;
import com.atifah.auth.vo.AuthTokenVO;
import com.atifah.auth.vo.RegisterResponse;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.security.config.JwtProperties;
import com.atifah.common.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final String PASSWORD = "Passw0rd1";

    @Mock
    private AuthAccountMapper authAccountMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private LoginAttemptService loginAttemptService;

    private final AuthProperties authProperties = new AuthProperties();

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        authService = new AuthServiceImpl(authAccountMapper, stringRedisTemplate, passwordEncoder,
                jwtTokenProvider, jwtProperties, refreshTokenService, loginAttemptService, authProperties);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setPhone(PHONE);
        request.setPassword(PASSWORD);
        return request;
    }

    private AuthAccount account(long userId, int status, String hash) {
        AuthAccount account = new AuthAccount();
        account.setId(1L);
        account.setUserId(userId);
        account.setPhone(PHONE);
        account.setStatus(status);
        account.setPasswordHash(hash);
        return account;
    }

    @Test
    void register_success_returnsUserId_andStoresEncodedPassword() {
        when(authAccountMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$encoded");

        RegisterResponse response = authService.register(registerRequest(), "idem-1");

        assertThat(response.getUserId()).isNotBlank();
        verify(authAccountMapper).insert(any(AuthAccount.class));
        verify(valueOperations).set(anyString(), eq(response.getUserId()), eq(authProperties.getIdempotentTtl()));
    }

    @Test
    void register_duplicatePhone_throws40900() {
        when(authAccountMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(registerRequest(), "idem-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("手机号已注册")
                .extracting("code").isEqualTo(40900);
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }

    @Test
    void register_sameIdempotencyKey_returnsFirstUserId() {
        when(valueOperations.get(anyString())).thenReturn("1234567890123456789");

        RegisterResponse response = authService.register(registerRequest(), "idem-3");

        assertThat(response.getUserId()).isEqualTo("1234567890123456789");
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }

    @Test
    void register_missingIdempotencyKey_throws40000() {
        assertThatThrownBy(() -> authService.register(registerRequest(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("缺少 Idempotency-Key")
                .extracting("code").isEqualTo(40000);
        verify(authAccountMapper, never()).insert(any(AuthAccount.class));
    }

    @Test
    void register_duplicateKeyOnInsert_throws40900() {
        when(authAccountMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("$2a$10$encoded");
        when(authAccountMapper.insert(any(AuthAccount.class))).thenThrow(new DuplicateKeyException("uk_auth_account_phone"));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> authService.register(registerRequest(), "idem-4"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("手机号已注册")
                .extracting("code").isEqualTo(40900);
    }

    @Test
    void login_success_returnsTokens() {
        when(authAccountMapper.selectOne(any())).thenReturn(account(123L, AuthAccountStatus.NORMAL.getCode(), "hash"));
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(jwtTokenProvider.generateToken(123L, "123")).thenReturn("access-token");
        when(refreshTokenService.issue(123L)).thenReturn("refresh-token");
        when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        AuthTokenVO vo = authService.login(loginRequest());

        assertThat(vo.getAccessToken()).isEqualTo("access-token");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(vo.getTokenType()).isEqualTo("Bearer");
        assertThat(vo.getExpiresIn()).isEqualTo(900L);
        assertThat(vo.getUserId()).isEqualTo("123");
        verify(loginAttemptService).clear(PHONE);
        verify(refreshTokenService).issue(123L);
    }

    @Test
    void login_phoneNotFound_throws40100_andRecordsFailure() {
        when(authAccountMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("手机号或密码错误")
                .extracting("code").isEqualTo(40100);
        verify(loginAttemptService).recordFailure(PHONE);
    }

    @Test
    void login_wrongPassword_throws40100_sameMessage() {
        when(authAccountMapper.selectOne(any())).thenReturn(account(123L, AuthAccountStatus.NORMAL.getCode(), "hash"));
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("手机号或密码错误")
                .extracting("code").isEqualTo(40100);
        verify(loginAttemptService).recordFailure(PHONE);
    }

    @Test
    void login_disabledAccount_throws40300_withoutRecordingFailure() {
        when(authAccountMapper.selectOne(any())).thenReturn(account(123L, AuthAccountStatus.DISABLED.getCode(), "hash"));
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号已禁用")
                .extracting("code").isEqualTo(40300);
        verify(loginAttemptService, never()).recordFailure(PHONE);
    }

    @Test
    void login_tooManyFailures_throws42900() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), "登录失败次数过多，请稍后再试"))
                .when(loginAttemptService).assertAllowed(PHONE);

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录失败次数过多，请稍后再试")
                .extracting("code").isEqualTo(42900);
        verify(authAccountMapper, never()).selectOne(any());
    }
}
