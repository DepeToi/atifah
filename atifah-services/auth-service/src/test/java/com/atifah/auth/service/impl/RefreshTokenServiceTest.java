package com.atifah.auth.service.impl;

import com.atifah.auth.config.AuthProperties;
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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 刷新令牌服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final AuthProperties authProperties = new AuthProperties();

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        refreshTokenService = new RefreshTokenServiceImpl(stringRedisTemplate, authProperties);
    }

    @Test
    void issue_storesSha256HashKeyWithUserIdAndTtl() {
        String rawToken = refreshTokenService.issue(123L);

        assertThat(rawToken).isNotBlank();
        byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
        assertThat(decoded).hasSize(32);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq("123"), eq(authProperties.getRefreshTokenTtl()));

        String key = keyCaptor.getValue();
        assertThat(key).startsWith("auth:refresh:");
        assertThat(key).doesNotContain(rawToken);
        assertThat(key).isEqualTo(CacheKeys.AUTH_REFRESH.formatted(refreshTokenService.hash(rawToken)));
    }

    @Test
    void issue_usesConfiguredTtl() {
        authProperties.setRefreshTokenTtl(Duration.ofDays(7));
        refreshTokenService.issue(1L);
        verify(valueOperations).set(anyString(), eq("1"), eq(Duration.ofDays(7)));
    }

    @Test
    void hash_returnsSha256Hex() {
        assertThat(refreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void revoke_deletesHashKey() {
        String rawToken = "some-raw-token";
        refreshTokenService.revoke(rawToken);
        verify(stringRedisTemplate).delete(CacheKeys.AUTH_REFRESH.formatted(refreshTokenService.hash(rawToken)));
    }
}
