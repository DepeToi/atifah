package com.atifah.auth.service;

import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.vo.AuthToken;
import com.atifah.common.security.config.JwtProperties;
import com.atifah.common.security.jwt.JwtTokenProvider;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 令牌工厂单元测试：随机令牌、哈希与 Access Token 组装。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class TokenFactoryTest {

    private static final String PHONE = "13800005678";

    private static final String MASKED_PHONE = "138****5678";

    private static final long USER_ID = 42L;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private TokenFactory tokenFactory;

    @BeforeEach
    void setUp() {
        tokenFactory = new TokenFactory(jwtTokenProvider, new JwtProperties(), new AuthProperties());
    }

    @Test
    void newRefreshTokenIsBase64UrlOf256Bits() {
        String first = tokenFactory.newRefreshToken();
        String second = tokenFactory.newRefreshToken();

        assertEquals(43, first.length(), "256 位随机数的 Base64URL 无填充长度为 43");
        assertTrue(first.matches("^[A-Za-z0-9_-]{43}$"), "只应包含 Base64URL 字符");
        assertNotEquals(first, second, "每次签发都必须是新令牌");
    }

    @Test
    void hashReturnsLowercaseSha256Hex() {
        assertEquals(sha256("atifah"), tokenFactory.hash("atifah"));
        assertEquals(64, tokenFactory.hash("atifah").length());
    }

    @Test
    void buildAuthTokenUsesMaskedPhoneAndConfiguredTtl() {
        when(jwtTokenProvider.generateToken(USER_ID, MASKED_PHONE)).thenReturn("jwt-access-token");

        AuthToken token = tokenFactory.buildAuthToken(buildAccount(), "raw-refresh-token");

        assertEquals(String.valueOf(USER_ID), token.userId());
        assertEquals("jwt-access-token", token.accessToken());
        assertEquals("raw-refresh-token", token.refreshToken());
        assertEquals("Bearer", token.tokenType());
        assertEquals(Duration.ofMinutes(15).toSeconds(), token.expiresIn());
        assertEquals(Duration.ofDays(7).toSeconds(), token.refreshExpiresIn());
    }

    private AuthAccount buildAccount() {
        AuthAccount account = new AuthAccount();
        account.setId(11L);
        account.setUserId(USER_ID);
        account.setPhone(PHONE);
        return account;
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
