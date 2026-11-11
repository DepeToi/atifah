package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.entity.AuthRefreshToken;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.mapper.AuthRefreshTokenMapper;
import com.atifah.auth.vo.AuthToken;
import com.atifah.common.security.config.JwtProperties;
import com.atifah.common.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录令牌签发单元测试，不依赖 MySQL / Redis / Nacos。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LoginTokenWriterTest {

    private static final String PHONE = "13800005678";

    private static final String MASKED_PHONE = "138****5678";

    private static final long USER_ID = 42L;

    private static final long ACCOUNT_ID = 11L;

    @Mock
    private AuthRefreshTokenMapper authRefreshTokenMapper;

    @Mock
    private AuthAccountMapper authAccountMapper;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private LoginTokenWriter tokenWriter;

    @BeforeEach
    void setUp() {
        tokenWriter = new LoginTokenWriter(authRefreshTokenMapper, authAccountMapper,
                new TokenFactory(jwtTokenProvider, new JwtProperties(), new AuthProperties()), new AuthProperties());
    }

    @Test
    void issueStoresOnlyHashAndReturnsTokens() {
        AuthAccount account = buildAccount();
        when(jwtTokenProvider.generateToken(USER_ID, MASKED_PHONE)).thenReturn("jwt-access-token");

        AuthToken token = tokenWriter.issue(account);

        assertEquals(String.valueOf(USER_ID), token.userId());
        assertEquals("jwt-access-token", token.accessToken());
        assertEquals("Bearer", token.tokenType());
        assertEquals(Duration.ofMinutes(15).toSeconds(), token.expiresIn());
        assertEquals(Duration.ofDays(7).toSeconds(), token.refreshExpiresIn());
        assertEquals(43, token.refreshToken().length(), "256 位随机数的 Base64URL 无填充长度为 43");

        ArgumentCaptor<AuthRefreshToken> captor = ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(authRefreshTokenMapper).insert(captor.capture());
        AuthRefreshToken stored = captor.getValue();
        assertNotNull(stored.getId());
        assertEquals(USER_ID, stored.getUserId());
        assertEquals(sha256(token.refreshToken()), stored.getTokenHash());
        assertNotEquals(token.refreshToken(), stored.getTokenHash(), "数据库不应保存原始 Refresh Token");
        assertTrue(stored.getExpiresAt().isAfter(LocalDateTime.now().plusDays(7).minusMinutes(1)));
        assertTrue(stored.getExpiresAt().isBefore(LocalDateTime.now().plusDays(7).plusMinutes(1)));
    }

    @Test
    void issueUpdatesLastLoginAtInSameCall() {
        AuthAccount account = buildAccount();

        tokenWriter.issue(account);

        verify(authAccountMapper).updateLastLoginAt(eq(ACCOUNT_ID), any(LocalDateTime.class));
    }

    @Test
    void issueUsesMaskedPhoneInAccessToken() {
        AuthAccount account = buildAccount();

        tokenWriter.issue(account);

        verify(jwtTokenProvider).generateToken(USER_ID, MASKED_PHONE);
    }

    private AuthAccount buildAccount() {
        AuthAccount account = new AuthAccount();
        account.setId(ACCOUNT_ID);
        account.setUserId(USER_ID);
        account.setPhone(PHONE);
        return account;
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
