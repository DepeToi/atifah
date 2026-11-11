package com.atifah.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atifah.auth.config.AuthProperties;
import com.atifah.auth.dto.RefreshTokenRequest;
import com.atifah.auth.entity.AuthAccount;
import com.atifah.auth.entity.AuthRefreshToken;
import com.atifah.auth.mapper.AuthAccountMapper;
import com.atifah.auth.mapper.AuthRefreshTokenMapper;
import com.atifah.auth.vo.AuthToken;
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

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 刷新与登出业务单元测试，不依赖 MySQL / Redis / Nacos。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String RAW_TOKEN = "0123456789012345678901234567890123456789012";

    private static final String NEW_TOKEN = "abcdefghijabcdefghijabcdefghijabcdefghijabcde";

    private static final String TOKEN_HASH = "a".repeat(64);

    private static final String NEW_TOKEN_HASH = "b".repeat(64);

    private static final long USER_ID = 42L;

    private static final long SESSION_ID = 100L;

    @Mock
    private AuthRefreshTokenMapper authRefreshTokenMapper;

    @Mock
    private AuthAccountMapper authAccountMapper;

    @Mock
    private TokenFactory tokenFactory;

    @Mock
    private RefreshTokenRevoker refreshTokenRevoker;

    private RefreshTokenService refreshTokenService;

    private Logger serviceLogger;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(authRefreshTokenMapper, authAccountMapper, tokenFactory,
                refreshTokenRevoker, new AuthProperties());
        serviceLogger = (Logger) LoggerFactory.getLogger(RefreshTokenService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void refreshSuccessRotatesToken() {
        AuthAccount account = buildAccount(0);
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), null);
        AuthToken expected = new AuthToken(String.valueOf(USER_ID), "new-access", NEW_TOKEN, "Bearer", 900, 604800);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);
        when(authAccountMapper.selectByUserId(USER_ID)).thenReturn(account);
        when(authRefreshTokenMapper.revoke(eq(SESSION_ID), any(LocalDateTime.class))).thenReturn(1);
        when(tokenFactory.newRefreshToken()).thenReturn(NEW_TOKEN);
        when(tokenFactory.hash(NEW_TOKEN)).thenReturn(NEW_TOKEN_HASH);
        when(tokenFactory.buildAuthToken(account, NEW_TOKEN)).thenReturn(expected);

        AuthToken token = refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN));

        assertEquals(expected, token);
        ArgumentCaptor<AuthRefreshToken> captor = ArgumentCaptor.forClass(AuthRefreshToken.class);
        verify(authRefreshTokenMapper).insert(captor.capture());
        AuthRefreshToken rotated = captor.getValue();
        assertEquals(USER_ID, rotated.getUserId());
        assertEquals(NEW_TOKEN_HASH, rotated.getTokenHash(), "新会话只保存哈希");
        assertTrue(rotated.getExpiresAt().isAfter(LocalDateTime.now().plusDays(7).minusMinutes(1)));
        assertTrue(rotated.getExpiresAt().isBefore(LocalDateTime.now().plusDays(7).plusMinutes(1)));
        verify(refreshTokenRevoker, never()).revokeAllActive(any());
        assertNoSensitiveLog();
    }

    @Test
    void refreshWithUnknownTokenReturnsUnauthorized() {
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(authRefreshTokenMapper, never()).insert(any(AuthRefreshToken.class));
        verify(refreshTokenRevoker, never()).revokeAllActive(any());
        assertNoSensitiveLog();
    }

    @Test
    void refreshWithExpiredTokenReturnsUnauthorized() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().minusSeconds(1), null);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(authRefreshTokenMapper, never()).revoke(any(), any());
        verify(refreshTokenRevoker, never()).revokeAllActive(any());
    }

    @Test
    void refreshWithTokenRevokedWithinGraceReturnsUnauthorized() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), LocalDateTime.now().minusSeconds(10));
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(refreshTokenRevoker, never()).revokeAllActive(any());
        verify(authRefreshTokenMapper, never()).insert(any(AuthRefreshToken.class));
    }

    @Test
    void refreshWithTokenRevokedBeyondGraceRevokesAllActiveTokens() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), LocalDateTime.now().minusSeconds(60));
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(refreshTokenRevoker).revokeAllActive(USER_ID);
        verify(authRefreshTokenMapper, never()).insert(any(AuthRefreshToken.class));
    }

    @Test
    void refreshWhenConcurrentRotationLostReturnsUnauthorized() {
        AuthAccount account = buildAccount(0);
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), null);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);
        when(authAccountMapper.selectByUserId(USER_ID)).thenReturn(account);
        when(authRefreshTokenMapper.revoke(eq(SESSION_ID), any(LocalDateTime.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(authRefreshTokenMapper, never()).insert(any(AuthRefreshToken.class));
        verify(refreshTokenRevoker, never()).revokeAllActive(any());
    }

    @Test
    void refreshWithUnknownAccountReturnsUnauthorized() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), null);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);
        when(authAccountMapper.selectByUserId(USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(authRefreshTokenMapper, never()).revoke(any(), any());
        verify(authRefreshTokenMapper, never()).insert(any(AuthRefreshToken.class));
    }

    @Test
    void refreshWithDisabledAccountReturnsUnauthorized() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), null);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);
        when(authAccountMapper.selectByUserId(USER_ID)).thenReturn(buildAccount(1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> refreshTokenService.refresh(new RefreshTokenRequest(RAW_TOKEN)));

        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(authRefreshTokenMapper, never()).revoke(any(), any());
        verify(authRefreshTokenMapper, never()).insert(any(AuthRefreshToken.class));
    }

    @Test
    void logoutRevokesActiveToken() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), null);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);
        when(authRefreshTokenMapper.revoke(eq(SESSION_ID), any(LocalDateTime.class))).thenReturn(1);

        refreshTokenService.logout(new RefreshTokenRequest(RAW_TOKEN));

        verify(authRefreshTokenMapper).revoke(eq(SESSION_ID), any(LocalDateTime.class));
        assertNoSensitiveLog();
    }

    @Test
    void logoutWithUnknownTokenSucceeds() {
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(null);

        refreshTokenService.logout(new RefreshTokenRequest(RAW_TOKEN));

        verify(authRefreshTokenMapper, never()).revoke(any(), any());
        assertNoSensitiveLog();
    }

    @Test
    void logoutWithRevokedTokenSucceedsWithoutUpdate() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().plusDays(1), LocalDateTime.now().minusSeconds(5));
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);

        refreshTokenService.logout(new RefreshTokenRequest(RAW_TOKEN));

        verify(authRefreshTokenMapper, never()).revoke(any(), any());
        assertNoSensitiveLog();
    }

    @Test
    void logoutWithExpiredTokenSucceedsWithoutUpdate() {
        AuthRefreshToken session = buildSession(LocalDateTime.now().minusSeconds(1), null);
        when(tokenFactory.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(authRefreshTokenMapper.selectByTokenHash(TOKEN_HASH)).thenReturn(session);

        refreshTokenService.logout(new RefreshTokenRequest(RAW_TOKEN));

        verify(authRefreshTokenMapper, never()).revoke(any(), any());
    }

    private AuthRefreshToken buildSession(LocalDateTime expiresAt, LocalDateTime revokedAt) {
        AuthRefreshToken session = new AuthRefreshToken();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setTokenHash(TOKEN_HASH);
        session.setExpiresAt(expiresAt);
        session.setRevokedAt(revokedAt);
        return session;
    }

    private AuthAccount buildAccount(int status) {
        AuthAccount account = new AuthAccount();
        account.setId(11L);
        account.setUserId(USER_ID);
        account.setPhone("13800005678");
        account.setStatus(status);
        return account;
    }

    /**
     * 断言日志不含原始令牌与令牌哈希。
     */
    private void assertNoSensitiveLog() {
        String logs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(logs.contains(RAW_TOKEN), "日志不应包含原始刷新令牌");
        assertFalse(logs.contains(TOKEN_HASH), "日志不应包含令牌哈希");
        assertFalse(logs.contains(NEW_TOKEN), "日志不应包含新签发的刷新令牌");
    }
}
