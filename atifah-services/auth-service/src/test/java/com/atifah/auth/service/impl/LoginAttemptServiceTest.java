package com.atifah.auth.service.impl;

import com.atifah.auth.config.AuthProperties;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String PHONE = "13800138000";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final AuthProperties authProperties = new AuthProperties();

    private LoginAttemptServiceImpl loginAttemptService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        loginAttemptService = new LoginAttemptServiceImpl(stringRedisTemplate, authProperties);
    }

    @Test
    void assertAllowed_noKey_doesNotThrow() {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertThatCode(() -> loginAttemptService.assertAllowed(PHONE)).doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_underThreshold_doesNotThrow() {
        when(valueOperations.get(anyString())).thenReturn("4");
        assertThatCode(() -> loginAttemptService.assertAllowed(PHONE)).doesNotThrowAnyException();
    }

    @Test
    void assertAllowed_atThreshold_throws42900() {
        when(valueOperations.get(anyString())).thenReturn("5");
        assertThatThrownBy(() -> loginAttemptService.assertAllowed(PHONE))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录失败次数过多，请稍后再试")
                .extracting("code").isEqualTo(42900);
    }

    @Test
    void recordFailure_firstFailure_setsTtl() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        loginAttemptService.recordFailure(PHONE);
        verify(valueOperations).increment(anyString());
        verify(stringRedisTemplate).expire(anyString(), eq(authProperties.getLoginFailureWindow()));
    }

    @Test
    void recordFailure_subsequentFailure_doesNotResetTtl() {
        when(valueOperations.increment(anyString())).thenReturn(3L);
        loginAttemptService.recordFailure(PHONE);
        verify(stringRedisTemplate, never()).expire(anyString(), eq(authProperties.getLoginFailureWindow()));
    }

    @Test
    void clear_deletesKey_notContainingPhone() {
        loginAttemptService.clear(PHONE);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).doesNotContain(PHONE);
    }

    @Test
    void recordFailure_keyUsesPhoneSha256() {
        when(valueOperations.increment(anyString())).thenReturn(2L);
        loginAttemptService.recordFailure(PHONE);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("auth:login:fail:");
        assertThat(keyCaptor.getValue()).doesNotContain(PHONE);
    }
}
